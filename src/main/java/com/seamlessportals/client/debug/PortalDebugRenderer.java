package com.seamlessportals.client.debug;

import com.seamlessportals.client.portal.PortalData;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class PortalDebugRenderer {
    public static void render(MatrixStack matrices, List<PortalData> portals, Vec3d cameraPos) {
        if (portals == null) return;

        for (PortalData portal : portals) {
            renderPortalDebug(matrices, portal, cameraPos);
        }
    }

    private static void renderPortalDebug(MatrixStack matrices, PortalData portal, Vec3d cameraPos) {
        Box box = portal.getBoundingBox();
        
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        // Draw bounding box
        WorldRenderer.drawBox(matrices, Tessellator.getInstance().getBuffer(), 
            box.minX, box.minY, box.minZ, 
            box.maxX, box.maxY, box.maxZ, 
            1.0f, 1.0f, 0.0f, 1.0f); // Yellow for box
            
        // Draw normal vector
        Vec3d center = portal.geometry.getCenter();
        Vec3d normal = portal.geometry.getNormal().multiply(2.0);
        
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix, (float)center.x, (float)center.y, (float)center.z).color(1.0f, 0.0f, 0.0f, 1.0f).next();
        bufferBuilder.vertex(matrix, (float)(center.x + normal.x), (float)(center.y + normal.y), (float)(center.z + normal.z)).color(1.0f, 0.0f, 0.0f, 1.0f).next();
        tessellator.draw();
        
        matrices.pop();
    }
}
