package com.seamlessportals.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.Framebuffer;
import com.mojang.blaze3d.systems.RenderSystem;

public class PortalFramebuffer {
    private Framebuffer framebuffer;
    private int width;
    private int height;

    public PortalFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        RenderSystem.assertOnRenderThread();
        this.framebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        this.framebuffer.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth != width || newHeight != height) {
            this.width = newWidth;
            this.height = newHeight;
            if (this.framebuffer != null) {
                this.framebuffer.delete();
            }
            init();
        }
    }

    public Framebuffer getFramebuffer() {
        return framebuffer;
    }

    public void beginRead() {
        this.framebuffer.beginRead();
    }

    public void endRead() {
        this.framebuffer.endRead();
    }

    public void beginWrite(boolean setViewport) {
        this.framebuffer.beginWrite(setViewport);
    }

    public void endWrite() {
        this.framebuffer.endWrite();
    }

    public void delete() {
        if (this.framebuffer != null) {
            this.framebuffer.delete();
            this.framebuffer = null;
        }
    }
}
