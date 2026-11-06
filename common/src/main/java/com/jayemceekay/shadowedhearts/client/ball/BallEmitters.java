package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.particle.PenumbraTrailSystem;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.render.SnagDissolveRenderer;
import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.client.render.IrisScaledWorldTarget;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.client.trail.BallTrailManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;

import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Client-side emitter system for thrown Poké Balls. Mirrors AuraEmitters pattern but renders
 * a glowing orb billboard and a motion trail, driven by server-authoritative state sync.
 */
public final class BallEmitters {
    private BallEmitters() {
    }
    private static final RandomSource random = RandomSource.create();
    private static final Map<Integer, BallInstance> ACTIVE = new ConcurrentHashMap<>();
    private static WeakReference<Object> clientLevelRef = new WeakReference<>(null);
    private static boolean clientLevelActive;
    private static boolean invalidIrisTransformWarningLogged;
    private static boolean irisTransactionDiagnosticLogged;

    /**
     * Called on client when a ball entity is created/loaded.
     *
     * <p>Balls may need a few ticks before aspects arrive from the
     * server, so unresolved instances are tracked briefly and classified later
     * in {@link #onRender(net.minecraft.client.Camera, float)}.
     */
    public static void startForEntity(EmptyPokeBallEntity entity) {
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)) return;
        long now = mc.level.getGameTime();
        // Register all balls; effect classification is deferred until aspects sync.
        ACTIVE.put(entity.getId(), new BallInstance(entity.getId(), entity, now, 4, 400, 8, false, false, false));
    }

    /**
     * Begins a short fade-out for the despawned entity.
     */
    public static void onEntityDespawn(int entityId) {
        DarkBallCaptureVfx.stop(entityId);
        var mc = Minecraft.getInstance();
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        ACTIVE.computeIfPresent(entityId, (id, inst) -> {
            inst.beginImmediateFadeOut(now, 6);
            return inst;
        });
    }

    /**
     * Maintains world-scoped Dark Ball GPU resources even while no world render
     * callback is running, such as on the title screen after disconnecting.
     */
    public static void onClientTick(Minecraft minecraft) {
        prepareClientLevel(minecraft);
        // Config screens and disconnects need cleanup even without a world-render callback.
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().snagShakeVfxEnabled()) {
            SnagShakeVfx.clearAll();
        }
        com.jayemceekay.shadowedhearts.client.render.VanillaWorldShaderFrame.onClientTick(minecraft);
    }

    /**
     * Per-frame world-render entry point for CPU-side tracking and particle emission.
     *
     * <p>World drawing is deferred to {@link #onRenderFBO(net.minecraft.client.Camera,
     * float, Matrix4f, Matrix4f)} with the scene depth retained before Fabulous
     * combines its transparency targets.
     */
    public static void onRender(net.minecraft.client.Camera camera, float partialTicks) {
        if (IrisCompat.IRIS_HANDLER != null
                && IrisCompat.IRIS_HANDLER.isShadowRenderActive()) return;
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)) return;
        // Advance the capture choreography before either native or Iris drawing.
        SnagCaptureVfx.tickAll(1f / 20f);
        DarkBallCaptureVfx.tickAll(1f / 20f);

        SnagThrowVfx.pruneAll();

        // Advance shake-phase VFX before either native or Iris drawing.
        IClientConfig cfg = ShadowedHeartsConfigs.getInstance().getClientConfig();
        if (cfg.snagShakeVfxEnabled()) {
            SnagShakeVfx.tickAll();
        } else {
            SnagShakeVfx.clearAll();
        }

        HeldBallSnagGlowRenderer.queueWorldHandsForIris(camera, partialTicks);

        for (Map.Entry<Integer, BallInstance> en : ACTIVE.entrySet()) {
            BallInstance inst = en.getValue();
            if (inst == null) {
                ACTIVE.remove(en.getKey());
                continue;
            }
            if (inst.isExpired(mc.level.getGameTime())) {
                ACTIVE.remove(en.getKey());
                continue;
            }

            Entity ent = inst.entityRef != null ? inst.entityRef.get() : null;
            boolean useEnt = ent != null && ent.isAlive() && ent.getId() == inst.entityId;
            if (!useEnt) continue;

            // Lazy-resolve ball effects once aspects sync arrives from server.
            if (!inst.resolved && ent instanceof EmptyPokeBallEntity ball) {
                boolean hasSnagAspect = ball.getAspects().contains("snag_ball");
                String ballName = ball.getPokeBall().getName().getPath();
                boolean usesPenumbraTrail = ballName.equals("penumbra_ball")
                        || ballName.equals("antumbra_ball")
                        || ballName.equals("umbra_ball");
                boolean isDarkBall = ballName.equals("dark_ball");
                if (hasSnagAspect || usesPenumbraTrail || isDarkBall) {
                    inst.isSnagBall = hasSnagAspect && !isDarkBall;
                    inst.usesPenumbraTrail = usesPenumbraTrail;
                    inst.isDarkBall = isDarkBall;
                    inst.resolved = true;
                }
                // If no effect resolves after ~5 ticks, stop tracking this ball.
                if (!inst.resolved && mc.level.getGameTime() - inst.startTick > 5) {
                    ACTIVE.remove(en.getKey());
                    continue;
                }
                if (!inst.resolved) continue; // still waiting for sync
            }

            double ix, iy, iz;
            ix = Mth.lerp(partialTicks, ent.xOld, ent.getX());
            iy = Mth.lerp(partialTicks, ent.yOld, ent.getY());
            iz = Mth.lerp(partialTicks, ent.zOld, ent.getZ());

            // Feed trail samples in world space; render in the entity-origin pose later
            // We use the interpolated world position
            BallTrailManager.addPointForId(inst.entityId, ix, iy, iz);

            // Share Penumbra's particles and density fog with Antumbra and Umbra.
            if (inst.usesPenumbraTrail && ent instanceof EmptyPokeBallEntity) {
                emitPenumbraTrailParticles(mc, ent, ix, iy, iz, inst);
            }

            // Emit snag trail density puffs along the ball's trajectory
            if (inst.isSnagBall) {
                emitSnagTrailPuffs(mc, ent, ix, iy, iz, inst);
            }

            // Start throw VFX while the ball is in flight (suppressed once capture VFX takes over)
            boolean snagCaptureVfxActive = SnagCaptureVfx.get(inst.entityId) != null;
            if (inst.isSnagBall && !snagCaptureVfxActive && ent instanceof EmptyPokeBallEntity ball) {
                if(ball.getCaptureState() == EmptyPokeBallEntity.CaptureState.NOT) {
                    SnagThrowVfx.start(ball);
                }
            }

            // Detect when a Snag Ball enters the capture beam phase (beamMode == 3 on the phased Pokémon)
            // and start the SnagCaptureVfx if not already running.
            if (inst.isSnagBall && !snagCaptureVfxActive && ent instanceof EmptyPokeBallEntity ball) {
                maybeStartSnagVfx(ball, mc);
            }
            if (inst.isDarkBall && ent instanceof EmptyPokeBallEntity ball) {
                inst.darkCaptureStart.tryStart(
                        ball.getCaptureState() == EmptyPokeBallEntity.CaptureState.HIT,
                        () -> maybeStartDarkBallVfx(ball, mc));
            }

            // Track Snag Balls that have entered the shake phase
            if (cfg.snagShakeVfxEnabled() && inst.isSnagBall && ent instanceof EmptyPokeBallEntity ball2) {
                SnagShakeVfx.maybeTrack(ball2);
            }
        }
    }

    /**
     * Vanilla late-render entry point. Iris submits the Snag pipelines through
     * its pre-final world hook instead of either loader's late callback.
     */
    public static void onRenderFBO(net.minecraft.client.Camera camera, float partialTicks) {
        onRenderFBO(camera, partialTicks, RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix());
    }

    /** Draws native ball effects into the caller's scene target with its retained world depth. */
    public static void onRenderFBO(net.minecraft.client.Camera camera, float partialTicks,
                                   Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
        if (IrisCompat.IRIS_HANDLER != null
                && (IrisCompat.IRIS_HANDLER.isShaderPackInUse()
                || IrisCompat.IRIS_HANDLER.isShadowRenderActive())) return;
        if (camera == null || !DarkBallRenderContext.isUsablePerspective(projectionMatrix)
                || !DarkBallRenderContext.isUsableModelView(modelViewMatrix)) return;
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)) return;
        try (var state = DensityFboPipeline.preserveRenderState();
             var snagView = SnagRenderContext.install(modelViewMatrix);
             var darkView = DarkBallRenderContext.install(modelViewMatrix, projectionMatrix)) {
            RenderSystem.setProjectionMatrix(new Matrix4f(projectionMatrix), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(modelViewMatrix);
            SnagCaptureVfx.renderAll(camera, partialTicks);
            SnagThrowVfx.renderAll(camera, partialTicks);
            if (ShadowedHeartsConfigs.getInstance().getClientConfig().snagShakeVfxEnabled()) {
                SnagShakeVfx.renderAll(camera, partialTicks);
            }
            renderSnagFboPasses(camera, partialTicks);
            renderDarkBallComposites(camera, partialTicks);
            DarkBallAnimeBallFinish.updateAndRender(camera, partialTicks);
        }
    }

    /** Whether native ball drawing needs this frame's retained scene depth. */
    public static boolean hasWorldEffects() {
        return hasSnagEffects() || !DarkBallCaptureVfx.getActiveInstances().isEmpty()
                || DarkBallAnimeBallFinish.hasActive();
    }

    /** Called inside Iris' core-shader/color-output scope before final passes. */
    public static boolean hasSnagEffects() {
        return SnagDissolveRenderer.hasQueuedMeshes()
                || HeldBallSnagGlowRenderer.hasQueuedMeshes()
                || !SnagCaptureVfx.getActiveInstances().isEmpty()
                || SnagThrowVfx.hasActive() || SnagShakeVfx.hasActive()
                || SnagTrailDensitySystem.hasActive();
    }

    /** Called inside Iris' core-shader/color-output scope before final passes. */
    public static void renderSnagIris() {
        if (!hasSnagEffects()) return;
        var iris = IrisCompat.IRIS_HANDLER;
        if (iris == null || !iris.isShaderPackInUse() || iris.isShadowRenderActive()) return;
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc) || mc.gameRenderer == null) return;
        var frame = iris.getIrisRenderingSnapshot();
        if (frame == null || frame.depthTexture <= 0
                || !DarkBallRenderContext.isUsableIrisFrame(frame.modelViewMatrix,
                frame.projectionMatrix, frame.renderWidth, frame.renderHeight)) return;
        var camera = mc.gameRenderer.getMainCamera();
        float partialTicks = camera.getPartialTickTime();
        try (var state = DensityFboPipeline.preserveRenderState();
             var view = SnagRenderContext.install(frame.modelViewMatrix);
             var scaled = IrisScaledWorldTarget.open(frame.renderWidth, frame.renderHeight, frame.depthTexture)) {
            if (scaled == null) return;
            RenderSystem.setProjectionMatrix(frame.projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(frame.modelViewMatrix);
            RenderSystem.viewport(0, 0, frame.renderWidth, frame.renderHeight);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            SnagDissolveRenderer.renderIris();
            RenderSystem.depthMask(false);
            GL11.glDepthMask(false);
            RenderSystem.enableBlend();
            GL11.glEnable(GL11.GL_BLEND);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            org.lwjgl.opengl.GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GL20.glBlendEquationSeparate(org.lwjgl.opengl.GL14.GL_FUNC_ADD, org.lwjgl.opengl.GL14.GL_FUNC_ADD);
            HeldBallSnagGlowRenderer.renderIris();
            SnagCaptureVfx.renderAll(camera, partialTicks);
            SnagThrowVfx.renderAll(camera, partialTicks);
            if (ShadowedHeartsConfigs.getInstance().getClientConfig().snagShakeVfxEnabled()) {
                SnagShakeVfx.renderAll(camera, partialTicks);
            }
            renderSnagFboPasses(camera, partialTicks);
            scaled.present();
        }
    }

    private static void renderSnagFboPasses(net.minecraft.client.Camera camera, float partialTicks) {

        // Snag trail density pipeline (orange smoke and purple motes)
        if (ShadowPokemonAuraSystem.isIrisShaderPackActive()) {
            SnagTrailDensitySystem.renderIris(camera, partialTicks);
        } else {
            SnagTrailDensitySystem.renderDensityPipeline(camera, partialTicks);
        }

        // Density FBO pass: render beam splats + particle splats → blur → composite
        if (!SnagCaptureVfx.getActiveInstances().isEmpty()
                && SnagDensityFBO.beginDensityPass()) {
            try {
                SnagCaptureVfx.renderAllDensityPass(camera, partialTicks);
            } finally {
                SnagDensityFBO.endDensityPass();
            }
            SnagDensityFBO.blur();
            SnagDensityFBO.composite();
        }

        // Bloom pass: re-render snag VFX into a half-res FBO, blur, composite
        IClientConfig cfg = ShadowedHeartsConfigs.getInstance().getClientConfig();
        if (cfg.snagBloomEnabled() && SnagBloomFBO.shouldRun() && SnagBloomFBO.beginBloomPass()) {
            try {
                SnagCaptureVfx.renderAll(camera, partialTicks);
            } finally {
                SnagBloomFBO.endBloomPass();
            }
            SnagBloomFBO.blurAndComposite();
        }
    }

    /**
     * Iris/Oculus entry point. The Iris pipeline calls this after binding its
     * default world target, while its scene depth is still available. Loader
     * late-render callbacks deliberately skip the Dark Ball branch while a
     * shader pack is active so every capture is submitted exactly once.
     */
    public static void renderDarkBallIris() {
        if (!ShadowPokemonAuraSystem.isIrisShaderPackActive()
                || IrisCompat.IRIS_HANDLER == null
                || IrisCompat.IRIS_HANDLER.isShadowRenderActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)
                || mc.gameRenderer == null
                || mc.level == null) {
            return;
        }
        if (DarkBallCaptureVfx.getActiveInstances().isEmpty()
                && !DarkBallAnimeBallFinish.hasActive()) {
            irisTransactionDiagnosticLogged = false;
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        var irisSnapshot = IrisCompat.IRIS_HANDLER
                .getIrisRenderingSnapshot();
        if (irisSnapshot == null
                || !DarkBallRenderContext.isUsableIrisFrame(
                irisSnapshot.modelViewMatrix,
                irisSnapshot.projectionMatrix,
                irisSnapshot.renderWidth,
                irisSnapshot.renderHeight)) {
            if (!invalidIrisTransformWarningLogged) {
                int width = irisSnapshot == null
                        ? 0 : irisSnapshot.renderWidth;
                int height = irisSnapshot == null
                        ? 0 : irisSnapshot.renderHeight;
                Shadowedhearts.LOGGER.warn(
                        "[ShadowedHearts] Dark Ball skipped one Iris world "
                                + "submission because its matched view/"
                                + "projection snapshot was unavailable or "
                                + "invalid (target={}x{}); capture-only model "
                                + "suppression remains active",
                        width, height);
                invalidIrisTransformWarningLogged = true;
            }
            return;
        }
        invalidIrisTransformWarningLogged = false;
        Matrix4f savedProjection = new Matrix4f(
                RenderSystem.getProjectionMatrix());
        Matrix4f savedModelView = new Matrix4f(
                RenderSystem.getModelViewMatrix());
        IntBuffer savedViewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);
        logIrisTransactionDiagnosticOnce(irisSnapshot, savedViewport);
        try (DarkBallRenderContext.Scope ignored =
                     DarkBallRenderContext.install(
                             irisSnapshot.modelViewMatrix,
                             irisSnapshot.projectionMatrix)) {
            RenderSystem.setProjectionMatrix(
                    irisSnapshot.projectionMatrix,
                    VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewMatrix().set(
                    irisSnapshot.modelViewMatrix);
            // bindDefault() binds Iris' world FBO but does not promise to
            // reset a shader pack's custom viewport. Size the scratch/depth
            // transaction from the same validated target as the matrix pair.
            RenderSystem.viewport(
                    0, 0,
                    irisSnapshot.renderWidth,
                    irisSnapshot.renderHeight);
            renderDarkBallComposites(
                    camera, camera.getPartialTickTime());
            DarkBallAnimeBallFinish.updateAndRender(camera, camera.getPartialTickTime());
        } finally {
            RenderSystem.getModelViewMatrix().set(savedModelView);
            RenderSystem.setProjectionMatrix(
                    savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.viewport(
                    savedViewport.get(0), savedViewport.get(1),
                    savedViewport.get(2), savedViewport.get(3));
        }
    }

    /**
     * Runs one complete clear -> draw -> resolve -> composite transaction per
     * capture. Screen-sized scratch targets are reused serially, while every
     * capture keeps independent exact-mask, proxy-depth and anime energy state.
     */
    private static void renderDarkBallComposites(
            net.minecraft.client.Camera camera,
            float partialTicks) {
        var captures = DarkBallCaptureVfx.orderedActiveCaptures(camera);
        if (captures.isEmpty()) {
            return;
        }

        DarkBallCaptureVfx.beginDirectCompositeFrame();

        for (int index = 0; index < captures.size(); index++) {
            DarkBallCaptureVfx capture = captures.get(index);
            boolean passAttempted = false;
            boolean passReady = false;
            boolean compositeRendered = false;
            boolean stateRestoreFailed = false;
            try {
                if (DarkBallCaptureVfx.prepareDensityPass(
                        capture, camera, partialTicks)) {
                    passAttempted = true;
                    passReady = DarkBallDensityFBO.beginDensityPass(
                            capture, true, true);
                    if (passReady) {
                        DarkBallCaptureVfx.renderDensityPass(
                                capture, camera, partialTicks);
                    }
                }
            } catch (RuntimeException failure) {
                Shadowedhearts.LOGGER.error(
                        "[ShadowedHearts] Dark Ball capture transaction failed "
                                + "before composite; isolating this capture",
                        failure);
                if (passReady) {
                    try {
                        passReady = DarkBallDensityFBO
                                .resetDensityPassAfterFailedDirectDraw();
                    } catch (RuntimeException resetFailure) {
                        passReady = false;
                        failure.addSuppressed(resetFailure);
                    }
                }
            } finally {
                if (passAttempted) {
                    try {
                        DarkBallDensityFBO.endDensityPass();
                    } catch (RuntimeException failure) {
                        passReady = false;
                        stateRestoreFailed = true;
                        Shadowedhearts.LOGGER.error(
                                "[ShadowedHearts] Dark Ball capture state "
                                        + "restore failed; isolating this "
                                        + "capture",
                                failure);
                    }
                }
            }

            if (stateRestoreFailed) {
                // The caller target is no longer trustworthy. Do not start a
                // later capture transaction or diagnostic draw on unknown GL
                // state; fail the remaining frame closed.
                abortDarkBallCapture(capture);
                return;
            }

            if (passReady) {
                try {
                    compositeRendered = DarkBallDensityFBO.composite(capture);
                } catch (RuntimeException failure) {
                    Shadowedhearts.LOGGER.error(
                            "[ShadowedHearts] Dark Ball capture composite "
                                    + "failed; aborting the remaining frame "
                                    + "because restored GL state is unknown",
                            failure);
                    abortDarkBallCapture(capture);
                return;
                }
            }
            DarkBallCaptureVfx.finishDirectCompositeCapture(
                    capture, compositeRendered);

        }
    }

    private static void abortDarkBallCapture(DarkBallCaptureVfx capture) {
        DarkBallCaptureVfx.finishDirectCompositeCapture(capture, false);
    }

    private static void logIrisTransactionDiagnosticOnce(
            com.jayemceekay.shadowedhearts.client.render.iris.IrisHandler
                    .IrisRenderingSnapshot snapshot,
            IntBuffer viewport) {
        if (irisTransactionDiagnosticLogged) {
            return;
        }
        irisTransactionDiagnosticLogged = true;
        Matrix4f renderSystemProjection = new Matrix4f(
                RenderSystem.getProjectionMatrix());
        Matrix4f renderSystemModelView = new Matrix4f(
                RenderSystem.getModelViewMatrix());
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball Iris transaction trace "
                        + "captures={} currentTarget={}x{} "
                        + "diffuseTarget={}x{} diffuseTex={} depthTex={} "
                        + "preViewport=({},{} {}x{}) drawFbo={} readFbo={} "
                        + "program={}",
                DarkBallCaptureVfx.getActiveInstances().size(),
                snapshot.renderWidth,
                snapshot.renderHeight,
                snapshot.diffuseWidth,
                snapshot.diffuseHeight,
                snapshot.diffuseTexture,
                snapshot.depthTexture,
                viewport.get(0), viewport.get(1),
                viewport.get(2), viewport.get(3),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball Iris matrix trace "
                        + "snapshotDet=({}, {}) renderSystemDelta=({}, {}) "
                        + "snapshotViewTranslation=({}) "
                        + "renderSystemViewTranslation=({}) "
                        + "projectionAspect={} targetAspect={}",
                snapshot.modelViewMatrix.determinant(),
                snapshot.projectionMatrix.determinant(),
                matrixMaximumDelta(
                        snapshot.modelViewMatrix,
                        renderSystemModelView),
                matrixMaximumDelta(
                        snapshot.projectionMatrix,
                        renderSystemProjection),
                formatTranslation(snapshot.modelViewMatrix),
                formatTranslation(renderSystemModelView),
                Math.abs(snapshot.projectionMatrix.m11()
                        / snapshot.projectionMatrix.m00()),
                (float) snapshot.renderWidth
                        / Math.max(snapshot.renderHeight, 1));
    }

    private static float matrixMaximumDelta(Matrix4f first,
                                            Matrix4f second) {
        float[] firstValues = new float[16];
        float[] secondValues = new float[16];
        first.get(firstValues);
        second.get(secondValues);
        float maximum = 0.0f;
        for (int index = 0; index < firstValues.length; index++) {
            maximum = Math.max(
                    maximum,
                    Math.abs(firstValues[index] - secondValues[index]));
        }
        return maximum;
    }

    private static String formatTranslation(Matrix4f matrix) {
        return String.format(
                Locale.ROOT,
                "%.6g,%.6g,%.6g",
                matrix.m30(), matrix.m31(), matrix.m32());
    }

    private static boolean prepareClientLevel(Minecraft mc) {
        Object currentLevel = mc == null ? null : mc.level;
        if (currentLevel == null) {
            if (clientLevelActive) {
                DarkBallCaptureVfx.clearAll();
                SnagShakeVfx.clearAll();
                DarkBallDensityFBO.destroy();
            }
            clientLevelRef = new WeakReference<>(null);
            clientLevelActive = false;
            return false;
        }

        if (!clientLevelActive || clientLevelRef.get() != currentLevel) {
            // A render callback is not guaranteed while the title screen is
            // active. Clear stale per-capture GPU resources on the first hook
            // for a replacement world as well as on an observed null world.
            DarkBallCaptureVfx.clearAll();
            SnagShakeVfx.clearAll();
            if (clientLevelActive) {
                DarkBallDensityFBO.destroy();
            }
            clientLevelRef = new WeakReference<>(currentLevel);
            clientLevelActive = true;
        }
        return true;
    }

    /**
     * Scans the level for a PokemonEntity whose {@code phasingTargetId} matches this ball's ID
     * and whose {@code beamMode} is 3 (the capture-beam recall phase). If found, starts the
     * {@link SnagCaptureVfx} for this ball.
     */
    private static void maybeStartSnagVfx(EmptyPokeBallEntity ball, Minecraft mc) {
        if (mc.level == null) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PokemonEntity pokemon)) continue;
            if (pokemon.getPhasingTargetId() == ball.getId() && pokemon.getBeamMode() == 3) {
                SnagCaptureVfx.start(ball, pokemon);
                return;
            }
        }
    }

    private static boolean maybeStartDarkBallVfx(EmptyPokeBallEntity ball, Minecraft mc) {
        if (mc.level == null) return false;
        if (ball.getCaptureState() != EmptyPokeBallEntity.CaptureState.HIT) return false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PokemonEntity pokemon)) continue;
            if (pokemon.getPhasingTargetId() == ball.getId()) {
                DarkBallCaptureVfx.startAtHit(ball, pokemon);
                return true;
            }
        }
        return false;
    }

    static final class OneShotStartLatch {
        private boolean started;

        boolean tryStart(boolean eligible, BooleanSupplier starter) {
            if (!eligible || started || !starter.getAsBoolean()) {
                return false;
            }
            started = true;
            return true;
        }
    }

    private static void renderOrb(PoseStack poseStack, MultiBufferSource buffer, float partialTicks) {
        final int FULLBRIGHT = 0x00F000F0;
        poseStack.pushPose();
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float base = 0.85f;
        poseStack.scale(base, base, base);
        if (ModShaders.BALL_ORB_GLOW != null) {
            try {
                apply(ModShaders.BALL_ORB_GLOW);
            } catch (Throwable ignored) {
            }
        }
        VertexConsumer vc = buffer.getBuffer(BallRenderTypes.ballOrbGlow());
        emitUnitQuad(vc, poseStack, FULLBRIGHT);
        poseStack.popPose();
    }

    private static void emitUnitQuad(VertexConsumer vc, PoseStack stack, int packedLight) {
        var last = stack.last();
        Matrix4f pose = last.pose();
        float x0 = -1f, y0 = -1f, x1 = 1f, y1 = 1f;
        vc.addVertex(pose, x0, y0, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y0, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y1, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x0, y1, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
    }

    /**
     * HUD helper: renders the same orb billboard used in-world, but in screen-space using the provided pose.
     * Scales a unit quad to the requested pixel size and routes through BallRenderTypes.ballGlow so the
     * BallGlowUniforms path and shader stay identical to in-world rendering.
     */
    public static void renderHudOrb(PoseStack poseStack, MultiBufferSource buffers, int sizePx, float alpha) {
        final int FULLBRIGHT = 0x00F000F0;
        poseStack.pushPose();
        float s = sizePx * 0.5f; // our quad is [-1,1]
        poseStack.scale(s, s, s);
        if (ModShaders.BALL_ORB_GLOW != null) {
            try {
                apply(ModShaders.BALL_ORB_GLOW);
            } catch (Throwable ignored) {
            }
        }
        VertexConsumer vc = buffers.getBuffer(BallRenderTypes.ballOrbGlowHud());
        emitUnitQuadAlpha(vc, poseStack, FULLBRIGHT, alpha);
        poseStack.popPose();
    }

    private static void emitUnitQuadAlpha(VertexConsumer vc, PoseStack stack, int packedLight, float alpha) {
        var last = stack.last();
        Matrix4f pose = last.pose();
        float x0 = -1f, y0 = -1f, x1 = 1f, y1 = 1f;
        vc.addVertex(pose, x0, y0, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y0, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y1, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x0, y1, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
    }

    /** Particles emitted per block of travel distance when the ball is in motion. */
    private static final double PARTICLES_PER_BLOCK = 12.0;

    /**
     * Spawns penumbra trail particles along the previous-to-current motion segment
     * so fast throws do not leave gaps.
     * <p>
     * Emission rate: 8–20 particles/tick while thrown (velocity-scaled).
     */
    private static void emitPenumbraTrailParticles(Minecraft mc, Entity ent, double ix, double iy, double iz, BallInstance inst) {
        if (mc.level == null) return;

        // Throttle spawning to once per game tick to avoid frame-rate dependent
        // particle density (e.g. 3x more particles at 60 FPS than 20 FPS).
        long gameTime = mc.level.getGameTime();
        if (gameTime == inst.lastSpawnTick) {
            // Still update lastTrailPos so the next tick's gap calculation is accurate
            inst.lastTrailPos = new Vec3(ix, iy + 0.22, iz);
            return;
        }
        inst.lastSpawnTick = gameTime;

        double vx = ent.getDeltaMovement().x;
        double vy = ent.getDeltaMovement().y;
        double vz = ent.getDeltaMovement().z;

        Vec3 curr = new Vec3(ix, iy + 0.22, iz);
        Vec3 prev = inst.lastTrailPos;

        if (prev != null) {
            double gap = prev.distanceTo(curr);
            // Scale particle count by distance traveled — enough for coverage without overcrowding
            int count = Math.max(6, Math.min(24, (int) (gap * PARTICLES_PER_BLOCK)));
            for (int i = 0; i < count; i++) {
                double t = (double) i / count;
                // More aggressive perpendicular spread for smokier trails (suggestion L)
                double lx = Mth.lerp(t, prev.x, curr.x) + (random.nextFloat() - 0.5) * 0.2;
                double ly = Mth.lerp(t, prev.y, curr.y) + (random.nextFloat() - 0.5) * 0.2;
                double lz = Mth.lerp(t, prev.z, curr.z) + (random.nextFloat() - 0.5) * 0.2;
                spawnPenumbraTrailPuff(mc, lx, ly, lz, vx, vy, vz);
            }
        } else {
            // First frame: seed a small cluster at the current position
            for (int i = 0; i < 3; i++) {
                double ox = curr.x + (random.nextFloat() - 0.5) * 0.40;
                double oy = curr.y + (random.nextFloat() - 0.5) * 0.30;
                double oz = curr.z + (random.nextFloat() - 0.5) * 0.40;
                spawnPenumbraTrailPuff(mc, ox, oy, oz, vx, vy, vz);
            }
        }
        inst.lastTrailPos = curr;
    }

    private static void spawnPenumbraTrailPuff(Minecraft mc,
                                                double x, double y, double z,
                                                double vx, double vy, double vz) {
        if (mc.level == null) return;

        // The density manager owns the visible trail. The old ParticleEngine
        // mirror used ZERO/ONE blending (invisible), but still applied a custom
        // shader outside the scoped Iris pass.
        PenumbraTrailSystem.registerPuff(x, y, z, vx, vy, vz);
    }

    /** Particles emitted per block of travel distance for snag ball trails. */
    private static final double SNAG_PARTICLES_PER_BLOCK = 14.0;

    /**
     * Spawns snag trail density puffs along the ball's trajectory, similar to
     * {@link #emitPenumbraTrailParticles} but feeding into
     * {@link SnagTrailDensitySystem} instead of the penumbra particle system.
     */
    private static void emitSnagTrailPuffs(Minecraft mc, Entity ent,
                                            double ix, double iy, double iz,
                                            BallInstance inst) {
        if (mc.level == null) return;

        long gameTime = mc.level.getGameTime();
        if (gameTime == inst.lastSnagSpawnTick) {
            inst.lastSnagTrailPos = new Vec3(ix, iy + 0.15, iz);
            return;
        }
        inst.lastSnagSpawnTick = gameTime;

        double vx = ent.getDeltaMovement().x;
        double vy = ent.getDeltaMovement().y;
        double vz = ent.getDeltaMovement().z;

        Vec3 curr = new Vec3(ix, iy + 0.15, iz);
        Vec3 prev = inst.lastSnagTrailPos;

        if (prev != null) {
            double gap = prev.distanceTo(curr);
            int count = Math.max(6, Math.min(28, (int) (gap * SNAG_PARTICLES_PER_BLOCK)));
            for (int i = 0; i < count; i++) {
                double t = (double) i / count;
                double lx = Mth.lerp(t, prev.x, curr.x) + (random.nextFloat() - 0.5) * 0.52;
                double ly = Mth.lerp(t, prev.y, curr.y) + (random.nextFloat() - 0.5) * 0.52;
                double lz = Mth.lerp(t, prev.z, curr.z) + (random.nextFloat() - 0.5) * 0.52;
                SnagTrailDensitySystem.registerOrangeSmokePuff(lx, ly, lz, vx, vy, vz);
                // Emit purple motes at lower density (roughly 1 in 3 positions)
                if (i % 3 == 0) {
                    SnagTrailDensitySystem.registerPurpleMote(lx, ly, lz, vx, vy, vz);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                double ox = curr.x + (random.nextFloat() - 0.5) * 0.25;
                double oy = curr.y + (random.nextFloat() - 0.5) * 0.20;
                double oz = curr.z + (random.nextFloat() - 0.5) * 0.25;
                SnagTrailDensitySystem.registerOrangeSmokePuff(ox, oy, oz, vx, vy, vz);
                SnagTrailDensitySystem.registerPurpleMote(ox, oy, oz, vx, vy, vz);
            }
        }
        inst.lastSnagTrailPos = curr;
    }

    private static final class BallInstance {
        /**
         * Lightweight tracking state for one rendered ball entity.
         *
         * <p>It stores only classification and trail-emission bookkeeping; the
         * actual particles, trails, and density fields are owned by their
         * specialized systems.
         */
        final int entityId;
        final WeakReference<Entity> entityRef;
        boolean isSnagBall;
        boolean usesPenumbraTrail;
        boolean isDarkBall;
        final OneShotStartLatch darkCaptureStart = new OneShotStartLatch();
        boolean resolved = false;
        long startTick;
        int fadeInTicks;
        int sustainTicks;
        int fadeOutTicks;
        Vec3 lastTrailPos;
        long lastSpawnTick = Long.MIN_VALUE;
        Vec3 lastSnagTrailPos;
        long lastSnagSpawnTick = Long.MIN_VALUE;

        BallInstance(int entityId, @Nullable Entity ent, long startTick, int fi, int sus, int fo,
                     boolean isSnagBall, boolean usesPenumbraTrail, boolean isDarkBall) {
            this.entityId = entityId;
            this.entityRef = new WeakReference<>(ent);
            this.isSnagBall = isSnagBall;
            this.usesPenumbraTrail = usesPenumbraTrail;
            this.isDarkBall = isDarkBall;
            this.startTick = startTick;
            this.fadeInTicks = Math.max(1, fi);
            this.sustainTicks = Math.max(0, sus);
            this.fadeOutTicks = Math.max(1, fo);
        }

        void beginImmediateFadeOut(long now, int outTicks) {
            this.startTick = now - (long) this.fadeInTicks - (long) this.sustainTicks;
            this.fadeOutTicks = Math.max(1, outTicks);
        }

        boolean isExpired(long now) {
            long total = (long) fadeInTicks + (long) sustainTicks + (long) fadeOutTicks;
            return now - startTick >= total;
        }
    }

    public static void apply(ShaderInstance shader) {
        if (shader == null) return;

        float time = Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        // These defaults are intentionally sparse: null means "leave the shader
        // JSON/default uniform alone" so older shader variants keep working.
        // Palette stops and thresholds
        float[] u_c0 = null;
        float[] u_c1 = new float[]{1.25f, 0.72f, 0.12f};
        float[] u_c2 = new float[]{1.25f, 0.25f, 0.10f};
        float[] u_c3 = null;
        Float u_t1 = 0.250f;
        Float u_t2 = null;
        Float u_t3 = null;
        float[] u_lumaCoeff = null;

        float[] u_glowTint = null;

        set1f(shader, "u_time", time);

        set1f(shader, "u_rimStrength", null);
        set1f(shader, "u_pulseSpeed", 0.125f);
        set1f(shader, "u_orbIntensity", 1.4f);
        set1f(shader, "u_orbSoftness", 1.0f);

        set1f(shader, "u_starStrength", 1.0f);
        set1f(shader, "u_starSharpness", 20.0f);
        set1f(shader, "u_starCount", 4.0f);
        set1f(shader, "u_starFalloff", 0.65f);
        set1f(shader, "u_starRotateSpeed", 0.0f);
        set1f(shader, "u_starPhase", 0.0f);

        set1f(shader, "u_glowMix", 1.0f);
        set1f(shader, "u_paletteSpeed", 0.20f);
        set1f(shader, "u_paletteShift", null);
        set1f(shader, "u_paletteSaturation", null);

        set1f(shader, "u_t1", u_t1);
        set1f(shader, "u_t2", u_t2);
        set1f(shader, "u_t3", u_t3);

        if (u_glowTint != null && u_glowTint.length >= 3) {
            set3f(shader, "u_glowTint", u_glowTint[0], u_glowTint[1], u_glowTint[2]);
        }

        if (u_c0 != null && u_c0.length >= 3)
            set3f(shader, "u_c0", u_c0[0], u_c0[1], u_c0[2]);
        if (u_c1 != null && u_c1.length >= 3)
            set3f(shader, "u_c1", u_c1[0], u_c1[1], u_c1[2]);
        if (u_c2 != null && u_c2.length >= 3)
            set3f(shader, "u_c2", u_c2[0], u_c2[1], u_c2[2]);
        if (u_c3 != null && u_c3.length >= 3)
            set3f(shader, "u_c3", u_c3[0], u_c3[1], u_c3[2]);
        if (u_lumaCoeff != null && u_lumaCoeff.length >= 3)
            set3f(shader, "u_lumaCoeff", u_lumaCoeff[0], u_lumaCoeff[1], u_lumaCoeff[2]);
    }

    private static void set1f(ShaderInstance shader, String name, Float value) {
        if (value == null) return;
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    private static void set3f(ShaderInstance shader, String name, float x, float y, float z) {
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(x, y, z);
    }
}
