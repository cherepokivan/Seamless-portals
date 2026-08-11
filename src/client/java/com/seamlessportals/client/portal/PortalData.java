package com.seamlessportals.client.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Immutable client-side description of a detected Nether portal.
 */
public final class PortalData {
    public final UUID id;
    public final BlockPos pos;
    public final Direction.Axis axis;
    public final PortalGeometry geometry;
    public final PortalDestination destination;

    public PortalData(BlockPos pos, Direction.Axis axis, PortalGeometry geometry, PortalDestination destination) {
        this.id = UUID.randomUUID();
        this.pos = pos;
        this.axis = axis;
        this.geometry = geometry;
        this.destination = destination;
    }

    public AABB getBoundingBox() {
        return geometry.boundingBox;
    }

    public boolean isVisible(Vec3 cameraPos, Vec3 lookVector) {
        Vec3 toPortal = Vec3.atCenterOf(pos).subtract(cameraPos);
        return toPortal.dot(lookVector) > -5.0D;
    }
}
