package com.uastest;

import android.content.Context;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.maps.MapView;
import com.uastest.Panes.UasPaneRegistry;
import com.uastest.Services.UasServiceRegistry;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Test: ML actions on UAS Tool mission waypoints.
 * <ul>
 *   <li>{@link UasServiceRegistry}: the mission tracker and the ML radial menu button;</li>
 *   <li>{@link UasPaneRegistry}: the UI: all missions, their waypoints and actions (toolbar
 *       button).</li>
 * </ul>
 * The plugin wires the services to the UI: the mission tracker's changes go to the panes.
 * Logs each mission's waypoints and actions to logcat (tag UasTest) each time they change.
 */
public class UasTestPlugin implements IPlugin {

    private final Context pluginContext;
    private final IHostUIService uiService;
    private final MapView mv;
    private final ToolbarItem toolbarItem;

    private UasServiceRegistry services;
    private UasPaneRegistry paneRegistry;

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
                        if (paneRegistry != null) paneRegistry.showMissions();
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        if (uiService == null) return;
        services = new UasServiceRegistry(mv);
        paneRegistry = new UasPaneRegistry(uiService, pluginContext, services);
        // Before services.onStart(): the tracker reads the map as soon as it starts.
        services.missionTracker.setListener(missions -> {
            if (paneRegistry != null) paneRegistry.onMissionsChanged();
            // Stream to each platform's onboard service from here: per mission, its waypoints in
            // flight order with their actions.
        });
        services.onStart();
        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        if (uiService == null) return;
        uiService.removeToolbarItem(toolbarItem);
        // UI first, and the listener off, so stopping the services doesn't update a closed pane.
        if (paneRegistry != null) {
            paneRegistry.onStop();
            paneRegistry = null;
        }
        if (services != null) {
            services.missionTracker.setListener(null);
            services.onStop();
            services = null;
        }
    }
}
