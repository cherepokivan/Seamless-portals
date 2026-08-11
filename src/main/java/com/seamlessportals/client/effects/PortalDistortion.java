package com.seamlessportals.client.effects;

import com.seamlessportals.client.config.PortalConfig;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public class PortalDistortion {
    private PostEffectProcessor distortionEffect;

    public void apply(float strength) {
        // In a real mod, we would load a custom shader via PostEffectProcessor
        // and apply it to the portal's framebuffer.
    }
}
