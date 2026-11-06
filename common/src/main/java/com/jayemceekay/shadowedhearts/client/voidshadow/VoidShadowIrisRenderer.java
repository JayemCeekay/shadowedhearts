package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.ball.SnagCaptureVfx;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisHandler;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.render.DepthCopyFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Isolated core material replay; never submits a core shader to a pack-owned MRT. */
public final class VoidShadowIrisRenderer {
    private static final int MAX_ENTITIES = 16;
    private static final int MAX_FRAME_VERTICES = 65536;
    private static final Map<Integer, VoidShadowIrisBufferSource.Captured> QUEUED = new LinkedHashMap<>();
    private static ByteBufferBuilder vertices;
    private static ClientLevel frameLevel;
    private static ShaderInstance frameShader;
    private static ShaderInstance failedShader;
    private static long frame;
    private static int vertexCount;
    private static TextureTarget materialTarget;
    private static int depthSourceFramebuffer;
    private static int colorOutputFramebuffer;
    private static boolean warnedFailure;

    private VoidShadowIrisRenderer() { }

    public static boolean isShadowPass() {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        return iris != null && iris.isShadowRenderActive();
    }

    public static boolean hasQueuedMeshes() { return !QUEUED.isEmpty(); }

    /** Iris main-world HEAD owns the lifetime, including frames with no visible Void Shadows. */
    public static void beginFrame() {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        if (iris != null && iris.isShadowRenderActive()) return;
        QUEUED.clear();
        vertexCount = 0;
        frame++;
        frameLevel = Minecraft.getInstance().level;
        frameShader = VoidShadowShaders.LIQUID == failedShader ? null : VoidShadowShaders.LIQUID;
    }

    public static MultiBufferSource capture(PokemonEntity entity, MultiBufferSource buffers) {
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        if (iris == null || !iris.isShaderPackInUse() || iris.isShadowRenderActive()
                || frameLevel == null || entity.level() != frameLevel || frameShader == null
                || frameShader != VoidShadowShaders.LIQUID
                || SnagCaptureVfx.getByPokemonId(entity.getId()) != null
                || (!QUEUED.containsKey(entity.getId()) && QUEUED.size() >= MAX_ENTITIES)) return buffers;
        return new VoidShadowIrisBufferSource(entity.getId(), frame, buffers);
    }

    public static void finishCapture(MultiBufferSource buffers) {
        if (!(buffers instanceof VoidShadowIrisBufferSource capture) || capture.frame != frame) return;
        var mesh = capture.finish();
        if (mesh == null) return;
        var previous = QUEUED.get(capture.entityId);
        int previousCount = previous == null ? 0 : previous.vertexCount();
        int nextCount = vertexCount - previousCount + mesh.vertexCount();
        if (nextCount > MAX_FRAME_VERTICES) return;
        QUEUED.put(capture.entityId, mesh);
        vertexCount = nextCount;
    }

    /** Called before final/composite passes, with Iris shader override disabled by the caller. */
    public static void renderIris() {
        List<VoidShadowIrisBufferSource.Captured> meshes = List.copyOf(QUEUED.values());
        QUEUED.clear();
        vertexCount = 0;
        IrisHandler iris = IrisCompat.IRIS_HANDLER;
        Minecraft mc = Minecraft.getInstance();
        if (meshes.isEmpty() || iris == null || !iris.isShaderPackInUse() || iris.isShadowRenderActive()
                || mc.level != frameLevel || frameShader == null || frameShader != VoidShadowShaders.LIQUID) return;
        IrisHandler.IrisRenderingSnapshot snapshot = iris.getIrisRenderingSnapshot();
        if (!usableFrame(snapshot)) return;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            try {
                int sceneFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                if (!ownsSceneTargets(sceneFbo, snapshot)) return;
                ColorTargetSize colorTarget = worldColorTargetSize();
                if (!colorTarget.usable()) return;
                int depthSource = depthSourceFramebuffer(snapshot);
                if (depthSource <= 0) return;
                ensureTarget(snapshot.renderWidth, snapshot.renderHeight);
                if (!DepthCopyFormat.prepareDepthTexture(depthSource, materialTarget.getDepthTextureId(),
                        snapshot.renderWidth, snapshot.renderHeight)) return;
                RenderSystem.colorMask(true, true, true, true);
                GL11.glColorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                materialTarget.setClearColor(0, 0, 0, 0);
                materialTarget.clear(Minecraft.ON_OSX);
                materialTarget.bindWrite(false);
                // bindWrite binds GL_FRAMEBUFFER, so select the source READ target afterwards.
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthSource);
                for (int error = 0; error < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; error++) { }
                GL30.glBlitFramebuffer(0, 0, snapshot.renderWidth, snapshot.renderHeight,
                        0, 0, snapshot.renderWidth, snapshot.renderHeight,
                        GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) return;
                RenderSystem.viewport(0, 0, snapshot.renderWidth, snapshot.renderHeight);
                RenderSystem.setProjectionMatrix(snapshot.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.getModelViewMatrix().set(snapshot.modelViewMatrix);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.enableDepthTest();
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                RenderSystem.colorMask(true, true, true, true);
                GL11.glColorMask(true, true, true, true);
                GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
                mc.gameRenderer.overlayTexture().setupOverlayColor();
                mc.gameRenderer.lightTexture().turnOnLightLayer();
                for (var mesh : meshes) drawBody(mesh);
                for (var mesh : meshes) drawEyes(mesh);
                // A larger fallback color texture must not be clipped by the scene
                // FBO's smaller depth attachment during fullscreen presentation.
                if (colorOutputFramebuffer(colorTarget) <= 0) return;
                RenderSystem.viewport(0, 0, colorTarget.width(), colorTarget.height());
                composite();
            } finally {
                detachExternalTargets();
            }
        } catch (RuntimeException failure) {
            // A rejected target or shader leaves the pack's native black model
            // visible. Do not retry the failed allocation/draw every frame.
            failedShader = frameShader;
            frameShader = null;
            if (!warnedFailure) {
                warnedFailure = true;
                Shadowedhearts.LOGGER.warn("[Void Shadow] Iris liquid material replay failed; retaining native material", failure);
            }
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

    private static boolean ownsSceneTargets(int framebuffer, IrisHandler.IrisRenderingSnapshot snapshot) {
        return framebuffer > 0
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == snapshot.depthTexture
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) > 0
                && GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    /** Iris' default output may be its fallback texture and either side of a flip. */
    private static ColorTargetSize worldColorTargetSize() {
        int texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int mip = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, mip, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, mip, GL11.GL_TEXTURE_HEIGHT);
            return new ColorTargetSize(texture, mip, width, height);
        } finally {
            // Raw queries must not change RenderSystem/GlStateManager's cached binding.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private record ColorTargetSize(int texture, int mip, int width, int height) {
        boolean usable() { return texture > 0 && mip >= 0 && width > 0 && height > 0 && width <= 16384 && height <= 16384; }
    }

    /** A scaled color attachment must not clip the full-resolution depth copy. */
    private static int depthSourceFramebuffer(IrisHandler.IrisRenderingSnapshot snapshot) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.depthTexture);
            if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != snapshot.renderWidth
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != snapshot.renderHeight) return 0;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
        if (depthSourceFramebuffer == 0) depthSourceFramebuffer = GL30.glGenFramebuffers();
        if (depthSourceFramebuffer == 0) return 0;
        // The encompassing render-state scope restores both caller bindings,
        // including every early return after creating this depth-only view.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthSourceFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, snapshot.depthTexture, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
                ? depthSourceFramebuffer : 0;
    }

    private static int colorOutputFramebuffer(ColorTargetSize target) {
        if (colorOutputFramebuffer == 0) colorOutputFramebuffer = GL30.glGenFramebuffers();
        if (colorOutputFramebuffer == 0) return 0;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, colorOutputFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, target.texture(), target.mip());
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL11.GL_NONE);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE
                ? colorOutputFramebuffer : 0;
    }

    /** Temporary views must not retain deleted Iris pack textures after this transaction. */
    private static void detachExternalTargets() {
        if (depthSourceFramebuffer != 0) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthSourceFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
        }
        if (colorOutputFramebuffer != 0) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, colorOutputFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, 0, 0);
        }
    }

    private static void ensureTarget(int width, int height) {
        if (materialTarget != null && materialTarget.width == width && materialTarget.height == height) return;
        if (materialTarget != null) materialTarget.destroyBuffers();
        materialTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
    }

    private static void drawBody(VoidShadowIrisBufferSource.Captured mesh) {
        for (var layer : mesh.body().layers()) {
            var material = VoidShadowBufferSource.material(layer.type());
            if (material == null) continue;
            RenderSystem.setShaderTexture(0, material.texture());
            ShaderInstance shader = VoidShadowShaders.liquid(material.opaque(), material.translucent() ? 0.01f : 0.1f);
            RenderSystem.setShader(() -> shader);
            if (material.cull()) {
                RenderSystem.enableCull();
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                RenderSystem.disableCull();
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            RenderSystem.depthMask(!material.translucent());
            GL11.glDepthMask(!material.translucent());
            if (material.translucent()) {
                RenderSystem.enableBlend();
                GL11.glEnable(GL11.GL_BLEND);
                RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            } else {
                RenderSystem.disableBlend();
                GL11.glDisable(GL11.GL_BLEND);
            }
            drawLayer(mesh.root(), layer);
        }
    }

    private static void drawEyes(VoidShadowIrisBufferSource.Captured mesh) {
        if (mesh.eyes() == null) return;
        RenderSystem.depthMask(false);
        GL11.glDepthMask(false);
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        // Added eyes contribute emissive RGB without changing the body's coverage.
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        RenderSystem.setShader(GameRenderer::getRendertypeEyesShader);
        for (var layer : mesh.eyes().layers()) {
            var material = VoidShadowBufferSource.material(layer.type());
            if (material == null) continue;
            RenderSystem.setShaderTexture(0, material.texture());
            drawLayer(mesh.root(), layer);
        }
    }

    private static void drawLayer(PoseStack.Pose root, VoidShadowSnapshotBufferSource.Layer layer) {
        // A private format prevents Iris replacing NEW_ENTITY with its extended stride.
        BufferBuilder builder = new BufferBuilder(storage(), VertexFormat.Mode.QUADS,
                ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT);
        for (var vertex : layer.vertices()) vertex.emit(root, builder);
        MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void composite() {
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, materialTarget.getColorTextureId());
        RenderSystem.disableDepthTest();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderSystem.depthMask(false);
        GL11.glDepthMask(false);
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        // The isolated target stores premultiplied color for translucent layers.
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO, GL11.GL_ONE);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO, GL11.GL_ONE);
        BufferBuilder quad = new BufferBuilder(storage(), VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        quad.addVertex(-1, -1, 0).setUv(0, 0).setColor(-1);
        quad.addVertex(1, -1, 0).setUv(1, 0).setColor(-1);
        quad.addVertex(1, 1, 0).setUv(1, 1).setColor(-1);
        quad.addVertex(-1, 1, 0).setUv(0, 1).setColor(-1);
        MeshData mesh = quad.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static ByteBufferBuilder storage() {
        if (vertices == null) vertices = new ByteBufferBuilder(8192 * 36);
        return vertices;
    }
}
