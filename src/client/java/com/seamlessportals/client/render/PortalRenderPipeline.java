package com.seamlessportals.client.render;

import com.seamlessportals.client.config.PortalConfig;
import com.seamlessportals.client.debug.PortalDebugRenderer;
import com.seamlessportals.client.portal.PortalData;
import com.seamlessportals.client.portal.PortalManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains per-portal virtual-camera and render-target state.
 *
 * <p>In Minecraft 26.2 the renderer is extraction-based, so the former direct
 * WorldRenderer/Framebuffer invocation path is deliberately not used. This
 * class preserves portal selection, mirror transforms, resolution allocation,
 * and frame lifecycle while keeping the renderer backend boundary explicit.</p>
 */
public final class PortalRenderPipeline {
    private final PortalConfig config;
    private final PortalManager portalManager;
    private final Map<UUID, PortalFramebuffer> framebuffers = new HashMap<>();
    private final Map<UUID, PortalCamera> virtualCameras = new HashMap<>();
    private boolean renderingPortal;

    public PortalRenderPipeline(PortalConfig config, PortalManager portalManager) {
        this.config = config;
        this.portalManager = portalManager;
    }

    /** Called from the end-of-client-tick callback after portal discovery. */
    public void tick(Minecraft client, boolean showDebug) {
        if (!config.enabled || config.previewMode == PortalConfig.PreviewMode.DISABLED || client.level == null) {
            releaseUnusedTargets(Map.of());
            return;
        }

        Camera camera = client.gameRenderer.mainCamera();
        Map<UUID, PortalData> currentPortals = new HashMap<>();
        for (PortalData portal : portalManager.getVisiblePortals()) {
            currentPortals.put(portal.id, portal);
            updatePortalPreview(portal, camera);
        }
        releaseUnusedTargets(currentPortals);

        if (showDebug) {
            PortalDebugRenderer.capture(portalManager.getVisiblePortals(), camera.position());
        }
    }

    private void updatePortalPreview(PortalData portal, Camera camera) {
        renderingPortal = true;
        try {
            PortalFramebuffer framebuffer = framebuffers.computeIfAbsent(
                portal.id, ignored -> new PortalFramebuffer(targetSize(), targetSize())
            );
            int targetSize = targetSize();
            framebuffer.resize(targetSize, targetSize);

            Vec3 sourceCenter = portal.geometry.getCenter();
            Vec3 cameraPosition = camera.position();
            Vec3 destinationPosition;
            if (config.previewMode == PortalConfig.PreviewMode.MIRROR) {
                Vec3 normal = portal.geometry.getNormal();
                Vec3 relative = cameraPosition.subtract(sourceCenter);
                destinationPosition = cameraPosition.subtract(normal.scale(2.0D * relative.dot(normal)));
            } else {
                destinationPosition = portal.destination.transformCamera(cameraPosition, sourceCenter);
            }

            virtualCameras.computeIfAbsent(portal.id, ignored -> new PortalCamera())
                .setPortalCamera(destinationPosition, camera.yaw(), camera.xRot());
        } finally {
            renderingPortal = false;
        }
    }

    private int targetSize() {
        return switch (config.portalQuality) {
            case LOW -> 256;
            case MEDIUM -> 512;
            case HIGH, AUTO -> 1024;
            case ULTRA -> 2048;
        };
    }

    private void releaseUnusedTargets(Map<UUID, PortalData> currentPortals) {
        framebuffers.entrySet().removeIf(entry -> {
            if (currentPortals.containsKey(entry.getKey())) {
                return false;
            }
            entry.getValue().dispose();
            virtualCameras.remove(entry.getKey());
            return true;
        });
    }

    public boolean isRenderingPortal() {
        return renderingPortal;
    }

    public PortalFramebuffer getFramebuffer(UUID id) {
        return framebuffers.get(id);
    }

    public PortalCamera getVirtualCamera(UUID id) {
        return virtualCameras.get(id);
    }
}
