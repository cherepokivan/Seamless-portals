package com.seamlessportals.client.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Immutable terrain and atmosphere data sent by the Paper integration for one
 * source portal. Positions in {@code voxels} are offsets from {@code target}.
 */
public record RemotePortalSnapshot(
    BlockPos sourcePortal,
    BlockPos target,
    Direction.Axis axis,
    Identifier targetDimension,
    long dayTime,
    boolean storming,
    List<Voxel> voxels,
    long receivedAtMillis
) {
    public static final int MAX_VOXELS = 6_000;
    public static final int AIR = 0;

    public RemotePortalSnapshot {
        voxels = List.copyOf(voxels);
        if (voxels.size() > MAX_VOXELS) {
            throw new IllegalArgumentException("Snapshot exceeds maximum voxel count");
        }
    }

    public boolean isFresh(long nowMillis) {
        return nowMillis - receivedAtMillis < 10_000L;
    }

    public record Voxel(byte x, byte y, byte z, byte material) {
    }
}
