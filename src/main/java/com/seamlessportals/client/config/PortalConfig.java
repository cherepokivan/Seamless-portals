package com.seamlessportals.client.config;

public class PortalConfig {
    public boolean enabled = true;
    public IntegrationMode serverIntegration = IntegrationMode.AUTO;
    public Quality portalQuality = Quality.HIGH;
    public int portalRenderDistance = 32;
    public int portalUpdateRate = 60;
    public int maxVisiblePortals = 4;
    public boolean recursiveRendering = true;
    public int recursionDepth = 1;
    public boolean distortion = true;
    public float distortionStrength = 0.25f;
    public boolean glow = true;
    public float glowStrength = 0.6f;
    public boolean particles = true;
    public float particleDensity = 0.5f;
    public boolean audioEnhancements = true;
    public boolean adaptiveQuality = true;
    public TargetFps targetFps = TargetFps.AUTO;
    public float portalRenderBudgetMs = 4.0f;
    public int smallPortalThreshold = 16;
    public PreviewMode previewMode = PreviewMode.NETHER;

    public enum PreviewMode {
        NETHER, MIRROR, DISABLED
    }

    public enum IntegrationMode {
        AUTO, PURE_CLIENT, ENHANCED
    }

    public enum Quality {
        LOW, MEDIUM, HIGH, ULTRA, AUTO
    }

    public enum TargetFps {
        FPS_30(30), FPS_60(60), FPS_90(90), FPS_120(120), FPS_144(144), FPS_165(165), FPS_240(240), AUTO(0);
        
        private final int value;
        TargetFps(int value) { this.value = value; }
        public int getValue() { return value; }
    }
}
