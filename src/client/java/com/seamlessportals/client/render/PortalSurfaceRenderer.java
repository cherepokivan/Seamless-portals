package com.seamlessportals.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
 * Renders a visible animated surface over every detected vanilla Nether portal.
 *
 * <p>The implementation follows the extraction/drawing model introduced by
 * Minecraft 26.2. It never calls the removed WorldRenderer API directly.</p>
 */
public final class PortalSurfaceRenderer {
    private static final RenderPipeline PORTAL_SURFACE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SeamlessPortalsClient.MOD_ID, "portal_surface"))
            .withDepthStencilState(Optional.empty())
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

        List<PortalRenderState> states = new ArrayList<>();
        float pulse = (float) ((Math.sin(System.nanoTime() / 1_000_000_000.0D * 1.65D) + 1.0D) * 0.5D);
        for (PortalData portal : SeamlessPortalsClient.getPortalManager().getVisiblePortals()) {
            float red;
            float green;
            float blue;
            if (config.previewMode == PortalConfig.PreviewMode.MIRROR) {
                red = 0.18F + 0.12F * pulse;
                green = 0.62F + 0.18F * pulse;
                blue = 0.92F;
            } else {
                // Nether palette: clearly distinct from vanilla purple portal texture.
                red = 0.82F + 0.18F * pulse;
                green = 0.16F + 0.10F * pulse;
                blue = 0.03F + 0.08F * pulse;
            }

            states.add(new PortalRenderState(
                portal.geometry.getCenter(),
                portal.geometry.width,
                portal.geometry.height,
                portal.axis,
                red, green, blue, 0.94F
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
            formatBinding,
            primitive,
            primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
        );

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer vertices = STAGED_BUFFER.getVertexBuilder(draw);
        Matrix4fc matrix = matrices.last().pose();
        for (PortalRenderState state : states) {
            addPortalQuad(matrix, vertices, state);
        }
        matrices.popPose();

        STAGED_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
        if (info != null) {
            execute(info, PORTAL_SURFACE);
        }
        STAGED_BUFFER.endFrame();
    }

    private static void addPortalQuad(Matrix4fc matrix, VertexConsumer vertices, PortalRenderState state) {
        float halfWidth = state.width() * 0.5F;
        float halfHeight = state.height() * 0.5F;
        float yMin = (float) state.center().y - halfHeight + 0.015F;
        float yMax = (float) state.center().y + halfHeight - 0.015F;
        float xMin = (float) state.center().x - halfWidth + 0.015F;
        float xMax = (float) state.center().x + halfWidth - 0.015F;
        float zMin = (float) state.center().z - halfWidth + 0.015F;
        float zMax = (float) state.center().z + halfWidth - 0.015F;

        if (state.axis() == net.minecraft.core.Direction.Axis.X) {
            float z = (float) state.center().z;
            quad(matrix, vertices, xMin, yMin, z, xMax, yMax, z, state);
        } else {
            float x = (float) state.center().x;
            quad(matrix, vertices, x, yMin, zMin, x, yMax, zMax, state);
        }
    }

    private static void quad(Matrix4fc matrix, VertexConsumer vertices,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             PortalRenderState state) {
        vertices.addVertex(matrix, x1, y1, z1).setColor(state.red(), state.green(), state.blue(), state.alpha());
        vertices.addVertex(matrix, x2, y1, z2).setColor(state.red(), state.green(), state.blue(), state.alpha());
        vertices.addVertex(matrix, x2, y2, z2).setColor(state.red(), state.green(), state.blue(), state.alpha());
        vertices.addVertex(matrix, x1, y2, z1).setColor(state.red(), state.green(), state.blue(), state.alpha());
    }

    private static void execute(StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
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
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    public static void close() {
        STAGED_BUFFER.close();
    }

    private record PortalRenderState(
        Vec3 center, float width, float height, net.minecraft.core.Direction.Axis axis,
        float red, float green, float blue, float alpha
    ) {
    }

    private static final PortalSurfaceRenderer INSTANCE = new PortalSurfaceRenderer();
}
