package com.uastest.Services;

import com.atakmap.android.maps.MapView;

/**
 * Everything the plugin runs besides its UI:
 * <ul>
 *   <li>{@link MissionTracker}: UAS Tool's missions as drawn on the map, waypoints in flight
 *       order, plus our action per waypoint;</li>
 *   <li>{@link RadialMenuService}: the ML button, on UAS Tool mission waypoints only.</li>
 * </ul>
 */
public class UasServiceRegistry {

    public final MissionTracker missionTracker;
    public final RadialMenuService radialMenu;

    public UasServiceRegistry(MapView mv) {
        missionTracker = new MissionTracker(mv);
        radialMenu = new RadialMenuService(mv.getContext(), missionTracker);
    }

    public void onStart() {
        missionTracker.start();
        radialMenu.start();
    }

    public void onStop() {
        radialMenu.stop();
        missionTracker.stop();
    }
}
