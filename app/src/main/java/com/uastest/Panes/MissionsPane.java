package com.uastest.Panes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.uastest.R;
import com.uastest.Services.MissionTracker;
import com.uastest.Services.UasServiceRegistry;

import java.util.List;
import java.util.Locale;

import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;

/**
 * Every mission as a card, and in each its waypoints in flight order with their actions. Tap a
 * waypoint to set its action.
 *
 * {@link #getPane()} inflates and builds the pane the first time it is asked for ({@link
 * #initPane} binds its views); {@link #refresh()} redraws it from the mission tracker: when it is
 * shown, and when the missions change while it is open.
 */
public final class MissionsPane {

    private static final int ACCENT = 0xFF37D0B4;
    private static final int ACCENT_BG = 0xFF16332F;
    private static final int TEXT_SECONDARY = 0xFF8FA3B5;
    private static final int ACTION_START = 0xFF4CD07D;
    private static final int ACTION_STOP = 0xFFFF6B5A;
    private static final int ACTION_CHANGE = 0xFFFFB547;

    private final Context ctx;
    private final UasServiceRegistry services;

    private Pane pane;
    private TextView tvCounts, tvEmpty;
    private LinearLayout missionsList;

    public MissionsPane(Context pluginContext, UasServiceRegistry services) {
        this.ctx = pluginContext;
        this.services = services;
    }

    public Pane getPane() {
        if (pane == null) {
            View root = PluginLayoutInflater.inflate(ctx, R.layout.uas_pane, null);
            initPane(root);
            pane = new PaneBuilder(root)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.4D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.7D)
                    .build();
        }
        return pane;
    }

    private void initPane(View root) {
        tvCounts = root.findViewById(R.id.tv_counts);
        tvEmpty = root.findViewById(R.id.tv_empty);
        missionsList = root.findViewById(R.id.missions);
    }

    public void refresh() {
        if (missionsList == null) return;   // not built yet
        List<MissionTracker.Mission> missions = services.missionTracker.missions();
        missionsList.removeAllViews();
        int waypoints = 0;
        for (MissionTracker.Mission m : missions) {
            missionsList.addView(missionCard(m));
            waypoints += m.waypoints.size();
        }
        tvEmpty.setVisibility(missions.isEmpty() ? View.VISIBLE : View.GONE);
        tvCounts.setText(missions.isEmpty() ? "" : plural(missions.size(), "mission")
                + " · " + plural(waypoints, "WP"));
    }

    private View missionCard(MissionTracker.Mission m) {
        View card = PluginLayoutInflater.inflate(ctx, R.layout.item_mission_card, null);
        ((TextView) card.findViewById(R.id.tv_name)).setText(m.name);
        TextView count = card.findViewById(R.id.tv_waypoints);
        count.setText(plural(m.waypoints.size(), "WP"));
        count.setTextColor(ACCENT);
        count.setBackgroundTintList(ColorStateList.valueOf(ACCENT_BG));

        LinearLayout rows = card.findViewById(R.id.waypoints);
        for (MissionTracker.Waypoint w : m.waypoints) rows.addView(waypointRow(w));
        return card;
    }

    private View waypointRow(MissionTracker.Waypoint w) {
        View row = PluginLayoutInflater.inflate(ctx, R.layout.item_waypoint_row, null);
        ((TextView) row.findViewById(R.id.tv_number)).setText(String.valueOf(w.number()));
        ((TextView) row.findViewById(R.id.tv_title)).setText(w.title);
        ((TextView) row.findViewById(R.id.tv_position)).setText(
                String.format(Locale.US, "%.5f, %.5f", w.lat, w.lon));

        TextView action = row.findViewById(R.id.tv_action);
        if (w.action == null) {
            action.setText(R.string.set_action);
            action.setTextColor(TEXT_SECONDARY);
            action.setBackgroundResource(R.drawable.uas_pill_outline);
        } else {
            int c = actionColor(w.action);
            action.setText(w.action);
            action.setTextColor(c);
            // The action's colour at ~20% over the card.
            action.setBackgroundTintList(ColorStateList.valueOf((c & 0x00FFFFFF) | 0x33000000));
        }
        row.setOnClickListener(v -> services.radialMenu.showActionPicker(w));
        return row;
    }

    private static int actionColor(String action) {
        switch (action) {
            case "START":
                return ACTION_START;
            case "STOP":
                return ACTION_STOP;
            default:
                return ACTION_CHANGE;
        }
    }

    private static String plural(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }
}
