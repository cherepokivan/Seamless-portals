package com.seamlessportals.client.portal;

import com.seamlessportals.client.config.PortalConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Maintains the portals visible from the local player's active camera.
 */
public final class PortalManager {
    private final PortalDetector detector = new PortalDetector();
    private final PortalConfig config;
    private List<PortalData> visiblePortals = List.of();

    public PortalManager(PortalConfig config) {
        this.config = config;
    }

    public void update(Minecraft client) {
        if (!config.enabled || client.level == null || client.player == null) {
            visiblePortals = List.of();
            return;
        }

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPosition = camera.position();
        Vec3 lookVector = new Vec3(camera.forwardVector());

        visiblePortals = detector.getActivePortals(client).stream()
            .filter(portal -> portal.isVisible(cameraPosition, lookVector))
            .sorted(Comparator.comparingDouble(portal -> Vec3.atCenterOf(portal.pos).distanceToSqr(cameraPosition)))
            .limit(config.maxVisiblePortals)
            .toList();
    }

    /**
     * Removes portal data belonging to the previous world or dimension.
     */
    public void reset() {
        detector.reset();
        visiblePortals = List.of();
    }

    public List<PortalData> getVisiblePortals() {
        return visiblePortals;
    }
}
