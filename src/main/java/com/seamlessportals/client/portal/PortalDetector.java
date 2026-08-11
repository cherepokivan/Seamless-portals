package com.seamlessportals.client.portal;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class PortalDetector {
    private final Set<BlockPos> knownPortalBlocks = new HashSet<>();
    private final List<PortalData> activePortals = new ArrayList<>();
    private long lastScanTime = 0;

    public List<PortalData> getActivePortals(MinecraftClient client) {
        World world = client.world;
        if (world == null) return new ArrayList<>();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime > 1000) { // Scan every second
            scanNearby(client);
            lastScanTime = currentTime;
        }

        return activePortals;
    }

    private void scanNearby(MinecraftClient client) {
        BlockPos playerPos = client.player.getBlockPos();
        int radius = 32;
        
        activePortals.clear();
        Set<BlockPos> visited = new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (visited.contains(checkPos)) continue;
                    
                    if (client.world.getBlockState(checkPos).isOf(Blocks.NETHER_PORTAL)) {
                        PortalData portal = detectPortalAt(client.world, checkPos, visited);
                        if (portal != null) {
                            activePortals.add(portal);
                        }
                    }
                }
            }
        }
    }

    private PortalData detectPortalAt(World world, BlockPos startPos, Set<BlockPos> visited) {
        // Simple flood fill to find the portal bounds
        Direction.Axis axis = world.getBlockState(startPos).get(net.minecraft.block.NetherPortalBlock.AXIS);
        
        BlockPos min = startPos;
        BlockPos max = startPos;
        
        List<BlockPos> portalBlocks = new ArrayList<>();
        List<BlockPos> queue = new ArrayList<>();
        queue.add(startPos);
        visited.add(startPos);
        
        int i = 0;
        while (i < queue.size()) {
            BlockPos pos = queue.get(i++);
            portalBlocks.add(pos);
            
            // Update min/max
            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
            
            // Check neighbors on the same axis plane
            for (Direction dir : Direction.values()) {
                if (dir.getAxis() == Direction.Axis.Y || dir.getAxis() == axis) {
                    BlockPos neighbor = pos.offset(dir);
                    if (!visited.contains(neighbor) && world.getBlockState(neighbor).isOf(Blocks.NETHER_PORTAL)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        
        PortalGeometry geometry = new PortalGeometry(min, max, axis);
        PortalDestination destination = PortalDestination.calculate(world, min, axis);
        
        return new PortalData(min, axis, geometry, destination);
    }
}
