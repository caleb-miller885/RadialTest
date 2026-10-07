package com.uastest;

import android.util.Log;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UAS Tool's missions as drawn on the map, plus our action per waypoint.
 *
 * Each mission is a sub-group of "UAS Routes". UAS Tool redraws a mission after every change
 * (new markers, new UIDs), creating its waypoint markers in mission order, so sorting them by
 * serial id gives the flight order. This is the list UAS Tool uploads.
 *
 * Markers don't survive a redraw, so we keep our own {@link Waypoint} per point and match each
 * redraw to it by position: an insert, delete or reorder leaves the other points where they were.
 * A drag shows up as one point gone and one new in the same count: the same waypoint, moved.
 * An action belongs to its Waypoint, so it follows the point, not its number.
 */
final class MissionTracker {

    static final String TAG = "UasTest";
    private static final String GROUP = "UAS Routes";
    private static final String META_ROUTE = "route_uid";
    private static final String META_TITLE = "mission_point_title";

    static final class Waypoint {
        final Mission mission;
        double lat, lon;
        String title;   // UAS Tool's, e.g. WP-3: renumbered by inserts and deletes
        String action;  // null: none

        Waypoint(Mission mission, PointMapItem marker) {
            this.mission = mission;
            update(marker);
        }

        void update(PointMapItem marker) {
            GeoPoint p = marker.getPoint();
            lat = p.getLatitude();
            lon = p.getLongitude();
            title = marker.getMetaString(META_TITLE, marker.getTitle());
        }

        boolean at(PointMapItem marker) {
            GeoPoint p = marker.getPoint();
            return Math.abs(lat - p.getLatitude()) < 1e-7 && Math.abs(lon - p.getLongitude()) < 1e-7;
        }

        /** 1-based place in the flight order. */
        int number() {
            return mission.waypoints.indexOf(this) + 1;
        }
    }

    static final class Mission {
        String name;
        final List<Waypoint> waypoints = new ArrayList<>();  // flight order
        boolean onMap;
    }

    interface Listener {
        /** The missions or their actions changed. Main thread. */
        void onMissionsChanged(List<Mission> missions);
    }

    // UAS Tool redraws a mission in a burst of removes and adds; read it once that is over.
    private static final long QUIET_MS = 300;

    private final MapView mv;
    private final Listener listener;
    private final Runnable sync = this::sync;
    // By route_uid, so a renamed mission keeps its actions. Kept when a mission leaves the map,
    // so they are still there if it is drawn again.
    private final Map<String, Mission> missions = new LinkedHashMap<>();

    private final MapEventDispatcher.MapEventDispatchListener mapListener = this::onMapEvent;

    MissionTracker(MapView mv, Listener listener) {
        this.mv = mv;
        this.listener = listener;
    }

    void start() {
        mv.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_ADDED, mapListener);
        mv.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_REMOVED, mapListener);
        sync();
    }

    void stop() {
        mv.getMapEventDispatcher().removeMapEventListener(MapEvent.ITEM_ADDED, mapListener);
        mv.getMapEventDispatcher().removeMapEventListener(MapEvent.ITEM_REMOVED, mapListener);
        mv.removeCallbacks(sync);
    }

    private void onMapEvent(MapEvent event) {
        if (isWaypoint(event.getItem())) {
            mv.removeCallbacks(sync);
            mv.postDelayed(sync, QUIET_MS);
        }
    }

    /** The missions on the map. */
    List<Mission> missions() {
        List<Mission> list = new ArrayList<>();
        for (Mission m : missions.values()) if (m.onMap) list.add(m);
        return list;
    }

    /** Our waypoint for a UAS Tool waypoint marker, or null (e.g. drawn since the last read). */
    Waypoint waypoint(MapItem marker) {
        if (!isWaypoint(marker)) return null;
        Mission m = missions.get(marker.getMetaString(META_ROUTE, ""));
        if (m == null || !m.onMap) return null;
        for (Waypoint w : m.waypoints) if (w.at((PointMapItem) marker)) return w;
        return null;
    }

    /** Sets (or with null, clears) a waypoint's action. */
    void setAction(Waypoint w, String action) {
        w.action = action;
        log(w.mission);
        listener.onMissionsChanged(missions());
    }

    /**
     * A marker UAS Tool draws for a mission item, as opposed to e.g. a survey's generated points:
     * the only ones it makes removable and movable. (No group check: a marker just removed from
     * the map has none.)
     */
    private static boolean isWaypoint(MapItem item) {
        return item instanceof PointMapItem && item.hasMetaValue(META_ROUTE)
                && item.getMetaBoolean("removable", false) && item.getMetaBoolean("movable", false);
    }

    private void sync() {
        for (Mission m : missions.values()) m.onMap = false;
        MapGroup routes = MapGroup.deepFindGroupByNameBreadthFirst(mv.getRootGroup(), GROUP);
        if (routes != null) {
            for (MapGroup g : routes.getChildGroups()) {
                List<PointMapItem> markers = markers(g);
                if (markers.isEmpty()) continue;
                String uid = markers.get(0).getMetaString(META_ROUTE, "");
                Mission m = missions.get(uid);
                if (m == null) missions.put(uid, m = new Mission());
                m.name = g.getFriendlyName();
                m.onMap = true;
                match(m, markers);
                log(m);
            }
        }
        listener.onMissionsChanged(missions());
    }

    /** A mission group's waypoint markers in flight order. */
    private static List<PointMapItem> markers(MapGroup g) {
        List<PointMapItem> markers = new ArrayList<>();
        MapGroup.deepMapItems(g, item -> {
            if (isWaypoint(item)) markers.add((PointMapItem) item);
            return false;
        });
        markers.sort((a, b) -> Long.compare(a.getSerialId(), b.getSerialId()));
        return markers;
    }

    /** Rebuilds a mission's waypoints from its markers, keeping each point's Waypoint. */
    private static void match(Mission m, List<PointMapItem> markers) {
        List<Waypoint> old = new ArrayList<>(m.waypoints);
        Waypoint[] matched = new Waypoint[markers.size()];
        int unmatched = -1, count = 0;
        for (int i = 0; i < markers.size(); i++) {
            for (Waypoint w : old) {
                if (w.at(markers.get(i))) {
                    matched[i] = w;
                    old.remove(w);
                    break;
                }
            }
            if (matched[i] == null) {
                unmatched = i;
                count++;
            }
        }

        // Dragged: same count, exactly one point gone and one new.
        if (count == 1 && old.size() == 1 && m.waypoints.size() == markers.size()) {
            matched[unmatched] = old.remove(0);
            Log.i(TAG, m.name + ": " + matched[unmatched].title + " moved");
        }

        for (Waypoint gone : old)
            if (gone.action != null)
                Log.i(TAG, m.name + ": " + gone.title + " removed, dropped " + gone.action);

        m.waypoints.clear();
        for (int i = 0; i < markers.size(); i++) {
            Waypoint w = matched[i] != null ? matched[i] : new Waypoint(m, markers.get(i));
            w.update(markers.get(i));
            m.waypoints.add(w);
        }
    }

    private static void log(Mission m) {
        StringBuilder sb = new StringBuilder(m.name).append(':');
        for (Waypoint w : m.waypoints)
            sb.append(' ').append(w.number()).append('=').append(w.title)
                    .append(w.action == null ? "" : ":" + w.action);
        Log.i(TAG, sb.toString());
    }
}
