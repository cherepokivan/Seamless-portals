package com.seamlessportals.client;

import com.seamlessportals.client.config.PortalConfig;
import com.seamlessportals.client.debug.PortalDebugOverlay;
import com.seamlessportals.client.portal.PortalManager;
import com.seamlessportals.client.render.PortalRenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SeamlessPortalsClient implements ClientModInitializer {
    public static final String MOD_ID = "seamless-portals";
    
    private static PortalConfig config = new PortalConfig();
    private static PortalManager portalManager;
    private static PortalRenderPipeline renderPipeline;

    private static KeyBinding toggleKey;
    private static KeyBinding debugKey;
    private static boolean showDebug = false;

    @Override
    public void onInitializeClient() {
        portalManager = new PortalManager(config);
        renderPipeline = new PortalRenderPipeline(config, portalManager);

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seamless-portals.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "category.seamless-portals"
        ));

        debugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seamless-portals.debug",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "category.seamless-portals"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                config.enabled = !config.enabled;
            }
            
            while (debugKey.wasPressed()) {
                showDebug = !showDebug;
            }
            
            portalManager.update(client);
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (showDebug) {
                PortalDebugOverlay.render(context, net.minecraft.client.MinecraftClient.getInstance());
            }
        });
    }

    public static PortalConfig getConfig() {
        return config;
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
