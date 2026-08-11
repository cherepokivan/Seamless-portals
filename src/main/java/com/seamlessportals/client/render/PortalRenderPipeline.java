package com.seamlessportals.client.render;

import com.seamlessportals.client.config.PortalConfig;
import com.seamlessportals.client.portal.PortalData;
import com.seamlessportals.client.portal.PortalManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PortalRenderPipeline {
    private final PortalConfig config;
    private final PortalManager portalManager;
    private final Map<UUID, PortalFramebuffer> framebuffers = new HashMap<>();
    private boolean isRenderingPortal = false;

    public PortalRenderPipeline(PortalConfig config, PortalManager portalManager) {
        this.config = config;
        this.portalManager = portalManager;
    }

    public void onRenderWorld(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, net.minecraft.client.render.Camera camera, WorldRenderer worldRenderer) {
        if (!config.enabled || isRenderingPortal) return;
        if (portalManager.getVisiblePortals() == null) return;

        for (PortalData portal : portalManager.getVisiblePortals()) {
            renderPortal(portal, matrices, tickDelta, limitTime, camera, worldRenderer);
        }
    }

    private void renderPortal(PortalData portal, MatrixStack matrices, float tickDelta, long limitTime, net.minecraft.client.render.Camera camera, WorldRenderer worldRenderer) {
        isRenderingPortal = true;
        
        MinecraftClient client = MinecraftClient.getInstance();
        PortalFramebuffer pfb = framebuffers.computeIfAbsent(portal.id, id -> new PortalFramebuffer(1024, 1024));
        
        // Calculate destination camera
        Vec3d portalCenter = portal.geometry.getCenter();
        Vec3d destCameraPos;
        
        if (config.previewMode == PortalConfig.PreviewMode.MIRROR) {
            // Mirror logic: Reflect position across portal plane
            Vec3d relative = camera.getPos().subtract(portalCenter);
            Vec3d normal = portal.geometry.getNormal();
            double dot = relative.dotProduct(normal);
            destCameraPos = camera.getPos().subtract(normal.multiply(2 * dot));
        } else {
            destCameraPos = portal.destination.transformCamera(camera.getPos(), portalCenter);
        }
        
        // Setup rendering state
        pfb.beginWrite(true);
        RenderSystem.clear(16640, MinecraftClient.IS_SYSTEM_MAC);
        
        // Render the destination world (Nether or Overworld)
        // In a production mod, this would involve calling worldRenderer.render() 
        // with the destination world context and the virtual camera.
        
        pfb.endWrite();

        // Apply mask and render to screen
        PortalMaskRenderer.renderMask(portal, matrices, pfb);
        
        isRenderingPortal = false;
    }

    public boolean isRenderingPortal() {
        return isRenderingPortal;
    }

    public PortalFramebuffer getFramebuffer(UUID id) {
        return framebuffers.get(id);
    }
}
