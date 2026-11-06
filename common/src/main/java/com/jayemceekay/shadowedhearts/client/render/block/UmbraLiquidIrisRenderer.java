package com.jayemceekay.shadowedhearts.client.render.block;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisHandler;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.render.IrisScaledWorldTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.LinkedHashMap;
import java.util.Map;

/** Replays tank liquid at Iris' world-color seam with the captured world matrices. */
public final class UmbraLiquidIrisRenderer {
    private static final int MAX_TANKS = 128;
    private static final Map<BlockPos, Capture> CAPTURES = new LinkedHashMap<>();
    private static ClientLevel frameLevel;
    private static ShaderInstance frameShader;
    private static ShaderInstance failedShader;
    private static boolean acceptingCaptures;
    private static boolean warnedFailure;

    private UmbraLiquidIrisRenderer() { }

    public static void beginFrame() {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        if (iris != null && iris.isShadowRenderActive()) return;
        CAPTURES.clear();
        frameLevel = Minecraft.getInstance().level;
        frameShader = ModShaders.UMBRA_LIQUID == failedShader ? null : ModShaders.UMBRA_LIQUID;
        acceptingCaptures = true;
    }

    /** False retains the atlas fallback; shadow-map passes deliberately omit the liquid. */
    public static boolean capture(BlockPos id, PoseStack.Pose pose,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  float seed, int quarterTurns, int light) {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        if (iris != null && iris.isShadowRenderActive()) return true;
        if (!acceptingCaptures || iris == null || !iris.isShaderPackInUse()
                || frameLevel == null || Minecraft.getInstance().level != frameLevel
                || frameShader == null || frameShader != ModShaders.UMBRA_LIQUID
                || !usableFrame(iris.getIrisRenderingSnapshot())
                || !pose.pose().isFinite() || !pose.normal().isFinite()
                || !Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)
                || !Float.isFinite(seed) || maxX <= minX || maxY <= minY || maxZ <= minZ
                || (!CAPTURES.containsKey(id) && CAPTURES.size() >= MAX_TANKS)) return false;
        // Copy the pose because the block-entity renderer immediately pops and reuses it.
        CAPTURES.put(id.immutable(), new Capture(pose.copy(), minX, minY, minZ,
                maxX, maxY, maxZ, seed, quarterTurns, light));
        return true;
    }

    public static boolean hasQueuedMeshes() { return !CAPTURES.isEmpty(); }

    /** An unsupported world-color seam must restore atlas rendering on the next frame. */
    public static void rejectFrame() {
        if (!CAPTURES.isEmpty()) failedShader = frameShader;
        acceptingCaptures = false;
        CAPTURES.clear();
    }

    /** Caller owns IrisWorldColorScope and has disabled Iris' shader override. */
    public static void renderIris() {
        acceptingCaptures = false;
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (CAPTURES.isEmpty() || iris == null || !iris.isShaderPackInUse()
                    || iris.isShadowRenderActive() || mc.level != frameLevel
                    || frameShader == null || frameShader != ModShaders.UMBRA_LIQUID) return;
            IrisHandler.IrisRenderingSnapshot snapshot = iris.getIrisRenderingSnapshot();
            if (!usableFrame(snapshot)) return;
            try (var state = DensityFboPipeline.preserveRenderState();
                 var target = IrisScaledWorldTarget.open(snapshot.renderWidth, snapshot.renderHeight,
                         snapshot.depthTexture)) {
                if (target == null) {
                    failedShader = frameShader;
                    return;
                }
                RenderSystem.viewport(0, 0, snapshot.renderWidth, snapshot.renderHeight);
                GL11.glViewport(0, 0, snapshot.renderWidth, snapshot.renderHeight);
                RenderSystem.setProjectionMatrix(snapshot.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.getModelViewMatrix().set(snapshot.modelViewMatrix);
                RenderSystem.setShader(() -> frameShader);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                mc.gameRenderer.lightTexture().turnOnLightLayer();
                RenderSystem.enableDepthTest();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                GL11.glDepthMask(false);
                RenderSystem.enableCull();
                GL11.glEnable(GL11.GL_CULL_FACE);
                RenderSystem.enableBlend();
                GL11.glEnable(GL11.GL_BLEND);
                RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
                RenderSystem.colorMask(true, true, true, true);
                GL11.glColorMask(true, true, true, true);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                try (ByteBufferBuilder memory = new ByteBufferBuilder(MAX_TANKS * 24 * 36)) {
                    BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS,
                            UmbraLiquidRenderTypes.FORMAT);
                    for (Capture tank : CAPTURES.values()) {
                        UmbraLiquidRenderTypes.emitVolume(builder, tank.pose(), tank.minX(), tank.minY(),
                                tank.minZ(), tank.maxX(), tank.maxY(), tank.maxZ(),
                                tank.seed(), tank.quarterTurns(), tank.light());
                    }
                    MeshData mesh = builder.build();
                    if (mesh != null) {
                        mesh.sortQuads(memory, VertexSorting.DISTANCE_TO_ORIGIN);
                        // Do not set up LIQUID's native Fabulous output target during Iris replay.
                        BufferUploader.drawWithShader(mesh);
                    }
                }
                if (!target.present()) throw new IllegalStateException("Unable to present Iris liquid target");
            }
        } catch (RuntimeException failure) {
            failedShader = frameShader;
            if (!warnedFailure) {
                warnedFailure = true;
                Shadowedhearts.LOGGER.warn("[Umbra Infuser] Iris liquid replay failed; retaining atlas material", failure);
            }
        } finally {
            CAPTURES.clear();
        }
    }

    private static boolean usableFrame(IrisHandler.IrisRenderingSnapshot snapshot) {
        return snapshot != null && snapshot.depthTexture > 0
                && snapshot.renderWidth > 0 && snapshot.renderHeight > 0
                && snapshot.renderWidth <= 16384 && snapshot.renderHeight <= 16384
                && (long) snapshot.renderWidth * snapshot.renderHeight <= 67108864L
                && snapshot.projectionMatrix.isFinite() && snapshot.modelViewMatrix.isFinite()
                && Float.isFinite(snapshot.projectionMatrix.determinant())
                && Float.isFinite(snapshot.modelViewMatrix.determinant())
                && Math.abs(snapshot.projectionMatrix.determinant()) > 1.0e-8f
                && Math.abs(snapshot.modelViewMatrix.determinant()) > 1.0e-8f
                && Math.abs(snapshot.projectionMatrix.m23()) > 1.0e-6f;
    }

    private record Capture(PoseStack.Pose pose, float minX, float minY, float minZ,
                           float maxX, float maxY, float maxZ, float seed, int quarterTurns, int light) { }
}
