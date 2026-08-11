package com.seamlessportals.client.render;

/**
 * Tracks the render-target allocation requested for one portal.
 *
 * <p>Minecraft 26.2 replaced the former framebuffer API. Actual GPU target
 * binding is isolated behind this class so that the portal pipeline can keep
 * its allocation and adaptive-quality decisions independent from renderer
 * backend changes.</p>
 */
public final class PortalFramebuffer {
    private int width;
    private int height;
    private boolean disposed;

    public PortalFramebuffer(int width, int height) {
        resize(width, height);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            throw new IllegalArgumentException("Portal framebuffer dimensions must be positive");
        }
        this.width = newWidth;
        this.height = newHeight;
        this.disposed = false;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public void dispose() {
        disposed = true;
    }
}
