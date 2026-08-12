package com.seamlessportals.client.render;

import com.seamlessportals.client.network.RemotePortalSnapshot;
import com.seamlessportals.client.portal.PortalData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts server-authoritative destination voxels into 3D coordinates behind
 * a source portal. Because the result uses a real depth for every block, normal
 * camera movement produces perspective parallax rather than a moving 2D image.
 */
public final class RemoteTerrainGeometry {
    private RemoteTerrainGeometry() {
    }

    public static List<MappedVoxel> mapVisible(
        PortalData portal, RemotePortalSnapshot snapshot, Vec3 cameraPosition
    ) {
        double inverseScale = 1.0D / portal.destination.scale;
        Vec3 sourceCenter = portal.geometry.getCenter();
        Vec3 normal = portal.geometry.getNormal();
        double cameraSide = cameraPosition.subtract(sourceCenter).dot(normal) >= 0.0D ? 1.0D : -1.0D;
        Map<BlockPos, Byte> materials = new HashMap<>();
        for (RemotePortalSnapshot.Voxel voxel : snapshot.voxels()) {
            if (voxel.material() != RemotePortalSnapshot.AIR) {
                materials.put(snapshot.target().offset(voxel.x(), voxel.y(), voxel.z()), voxel.material());
            }
        }

        List<MappedVoxel> result = new ArrayList<>();
        for (RemotePortalSnapshot.Voxel voxel : snapshot.voxels()) {
            if (voxel.material() == RemotePortalSnapshot.AIR) {
                continue;
            }
            double horizontal = horizontalOffset(portal.axis, voxel) * inverseScale;
            double vertical = voxel.y();
            double depth = depthOffset(portal.axis, voxel) * inverseScale;
            Vec3 mappedCenter = mapToSourceSpace(portal.axis, sourceCenter, cameraSide, horizontal, vertical, depth);
            if (!isVisibleThroughPortal(portal, cameraPosition, mappedCenter)) {
                continue;
            }
            BlockPos absolute = snapshot.target().offset(voxel.x(), voxel.y(), voxel.z());
            int exposedFaces = exposedFaces(materials, absolute);
            if (exposedFaces != 0) {
                result.add(new MappedVoxel(mappedCenter, voxel.material(), exposedFaces));
            }
        }
        return List.copyOf(result);
    }

    private static double horizontalOffset(Direction.Axis axis, RemotePortalSnapshot.Voxel voxel) {
        return axis == Direction.Axis.X ? voxel.x() : voxel.z();
    }

    private static double depthOffset(Direction.Axis axis, RemotePortalSnapshot.Voxel voxel) {
        return axis == Direction.Axis.X ? voxel.z() : voxel.x();
    }

    private static Vec3 mapToSourceSpace(Direction.Axis axis, Vec3 sourceCenter, double cameraSide,
                                         double horizontal, double vertical, double depth) {
        // Place the target origin four units beyond the portal, then map its
        // depth away from the viewer. The reverse sign keeps terrain behind the
        // surface when the player looks from either side of the portal.
        double behind = -cameraSide * (4.0D + depth);
        if (axis == Direction.Axis.X) {
            return new Vec3(sourceCenter.x + horizontal, sourceCenter.y + vertical, sourceCenter.z + behind);
        }
        return new Vec3(sourceCenter.x + behind, sourceCenter.y + vertical, sourceCenter.z + horizontal);
    }

    private static boolean isVisibleThroughPortal(PortalData portal, Vec3 camera, Vec3 point) {
        Vec3 center = portal.geometry.getCenter();
        Direction.Axis axis = portal.axis;
        double plane = axis == Direction.Axis.X ? center.z : center.x;
        double cameraCoordinate = axis == Direction.Axis.X ? camera.z : camera.x;
        double pointCoordinate = axis == Direction.Axis.X ? point.z : point.x;
        double denominator = pointCoordinate - cameraCoordinate;
        if (Math.abs(denominator) < 0.00001D) {
            return false;
        }
        double t = (plane - cameraCoordinate) / denominator;
        if (t <= 0.0D || t >= 1.0D) {
            return false;
        }
        Vec3 hit = camera.add(point.subtract(camera).scale(t));
        double horizontal = axis == Direction.Axis.X ? hit.x - center.x : hit.z - center.z;
        return Math.abs(horizontal) <= portal.geometry.width * 0.5D + 0.6D
            && Math.abs(hit.y - center.y) <= portal.geometry.height * 0.5D + 0.6D;
    }

    private static int exposedFaces(Map<BlockPos, Byte> materials, BlockPos block) {
        int faces = 0;
        if (!materials.containsKey(block.north())) faces |= Face.NORTH.bit;
        if (!materials.containsKey(block.south())) faces |= Face.SOUTH.bit;
        if (!materials.containsKey(block.west())) faces |= Face.WEST.bit;
        if (!materials.containsKey(block.east())) faces |= Face.EAST.bit;
        if (!materials.containsKey(block.above())) faces |= Face.UP.bit;
        if (!materials.containsKey(block.below())) faces |= Face.DOWN.bit;
        return faces;
    }

    public record MappedVoxel(Vec3 center, byte material, int exposedFaces) {
        public boolean has(Face face) {
            return (exposedFaces & face.bit) != 0;
        }
    }

    public enum Face {
        NORTH(1), SOUTH(2), WEST(4), EAST(8), UP(16), DOWN(32);
        private final int bit;

        Face(int bit) {
            this.bit = bit;
        }
    }
}
