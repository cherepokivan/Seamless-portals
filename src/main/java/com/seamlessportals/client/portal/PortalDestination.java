package com.seamlessportals.client.portal;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PortalDestination {
    public final RegistryKey<World> dimension;
    public final BlockPos pos;
    public final Direction.Axis axis;
    public final double scale;

    public PortalDestination(RegistryKey<World> dimension, BlockPos pos, Direction.Axis axis, double scale) {
        this.dimension = dimension;
        this.pos = pos;
        this.axis = axis;
        this.scale = scale;
    }

    public static PortalDestination calculate(World currentWorld, BlockPos portalPos, Direction.Axis sourceAxis) {
        RegistryKey<World> targetDim;
        double scale;
        
        if (currentWorld.getRegistryKey() == World.OVERWORLD) {
            targetDim = World.NETHER;
            scale = 0.125; // 1/8
        } else if (currentWorld.getRegistryKey() == World.NETHER) {
            targetDim = World.OVERWORLD;
            scale = 8.0;
        } else {
            targetDim = World.OVERWORLD;
            scale = 1.0;
        }
        
        BlockPos targetPos = new BlockPos(
            (int)(portalPos.getX() * scale),
            portalPos.getY(),
            (int)(portalPos.getZ() * scale)
        );
        
        // In a real scenario, we would search for an actual portal at targetPos
        // For this prototype, we assume the same axis for simplicity or a default
        return new PortalDestination(targetDim, targetPos, sourceAxis, scale);
    }

    public Matrix4f getTransformationMatrix(Vec3d sourceCenter, Vec3d destCenter, Direction.Axis sourceAxis, Direction.Axis destAxis) {
        Matrix4f matrix = new Matrix4f();
        
        // 1. Translate to source portal center
        matrix.translate(new Vector3f((float)sourceCenter.x, (float)sourceCenter.y, (float)sourceCenter.z));
        
        // 2. Apply rotation if axes differ
        if (sourceAxis != destAxis) {
            // Rotate 90 degrees around Y axis as an example
            matrix.rotate(new Quaternionf().fromAxisAngleDeg(0, 1, 0, 90));
        }
        
        // 3. Apply scaling
        matrix.scale((float)scale, 1.0f, (float)scale);
        
        // 4. Translate to destination portal center
        matrix.translate(new Vector3f((float)-destCenter.x, (float)-destCenter.y, (float)-destCenter.z));
        
        return matrix.invert();
    }

    public Vec3d transformCamera(Vec3d cameraPos, Vec3d sourceCenter) {
        Vec3d relative = cameraPos.subtract(sourceCenter);
        Vec3d targetCenter = Vec3d.ofCenter(pos);
        
        // Apply scaling (X and Z only for Nether/Overworld)
        double tx = relative.x * scale;
        double ty = relative.y; // Y is not scaled in vanilla portals
        double tz = relative.z * scale;
        
        return targetCenter.add(tx, ty, tz);
    }
}
