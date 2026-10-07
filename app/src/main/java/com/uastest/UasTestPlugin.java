package com.uastest;

import android.content.Context;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Test: ML actions on UAS Tool mission waypoints.
 * <ul>
 *   <li>{@link MissionTracker}: UAS Tool's missions as drawn on the map, waypoints in flight
 *       order, plus our action per waypoint.</li>
 *   <li>{@link RadialMenuService}: the ML button, on UAS Tool mission waypoints only.</li>
 *   <li>{@link MissionPane}: all missions, their waypoints and actions (toolbar button).</li>
 * </ul>
 * Logs each mission's waypoints and actions to logcat (tag UasTest) each time they change.
 */
public class UasTestPlugin implements IPlugin {

    private final Context pluginContext;
    private final IHostUIService uiService;
    private final MapView mv;
    private final ToolbarItem toolbarItem;

    private MissionTracker tracker;
    private RadialMenuService radial;
    private MissionPane pane;

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
                        if (pane != null) pane.show();
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        if (uiService == null) return;
        tracker = new MissionTracker(mv, missions -> {
            if (pane != null) pane.refresh();
            // Stream to each platform's onboard service from here: per mission, its waypoints in
            // flight order with their actions.
        });
        radial = new RadialMenuService(mv.getContext(), tracker);
        pane = new MissionPane(uiService, pluginContext, tracker, radial);
        tracker.start();
        radial.start();
        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        if (uiService == null) return;
        uiService.removeToolbarItem(toolbarItem);
        if (pane != null) pane.close();
        if (radial != null) radial.stop();
        if (tracker != null) tracker.stop();
        pane = null;
        radial = null;
        tracker = null;
    }
}
