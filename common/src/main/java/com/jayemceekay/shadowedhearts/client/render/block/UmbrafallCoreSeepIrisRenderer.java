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
import java.util.List;
import java.util.Map;

/** Replays exposed-core ink at Iris' world-color seam, before pack compositing. */
public final class UmbrafallCoreSeepIrisRenderer {
    private static final int MAX_CORES = 128;
    private static final int MAX_QUADS_PER_CORE = 4096;
    private static final int MAX_FRAME_QUADS = 16384;
    private static final Map<BlockPos, Capture> CAPTURES = new LinkedHashMap<>();
    private static ClientLevel frameLevel;
    private static ShaderInstance frameShader;
    private static ShaderInstance failedShader;
    private static int queuedQuads;
    private static boolean acceptingCaptures;
    private static boolean warnedFailure;

    private UmbrafallCoreSeepIrisRenderer() { }

    public static void beginFrame() {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        // A nested shadow pass must not discard the pending world-color meshes.
        if (iris != null && iris.isShadowRenderActive()) return;
        clearCaptures();
        ClientLevel level = Minecraft.getInstance().level;
        if (level != frameLevel || (failedShader != null && failedShader != ModShaders.UMBRAFALL_CORE_SEEP)) {
            failedShader = null;
            warnedFailure = false;
        }
        frameLevel = level;
        frameShader = ModShaders.UMBRAFALL_CORE_SEEP == failedShader ? null : ModShaders.UMBRAFALL_CORE_SEEP;
        acceptingCaptures = level != null && frameShader != null && iris != null && iris.isShaderPackInUse();
    }

    /** A rejected capture leaves the baked core visible without a mismatched custom pass. */
    public static boolean capture(BlockPos id, PoseStack.Pose pose,
                                  List<UmbrafallCoreSeepGeometry.Quad> quads, int light) {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        if (iris != null && iris.isShadowRenderActive()) return false;
        if (!acceptingCaptures || iris == null || !iris.isShaderPackInUse()
                || frameLevel == null || Minecraft.getInstance().level != frameLevel
                || frameShader == null || frameShader != ModShaders.UMBRAFALL_CORE_SEEP
                || !usableFrame(iris.getIrisRenderingSnapshot())
                || id == null || pose == null || !pose.pose().isFinite() || !pose.normal().isFinite()
                || quads == null || quads.isEmpty() || quads.size() > MAX_QUADS_PER_CORE
                || (!CAPTURES.containsKey(id) && CAPTURES.size() >= MAX_CORES)) return false;
        Capture previous = CAPTURES.get(id);
        int replacedQuads = previous == null ? 0 : previous.quads().size();
        if (queuedQuads - replacedQuads + quads.size() > MAX_FRAME_QUADS) return false;
        for (UmbrafallCoreSeepGeometry.Quad quad : quads) {
            if (quad == null || !quad.isFinite()) return false;
        }
        // Geometry records are immutable; the list and pose can be reused by the caller.
        CAPTURES.put(id.immutable(), new Capture(pose.copy(), List.copyOf(quads), light));
        queuedQuads += quads.size() - replacedQuads;
        return true;
    }

    public static boolean hasQueuedMeshes() { return !CAPTURES.isEmpty(); }

    /** Unsupported pack targets stay on the baked texture until shader reload or world change. */
    public static void rejectFrame() {
        if (!CAPTURES.isEmpty()) failedShader = frameShader;
        acceptingCaptures = false;
        clearCaptures();
    }

    /** Release all frame/world references on disconnect and renderer reset. */
    public static void clear() {
        clearCaptures();
        frameLevel = null;
        frameShader = null;
        failedShader = null;
        acceptingCaptures = false;
        warnedFailure = false;
    }

    /** Caller owns IrisWorldColorScope and has disabled Iris' shader override. */
    public static void renderIris() {
        acceptingCaptures = false;
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (CAPTURES.isEmpty() || iris == null || !iris.isShaderPackInUse()
                    || iris.isShadowRenderActive() || mc.level != frameLevel
                    || frameShader == null || frameShader != ModShaders.UMBRAFALL_CORE_SEEP) return;
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
                RenderSystem.disableCull();
                GL11.glDisable(GL11.GL_CULL_FACE);
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
                try (ByteBufferBuilder memory = new ByteBufferBuilder(queuedQuads * 4 * 36)) {
                    BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS,
                            UmbrafallCoreSeepRenderTypes.FORMAT);
                    for (Capture core : CAPTURES.values()) {
                        UmbrafallCoreSeepRenderTypes.emit(builder, core.pose(), core.quads(), core.light());
                    }
                    MeshData mesh = builder.build();
                    if (mesh != null) {
                        mesh.sortQuads(memory, VertexSorting.DISTANCE_TO_ORIGIN);
                        // The native render type would rebind Fabulous' item/entity target.
                        BufferUploader.drawWithShader(mesh);
                    }
                }
                if (!target.present()) throw new IllegalStateException("Unable to present Iris core seep target");
            }
        } catch (RuntimeException failure) {
            failedShader = frameShader;
            if (!warnedFailure) {
                warnedFailure = true;
                Shadowedhearts.LOGGER.warn("[Umbrafall] Iris core seep replay failed; retaining baked core texture", failure);
            }
        } finally {
            clearCaptures();
        }
    }

    static boolean usableFrame(IrisHandler.IrisRenderingSnapshot snapshot) {
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

    private static void clearCaptures() {
        CAPTURES.clear();
        queuedQuads = 0;
    }

    private record Capture(PoseStack.Pose pose, List<UmbrafallCoreSeepGeometry.Quad> quads, int light) { }
}
