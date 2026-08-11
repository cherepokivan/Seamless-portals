package com.seamlessportals.client.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Predicted target of a vanilla Nether portal, including dimension scaling.
 */
public final class PortalDestination {
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;
    public final Direction.Axis axis;
    public final double scale;

    public PortalDestination(ResourceKey<Level> dimension, BlockPos pos, Direction.Axis axis, double scale) {
        this.dimension = dimension;
        this.pos = pos;
        this.axis = axis;
        this.scale = scale;
    }

    public static PortalDestination calculate(Level currentLevel, BlockPos portalPos, Direction.Axis sourceAxis) {
        ResourceKey<Level> targetDimension;
        double scale;

        if (currentLevel.dimension().equals(Level.OVERWORLD)) {
            targetDimension = Level.NETHER;
            scale = 0.125D;
        } else if (currentLevel.dimension().equals(Level.NETHER)) {
            targetDimension = Level.OVERWORLD;
            scale = 8.0D;
        } else {
            targetDimension = Level.OVERWORLD;
            scale = 1.0D;
        }

        BlockPos targetPos = BlockPos.containing(
            portalPos.getX() * scale,
            portalPos.getY(),
            portalPos.getZ() * scale
        );
        return new PortalDestination(targetDimension, targetPos, sourceAxis, scale);
    }

    public Matrix4f getTransformationMatrix(Vec3 sourceCenter, Vec3 destinationCenter,
                                            Direction.Axis sourceAxis, Direction.Axis destinationAxis) {
        Matrix4f matrix = new Matrix4f()
            .translate(new Vector3f((float) sourceCenter.x, (float) sourceCenter.y, (float) sourceCenter.z));

        if (sourceAxis != destinationAxis) {
            matrix.rotate(new Quaternionf().fromAxisAngleDeg(0.0F, 1.0F, 0.0F, 90.0F));
        }

        matrix.scale((float) scale, 1.0F, (float) scale);
        matrix.translate(new Vector3f(
            (float) -destinationCenter.x,
            (float) -destinationCenter.y,
            (float) -destinationCenter.z
        ));
        return matrix.invert();
    }

    public Vec3 transformCamera(Vec3 cameraPosition, Vec3 sourceCenter) {
        Vec3 relative = cameraPosition.subtract(sourceCenter);
        Vec3 targetCenter = Vec3.atCenterOf(pos);
        return targetCenter.add(relative.x * scale, relative.y, relative.z * scale);
    }
}
