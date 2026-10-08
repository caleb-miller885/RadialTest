package com.uastest.Panes;

import android.content.Context;

import com.uastest.Services.UasServiceRegistry;

import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;

/** The plugin's UI: one ATAK pane showing the {@link MissionsPage}. */
public class UasPaneRegistry {

    private final IHostUIService uiService;
    private final MissionsPage missionsPage;
    private final Pane pane;

    public UasPaneRegistry(IHostUIService uiService, Context pluginContext, UasServiceRegistry services) {
        this.uiService = uiService;
        missionsPage = new MissionsPage(pluginContext, services);
        pane = new PaneBuilder(missionsPage.getView())
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.33D)
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                .build();
    }

    /** Toolbar button. */
    public void show() {
        missionsPage.refresh();
        if (!uiService.isPaneVisible(pane)) uiService.showPane(pane, null);
    }

    /** From the mission tracker (main thread): redraw if open. */
    public void onMissionsChanged() {
        if (uiService.isPaneVisible(pane)) missionsPage.refresh();
    }

    public void onStop() {
        if (uiService.isPaneVisible(pane)) uiService.closePane(pane);
    }
}
