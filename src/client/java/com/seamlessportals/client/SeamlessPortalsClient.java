package com.seamlessportals.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.seamlessportals.client.config.PortalConfig;
import com.seamlessportals.client.portal.PortalManager;
import com.seamlessportals.client.render.PortalRenderPipeline;
import com.seamlessportals.client.render.PortalSurfaceRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Client entrypoint for Seamless Portals.
 */
public final class SeamlessPortalsClient implements ClientModInitializer {
    public static final String MOD_ID = "seamless-portals";

    private static final PortalConfig CONFIG = new PortalConfig();
    private static PortalManager portalManager;
    private static PortalRenderPipeline renderPipeline;
    private static KeyMapping toggleKey;
    private static KeyMapping debugKey;
    private static boolean showDebug;

    @Override
    public void onInitializeClient() {
        portalManager = new PortalManager(CONFIG);
        renderPipeline = new PortalRenderPipeline(CONFIG, portalManager);
        PortalSurfaceRenderer.register();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "general")
        );
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.seamless-portals.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_F7, category
        ));
        debugKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.seamless-portals.debug", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                CONFIG.enabled = !CONFIG.enabled;
            }
            while (debugKey.consumeClick()) {
                showDebug = !showDebug;
            }
            portalManager.update(client);
            renderPipeline.tick(client, showDebug);
        });
    }

    public static PortalConfig getConfig() {
        return CONFIG;
    }

    public static boolean isShowDebug() {
        return showDebug;
    }

    public static PortalManager getPortalManager() {
        return portalManager;
    }

    public static PortalRenderPipeline getRenderPipeline() {
        return renderPipeline;
    }
}
