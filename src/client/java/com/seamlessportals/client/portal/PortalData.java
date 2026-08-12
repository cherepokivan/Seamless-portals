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

    /**
     * The renderer performs proper depth testing, so portal selection must not
     * discard the back face based on the camera direction. Keeping all nearby
     * portals here makes the effect symmetric on both sides of the frame.
     */
    public boolean isVisible(Vec3 cameraPos, Vec3 lookVector) {
        return true;
    }
}
