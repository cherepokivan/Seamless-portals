package com.seamlessportals.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seamlessportals.client.SeamlessPortalsClient;
import com.seamlessportals.client.network.PortalWorldSync;
import com.seamlessportals.client.network.RemotePortalSnapshot;
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
 * Renders an immutable Paper-provided destination snapshot as actual 3D voxel
 * geometry. Every block retains a different depth after portal transformation,
 * so it produces normal perspective parallax when the local camera moves.
 */
public final class RemotePortalRenderer {
    private static final RenderPipeline SKY_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SeamlessPortalsClient.MOD_ID, "portal_remote_sky"))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build()
    );
    private static final RenderPipeline TERRAIN_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SeamlessPortalsClient.MOD_ID, "portal_remote_terrain"))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build()
    );
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(
        () -> "Seamless Portals remote terrain buffer", RenderType.BIG_BUFFER_SIZE
    );
    private static final RemotePortalRenderer INSTANCE = new RemotePortalRenderer();

    private volatile List<RenderState> states = List.of();

    private RemotePortalRenderer() {
    }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(RemotePortalRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(RemotePortalRenderer::draw);
    }

    public static boolean hasLiveTerrain(PortalData portal) {
        return PortalWorldSync.hasSnapshot(portal);
    }

    public static void clear() {
        INSTANCE.states = List.of();
    }

    private static void extract(LevelExtractionContext context) {
        if (!SeamlessPortalsClient.getConfig().enabled) {
            INSTANCE.states = List.of();
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        List<RenderState> extracted = new ArrayList<>();
        for (PortalData portal : SeamlessPortalsClient.getPortalManager().getVisiblePortals()) {
            RemotePortalSnapshot snapshot = PortalWorldSync.getSnapshot(portal);
            if (snapshot == null) {
                continue;
            }
            List<RemoteTerrainGeometry.MappedVoxel> voxels = RemoteTerrainGeometry.mapVisible(portal, snapshot, camera);
            extracted.add(new RenderState(portal, snapshot, List.copyOf(voxels), skyColor(snapshot)));
        }
        INSTANCE.states = List.copyOf(extracted);
    }

    private static void draw(LevelRenderContext context) {
        List<RenderState> renderStates = INSTANCE.states;
        if (renderStates.isEmpty()) {
            return;
        }
        VertexFormat skyFormat = SKY_PIPELINE.getVertexFormatBinding(0);
        VertexFormat terrainFormat = TERRAIN_PIPELINE.getVertexFormatBinding(0);
        if (skyFormat == null || terrainFormat == null) {
            return;
        }
        PrimitiveTopology primitive = TERRAIN_PIPELINE.getPrimitiveTopology();
        StagedVertexBuffer.Draw skyDraw = STAGED_BUFFER.appendDraw(
            skyFormat, primitive,
            primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
        );
        StagedVertexBuffer.Draw terrainDraw = STAGED_BUFFER.appendDraw(
            terrainFormat, primitive,
            primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
        );

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4fc matrix = matrices.last().pose();
        VertexConsumer skyVertices = STAGED_BUFFER.getVertexBuilder(skyDraw);
        VertexConsumer terrainVertices = STAGED_BUFFER.getVertexBuilder(terrainDraw);
        for (RenderState state : renderStates) {
            renderSky(matrix, skyVertices, state.portal(), state.sky());
            for (RemoteTerrainGeometry.MappedVoxel voxel : state.voxels()) {
                renderVoxel(matrix, terrainVertices, voxel, state.portal().destination.scale, state.snapshot());
            }
        }
        matrices.popPose();

        STAGED_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo skyInfo = STAGED_BUFFER.getExecuteInfo(skyDraw);
        if (skyInfo != null) {
            execute(skyInfo, SKY_PIPELINE);
        }
        StagedVertexBuffer.ExecuteInfo terrainInfo = STAGED_BUFFER.getExecuteInfo(terrainDraw);
        if (terrainInfo != null) {
            execute(terrainInfo, TERRAIN_PIPELINE);
        }
        STAGED_BUFFER.endFrame();
    }

    private static void renderSky(Matrix4fc matrix, VertexConsumer vertices, PortalData portal, Color color) {
        Vec3 center = portal.geometry.getCenter();
        float halfWidth = portal.geometry.width * 0.5F - 0.015F;
        float halfHeight = portal.geometry.height * 0.5F - 0.015F;
        if (portal.axis == net.minecraft.core.Direction.Axis.X) {
            quad(matrix, vertices,
                (float) center.x - halfWidth, (float) center.y - halfHeight, (float) center.z,
                (float) center.x + halfWidth, (float) center.y + halfHeight, (float) center.z,
                color
            );
        } else {
            quad(matrix, vertices,
                (float) center.x, (float) center.y - halfHeight, (float) center.z - halfWidth,
                (float) center.x, (float) center.y + halfHeight, (float) center.z + halfWidth,
                color
            );
        }
    }

    private static void renderVoxel(Matrix4fc matrix, VertexConsumer vertices,
                                    RemoteTerrainGeometry.MappedVoxel voxel, double sourceToTargetScale,
                                    RemotePortalSnapshot snapshot) {
        double horizontalHalf = 0.5D / sourceToTargetScale;
        double verticalHalf = 0.5D;
        Vec3 c = voxel.center();
        float xMin = (float) (c.x - horizontalHalf);
        float xMax = (float) (c.x + horizontalHalf);
        float yMin = (float) (c.y - verticalHalf);
        float yMax = (float) (c.y + verticalHalf);
        float zMin = (float) (c.z - horizontalHalf);
        float zMax = (float) (c.z + horizontalHalf);
        Color base = materialColor(voxel.material(), snapshot);

        if (voxel.has(RemoteTerrainGeometry.Face.NORTH)) faceNorth(matrix, vertices, xMin, xMax, yMin, yMax, zMin, shade(base, 0.72F));
        if (voxel.has(RemoteTerrainGeometry.Face.SOUTH)) faceSouth(matrix, vertices, xMin, xMax, yMin, yMax, zMax, shade(base, 0.86F));
        if (voxel.has(RemoteTerrainGeometry.Face.WEST)) faceWest(matrix, vertices, xMin, yMin, yMax, zMin, zMax, shade(base, 0.68F));
        if (voxel.has(RemoteTerrainGeometry.Face.EAST)) faceEast(matrix, vertices, xMax, yMin, yMax, zMin, zMax, shade(base, 0.80F));
        if (voxel.has(RemoteTerrainGeometry.Face.UP)) faceUp(matrix, vertices, xMin, xMax, yMax, zMin, zMax, shade(base, 1.12F));
        if (voxel.has(RemoteTerrainGeometry.Face.DOWN)) faceDown(matrix, vertices, xMin, xMax, yMin, zMin, zMax, shade(base, 0.48F));
    }

    private static void quad(Matrix4fc matrix, VertexConsumer vertices,
                             float x1, float y1, float z1, float x2, float y2, float z2, Color color) {
        vertices.addVertex(matrix, x1, y1, z1).setColor(color.r(), color.g(), color.b(), 1.0F);
        vertices.addVertex(matrix, x2, y1, z2).setColor(color.r(), color.g(), color.b(), 1.0F);
        vertices.addVertex(matrix, x2, y2, z2).setColor(color.r(), color.g(), color.b(), 1.0F);
        vertices.addVertex(matrix, x1, y2, z1).setColor(color.r(), color.g(), color.b(), 1.0F);
    }

    private static void faceNorth(Matrix4fc m, VertexConsumer v, float xl, float xh, float yl, float yh, float z, Color c) { quad(m, v, xh, yl, z, xl, yh, z, c); }
    private static void faceSouth(Matrix4fc m, VertexConsumer v, float xl, float xh, float yl, float yh, float z, Color c) { quad(m, v, xl, yl, z, xh, yh, z, c); }
    private static void faceWest(Matrix4fc m, VertexConsumer v, float x, float yl, float yh, float zl, float zh, Color c) { quad(m, v, x, yl, zh, x, yh, zl, c); }
    private static void faceEast(Matrix4fc m, VertexConsumer v, float x, float yl, float yh, float zl, float zh, Color c) { quad(m, v, x, yl, zl, x, yh, zh, c); }
    private static void faceUp(Matrix4fc m, VertexConsumer v, float xl, float xh, float y, float zl, float zh, Color c) { quad(m, v, xl, y, zh, xh, y, zl, c); }
    private static void faceDown(Matrix4fc m, VertexConsumer v, float xl, float xh, float y, float zl, float zh, Color c) { quad(m, v, xl, y, zl, xh, y, zh, c); }

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
            () -> "Seamless Portals remote terrain", colorTexture, Optional.empty(),
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

    private static Color skyColor(RemotePortalSnapshot snapshot) {
        if (snapshot.targetDimension().getPath().contains("the_nether")) {
            return new Color(0.20F, 0.045F, 0.018F);
        }
        float day = ((snapshot.dayTime() % 24_000L) / 24_000.0F);
        float sun = Math.max(0.0F, (float) Math.sin((day - 0.25F) * Math.PI * 2.0F));
        if (snapshot.storming()) {
            sun *= 0.45F;
        }
        return new Color(0.025F + sun * 0.38F, 0.045F + sun * 0.52F, 0.12F + sun * 0.68F);
    }

    private static Color materialColor(byte material, RemotePortalSnapshot snapshot) {
        Color base = switch (material) {
            case 1 -> new Color(0.25F, 0.55F, 0.12F); // grass
            case 2 -> new Color(0.38F, 0.23F, 0.11F); // dirt
            case 3 -> new Color(0.43F, 0.43F, 0.45F); // stone
            case 4 -> new Color(0.06F, 0.30F, 0.78F); // water
            case 5 -> new Color(0.12F, 0.40F, 0.10F); // leaves
            case 6 -> new Color(0.37F, 0.22F, 0.08F); // wood
            case 7 -> new Color(0.76F, 0.68F, 0.42F); // sand
            case 8 -> new Color(0.90F, 0.94F, 0.98F); // snow
            case 9 -> new Color(0.42F, 0.74F, 0.88F); // ice
            case 10 -> new Color(0.95F, 0.19F, 0.015F); // lava
            case 11 -> new Color(0.44F, 0.10F, 0.045F); // netherrack
            case 12 -> new Color(0.23F, 0.24F, 0.24F); // basalt
            case 13 -> new Color(0.10F, 0.095F, 0.095F); // blackstone
            case 14 -> new Color(0.82F, 0.78F, 0.67F); // quartz
            case 15 -> new Color(0.25F, 0.05F, 0.05F); // nether bricks
            case 16 -> new Color(0.86F, 0.84F, 0.52F); // end stone
            case 17 -> new Color(0.20F, 0.22F, 0.24F); // deepslate
            case 19 -> new Color(0.35F, 0.60F, 0.70F); // ore
            case 20 -> new Color(0.55F, 0.76F, 0.83F); // glass
            default -> new Color(0.50F, 0.38F, 0.28F);
        };
        return shade(base, terrainLight(snapshot));
    }

    private static float terrainLight(RemotePortalSnapshot snapshot) {
        if (snapshot.targetDimension().getPath().contains("the_nether")) {
            return 0.68F;
        }
        float day = ((snapshot.dayTime() % 24_000L) / 24_000.0F);
        float sun = Math.max(0.0F, (float) Math.sin((day - 0.25F) * Math.PI * 2.0F));
        float light = 0.22F + sun * 0.78F;
        return snapshot.storming() ? light * 0.70F : light;
    }

    private static Color shade(Color color, float multiplier) {
        return new Color(
            Math.min(1.0F, color.r() * multiplier),
            Math.min(1.0F, color.g() * multiplier),
            Math.min(1.0F, color.b() * multiplier)
        );
    }

    private record RenderState(PortalData portal, RemotePortalSnapshot snapshot,
                               List<RemoteTerrainGeometry.MappedVoxel> voxels, Color sky) {
    }

    private record Color(float r, float g, float b) {
    }
}
