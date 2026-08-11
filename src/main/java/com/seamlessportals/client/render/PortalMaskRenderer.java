package com.seamlessportals.client.render;

import com.seamlessportals.client.portal.PortalData;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

public class PortalMaskRenderer {
    public static void renderMask(PortalData portal, MatrixStack matrices, PortalFramebuffer pfb) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, pfb.getFramebuffer().getColorAttachment());
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        
        matrices.push();
        // Translate to portal position and rotate based on axis
        Vec3d center = portal.geometry.getCenter();
        matrices.translate(center.x, center.y, center.z);
        
        if (portal.axis == net.minecraft.util.math.Direction.Axis.X) {
            matrices.multiply(org.joml.Quaternionf.fromAxisAngleDeg(0, 1, 0, 90));
        }
        
        float hw = portal.geometry.width / 2.0f;
        float hh = portal.geometry.height / 2.0f;
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bufferBuilder.vertex(matrix, -hw, -hh, 0).texture(0, 0).next();
        bufferBuilder.vertex(matrix, hw, -hh, 0).texture(1, 0).next();
        bufferBuilder.vertex(matrix, hw, hh, 0).texture(1, 1).next();
        bufferBuilder.vertex(matrix, -hw, hh, 0).texture(0, 1).next();
        tessellator.draw();
        
        matrices.pop();
    }
}
