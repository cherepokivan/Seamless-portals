package com.seamlessportals.client.gui;

import com.seamlessportals.client.SeamlessPortalsClient;
import com.seamlessportals.client.config.PortalConfig;

/**
 * Configuration controller. The visible 26.2 screen is intentionally kept
 * separate from this state object because the former Screen/ButtonWidget API
 * changed with the extraction-based UI renderer.
 */
public final class PortalConfigScreen {
    private PortalConfigScreen() {
    }

    public static void toggleEnabled() {
        SeamlessPortalsClient.getConfig().enabled = !SeamlessPortalsClient.getConfig().enabled;
    }

    public static PortalConfig.PreviewMode cyclePreviewMode() {
        PortalConfig config = SeamlessPortalsClient.getConfig();
        PortalConfig.PreviewMode[] modes = PortalConfig.PreviewMode.values();
        config.previewMode = modes[(config.previewMode.ordinal() + 1) % modes.length];
        return config.previewMode;
    }
}
