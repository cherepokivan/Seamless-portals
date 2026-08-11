package com.seamlessportals.client.effects;

/**
 * Stores the requested portal distortion intensity for the 26.2 renderer
 * bridge. GPU application is intentionally renderer-backend specific.
 */
public final class PortalDistortion {
    private float strength;

    public void apply(float strength) {
        this.strength = Math.clamp(strength, 0.0F, 1.0F);
    }

    public float strength() {
        return strength;
    }
}
