package com.seamlessportals.client.debug;

import com.seamlessportals.client.SeamlessPortalsClient;
import com.seamlessportals.client.portal.PortalData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces debug text for a 26.2 HUD bridge without depending on the legacy
 * DrawContext callback removed from Fabric API.
 */
public final class PortalDebugOverlay {
    private PortalDebugOverlay() {
    }

    public static List<String> lines(Minecraft client) {
        if (!SeamlessPortalsClient.getConfig().enabled || client.level == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        List<PortalData> portals = SeamlessPortalsClient.getPortalManager().getVisiblePortals();
        lines.add("Seamless Portals Debug");
        lines.add("Current dimension: " + client.level.dimension().identifier());
        lines.add("Visible portals: " + portals.size());

        Vec3 cameraPosition = client.gameRenderer.mainCamera().position();
        int count = 1;
        for (PortalData portal : portals) {
            lines.add("Portal #" + count++ + " -> " + portal.destination.dimension.identifier());
            lines.add("  position: " + portal.pos.toShortString() + ", axis: " + portal.axis);
            lines.add(String.format("  distance: %.2f", cameraPosition.distanceTo(portal.geometry.getCenter())));
        }
        return List.copyOf(lines);
    }
}
