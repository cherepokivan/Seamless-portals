package com.seamlessportals.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seamlessportals.client.SeamlessPortalsClient;
import com.seamlessportals.client.config.PortalConfig;
import com.seamlessportals.client.portal.PortalData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Renders a depth-tested animated procedural Nether surface on detected portal
 * interiors. It uses the extraction/drawing renderer of Minecraft 26.2 and
 * therefore works without the legacy WorldRenderer API.
 */
public final class PortalSurfaceRenderer {
    private static final RenderPipeline PORTAL_SURFACE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SeamlessPortalsClient.MOD_ID, "portal_surface"))
            // DEFAULT performs the normal depth comparison, so opaque terrain
            // blocks correctly hide a portal located behind them.
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(
        () -> "Seamless Portals surface buffer", RenderType.SMALL_BUFFER_SIZE
    );

    private volatile List<PortalRenderState> renderStates = List.of();

    private PortalSurfaceRenderer() {
    }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(PortalSurfaceRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(PortalSurfaceRenderer::draw);
    }

    private static void extract(LevelExtractionContext context) {
        PortalConfig config = SeamlessPortalsClient.getConfig();
        if (!config.enabled || config.previewMode == PortalConfig.PreviewMode.DISABLED) {
            INSTANCE.renderStates = List.of();
            return;
        }

        float time = (float) (System.nanoTime() / 1_000_000_000.0D);
        List<PortalRenderState> states = new ArrayList<>();
        for (PortalData portal : SeamlessPortalsClient.getPortalManager().getVisiblePortals()) {
            if (RemotePortalRenderer.hasLiveTerrain(portal)) {
                continue;
            }
            states.add(new PortalRenderState(
                portal.geometry.getCenter(), portal.geometry.width, portal.geometry.height,
                portal.axis, config.previewMode, time
            ));
        }
        INSTANCE.renderStates = List.copyOf(states);
    }

    private static void draw(LevelRenderContext context) {
        List<PortalRenderState> states = INSTANCE.renderStates;
        if (states.isEmpty()) {
            return;
        }

        VertexFormat formatBinding = PORTAL_SURFACE.getVertexFormatBinding(0);
        if (formatBinding == null) {
            return;
        }

        PrimitiveTopology primitive = PORTAL_SURFACE.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(
            formatBinding, primitive,
            primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
        );

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer vertices = STAGED_BUFFER.getVertexBuilder(draw);
        Matrix4fc matrix = matrices.last().pose();
        for (PortalRenderState state : states) {
            renderNetherPattern(matrix, vertices, state);
        }
        matrices.popPose();

        STAGED_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
        if (info != null) {
            execute(info);
        }
        STAGED_BUFFER.endFrame();
    }

    /**
     * Generates an animated lava-and-shadow pattern from geometry instead of a
     * single color. It avoids external image assets, which also avoids mobile
     * texture-upload issues in third-party launchers.
     */
    private static void renderNetherPattern(Matrix4fc matrix, VertexConsumer vertices, PortalRenderState state) {
        int columns = Math.max(10, Math.round(state.width() * 8.0F));
        int rows = Math.max(14, Math.round(state.height() * 8.0F));
        float halfWidth = state.width() * 0.5F;
        float halfHeight = state.height() * 0.5F;
        float cellWidth = state.width() / columns;
        float cellHeight = state.height() / rows;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float u = (column + 0.5F) / columns;
                float v = (row + 0.5F) / rows;
                Color color = colorAt(u, v, state.time(), state.previewMode());

                float horizontalMin = -halfWidth + column * cellWidth + 0.0025F;
                float horizontalMax = horizontalMin + cellWidth - 0.005F;
                float yMin = (float) state.center().y - halfHeight + row * cellHeight + 0.0025F;
                float yMax = yMin + cellHeight - 0.005F;

                if (state.axis() == net.minecraft.core.Direction.Axis.X) {
                    float z = (float) state.center().z;
                    float xMin = (float) state.center().x + horizontalMin;
                    float xMax = (float) state.center().x + horizontalMax;
                    quad(matrix, vertices, xMin, yMin, z, xMax, yMax, z, color);
                } else {
                    float x = (float) state.center().x;
                    float zMin = (float) state.center().z + horizontalMin;
                    float zMax = (float) state.center().z + horizontalMax;
                    quad(matrix, vertices, x, yMin, zMin, x, yMax, zMax, color);
                }
            }
        }
    }

    private static Color colorAt(float u, float v, float time, PortalConfig.PreviewMode mode) {
        float flowing = (float) (
            Math.sin((u * 17.0F) + (time * 2.15F) + Math.sin(v * 9.0F - time * 0.8F) * 2.0F)
                + Math.sin(v * 21.0F - time * 1.45F)
        ) * 0.25F + 0.5F;
        float vein = flowing * flowing;

        if (mode == PortalConfig.PreviewMode.MIRROR) {
            return new Color(0.03F + vein * 0.12F, 0.16F + vein * 0.40F, 0.28F + vein * 0.62F);
        }
        return new Color(0.09F + vein * 0.82F, 0.01F + vein * 0.24F, 0.006F + vein * 0.055F);
    }

    private static void quad(Matrix4fc matrix, VertexConsumer vertices,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             Color color) {
        vertices.addVertex(matrix, x1, y1, z1).setColor(color.red(), color.green(), color.blue(), 1.0F);
        vertices.addVertex(matrix, x2, y1, z2).setColor(color.red(), color.green(), color.blue(), 1.0F);
        vertices.addVertex(matrix, x2, y2, z2).setColor(color.red(), color.green(), color.blue(), 1.0F);
        vertices.addVertex(matrix, x1, y2, z1).setColor(color.red(), color.green(), color.blue(), 1.0F);
    }

    private static void execute(StagedVertexBuffer.ExecuteInfo info) {
        Minecraft client = Minecraft.getInstance();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX
        );
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = target.getColorTextureView();
        if (colorTexture == null) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "Seamless Portals portal surface", colorTexture, Optional.empty(),
            target.getDepthTextureView(), OptionalDouble.empty()
        )) {
            pass.setPipeline(PORTAL_SURFACE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    /**
     * Drops geometry extracted for a previous world before its render pass can
     * be reused after a connection or level transition.
     */
    public static void clear() {
        INSTANCE.renderStates = List.of();
    }

    public static void close() {
        clear();
        STAGED_BUFFER.close();
    }

    private record PortalRenderState(
        Vec3 center, float width, float height, net.minecraft.core.Direction.Axis axis,
        PortalConfig.PreviewMode previewMode, float time
    ) {
    }

    private record Color(float red, float green, float blue) {
    }

    private static final PortalSurfaceRenderer INSTANCE = new PortalSurfaceRenderer();
}
