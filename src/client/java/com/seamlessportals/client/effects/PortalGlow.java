package com.seamlessportals.client.effects;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable glow request consumed by the renderer bridge.
 */
public final class PortalGlow {
    private Vec3 position = Vec3.ZERO;
    private float strength;

    public void render(Vec3 position, float strength) {
        this.position = position;
        this.strength = Math.clamp(strength, 0.0F, 1.0F);
    }

    public Vec3 position() {
        return position;
    }

    public float strength() {
        return strength;
    }
}
