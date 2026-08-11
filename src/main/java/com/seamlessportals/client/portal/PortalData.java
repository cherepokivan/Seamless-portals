package com.seamlessportals.client.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;

import java.util.UUID;

public class PortalData {
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

    public Box getBoundingBox() {
        return geometry.boundingBox;
    }

    public boolean isVisible(Vec3d cameraPos, Vec3d lookVec) {
        // Basic visibility check: is the camera in front of the portal plane?
        Vec3d toPortal = Vec3d.ofCenter(pos).subtract(cameraPos);
        double dot = toPortal.dotProduct(lookVec);
        return dot > -5.0; // Allow some margin
    }
}
