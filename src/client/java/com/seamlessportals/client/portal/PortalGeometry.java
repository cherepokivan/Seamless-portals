package com.seamlessportals.client.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bounds and orientation of a contiguous Nether portal surface.
 */
public final class PortalGeometry {
    public final BlockPos minPos;
    public final BlockPos maxPos;
    public final Direction.Axis axis;
    public final AABB boundingBox;
    public final float width;
    public final float height;

    public PortalGeometry(BlockPos minPos, BlockPos maxPos, Direction.Axis axis) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.axis = axis;
        this.width = axis == Direction.Axis.X
            ? maxPos.getZ() - minPos.getZ() + 1
            : maxPos.getX() - minPos.getX() + 1;
        this.height = maxPos.getY() - minPos.getY() + 1;
        this.boundingBox = new AABB(
            minPos.getX(), minPos.getY(), minPos.getZ(),
            maxPos.getX() + 1, maxPos.getY() + 1, maxPos.getZ() + 1
        );
    }

    public Vec3 getCenter() {
        return boundingBox.getCenter();
    }

    public Vec3 getNormal() {
        return axis == Direction.Axis.X ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
    }
}
