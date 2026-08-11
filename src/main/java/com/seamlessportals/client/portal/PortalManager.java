package com.seamlessportals.client.portal;

import com.seamlessportals.client.config.PortalConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PortalManager {
    private final PortalDetector detector = new PortalDetector();
    private final PortalConfig config;
    private List<PortalData> visiblePortals;

    public PortalManager(PortalConfig config) {
        this.config = config;
    }

    public void update(MinecraftClient client) {
        if (!config.enabled) {
            visiblePortals = null;
            return;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d lookVec = Vec3d.fromPolar(client.gameRenderer.getCamera().getPitch(), client.gameRenderer.getCamera().getYaw());

        visiblePortals = detector.getActivePortals(client).stream()
            .filter(portal -> portal.isVisible(cameraPos, lookVec))
            .sorted(Comparator.comparingDouble(portal -> Vec3d.ofCenter(portal.pos).squaredDistanceTo(cameraPos)))
            .limit(config.maxVisiblePortals)
            .collect(Collectors.toList());
    }

    public List<PortalData> getVisiblePortals() {
        return visiblePortals;
    }
}
