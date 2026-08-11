package com.seamlessportals.client.effects;

import net.minecraft.core.BlockPos;

/**
 * Stores a particle emission request for the Minecraft 26.2 renderer bridge.
 */
public final class PortalParticles {
    private BlockPos position = BlockPos.ZERO;
    private float density;

    public void spawn(BlockPos position, float density) {
        this.position = position;
        this.density = Math.clamp(density, 0.0F, 1.0F);
    }

    public BlockPos position() {
        return position;
    }

    public float density() {
        return density;
    }
}
