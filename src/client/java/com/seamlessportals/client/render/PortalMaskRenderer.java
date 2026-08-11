package com.seamlessportals.client.render;

import com.seamlessportals.client.portal.PortalData;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry contract for the portal mask submitted by the 26.2 renderer bridge.
 */
public final class PortalMaskRenderer {
    private PortalMaskRenderer() {
    }

    public static PortalMask createMask(PortalData portal, PortalFramebuffer framebuffer) {
        Vec3 center = portal.geometry.getCenter();
        return new PortalMask(
            center,
            portal.geometry.width,
            portal.geometry.height,
            portal.axis,
            framebuffer.getWidth(),
            framebuffer.getHeight()
        );
    }

    public record PortalMask(Vec3 center, float width, float height,
                             net.minecraft.core.Direction.Axis axis,
                             int textureWidth, int textureHeight) {
    }
}
