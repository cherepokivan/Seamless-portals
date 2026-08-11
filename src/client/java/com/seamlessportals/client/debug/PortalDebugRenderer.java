package com.seamlessportals.client.debug;

import com.seamlessportals.client.portal.PortalData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stores the latest debug information independently of the 26.2 extraction
 * renderer. A renderer bridge can consume this snapshot without touching the
 * game render pipeline directly.
 */
public final class PortalDebugRenderer {
    private static volatile DebugSnapshot latestSnapshot = new DebugSnapshot(List.of(), Vec3.ZERO);

    private PortalDebugRenderer() {
    }

    public static void capture(List<PortalData> portals, Vec3 cameraPosition) {
        latestSnapshot = new DebugSnapshot(List.copyOf(portals), cameraPosition);
    }

    public static DebugSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public record DebugSnapshot(List<PortalData> portals, Vec3 cameraPosition) {
    }
}
