package com.uasml.spike;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.assets.MapDataRef;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuHandler;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.widgets.WidgetIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Spike: can a separate plugin (1) add a button to UAS Tool's waypoint radial menu and
 * (2) see UAS Tool's route waypoints come and go?
 *
 * UAS Tool draws each waypoint as a b-m-p-w marker in the "UAS Routes" map group, tagged with
 * meta "route_uid" (the route) and "uas_route_id" (the waypoint number).
 */
public class SpikePlugin implements IPlugin {

    private static final String TAG = "UasMlSpike";
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
        if (isUasWaypoint(item)) menu.addChildWidget(mlButton(menu));
    };

    private final MapEventDispatcher.MapEventDispatchListener waypointListener = event -> {
        MapItem item = event.getItem();
        if (!isUasWaypoint(item)) return;
        Log.i(TAG, event.getType() + " " + describe(item));
        if (MapEvent.ITEM_DRAG_DROPPED.equals(event.getType()))
            toast("Moved " + describe(item));
    };

    public SpikePlugin(IServiceController serviceController) {
        pluginContext = serviceController.getService(PluginContextProvider.class).getPluginContext();
        pluginContext.setTheme(R.style.ATAKPluginTheme);
        uiService = serviceController.getService(IHostUIService.class);
        mv = MapView.getMapView();

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
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

    private MapMenuButtonWidget mlButton(MapMenuWidget menu) {
        MapMenuButtonWidget button = new MapMenuButtonWidget(mv.getContext());
        // Same sizing as the sample radialmenudemo: menu defaults, average weight of siblings.
        button.setOrientation(button.getOrientationAngle(), menu.getInnerRadius());
        button.setButtonSize(button.getButtonSpan(), menu.getButtonWidth());
        button.setLayoutWeight(1f);
        button.setIcon(new WidgetIcon.Builder()
                .setAnchor(16, 16)
                .setSize(32, 32)
                .setImageRef(0, MapDataRef.parseUri("asset:///icons/target.png"))
                .build());
        button.setOnClickAction((mapView, item) -> {
            MapMenuReceiver.getInstance().hideMenu();
            showActionPicker(item);
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
