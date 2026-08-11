package com.seamlessportals.client.portal;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Client-side detector for loaded vanilla Nether portal blocks.
 */
public final class PortalDetector {
    private final List<PortalData> activePortals = new ArrayList<>();
    private long lastScanTime;

    public List<PortalData> getActivePortals(Minecraft client) {
        if (client.level == null || client.player == null) {
            return List.of();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime >= 1_000L) {
            scanNearby(client);
            lastScanTime = currentTime;
        }
        return List.copyOf(activePortals);
    }

    private void scanNearby(Minecraft client) {
        Level level = client.level;
        BlockPos playerPosition = client.player.blockPosition();
        int radius = 32;
        activePortals.clear();
        Set<BlockPos> visited = new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPosition = playerPosition.offset(x, y, z);
                    if (!visited.add(checkPosition)) {
                        continue;
                    }
                    if (level.getBlockState(checkPosition).is(Blocks.NETHER_PORTAL)) {
                        PortalData portal = detectPortalAt(level, checkPosition, visited);
                        if (portal != null) {
                            activePortals.add(portal);
                        }
                    }
                }
            }
        }
    }

    private PortalData detectPortalAt(Level level, BlockPos startPosition, Set<BlockPos> visited) {
        Direction.Axis axis = level.getBlockState(startPosition).getValue(NetherPortalBlock.AXIS);
        BlockPos min = startPosition;
        BlockPos max = startPosition;
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPosition);

        while (!queue.isEmpty()) {
            BlockPos position = queue.remove();
            min = new BlockPos(
                Math.min(min.getX(), position.getX()),
                Math.min(min.getY(), position.getY()),
                Math.min(min.getZ(), position.getZ())
            );
            max = new BlockPos(
                Math.max(max.getX(), position.getX()),
                Math.max(max.getY(), position.getY()),
                Math.max(max.getZ(), position.getZ())
            );

            for (Direction direction : Direction.values()) {
                if (direction.getAxis() == Direction.Axis.Y || direction.getAxis() == axis) {
                    BlockPos neighbour = position.relative(direction);
                    if (visited.add(neighbour) && level.getBlockState(neighbour).is(Blocks.NETHER_PORTAL)) {
                        queue.add(neighbour);
                    }
                }
            }
        }

        PortalGeometry geometry = new PortalGeometry(min, max, axis);
        PortalDestination destination = PortalDestination.calculate(level, min, axis);
        return new PortalData(min, axis, geometry, destination);
    }
}
