package com.uastest.Panes;

import android.content.Context;

import com.uastest.Services.UasServiceRegistry;

import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;

/** The plugin's UI: the {@link MissionsPane}, opened from the toolbar button. */
public class UasPaneRegistry {

    private final IHostUIService uiService;
    private final MissionsPane missionsPane;
    private Pane missions;   // built the first time it is shown

    public UasPaneRegistry(IHostUIService uiService, Context pluginContext, UasServiceRegistry services) {
        this.uiService = uiService;
        missionsPane = new MissionsPane(pluginContext, services);
    }

    /** Toolbar button. */
    public void showMissions() {
        missions = missionsPane.getPane();
        missionsPane.refresh();
        if (!uiService.isPaneVisible(missions)) uiService.showPane(missions, null);
    }

    /** From the mission tracker's listener (main thread): redraw if open. */
    public void onMissionsChanged() {
        if (missions != null && uiService.isPaneVisible(missions)) missionsPane.refresh();
    }

    public void onStop() {
        if (missions != null && uiService.isPaneVisible(missions)) uiService.closePane(missions);
    }
}
