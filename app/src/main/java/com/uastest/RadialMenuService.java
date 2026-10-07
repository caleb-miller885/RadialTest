package com.uastest;

import android.app.AlertDialog;
import android.content.Context;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuHandler;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.widgets.WidgetIcon;

import gov.tak.api.widgets.IMapMenuButtonWidget;

/**
 * Adds an ML button to the radial menu of UAS Tool mission waypoints, and only those: not survey
 * points, nor other UAS Tool items.
 */
final class RadialMenuService {

    private final Context context;
    private final MissionTracker tracker;

    // Run after the core handlers (which are <= 0) so ours is the last word on the menu.
    private static final int PRIORITY = 100;

    private final MapMenuHandler handler;

    RadialMenuService(Context atakContext, MissionTracker tracker) {
        this.context = atakContext;
        this.tracker = tracker;
        handler = (item, menu) -> {
            if (tracker.waypoint(item) != null) menu.addChildWidget(mlButton(menu));
        };
    }

    void start() {
        MapMenuReceiver.getInstance().registerMapMenuHandler(handler, PRIORITY);
    }

    void stop() {
        MapMenuReceiver.getInstance().unregisterMapMenuHandler(handler);
    }

    private MapMenuButtonWidget mlButton(MapMenuWidget menu) {
        MapMenuButtonWidget button = new MapMenuButtonWidget(context);
        // Sized like its siblings (as the SDK's radialmenudemo does); ATAK lays the menu out by
        // weight after this handler runs.
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
                .setImageRef(0, MapDataRef.parseUri("asset:///icons/target.png"))
                .build());
        button.setOnButtonClickHandler(new IMapMenuButtonWidget.OnButtonClickHandler() {
            @Override
            public boolean isSupported(Object opaque) {
                return opaque instanceof MapItem;
            }

            @Override
            public void performAction(Object opaque) {
                MapMenuReceiver.getInstance().hideMenu();
                MissionTracker.Waypoint w = tracker.waypoint((MapItem) opaque);
                if (w != null) showActionPicker(w);
            }
        });
        return button;
    }

    /** Start / stop / change / clear for one waypoint. Also used by the pane. */
    void showActionPicker(MissionTracker.Waypoint w) {
        String[] choices = {"Start", "Stop", "Change", "Clear"};
        String[] actions = {"START", "STOP", "CHANGE", null};
        new AlertDialog.Builder(context)
                .setTitle("ML · " + w.mission.name + " · " + w.title)
                .setItems(choices, (d, which) -> tracker.setAction(w, actions[which]))
                .show();
    }
}
