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

/**
 * Every mission as a card, and in each its waypoints in flight order with their actions. Tap a
 * waypoint to set its action.
 */
public final class MissionsPage {

    private final Context ctx;
    private final UasServiceRegistry services;
    private final View root;
    private final TextView counts;
    private final TextView empty;
    private final LinearLayout missionsList;

    public MissionsPage(Context pluginContext, UasServiceRegistry services) {
        this.ctx = pluginContext;
        this.services = services;
        root = PluginLayoutInflater.inflate(ctx, R.layout.uas_pane, null);
        counts = root.findViewById(R.id.tv_counts);
        empty = root.findViewById(R.id.tv_empty);
        missionsList = root.findViewById(R.id.missions);
    }

    public View getView() {
        return root;
    }

    public void refresh() {
        List<MissionTracker.Mission> missions = services.missionTracker.missions();
        missionsList.removeAllViews();
        int waypoints = 0;
        for (MissionTracker.Mission m : missions) {
            missionsList.addView(missionCard(m));
            waypoints += m.waypoints.size();
        }
        empty.setVisibility(missions.isEmpty() ? View.VISIBLE : View.GONE);
        counts.setText(missions.isEmpty() ? "" : plural(missions.size(), "mission")
                + " · " + plural(waypoints, "WP"));
    }

    private View missionCard(MissionTracker.Mission m) {
        View card = PluginLayoutInflater.inflate(ctx, R.layout.item_mission_card, null);
        ((TextView) card.findViewById(R.id.tv_name)).setText(m.name);
        TextView count = card.findViewById(R.id.tv_waypoints);
        count.setText(plural(m.waypoints.size(), "WP"));
        count.setTextColor(color(R.color.uas_accent));
        count.setBackgroundTintList(ColorStateList.valueOf(color(R.color.uas_accent_bg)));

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
            action.setTextColor(color(R.color.uas_text_secondary));
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

    private int actionColor(String action) {
        switch (action) {
            case "START":
                return color(R.color.uas_action_start);
            case "STOP":
                return color(R.color.uas_action_stop);
            default:
                return color(R.color.uas_action_change);
        }
    }

    private int color(int res) {
        return ctx.getColor(res);
    }

    private static String plural(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }
}
