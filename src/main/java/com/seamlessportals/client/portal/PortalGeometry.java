package com.seamlessportals.client.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class PortalGeometry {
    public final BlockPos minPos;
    public final BlockPos maxPos;
    public final Direction.Axis axis;
    public final Box boundingBox;
    public final float width;
    public final float height;

    public PortalGeometry(BlockPos minPos, BlockPos maxPos, Direction.Axis axis) {
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.axis = axis;
        
        this.width = axis == Direction.Axis.X ? (maxPos.getZ() - minPos.getZ() + 1) : (maxPos.getX() - minPos.getX() + 1);
        this.height = maxPos.getY() - minPos.getY() + 1;
        
        this.boundingBox = new Box(
            minPos.getX(), minPos.getY(), minPos.getZ(),
            maxPos.getX() + 1, maxPos.getY() + 1, maxPos.getZ() + 1
        );
    }

    public Vec3d getCenter() {
        return boundingBox.getCenter();
    }

    public Vec3d getNormal() {
        return axis == Direction.Axis.X ? new Vec3d(1, 0, 0) : new Vec3d(0, 0, 1);
    }
}
