package com.uastest;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuHandler;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.widgets.WidgetIcon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import com.atakmap.coremap.cot.event.CotEvent;
import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Test: can a separate plugin (1) add a button to UAS Tool's waypoint radial menu and
 * (2) see UAS Tool's route waypoints come and go?
 *
 * UAS Tool draws each waypoint as a b-m-p-w marker in the "UAS Routes" map group, tagged with
 * meta "route_uid" (the route) and "uas_route_id" (the waypoint number).
 *
 * Also dumps everything about UAS-looking items to logcat (adb logcat -s UasTest): every
 * metadata key, the map group path and the CoT ATAK would make for the item. Long-press any
 * other item for an eye button that dumps it too, to compare against.
 */
public class UasTestPlugin implements IPlugin {

    private static final String TAG = "UasTest";
    private static final String META_ROUTE = "route_uid";
    private static final String META_WP = "uas_route_id";
    private static final String[] MODELS = {"vehicle-det v2", "person-det v1", "boat-det v1"};

    private final Context pluginContext;
    private final IHostUIService uiService;
    private final MapView mv;
    private final ToolbarItem toolbarItem;

    // route_uid#wp -> action label. Keyed by waypoint number, not marker UID: UAS Tool
    // recreates its markers on every redraw.
    private final Map<String, String> tags = new HashMap<>();

    private final MapMenuHandler menuHandler = (item, menu) -> {
        Log.i(TAG, "menu for " + (item == null ? "map" : item.getType() + " " + item.getUID())
                + ", " + menu.getChildWidgetCount() + " buttons");
        if (isUasWaypoint(item)) {
            menu.addChildWidget(button(menu, "asset:///icons/target.png", i -> {
                dump("radial ML", i);
                showActionPicker(i);
            }));
        } else if (item != null) {
            menu.addChildWidget(button(menu, "asset:///icons/eye.png", i -> {
                dump("radial dump", i);
                toast("Dumped " + i.getType() + " to logcat");
            }));
        }
    };

    private final MapEventDispatcher.MapEventDispatchListener waypointListener = event -> {
        MapItem item = event.getItem();
        if (!looksUas(item)) return;
        if (MapEvent.ITEM_REMOVED.equals(event.getType()))
            Log.i(TAG, "item_removed " + item.getUID() + " " + item.getType());
        else
            dump(event.getType(), item);
        if (MapEvent.ITEM_DRAG_DROPPED.equals(event.getType()) && isUasWaypoint(item))
            toast("Moved " + describe(item));
    };

    public UasTestPlugin(IServiceController serviceController) {
        pluginContext = serviceController.getService(PluginContextProvider.class).getPluginContext();
        pluginContext.setTheme(R.style.ATAKPluginTheme);
        uiService = serviceController.getService(IHostUIService.class);
        mv = MapView.getMapView();

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showWaypointList();
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        if (uiService == null) return;
        // Run after the core handlers (which are <= 0) so ours is the last word on the menu.
        MapMenuReceiver.getInstance().registerMapMenuHandler(menuHandler, 100);
        for (String type : new String[]{MapEvent.ITEM_ADDED, MapEvent.ITEM_REMOVED,
                MapEvent.ITEM_DRAG_DROPPED})
            mv.getMapEventDispatcher().addMapEventListener(type, waypointListener);
        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        if (uiService == null) return;
        MapMenuReceiver.getInstance().unregisterMapMenuHandler(menuHandler);
        for (String type : new String[]{MapEvent.ITEM_ADDED, MapEvent.ITEM_REMOVED,
                MapEvent.ITEM_DRAG_DROPPED})
            mv.getMapEventDispatcher().removeMapEventListener(type, waypointListener);
        uiService.removeToolbarItem(toolbarItem);
    }

    // --- radial ---

    private MapMenuButtonWidget button(MapMenuWidget menu, String iconUri, Consumer<MapItem> onClick) {
        MapMenuButtonWidget button = new MapMenuButtonWidget(mv.getContext());
        // Same sizing as the sample radialmenudemo: menu defaults, average weight of siblings
        // (ATAK lays the menu out by weight after this handler runs).
        button.setOrientation(button.getOrientationAngle(), menu.getInnerRadius());
        button.setButtonSize(button.getButtonSpan(), menu.getButtonWidth());
        float weight = 0f;
        int buttons = 0;
        for (int i = 0; i < menu.getChildWidgetCount(); i++) {
            if (menu.getChildWidgetAt(i) instanceof MapMenuButtonWidget) {
                weight += ((MapMenuButtonWidget) menu.getChildWidgetAt(i)).getLayoutWeight();
                buttons++;
            }
        }
        button.setLayoutWeight(buttons == 0 ? 1f : weight / buttons);
        // WidgetIcon is deprecated in favour of gov.tak...Icon, but ATAK 5.7's radial renderer
        // (GLMapMenuButtonWidget) still reads getIcon(), which is null for anything else: crash.
        button.setIcon(new WidgetIcon.Builder()
                .setAnchor(16, 16)
                .setSize(32, 32)
                .setImageRef(0, MapDataRef.parseUri(iconUri))
                .build());
        button.setOnButtonClickHandler(new IMapMenuButtonWidget.OnButtonClickHandler() {
            @Override
            public boolean isSupported(Object opaque) {
                return opaque instanceof MapItem;
            }

            @Override
            public void performAction(Object opaque) {
                Log.i(TAG, "button " + iconUri + " clicked on " + ((MapItem) opaque).getUID());
                MapMenuReceiver.getInstance().hideMenu();
                onClick.accept((MapItem) opaque);
            }
        });
        return button;
    }

    private void showActionPicker(MapItem item) {
        String[] actions = {"Start pipeline", "Switch model", "Stop pipeline", "Clear"};
        new AlertDialog.Builder(mv.getContext())
                .setTitle("ML · " + describe(item))
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0: pickModel(item, "START"); break;
                        case 1: pickModel(item, "SWITCH"); break;
                        case 2: setTag(item, "STOP"); break;
                        default: setTag(item, null); break;
                    }
                })
                .show();
    }

    private void pickModel(MapItem item, String verb) {
        new AlertDialog.Builder(mv.getContext())
                .setTitle(verb + " · model")
                .setItems(MODELS, (d, which) -> setTag(item, verb + " " + MODELS[which]))
                .show();
    }

    private void setTag(MapItem item, String action) {
        String key = key(item);
        if (action == null) tags.remove(key);
        else tags.put(key, action);
        Log.i(TAG, "tag " + key + " = " + action);
        toast(describe(item) + " → " + (action == null ? "cleared" : action));
    }

    // --- list ---

    private void showWaypointList() {
        List<String> rows = new ArrayList<>();
        MapGroup routes = MapGroup.deepFindGroupByNameBreadthFirst(mv.getRootGroup(), "UAS Routes");
        if (routes != null) {
            MapGroup.deepMapItems(routes, item -> {
                dump("list", item);
                if (isUasWaypoint(item)) {
                    String tag = tags.get(key(item));
                    rows.add(describe(item) + (tag == null ? "" : "   ▸ " + tag));
                }
                return false;
            });
        }
        if (rows.isEmpty()) rows.add(routes == null ? "No \"UAS Routes\" group yet" : "No waypoints");
        java.util.Collections.sort(rows);
        new AlertDialog.Builder(mv.getContext())
                .setTitle("UAS Tool waypoints (" + tags.size() + " tagged)")
                .setItems(rows.toArray(new String[0]), null)
                .setPositiveButton("OK", null)
                .show();
    }

    // --- dump ---

    /** Everything about an item: identity, group path, all metadata, and its CoT. */
    private static void dump(String reason, MapItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(reason).append(" uid=").append(item.getUID())
                .append(" type=").append(item.getType())
                .append(" class=").append(item.getClass().getName())
                .append(" title=").append(item.getTitle()).append('\n');
        sb.append("group: ").append(groupPath(item)).append('\n');
        for (String k : new TreeSet<>(item.getAttributeNames())) {
            Object v = item.get(k);
            if (v instanceof int[]) v = Arrays.toString((int[]) v);
            else if (v instanceof Object[]) v = Arrays.toString((Object[]) v);
            sb.append("meta ").append(k).append(" = ").append(v)
                    .append(v == null ? "" : "  (" + v.getClass().getSimpleName() + ")").append('\n');
        }
        try {
            CotEvent cot = CotEventFactory.createCotEvent(item);
            sb.append("cot: ").append(cot == null ? "(none)" : cot.toString()).append('\n');
        } catch (Exception e) {
            sb.append("cot: failed ").append(e).append('\n');
        }
        sb.append("=== end ").append(item.getUID());
        // logcat truncates long lines; log line by line, splitting any very long ones.
        for (String line : sb.toString().split("\n"))
            for (int i = 0; i < line.length(); i += 3000)
                Log.i(TAG, line.substring(i, Math.min(line.length(), i + 3000)));
    }

    private static String groupPath(MapItem item) {
        StringBuilder path = new StringBuilder();
        for (MapGroup g = item.getGroup(); g != null; g = g.getParentGroup())
            path.insert(0, "/" + g.getFriendlyName());
        return path.length() == 0 ? "(none)" : path.toString();
    }

    /** UAS waypoint, anything under a group with "UAS" in its name, or any "uas" metadata key. */
    private static boolean looksUas(MapItem item) {
        if (item == null) return false;
        if (isUasWaypoint(item) || groupPath(item).toLowerCase().contains("uas")) return true;
        for (String k : item.getAttributeNames())
            if (k.toLowerCase().contains("uas")) return true;
        return false;
    }

    // --- helpers ---

    private static boolean isUasWaypoint(MapItem item) {
        return item != null && item.hasMetaValue(META_ROUTE);
    }

    private static String key(MapItem item) {
        return item.getMetaString(META_ROUTE, "?") + "#" + item.getMetaInteger(META_WP, -1);
    }

    private static String describe(MapItem item) {
        String s = "WP" + item.getMetaInteger(META_WP, -1)
                + " [" + shortId(item.getMetaString(META_ROUTE, "?")) + "]";
        if (item instanceof PointMapItem) {
            PointMapItem p = (PointMapItem) item;
            s += String.format(" %.5f,%.5f", p.getPoint().getLatitude(), p.getPoint().getLongitude());
        }
        return s;
    }

    private static String shortId(String uid) {
        return uid.length() > 8 ? uid.substring(0, 8) : uid;
    }

    private void toast(String msg) {
        mv.post(() -> Toast.makeText(mv.getContext(), msg, Toast.LENGTH_SHORT).show());
    }
}
