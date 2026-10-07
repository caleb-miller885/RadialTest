package com.uastest;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;

/** Every mission, and under each its waypoints with their actions. Tap a waypoint to set one. */
public final class MissionPane {

    private final IHostUIService ui;
    private final Context ctx;
    private final MissionTracker tracker;
    private final RadialMenuService radial;
    private final LinearLayout list;
    private final Pane pane;

    public MissionPane(IHostUIService ui, Context pluginContext, MissionTracker tracker,
            RadialMenuService radial) {
        this.ui = ui;
        this.ctx = pluginContext;
        this.tracker = tracker;
        this.radial = radial;

        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(12));
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(list);

        pane = new PaneBuilder(scroll)
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.33D)
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                .build();
    }

    public void show() {
        render();
        if (!ui.isPaneVisible(pane)) ui.showPane(pane, null);
    }

    /** Redraws if open; call whenever the mission list changes. */
    public void refresh() {
        if (ui.isPaneVisible(pane)) render();
    }

    public void close() {
        if (ui.isPaneVisible(pane)) ui.closePane(pane);
    }

    private void render() {
        list.removeAllViews();
        list.addView(text("UAS Tool missions", 18, Color.WHITE, true));
        List<MissionTracker.Mission> missions = tracker.missions();
        if (missions.isEmpty())
            list.addView(text("No UAS Tool missions on the map.", 14, Color.LTGRAY, false));

        for (MissionTracker.Mission m : missions) {
            TextView header = text(m.name, 16, Color.WHITE, true);
            header.setPadding(0, dp(14), 0, dp(2));
            list.addView(header);
            list.addView(text(m.waypoints.size() + " waypoints", 11, Color.GRAY, false));

            for (MissionTracker.Waypoint w : m.waypoints) {
                TextView row = text(String.format("%2d  %-6s %.5f, %.5f   %s", w.number(), w.title,
                        w.lat, w.lon, w.action == null ? "—" : "▸ " + w.action),
                        14, w.action == null ? Color.LTGRAY : Color.rgb(120, 200, 255), false);
                row.setTypeface(Typeface.MONOSPACE);
                row.setPadding(dp(8), dp(6), 0, dp(6));
                row.setOnClickListener(v -> radial.showActionPicker(w));
                list.addView(row);
            }
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
