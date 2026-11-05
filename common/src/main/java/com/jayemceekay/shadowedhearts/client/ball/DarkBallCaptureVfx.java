package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallCaptureTimings;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dark Ball capture VFX: a captured silhouette that deforms and flows into
 * the ball as anime energy.
 */
public final class DarkBallCaptureVfx {

    public static final float TIMING_SCALE = DarkBallCaptureTimings.TIMING_SCALE;
    public static final float VFX_END = DarkBallCaptureTimings.VFX_DURATION_SECONDS;
    private static final float CONVERSION_START_FRACTION = 0.021621622f;
    private static final float CONVERSION_SOLID_FRACTION = 0.15135135f;
    private static final float TURBULENCE_START_FRACTION = 0.05383784f;
    private static final float SIPHON_START_FRACTION = 0.45383784f;
    private static final float SIPHON_END_FRACTION = 0.970f;
    private static final float BALL_ABSORB_END_FRACTION = 0.990f;

    public static final float CONVERSION_START =
            VFX_END * CONVERSION_START_FRACTION;
    public static final float CONVERSION_SOLID =
            VFX_END * CONVERSION_SOLID_FRACTION;
    public static final float FROZEN_MODEL_CROSSFADE_START = CONVERSION_START;
    public static final float FROZEN_MODEL_CROSSFADE_END =
            CONVERSION_SOLID * 0.62f;
    static final float CAPTURE_CLEANUP_AGE_SECONDS =
            DarkBallCaptureTimings.HIT_TO_FALL_DELAY_SECONDS;
    public static final float TURBULENCE_START =
            VFX_END * TURBULENCE_START_FRACTION;
    public static final float SIPHON_START =
            VFX_END * SIPHON_START_FRACTION;
    public static final float SIPHON_END =
            VFX_END * SIPHON_END_FRACTION;
    public static final float BALL_ABSORB_END =
            VFX_END * BALL_ABSORB_END_FRACTION;
    static final float PROXY_DEPTH_FADE_START =
            SIPHON_END + (BALL_ABSORB_END - SIPHON_END) * 0.5f;
    private static final int MAX_SNAPSHOT_QUADS = 12000;
    private static final int FULLBRIGHT = 0x00F000F0;
    /**
     * One delayed render/simulation-step budget reserved by activation
     * scheduling. This is not a cap on the visual choreography clock.
     */
    static final float BODY_ACTIVATION_RAMP_SECONDS =
            VFX_END * 0.045f;
    static final float MIN_BODY_ACTIVATION_RAMP_SECONDS =
            Math.min(0.020f, VFX_END * 0.010f);
    /**
     * Isolates capture preparation from Minecraft's shared background pool.
     * Two daemon workers preserve simultaneous-capture progress without
     * allowing voxel/SDF work to fan out across every processor.
     */
    private static final DarkBallPreparationExecutor PREPARATION_EXECUTOR =
            new DarkBallPreparationExecutor(2, 8);
    private static final MultiBufferSource.BufferSource MASK_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private static final Map<Integer, DarkBallCaptureVfx> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_CAPTURE_GENERATION =
            new AtomicLong();
    private static final DarkBallPresentationClock PRESENTATION_CLOCK =
            new DarkBallPresentationClock();
    private static float lastChoreographyDeltaSeconds;
    private static long lastMaskFrameToken = Long.MIN_VALUE;
    private static boolean maskCapturedThisFrame;
    private static boolean renderingModelMask;
    private static float compositeProxyDepthAvailable;
    private static boolean compositeExactMaskReady;
    private static boolean compositeAnimeEnergyCoverage;
    private static float compositeAnimeIntakePulse;
    private static final Vector2f compositeAnimeFlowDirection = new Vector2f(0, 1);
    private static float compositeDeformationBlend;
    private static final Vector4f compositeBodyUvBounds = new Vector4f(0f, 0f, 1f, 1f);
    private static final Matrix4f compositeInvProjection = new Matrix4f();
    private static boolean reconstructionAuxiliaryTargetsCleared;
    private static ModelSnapshotCapture activeSnapshotCapture;
    private static TexturedSnapshotCapture activeTexturedSnapshotCapture;

    public static void start(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.darkBallReplacement()) return;
        installCapture(ball, pokemon);
    }

    public static void startAtHit(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        // Decide once at the hit. Existing captures retain their snapshot ownership and clock.
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.darkBallReplacement()) return;
        DarkBallCaptureVfx vfx = installCapture(ball, pokemon);
        vfx.markHitStage();
    }

    /**
     * Installs the sole presentation authority for one Pokemon.
     *
     * <p>Different Pokemon retain fully independent active captures. A second
     * ball targeting the same Pokemon, however, must replace the older
     * capture: once the older snapshot suppresses the live model, the newer
     * request can no longer pass through the renderer to build its own
     * snapshot. Serializing installation also keeps repeated HIT callbacks
     * idempotent while async preparation completes.</p>
     */
    private static synchronized DarkBallCaptureVfx installCapture(
            EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        DarkBallCaptureVfx capture = installCapture(ball.getId(), pokemon.getId());
        DarkBallAnimeBallFinish.arm(ball);
        return capture;
    }

    static synchronized DarkBallCaptureVfx installCapture(int ballId, int pokemonId) {
        DarkBallCaptureVfx current = ACTIVE.get(ballId);
        if (current != null && current.pokemonId == pokemonId) {
            return current;
        }

        for (Map.Entry<Integer, DarkBallCaptureVfx> entry
                : List.copyOf(ACTIVE.entrySet())) {
            DarkBallCaptureVfx active = entry.getValue();
            if ((entry.getKey() == ballId || active.pokemonId == pokemonId)
                    && ACTIVE.remove(entry.getKey(), active)) {
                DarkBallAnimeBallFinish.stop(active.ballId);
                active.destroy();
            }
        }

        DarkBallCaptureVfx installed = new DarkBallCaptureVfx(ballId, pokemonId);
        ACTIVE.put(ballId, installed);
        return installed;
    }

    public static void stop(int ballId) {
        DarkBallAnimeBallFinish.stop(ballId);
        DarkBallCaptureVfx removed = ACTIVE.remove(ballId);
        if (removed != null) {
            removed.destroy();
        }
    }

    public static void clearAll() {
        DarkBallAnimeBallFinish.clear();
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            vfx.destroy();
        }
        ACTIVE.clear();
        PRESENTATION_CLOCK.reset();
        lastChoreographyDeltaSeconds = 0.0f;
        activeSnapshotCapture = null;
        activeTexturedSnapshotCapture = null;
        maskCapturedThisFrame = false;
        renderingModelMask = false;
    }

    /** Retire resources and captured material handles before shader reload. */
    public static void onResourceReload() {
        clearAll();
        DarkBallDensityFBO.destroy();
    }

    public static DarkBallCaptureVfx get(int ballId) {
        return ACTIVE.get(ballId);
    }

    /** Closure callbacks share the live capture clock, including its pause behavior. */
    public static float getRemainingAnimeCaptureSeconds(int ballId) {
        DarkBallCaptureVfx vfx = ACTIVE.get(ballId);
        return vfx != null
                ? Math.max(VFX_END - vfx.age, 0.0f) : Float.NaN;
    }

    /** Returns true only after the direct replacement has rendered successfully. */
    public static boolean isReplacementReady(int ballId) {
        DarkBallCaptureVfx vfx = ACTIVE.get(ballId);
        return vfx != null
                && vfx.snapshotApplied && vfx.directReplacementReady;
    }

    public static java.util.Collection<DarkBallCaptureVfx> getActiveInstances() {
        return List.copyOf(ACTIVE.values());
    }

    /** Clears last frame's readiness before the direct FBO pipeline runs. */
    public static void beginDirectCompositeFrame() {
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            vfx.directReplacementReady = false;
        }
    }

    /** Publishes readiness only to the capture whose exact-core postprocess ran. */
    static void finishDirectCompositeCapture(
            DarkBallCaptureVfx vfx,
            boolean compositeRendered) {
        if (vfx != null && ACTIVE.get(vfx.ballId) == vfx) {
            vfx.directReplacementReady = compositeRendered;
        }
    }

    public static DarkBallCaptureVfx getByPokemonId(int pokemonId) {
        DarkBallCaptureVfx newest = null;
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            if (vfx.pokemonId == pokemonId
                    && (newest == null
                    || vfx.captureGeneration > newest.captureGeneration)) {
                newest = vfx;
            }
        }
        return newest;
    }

    public static boolean shouldHideOriginalModel(PokemonEntity entity) {
        if (entity == null) {
            return false;
        }
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            if (vfx.pokemonId == entity.getId() && vfx.snapshotApplied) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether Minecraft's separately rendered entity shadow should be
     * suppressed for this Pokemon.
     *
     * <p>The model and its shadow have deliberately different handoff
     * authorities. While a snapshot is requested, the model must still run
     * through its capture-only buffer so the replacement can be built, but its
     * vanilla ground shadow is not part of that capture. Hide the shadow from
     * the first requested capture frame onward, then keep it hidden while the
     * captured replacement owns the presentation.
     */
    public static boolean shouldHideEntityShadow(PokemonEntity entity) {
        if (entity == null) {
            return false;
        }
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            if (vfx.pokemonId == entity.getId()
                    && (vfx.snapshotRequested || vfx.snapshotApplied)) {
                return true;
            }
        }
        return false;
    }

    public static boolean wantsSnapshotCapture(PokemonEntity entity) {
        if (isIrisShadowRenderActive()) {
            return false;
        }
        DarkBallCaptureVfx vfx = entity == null ? null : getByPokemonId(entity.getId());
        return vfx != null && vfx.snapshotRequested && !vfx.snapshotApplied;
    }

    public static MultiBufferSource wrapSnapshotBuffer(PokemonEntity entity,
                                                       ResourceLocation texture,
                                                       MultiBufferSource delegate) {
        if (entity == null) {
            return delegate;
        }
        if (isIrisShadowRenderActive()) {
            return delegate;
        }

        DarkBallCaptureVfx vfx = getByPokemonId(entity.getId());
        if (vfx == null || !vfx.snapshotRequested || vfx.snapshotApplied) {
            return delegate;
        }

        // Once the Dark Ball has requested its hit snapshot, the original
        // Pokemon must never reach the live color or depth buffers. If capture
        // inputs are temporarily unavailable, suppress this render and retry
        // next frame instead of leaking the model through the handoff.
        if (texture == null || delegate == null) {
            return DarkBallFieldMaskBufferSource.discarding();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return DarkBallFieldMaskBufferSource.discarding();
        }

        vfx.snapshotQuads.clear();
        vfx.texturedSnapshotQuads.clear();
        vfx.capturedModelVertices.clear();
        vfx.capturedModelTriangles.clear();
        vfx.voxelVolume = null;
        vfx.voxelReadyAge = Float.NaN;
        vfx.voxelBuildMillis = -1L;
        vfx.surfaceReadyAge = Float.NaN;
        vfx.surfaceBuildMillis = -1L;
        vfx.cancelPendingVoxelBuild();
        vfx.destroyCapturedVolumeResources();
        vfx.snapshotBounds = null;
        vfx.snapshotTexture = texture;
        vfx.texturedSnapshotCapturing = true;

        DarkBallFieldMaskBufferSource wrapper = new DarkBallFieldMaskBufferSource(
                vfx.texturedSnapshotQuads,
                vfx.capturedModelVertices,
                vfx.capturedModelTriangles,
                mc.gameRenderer.getMainCamera().getPosition()
        );
        activeTexturedSnapshotCapture = new TexturedSnapshotCapture(vfx, entity, wrapper);
        return wrapper;
    }

    public static void tickAll(float ignoredDt) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        float choreographyDt = PRESENTATION_CLOCK.sample(
                now, minecraft.isPaused(), !ACTIVE.isEmpty());
        lastChoreographyDeltaSeconds = choreographyDt;

        ACTIVE.entrySet().removeIf(entry -> {
            DarkBallCaptureVfx capture = entry.getValue();
            Entity ball = minecraft.level == null ? null
                    : minecraft.level.getEntity(capture.ballId);
            Entity pokemon = minecraft.level == null ? null
                    : minecraft.level.getEntity(capture.pokemonId);
            boolean aborted = !(ball instanceof EmptyPokeBallEntity pokeBall)
                    || !(pokemon instanceof PokemonEntity)
                    || (capture.hitStageSeen
                    && pokeBall.getCaptureState() == EmptyPokeBallEntity.CaptureState.NOT);
            if (aborted) {
                DarkBallAnimeBallFinish.stop(capture.ballId);
                capture.destroy();
                return true;
            }
            capture.tick(choreographyDt);
            boolean expired = capture.age
                    > CAPTURE_CLEANUP_AGE_SECONDS;
            if (expired) {
                capture.destroy();
            }
            return expired;
        });
    }

    /**
     * Returns uncapped monotonic elapsed time for the capture choreography.
     *
     * <p>The instance tick has no simulation consumer: it only installs
     * asynchronous results and advances {@link #age}. Splat and volume
     * simulations separately cap or fixed-step their age-derived deltas, so a
     * slow render frame must not stretch the capture sequence.
     */
    static float choreographyDeltaSeconds(long previousNanos,
                                          long currentNanos) {
        return DarkBallPresentationClock.elapsedSeconds(previousNanos, currentNanos);
    }

    /**
     * Returns an immutable, deterministic back-to-front render queue.
     *
     * <p>Each entry is composited in its own complete FBO transaction. That
     * keeps exact masks, proxy depth, anime material, and silhouette
     * distance fields isolated while allowing the expensive screen-sized
     * scratch targets to be reused. Nearer captures render last so two Dark
     * Ball presentations have stable overlap ownership.</p>
     */
    static List<DarkBallCaptureVfx> orderedActiveCaptures(Camera camera) {
        List<DarkBallCaptureVfx> ordered = new ArrayList<>(ACTIVE.values());
        if (ordered.size() < 2) {
            return List.copyOf(ordered);
        }
        Vec3 cameraPosition = camera == null
                ? Vec3.ZERO
                : camera.getPosition();
        Vector3f lookVector = camera == null
                ? new Vector3f(0.0f, 0.0f, 1.0f)
                : camera.getLookVector();
        Vec3 cameraForward = new Vec3(
                lookVector.x(), lookVector.y(), lookVector.z());
        ordered.sort(Comparator
                .comparingDouble((DarkBallCaptureVfx vfx) ->
                        vfx.cameraDepth(cameraPosition, cameraForward))
                .reversed()
                .thenComparingInt(vfx -> vfx.ballId)
                .thenComparingLong(vfx -> vfx.captureGeneration));
        return List.copyOf(ordered);
    }

    private double cameraDepth(Vec3 cameraPosition, Vec3 cameraForward) {
        Vec3 center = snapshotBounds != null ? snapshotCenter : null;
        Minecraft mc = Minecraft.getInstance();
        if (center == null && mc.level != null) {
            Entity pokemon = mc.level.getEntity(pokemonId);
            if (pokemon != null) {
                center = pokemon.getBoundingBox().getCenter();
            } else {
                Entity ball = mc.level.getEntity(ballId);
                if (ball != null) {
                    center = ball.position();
                }
            }
        }
        return center == null
                ? Double.POSITIVE_INFINITY
                : center.subtract(cameraPosition).dot(cameraForward);
    }

    /**
     * Resets and preloads the static render-thread composite context for one
     * capture before its FBO chooses resolution and projected bounds.
     */
    static boolean prepareDensityPass(
            DarkBallCaptureVfx vfx,
            Camera camera,
            float partialTick) {
        if (vfx == null || camera == null || ACTIVE.get(vfx.ballId) != vfx) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        Vec3 camPos = camera.getPosition();
        resetCompositeContext(camera, camPos);

        Entity ballEntity = mc.level.getEntity(vfx.ballId);
        Entity pokemonEntity = mc.level.getEntity(vfx.pokemonId);
        if (!(ballEntity instanceof EmptyPokeBallEntity ball)
                || !(pokemonEntity instanceof PokemonEntity pokemon)) {
            return false;
        }
        Vec3 ballPos = new Vec3(
                Mth.lerp(partialTick, ball.xOld, ball.getX()),
                Mth.lerp(partialTick, ball.yOld, ball.getY()),
                Mth.lerp(partialTick, ball.zOld, ball.getZ()))
                .add(0.0, 0.08, 0.0);
        AABB bounds = vfx.snapshotBounds != null
                ? vfx.snapshotBounds
                : pokemon.getBoundingBox();
        Vec3 pokemonCenter = vfx.snapshotBounds != null
                ? vfx.snapshotCenter
                : bounds.getCenter().add(
                        0.0, pokemon.getBbHeight() * 0.04, 0.0);
        if (vfx.hasTexturedSnapshotMask() || vfx.voxelVolume != null) {
            // Preload only bounds and immutable composite inputs. Activation
            // belongs to the actual draw below, after the caller has secured
            // an FBO transaction for this capture.
            vfx.updateCompositeReconstructionInputs(
                    camera, camPos, ballPos, pokemonCenter, bounds, false);
        }
        return true;
    }

    /** Draws only the capture prepared by {@link #prepareDensityPass}. */
    static void renderDensityPass(
            DarkBallCaptureVfx vfx,
            Camera camera,
            float partialTick) {
        if (vfx == null || camera == null || ACTIVE.get(vfx.ballId) != vfx) {
            return;
        }
        vfx.renderDensityPass(
                camera, camera.getPosition(), partialTick, true);
    }

    private static void resetCompositeContext(Camera camera, Vec3 camPos) {
        compositeProxyDepthAvailable = 0.0f;
        compositeExactMaskReady = false;
        compositeAnimeEnergyCoverage = false;
        compositeAnimeIntakePulse = 0;
        compositeAnimeFlowDirection.set(0, 1);
        compositeDeformationBlend = 0;
        compositeBodyUvBounds.set(0, 0, 1, 1);
        compositeInvProjection.set(RenderSystem.getProjectionMatrix()).invert();
        reconstructionAuxiliaryTargetsCleared = false;
    }

    public static float getCompositeProxyDepthAvailable() {
        return compositeProxyDepthAvailable;
    }

    public static boolean isCompositeExactMaskReady() {
        return compositeExactMaskReady;
    }

    public static boolean isCompositeAnimeEnergyCoverage() { return compositeAnimeEnergyCoverage; }
    public static float getCompositeAnimeIntakePulse() { return compositeAnimeIntakePulse; }
    public static Vector2f getCompositeAnimeFlowDirection() { return compositeAnimeFlowDirection; }

    public static float getCompositeDeformationBlend() {
        return compositeDeformationBlend;
    }

    public static Vector4f getCompositeBodyUvBounds() {
        return compositeBodyUvBounds;
    }

    public static Matrix4f getCompositeInvProjection() {
        return compositeInvProjection;
    }

    public static void beginModelSnapshotCapture(PokemonEntity entity, PoseStack renderStack, Bone rootPart) {
        activeSnapshotCapture = null;
        if (entity == null || renderStack == null || rootPart == null || renderingModelMask) {
            return;
        }
        if (AuraReaderPulseRenderer.IRIS_HANDLER != null && AuraReaderPulseRenderer.IRIS_HANDLER.isShadowRenderActive()) {
            return;
        }

        DarkBallCaptureVfx vfx = getByPokemonId(entity.getId());
        if (vfx == null || !vfx.snapshotRequested || vfx.snapshotApplied) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 sourceWorld = new Vec3(
                Mth.lerp(partialTicks, entity.xOld, entity.getX()),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()),
                Mth.lerp(partialTicks, entity.zOld, entity.getZ())
        );
        Vector3f rawModelOrigin = renderStack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        Vec3 poseToWorldOffset = sourceWorld.subtract(rawModelOrigin.x, rawModelOrigin.y, rawModelOrigin.z);

        boolean captureGeometry = !vfx.texturedSnapshotCapturing;
        if (captureGeometry) {
            vfx.snapshotQuads.clear();
        }
        activeSnapshotCapture = new ModelSnapshotCapture(
                vfx,
                entity,
                poseToWorldOffset,
                captureGeometry);
    }

    public static void captureRenderedModelPart(ModelPart part, PoseStack stack) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        if (capture == null || part == null || stack == null) {
            return;
        }

        Matrix4f pose = stack.last().pose();
        if (capture.captureGeometry
                && capture.vfx.snapshotQuads.size() < MAX_SNAPSHOT_QUADS) {
            for (ModelPart.Cube cube : part.cubes) {
                if (capture.vfx.snapshotQuads.size()
                        >= MAX_SNAPSHOT_QUADS) {
                    break;
                }
                captureCubeFaces(capture, pose, cube);
            }
        }
    }

    public static void endModelSnapshotCapture(PokemonEntity entity) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        activeSnapshotCapture = null;
        if (capture == null || capture.entity != entity) {
            return;
        }
        if (capture.captureGeometry) {
            capture.vfx.finishSnapshotCapture(capture);
        }
    }

    public static void finishTexturedSnapshotCapture(PokemonEntity entity) {
        if (isIrisShadowRenderActive()) {
            return;
        }
        TexturedSnapshotCapture capture = activeTexturedSnapshotCapture;
        if (capture == null || capture.entity != entity) {
            return;
        }

        activeTexturedSnapshotCapture = null;
        capture.bufferSource.finishCapture();
        capture.vfx.finishTexturedSnapshotCapture();
    }

    private static boolean isIrisShadowRenderActive() {
        return AuraReaderPulseRenderer.IRIS_HANDLER != null
                && AuraReaderPulseRenderer.IRIS_HANDLER.isShadowRenderActive();
    }

    public static void renderModelMask(PokemonEntity entity,
                                       ResourceLocation texture,
                                       RenderContext context,
                                       PoseStack stack,
                                       Bone rootPart) {
        if (renderingModelMask || entity == null || texture == null || context == null || rootPart == null) {
            return;
        }

        DarkBallCaptureVfx vfx = getByPokemonId(entity.getId());
        if (vfx == null || ModShaders.DARK_BALL_MASK == null) {
            return;
        }

        float strength = vfx.getMaskStrength();
        if (strength <= 0.002f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(true);
        long frameToken = currentFrameToken(mc, partialTicks);
        if (frameToken != lastMaskFrameToken) {
            lastMaskFrameToken = frameToken;
            maskCapturedThisFrame = false;
        }

        boolean clearForFirstMask = !maskCapturedThisFrame;
        if (!DarkBallDensityFBO.beginDensityPass(
                vfx, clearForFirstMask, clearForFirstMask)) {
            return;
        }

        try {
            if (!DarkBallDensityFBO.beginMaskPass(clearForFirstMask, clearForFirstMask)) {
                return;
            }
            renderingModelMask = true;
            setupMaskUniforms(entity, vfx);
            RenderType maskType = BallRenderTypes.darkBallMask(texture);
            VertexConsumer consumer = MASK_BUFFERS.getBuffer(maskType);
            rootPart.render(context, stack, consumer, FULLBRIGHT, OverlayTexture.NO_OVERLAY, packMaskColor(strength));
            MASK_BUFFERS.endBatch(maskType);
            maskCapturedThisFrame = true;
        } finally {
            renderingModelMask = false;
            DarkBallDensityFBO.endDensityPass();
        }
    }

    public static boolean hasModelMaskThisFrame(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        return maskCapturedThisFrame && lastMaskFrameToken == currentFrameToken(mc, partialTicks);
    }

    private final int ballId;
    private final int pokemonId;
    private final long captureGeneration =
            NEXT_CAPTURE_GENERATION.incrementAndGet();
    private boolean mediumReducedCompositeLatched;
    private final List<SnapshotQuad> snapshotQuads = new ArrayList<>();
    private final List<DarkBallFieldMaskBufferSource.CapturedQuad> texturedSnapshotQuads = new ArrayList<>();
    private final List<Vec3> capturedModelVertices = new ArrayList<>();
    private final List<DarkBallFieldMaskBufferSource.CapturedTriangle> capturedModelTriangles = new ArrayList<>();

    boolean isMediumReducedCompositeLatched() {
        return mediumReducedCompositeLatched;
    }

    void setMediumReducedCompositeLatched(boolean latched) {
        mediumReducedCompositeLatched = latched;
    }

    public float age = 0f;
    private boolean hitStageSeen = false;
    private boolean snapshotRequested = false;
    private boolean snapshotApplied = false;
    private boolean directReplacementReady = false;
    private boolean texturedSnapshotCapturing = false;
    private boolean voxelBuildLogged = false;
    private ResourceLocation snapshotTexture;
    private Vec3 snapshotCenter = Vec3.ZERO;
    private float snapshotExtent = 1.0f;
    private AABB snapshotBounds;
    private DarkBallVolumeBuildResult voxelVolume;
    private DarkBallVfxQuality captureQuality;
    private DarkBallAnimeSourceRenderer sourceRenderer;
    private DarkBallAnimeEnergyRenderer animeEnergyRenderer;
    private DarkBallAnimeMotion.Binding animeMotionBinding;
    private DarkBallPreparationTimings preparationTimings;
    private long nextFrameTimingLogNanos;
    private float voxelReadyAge = Float.NaN;
    private long voxelBuildMillis = -1L;
    private CompletableFuture<VoxelBuildOutput> voxelBuildFuture;
    private CompletableFuture<SurfaceMeshBuildOutput> surfaceMeshBuildFuture;
    private CompletableFuture<SurfaceMeshBuildOutput> preparationJob;
    private AtomicBoolean surfaceMeshBuildCancellation;
    private float directBodyActivationAge = Float.NaN;
    private float firstBodySubmissionAge = Float.NaN;
    private float surfaceReadyAge = Float.NaN;
    private long surfaceBuildMillis = -1L;
    private DarkBallSurfaceMesh surfaceMesh;
    private final DarkBallGpuTimer gpuTimer = new DarkBallGpuTimer();
    private long bodyDrawCpuMicros = -1L;
    private long depthRestoreCpuMicros = -1L;
    private PreparationState preparationState = initialPreparationState();
    private boolean preparationStateLogged;
    private boolean bodyDeadlineLogged;
    private int voxelBuildGeneration;

    private DarkBallCaptureVfx(int ballId, int pokemonId) {
        this.ballId = ballId;
        this.pokemonId = pokemonId;
    }

    DarkBallVfxQuality quality() {
        if (captureQuality == null) {
            captureQuality = DarkBallVfxQuality.parse(
                    com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                            .getInstance().getClientConfig().darkBallVfxQuality());
        }
        return captureQuality;
    }

    DarkBallGpuTimer gpuTimer() {
        return gpuTimer;
    }

    void logFrameTimings() {
        if (!Boolean.getBoolean(DarkBallGpuTimer.ENABLED_PROPERTY)) return;
        long now = System.nanoTime();
        if (now < nextFrameTimingLogNanos) return;
        nextFrameTimingLogNanos = now + 1_000_000_000L;
        Shadowedhearts.LOGGER.info("[ShadowedHearts] Dark Ball anime frame pokemon={} generation={} bodyCpu_us={} depthCpu_us={} compositeGpu_us={}",
                pokemonId, captureGeneration, bodyDrawCpuMicros, depthRestoreCpuMicros,
                gpuTimer.latestMicros(DarkBallGpuTimer.Stage.COMPOSITE));
    }

    private void markHitStage() {
        if (!hitStageSeen) {
            hitStageSeen = true;
            age = 0f;
        }
        if (!snapshotApplied) {
            snapshotRequested = true;
        }
    }

    public float getDesiredPokemonScale() {
        if (age < SIPHON_START) {
            return 1f;
        }
        float t = remapClamped(age, SIPHON_START, SIPHON_END);
        return 1f - smoothstep(0f, 1f, t);
    }

    public float getDissolveProgress() {
        return remapClamped(age, CONVERSION_START, CONVERSION_SOLID);
    }

    public float getAge() {
        return age;
    }

    private float getMaskStrength() {
        float conversion = 0.42f + smoothstep(0f, CONVERSION_SOLID, age) * 0.58f;
        float siphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float release = 1f - smoothstep(0.16f, 0.82f, siphon);
        return conversion * release;
    }

    private float getReconstructionMaskStrength() {
        // This texture is infrastructure, not a visible formation layer. Once
        // captured it is the mandatory coverage authority for every displayed
        // body frame.
        return snapshotApplied && age < VFX_END ? 1.0f : 0.0f;
    }

    private float getDeformationBlend() {
        // Preserve the texture-exact capture while it forms, then hand visual
        // authority to the deformed volume before extraction begins. Keeping
        // the snapshot after this point would pin the original outline in
        // place and turn real SDF displacement into a detached halo.
        return silhouetteHandoffBlendAt(age);
    }

    static float silhouetteHandoffBlendAt(float captureAge) {
        return smoothstep(FROZEN_MODEL_CROSSFADE_START,
                FROZEN_MODEL_CROSSFADE_END, captureAge);
    }

    static float directBodyActivationBlendAt(float captureAge,
                                             float activationAge) {
        if (!Float.isFinite(activationAge)) {
            return 0.0f;
        }
        if (activationAge <= TURBULENCE_START) {
            return 1.0f;
        }
        float rampSeconds =
                lateActivationRampSecondsAt(activationAge);
        return smoothstep(
                activationAge,
                activationAge + rampSeconds,
                captureAge);
    }

    static float lateActivationRampSecondsAt(float activationAge) {
        if (!Float.isFinite(activationAge)) {
            return BODY_ACTIVATION_RAMP_SECONDS;
        }
        float remaining = Math.max(VFX_END - activationAge, 0.0f);
        if (remaining <= 0.0f) {
            return 0.0f;
        }
        return Math.min(
                BODY_ACTIVATION_RAMP_SECONDS,
                Math.max(
                        MIN_BODY_ACTIVATION_RAMP_SECONDS,
                        remaining * 0.30f));
    }

    private void tick(float choreographyDt) {
        // Timestamp readiness at this frame, including its elapsed preparation time.
        age += choreographyDt;
        installVoxelBuildIfReady();
        installSurfaceMeshBuildIfReady();
        if (!bodyDeadlineLogged && snapshotApplied && age >= VFX_END
                && !Float.isFinite(firstBodySubmissionAge)) {
            bodyDeadlineLogged = true;
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball body missed visible window for pokemon {} "
                            + "(age={}ms, budget={}ms, preparation={}, voxelReady={}, sourceReady={}, "
                            + "voxelBuild={}ms, sourceBuild={}ms); no active body draw was submitted before deadline",
                    pokemonId, Math.round(age * 1000.0f), Math.round(VFX_END * 1000.0f),
                    preparationState, voxelVolume != null, surfaceMesh != null,
                    voxelBuildMillis, surfaceBuildMillis);
        }
    }

    private void destroy() {
        cancelPendingVoxelBuild();
        destroyCapturedVolumeResources();
        gpuTimer.close();
    }

    private void cancelPendingVoxelBuild() {
        voxelBuildGeneration++;
        CompletableFuture<SurfaceMeshBuildOutput> job = preparationJob;
        preparationJob = null;
        if (job != null) {
            job.cancel(false);
        }
        CompletableFuture<VoxelBuildOutput> pending = voxelBuildFuture;
        voxelBuildFuture = null;
        if (pending != null) {
            pending.cancel(false);
        }
        cancelPendingSurfaceMeshBuild();
        surfaceMesh = null;
        animeMotionBinding = null;
        preparationTimings = null;
        nextFrameTimingLogNanos = 0L;
        directBodyActivationAge = Float.NaN;
        firstBodySubmissionAge = Float.NaN;
        destroySurfaceMeshRenderer();
        preparationState = initialPreparationState();
        preparationStateLogged = false;
        bodyDeadlineLogged = false;
    }

    private void cancelPendingSurfaceMeshBuild() {
        AtomicBoolean cancellation = surfaceMeshBuildCancellation;
        surfaceMeshBuildCancellation = null;
        if (cancellation != null) {
            cancellation.set(true);
        }
        CompletableFuture<SurfaceMeshBuildOutput> pending =
                surfaceMeshBuildFuture;
        surfaceMeshBuildFuture = null;
        if (pending != null) {
            pending.cancel(false);
        }
        directBodyActivationAge = Float.NaN;
    }

    private void destroyCapturedVolumeResources() {
        voxelVolume = null;
    }

    private void destroySurfaceMeshRenderer() {
        if (animeEnergyRenderer != null) {
            animeEnergyRenderer.close();
            animeEnergyRenderer = null;
        }
        if (sourceRenderer != null) {
            sourceRenderer.close();
            sourceRenderer = null;
        }
    }

    private void startDirectBodyActivationIfNeeded() {
        if (preparationState != PreparationState.READY
                || Float.isFinite(directBodyActivationAge)) {
            return;
        }
        // Record the first frame that can actually submit the anime energy
        // body. Preserving this timestamp across a failed direct transaction
        // keeps exact-mask recovery from changing the choreography clock.
        directBodyActivationAge = age;
    }

    private float currentSurfacePresentationAge() {
        return DarkBallAnimePresentation.presentationAgeAt(age, directBodyActivationAge, VFX_END);
    }



    private float currentDirectBodyActivationBlend() {
        if (preparationState != PreparationState.READY) {
            return 0.0f;
        }
        return directBodyActivationBlendAt(
                age, directBodyActivationAge);
    }

    private void renderDensityPass(Camera camera, Vec3 camPos, float partialTick,
                                   boolean allowDirectVolume) {
        bodyDrawCpuMicros = -1L;
        depthRestoreCpuMicros = -1L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity ballEnt = mc.level.getEntity(ballId);
        Entity pokemonEnt = mc.level.getEntity(pokemonId);
        if (!(ballEnt instanceof EmptyPokeBallEntity ball)) {
            return;
        }
        if (!(pokemonEnt instanceof PokemonEntity pokemon)) {
            return;
        }

        boolean renderReconstructionMask = hasTexturedSnapshotMask();
        Vec3 ballPos = new Vec3(
                Mth.lerp(partialTick, ball.xOld, ball.getX()),
                Mth.lerp(partialTick, ball.yOld, ball.getY()),
                Mth.lerp(partialTick, ball.zOld, ball.getZ())
        ).add(0, 0.08, 0);

        AABB bounds = snapshotBounds != null ? snapshotBounds : pokemon.getBoundingBox();
        Vec3 pokemonCenter = snapshotBounds != null
                ? snapshotCenter
                : bounds.getCenter().add(0, pokemon.getBbHeight() * 0.04, 0);
        if ((allowDirectVolume && voxelVolume != null) || renderReconstructionMask) {
            // Capture immutable projection inputs before the anime draw.
            updateCompositeReconstructionInputs(camera, camPos, ballPos,
                    pokemonCenter, bounds, allowDirectVolume);
        }
        boolean reconstructionAuxiliaryRendered = false;
        if (allowDirectVolume && renderReconstructionMask) {
            reconstructionAuxiliaryRendered = renderReconstructionAuxiliaryTargets(camPos);
            compositeExactMaskReady |= reconstructionAuxiliaryRendered;
            restoreDensityPassDrawState();
        }
        if (allowDirectVolume && voxelVolume != null) {
            if (preparationState == PreparationState.PENDING) {
                // Preparation wall time does not scale with a shortened visual
                // profile. Keep the exact captured mask while pending and
                // accept the immutable anime source whenever it completes;
                // the instance schedule will compress only the remaining
                // turbulence/collapse choreography.
                return;
            }
            if (preparationState == PreparationState.READY) {
                renderAnimeBody(ballPos, camera, camPos);
                return;
            }
        }
        // If anime source is not ready, the captured exact mask remains
        // authoritative. The live Pokemon stays suppressed.
    }

    private boolean renderAnimeBody(Vec3 ballPos, Camera camera, Vec3 cameraPosition) {
        bodyDrawCpuMicros = -1L;
        depthRestoreCpuMicros = -1L;
        if (surfaceMesh == null || voxelVolume == null || animeMotionBinding == null
                || animeMotionBinding.energyShape() == null) {
            failAnimeBody("anime source or energy binding unavailable");
            return false;
        }
        Vector3f ballLocal = volumeWorldToLocal(voxelVolume, ballPos);
        startDirectBodyActivationIfNeeded();
        float presentationAge = currentSurfacePresentationAge();
        float handoff = DarkBallAnimePresentation.energyHandoffAt(age, directBodyActivationAge, VFX_END);
        DarkBallAnimePresentation.Tracks tracks = DarkBallAnimePresentation.at(presentationAge, VFX_END);
        DarkBallAnimeMotion.Frame frame = animeMotionBinding.frame(new Vec3(ballLocal.x, ballLocal.y, ballLocal.z),
                tracks.pull(), tracks.broadLobes() * currentDirectBodyActivationBlend(), presentationAge, tracks.squash());
        boolean active = tracks.pull() < 1.0f;
        boolean submittedDepth = false;
        String failure = null;
        long started = System.nanoTime();
        try (var timing = DarkBallGpuTimer.measure(gpuTimer, DarkBallGpuTimer.Stage.HYBRID_CORE)) {
            if (active && handoff > 0) {
                if (animeEnergyRenderer == null) animeEnergyRenderer = new DarkBallAnimeEnergyRenderer();
                DarkBallAnimeEnergyBody.Frame energy = DarkBallAnimeEnergyBody.frame(frame, handoff);
                if (!animeEnergyRenderer.render(energy, voxelVolume, camera, tracks.conversion() * handoff))
                    failure = "anime energy draw unavailable";
                submittedDepth = energy.primitiveCount() > 0 || energy.rest() != null;
            }
            if (active && failure == null && handoff < 1) {
                // Preserve energy coverage before restoring terrain depth for the complementary source draw.
                if (!DarkBallDensityFBO.prepareBodyDepthSnapshot(this)
                        || !DarkBallDensityFBO.restoreSceneDepthAfterDirectDraw()) {
                    failure = "anime handoff depth snapshot/restore failed";
                } else {
                    if (sourceRenderer == null) sourceRenderer = new DarkBallAnimeSourceRenderer();
                    DarkBallAnimeMotion.Frame source = animeMotionBinding.frame(frame.target(),
                            frame.pullProgress(), 0, presentationAge, 0);
                    if (!sourceRenderer.render(surfaceMesh, voxelVolume, camera,
                            tracks.conversion() * (1 - handoff), source, handoff > 0))
                        failure = "anime source handoff unavailable";
                    submittedDepth = true;
                }
            }
        } catch (Throwable error) {
            failure = "anime transaction threw " + error.getClass().getSimpleName();
            Shadowedhearts.LOGGER.error("Dark Ball anime draw failed for pokemon {}; retaining captured mask", pokemonId, error);
        } finally {
            bodyDrawCpuMicros = elapsedMicros(started);
        }
        if (failure == null && submittedDepth) {
            started = System.nanoTime();
            try (var timing = DarkBallGpuTimer.measure(gpuTimer, DarkBallGpuTimer.Stage.DEPTH_RESTORE)) {
                if (!DarkBallDensityFBO.restoreSceneDepthAfterDirectDraw()) failure = "scene depth restore failed";
            } finally {
                depthRestoreCpuMicros = elapsedMicros(started);
            }
        }
        if (failure != null) {
            DarkBallDensityFBO.resetDensityPassAfterFailedDirectDraw();
            destroySurfaceMeshRenderer();
            failAnimeBody(failure);
            return false;
        }
        compositeAnimeEnergyCoverage = handoff > 0;
        compositeAnimeIntakePulse = tracks.intakePulse();
        compositeDeformationBlend = compositeAnimeEnergyCoverage ? 1.0f
                : Math.max(compositeDeformationBlend, tracks.conversion());
        Vector2f bodyUv = projectWorldToScreenUv(snapshotCenter, camera, cameraPosition);
        Vector2f ballUv = projectWorldToScreenUv(ballPos, camera, cameraPosition);
        if (bodyUv != null && ballUv != null) {
            Vector2f direction = new Vector2f(ballUv).sub(bodyUv);
            if (direction.lengthSquared() > 1.0e-8f) compositeAnimeFlowDirection.set(direction.normalize());
        }
        DarkBallDensityFBO.markBodyMaterialRendered(DarkBallProjectedEffectBounds.UvBounds.fullScreen(), active);
        if (active && submittedDepth) {
            if (!Float.isFinite(firstBodySubmissionAge)) firstBodySubmissionAge = age;
            logPreparationState("anime energy body, transport="
                    + (animeMotionBinding.restVolume() != null ? "elastic-pose-to-arc" : "cohesive-arc"));
        }
        return true;
    }

    private static long elapsedMicros(long startedNanos) {
        return Math.max(
                (System.nanoTime() - startedNanos) / 1_000L,
                0L);
    }

    private void logPreparationState(String description) {
        if (preparationStateLogged) {
            return;
        }
        preparationStateLogged = true;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball render path for pokemon {}: {} "
                        + "(duration={}s, firstBodyFrame={}ms, sourceReady={}ms)",
                pokemonId,
                description,
                VFX_END,
                Math.round(firstBodySubmissionAge * 1000.0f),
                Math.round(surfaceReadyAge * 1000.0f));
    }

    private void failAnimeBody(String description) {
        boolean firstFailure =
                preparationState != PreparationState.FAILED;
        preparationState = PreparationState.FAILED;
        if (!firstFailure) {
            return;
        }
        Shadowedhearts.LOGGER.warn(
                "[ShadowedHearts] Dark Ball anime path for pokemon {} "
                        + "failed after selection: {} (duration={}s); "
                        + "retaining the exact captured mask",
                pokemonId,
                description,
                VFX_END);
    }

    private static void restoreDensityPassDrawState() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
    }

    private boolean hasTexturedSnapshotMask() {
        return snapshotApplied
                && snapshotTexture != null
                && !texturedSnapshotQuads.isEmpty()
                && ModShaders.DARK_BALL_MASK != null
                && getReconstructionMaskStrength() > 0.002f;
    }

    private boolean renderReconstructionAuxiliaryTargets(Vec3 camPos) {
        if (snapshotTexture == null || texturedSnapshotQuads.isEmpty()) {
            return false;
        }

        boolean clearAuxiliaryTargets = !reconstructionAuxiliaryTargetsCleared;
        boolean exactMaskRendered = false;
        try (var timing = DarkBallGpuTimer.measure(gpuTimer, DarkBallGpuTimer.Stage.MASK_DRAW)) {
            if (DarkBallDensityFBO.beginMaskPass(clearAuxiliaryTargets, true)) {
                setupSnapshotMaskUniforms();
                exactMaskRendered = renderTexturedSnapshotMask(camPos);
            }
        }

        float proxyStrength = getProxyDepthStrength();
        if (proxyStrength > 0.002f && ModShaders.DARK_BALL_PROXY_DEPTH != null) {
            try (var timing = DarkBallGpuTimer.measure(gpuTimer, DarkBallGpuTimer.Stage.PROXY_DRAW)) {
                boolean frontRendered = renderTexturedSnapshotProxyDepth(
                        camPos, proxyStrength, clearAuxiliaryTargets);
                if (frontRendered) {
                    compositeProxyDepthAvailable = 1.0f;
                }
            }
        }

        reconstructionAuxiliaryTargetsCleared = true;
        DarkBallDensityFBO.resumeDensityPass();
        return exactMaskRendered;
    }

    private boolean renderTexturedSnapshotMask(Vec3 camPos) {
        float strength = getReconstructionMaskStrength();
        if (strength <= 0.002f || snapshotTexture == null || texturedSnapshotQuads.isEmpty()) {
            return false;
        }

        setupSnapshotMaskUniforms();
        RenderType maskType = null;
        VertexConsumer consumer = null;
        DarkBallFieldMaskBufferSource.CapturedMaterial currentMaterial = null;
        Matrix4f identity = new Matrix4f();
        int color = packMaskColor(strength);

        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            if (maskType == null || !java.util.Objects.equals(currentMaterial, quad.material())) {
                if (maskType != null) {
                    MASK_BUFFERS.endBatch(maskType);
                }
                currentMaterial = quad.material();
                ResourceLocation materialTexture = currentMaterial == null
                        ? snapshotTexture : currentMaterial.texture();
                maskType = BallRenderTypes.darkBallMask(materialTexture);
                setSnapshotMaterialAlpha(ModShaders.DARK_BALL_MASK, currentMaterial);
                consumer = MASK_BUFFERS.getBuffer(maskType);
            }
            addTexturedSnapshotVertex(consumer, identity, quad.p1(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p2(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p3(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p4(), camPos, color);
        }

        if (maskType != null) {
            MASK_BUFFERS.endBatch(maskType);
        }
        setSnapshotMaterialAlpha(ModShaders.DARK_BALL_MASK, null);
        return true;
    }

    private boolean renderTexturedSnapshotProxyDepth(
            Vec3 camPos, float strength, boolean clearTarget) {
        if (!DarkBallDensityFBO.beginProxyDepthPass(clearTarget, true)) {
            return false;
        }

        ShaderInstance shader = ModShaders.DARK_BALL_PROXY_DEPTH;
        if (shader == null) {
            return false;
        }

        Uniform uProxyAlpha = shader.getUniform("u_proxyAlpha");
        if (uProxyAlpha != null) {
            uProxyAlpha.set(Mth.clamp(strength, 0.0f, 1.0f));
        }

        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_BACK);

        RenderType proxyType = null;
        VertexConsumer consumer = null;
        DarkBallFieldMaskBufferSource.CapturedMaterial currentMaterial = null;
        Matrix4f identity = new Matrix4f();
        int color = packMaskColor(1.0f);

        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            if (proxyType == null || !java.util.Objects.equals(currentMaterial, quad.material())) {
                if (proxyType != null) {
                    MASK_BUFFERS.endBatch(proxyType);
                }
                currentMaterial = quad.material();
                ResourceLocation materialTexture = currentMaterial == null
                        ? snapshotTexture : currentMaterial.texture();
                proxyType = BallRenderTypes.darkBallProxyDepth(materialTexture);
                setSnapshotMaterialAlpha(shader, currentMaterial);
                consumer = MASK_BUFFERS.getBuffer(proxyType);
            }
            addTexturedSnapshotVertex(consumer, identity, quad.p1(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p2(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p3(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p4(), camPos, color);
        }

        if (proxyType != null) {
            MASK_BUFFERS.endBatch(proxyType);
        }
        setSnapshotMaterialAlpha(shader, null);
        GL11.glCullFace(GL11.GL_BACK);
        return true;
    }

    private static void setSnapshotMaterialAlpha(ShaderInstance shader,
            DarkBallFieldMaskBufferSource.CapturedMaterial material) {
        if (shader != null) {
            Uniform uniform = shader.getUniform("u_ignoreTextureAlpha");
            if (uniform != null) {
                uniform.set(material != null
                        && material.alphaMode() == DarkBallFieldMaskBufferSource.AlphaMode.OPAQUE ? 1.0f : 0.0f);
            }
        }
    }

    private static void addTexturedSnapshotVertex(VertexConsumer consumer,
                                                  Matrix4f pose,
                                                  DarkBallFieldMaskBufferSource.CapturedVertex vertex,
                                                  Vec3 camPos,
                                                  int color) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * (vertex.alpha() / 255.0f));
        consumer.addVertex(pose,
                        (float) (vertex.worldPos().x - camPos.x),
                        (float) (vertex.worldPos().y - camPos.y),
                        (float) (vertex.worldPos().z - camPos.z))
                .setColor(vertex.red(), vertex.green(), vertex.blue(), alpha)
                .setUv(vertex.u(), vertex.v())
                .setUv1(vertex.overlayU(), vertex.overlayV())
                .setUv2(vertex.lightU(), vertex.lightV())
                .setNormal(vertex.normalX(), vertex.normalY(), vertex.normalZ());
    }

    private void setupSnapshotMaskUniforms() {
        ShaderInstance shader = ModShaders.DARK_BALL_MASK;
        if (shader == null) {
            return;
        }
        RenderSystem.setupShaderLights(shader);

        float expand = 0.0f;
        float screenExpand = 0.0f;

        Uniform uExpand = shader.getUniform("MaskExpand");
        if (uExpand != null) {
            uExpand.set(expand);
        }
        Uniform uScreenExpand = shader.getUniform("MaskScreenExpand");
        if (uScreenExpand != null) {
            uScreenExpand.set(screenExpand);
        }
    }

    private float getProxyDepthStrength() {
        // Depth is also reconstruction infrastructure. Making it available on
        // the first exact-mask frame preserves the captured surface for later
        // highlights without exposing the proxy as visible geometry.
        return proxyDepthStrengthAt(age);
    }

    static float proxyDepthStrengthAt(float captureAge) {
        return Mth.clamp(
                1.0f - smoothstep(
                        PROXY_DEPTH_FADE_START,
                        BALL_ABSORB_END,
                        captureAge),
                0.0f,
                1.0f);
    }

    private void updateCompositeReconstructionInputs(Camera camera, Vec3 camPos,
                                                      Vec3 ballPos, Vec3 pokemonCenter,
                                                      AABB bodyBounds, boolean allowDirectVolume) {
        if (allowDirectVolume && voxelVolume != null && animeMotionBinding != null)
            startDirectBodyActivationIfNeeded();
        compositeDeformationBlend = Math.max(compositeDeformationBlend, getDeformationBlend());
        if (bodyBounds != null) {
            Vector3d bodyCenter = new Vector3d(
                    (bodyBounds.minX + bodyBounds.maxX) * 0.5,
                    (bodyBounds.minY + bodyBounds.maxY) * 0.5,
                    (bodyBounds.minZ + bodyBounds.maxZ) * 0.5);
            float halfX =
                    (float) ((bodyBounds.maxX - bodyBounds.minX) * 0.5);
            float halfY =
                    (float) ((bodyBounds.maxY - bodyBounds.minY) * 0.5);
            float halfZ =
                    (float) ((bodyBounds.maxZ - bodyBounds.minZ) * 0.5);
            DarkBallProjectedEffectBounds.Projection bodyProjection =
                    DarkBallProjectedEffectBounds.begin(
                            new Matrix4f(RenderSystem.getProjectionMatrix()),
                            camera.rotation(),
                            new Vector3d(camPos.x, camPos.y, camPos.z));
            bodyProjection.includeOrientedBox(
                    bodyCenter,
                    new Vector3d(1.0, 0.0, 0.0),
                    new Vector3d(0.0, 1.0, 0.0),
                    new Vector3d(0.0, 0.0, 1.0),
                    -halfX, -halfY, -halfZ,
                    halfX, halfY, halfZ);
            DarkBallProjectedEffectBounds.UvBounds bodyUvBounds =
                    bodyProjection.finish(1, 1, 0);
            compositeBodyUvBounds.set(
                    bodyUvBounds.minU(), bodyUvBounds.minV(),
                    bodyUvBounds.maxU(), bodyUvBounds.maxV());
        }
    }

    private static Vector2f projectWorldToScreenUv(Vec3 world, Camera camera, Vec3 camPos) {
        Vector3f view = new Vector3f(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z)
        );
        view.rotate(new Quaternionf(camera.rotation()).conjugate());

        Vector4f clip = new Vector4f(view.x, view.y, view.z, 1.0f);
        RenderSystem.getProjectionMatrix().transform(clip);
        if (clip.w <= 1e-5f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new Vector2f(ndcX * 0.5f + 0.5f, ndcY * 0.5f + 0.5f);
    }

    private void finishSnapshotCapture(ModelSnapshotCapture capture) {
        if (snapshotQuads.isEmpty() || !capture.bounds.hasBounds) {
            return;
        }

        snapshotCenter = capture.bounds.center();
        snapshotExtent = capture.bounds.extent();
        snapshotBounds = capture.bounds.toAabb();
        snapshotRequested = false;
        snapshotApplied = true;
        maskCapturedThisFrame = false;
    }

    private void finishTexturedSnapshotCapture() {
        texturedSnapshotCapturing = false;
        if (texturedSnapshotQuads.isEmpty()) {
            snapshotTexture = null;
            return;
        }

        SnapshotBounds bounds = new SnapshotBounds();
        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            bounds.include(quad.p1().worldPos());
            bounds.include(quad.p2().worldPos());
            bounds.include(quad.p3().worldPos());
            bounds.include(quad.p4().worldPos());
        }
        if (!bounds.hasBounds) {
            snapshotTexture = null;
            return;
        }

        snapshotCenter = bounds.center();
        snapshotExtent = bounds.extent();
        snapshotBounds = bounds.toAabb();
        buildVoxelVolumeFromSnapshot(bounds);
        snapshotRequested = false;
        snapshotApplied = true;
        maskCapturedThisFrame = false;
    }

    private void buildVoxelVolumeFromSnapshot(SnapshotBounds bounds) {
        cancelPendingVoxelBuild();
        destroyCapturedVolumeResources();
        if (capturedModelVertices.size() < 12 && capturedModelTriangles.size() < 6) {
            logVoxelBuild("insufficient captured mesh", capturedModelVertices.size(), capturedModelTriangles.size(), 0);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(ballId) instanceof EmptyPokeBallEntity ball)) return;
        Vec3 ballPos = ball.position().add(0, 0.08, 0);
        Vec3 center = snapshotCenter;
        AABB capturedBounds = bounds.toAabb();
        List<Vec3> vertices = List.copyOf(capturedModelVertices);
        List<DarkBallFieldMaskBufferSource.CapturedTriangle> triangles = List.copyOf(capturedModelTriangles);
        DarkBallSnapshotTextures.CapturedResources resources = DarkBallSnapshotTextures.captureResources(
                mc.getResourceManager(), triangles, snapshotTexture);
        int generation = voxelBuildGeneration;
        AtomicBoolean cancellation = new AtomicBoolean();
        surfaceMeshBuildCancellation = cancellation;
        CompletableFuture<VoxelBuildOutput> voxelResult = new CompletableFuture<>();
        voxelBuildFuture = voxelResult;
        long submitted = System.nanoTime();
        preparationJob = PREPARATION_EXECUTOR.submit(cancellation, () -> {
            long started = System.nanoTime();
            DarkBallSnapshotTextures textures = resources.decode(cancellation::get);
            long decoded = System.nanoTime();
            DarkBallVolumeBuildResult volume = DarkBallVolumeVoxelizer.build(ballPos, center, capturedBounds,
                    vertices, triangles, textures, cancellation::get);
            long voxelBuilt = System.nanoTime();
            if (cancellation.get()) throw new CancellationException();
            voxelResult.complete(new VoxelBuildOutput(generation, volume, vertices.size(), triangles.size(),
                    (voxelBuilt - started) / 1_000_000L));
            if (volume == null) throw new IllegalStateException("Anime source volume unavailable");
            DarkBallAnimeSourceMesh.MeshResult source = DarkBallAnimeSourceMesh.prepare(triangles, textures, volume, cancellation::get);
            if (source.mesh() == null) throw new IllegalStateException("Anime source geometry unavailable: " + source.stats().summary());
            Vector3f local = volumeWorldToLocal(volume, ballPos);
            DarkBallAnimeMotion.Binding binding = DarkBallAnimeMotion.bindEnergy(source.mesh(),
                    new Vec3(local.x, local.y, local.z), cancellation::get);
            DarkBallAnimeRestVolume rest = DarkBallAnimeRestVolume.prepare(volume, binding.radius(), cancellation::get);
            if (rest != null) binding = binding.withRestVolume(rest, cancellation::get);
            else Shadowedhearts.LOGGER.warn("Dark Ball source envelope unavailable; retaining cohesive energy body");
            DarkBallPreparationTimings timings = DarkBallPreparationTimings.completed(submitted, started, decoded, voxelBuilt, System.nanoTime());
            return new SurfaceMeshBuildOutput(generation, source.mesh(), binding, timings,
                    (System.nanoTime() - started) / 1_000_000L);
        });
        preparationJob.whenComplete((result, failure) -> {
            if (failure != null) voxelResult.completeExceptionally(failure);
        });
        surfaceMeshBuildFuture = preparationJob;
    }

    private void installVoxelBuildIfReady() {
        CompletableFuture<VoxelBuildOutput> pending = voxelBuildFuture;
        if (pending == null || !pending.isDone()) return;
        voxelBuildFuture = null;
        try {
            VoxelBuildOutput result = pending.join();
            if (result == null || result.generation() != voxelBuildGeneration) return;
            voxelVolume = result.volume();
            voxelReadyAge = age;
            voxelBuildMillis = result.buildMillis();
            logVoxelBuild("built asynchronously in " + result.buildMillis() + " ms", result.vertexCount(),
                    result.triangleCount(), countVoxelCandidates(voxelVolume));
            logVoxelSdfStats(voxelVolume);
        } catch (CancellationException ignored) {
        } catch (CompletionException failure) {
            Shadowedhearts.LOGGER.warn("Dark Ball source volume failed for pokemon {}; retaining captured mask", pokemonId, failure.getCause());
        }
    }

    private void installSurfaceMeshBuildIfReady() {
        CompletableFuture<SurfaceMeshBuildOutput> pending = surfaceMeshBuildFuture;
        if (pending == null || !pending.isDone()) return;
        surfaceMeshBuildFuture = null;
        surfaceMeshBuildCancellation = null;
        try {
            SurfaceMeshBuildOutput result = pending.join();
            if (result == null || result.generation() != voxelBuildGeneration) return;
            surfaceMesh = result.mesh();
            animeMotionBinding = result.animeBinding();
            surfaceReadyAge = age;
            surfaceBuildMillis = result.buildMillis();
            preparationTimings = result.timings();
            preparationState = PreparationState.READY;
            Shadowedhearts.LOGGER.info("Dark Ball anime source for pokemon {} prepared in {} ms "
                            + "(captureAge={}ms, remaining={}ms; {})",
                    pokemonId, surfaceBuildMillis, Math.round(age * 1000.0f),
                    Math.round(Math.max(0.0f, VFX_END - age) * 1000.0f), preparationTimings.summary());
        } catch (CancellationException ignored) {
        } catch (CompletionException failure) {
            preparationState = PreparationState.FAILED;
            Shadowedhearts.LOGGER.warn("Dark Ball anime preparation failed for pokemon {}; retaining captured mask", pokemonId, failure.getCause());
        }
    }

    private static PreparationState initialPreparationState() {
        return PreparationState.PENDING;
    }

    private static void captureCubeFaces(ModelSnapshotCapture capture, Matrix4f pose, ModelPart.Cube cube) {
        float minX = cube.minX / 16.0f;
        float minY = cube.minY / 16.0f;
        float minZ = cube.minZ / 16.0f;
        float maxX = cube.maxX / 16.0f;
        float maxY = cube.maxY / 16.0f;
        float maxZ = cube.maxZ / 16.0f;

        float inflate = 0.006f;
        minX -= inflate;
        minY -= inflate;
        minZ -= inflate;
        maxX += inflate;
        maxY += inflate;
        maxZ += inflate;

        addSnapshotQuad(capture, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        addSnapshotQuad(capture, pose, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ);
        addSnapshotQuad(capture, pose, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ);
        addSnapshotQuad(capture, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        addSnapshotQuad(capture, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        addSnapshotQuad(capture, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ);
    }

    private static void addSnapshotQuad(ModelSnapshotCapture capture,
                                        Matrix4f pose,
                                        float x1, float y1, float z1,
                                        float x2, float y2, float z2,
                                        float x3, float y3, float z3,
                                        float x4, float y4, float z4) {
        if (capture.vfx.snapshotQuads.size() >= MAX_SNAPSHOT_QUADS) {
            return;
        }

        Vec3 p1 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x1, y1, z1);
        Vec3 p2 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x2, y2, z2);
        Vec3 p3 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x3, y3, z3);
        Vec3 p4 = transformSnapshotPoint(pose, capture.poseToWorldOffset, x4, y4, z4);
        Vec3 center = new Vec3(
                (p1.x + p2.x + p3.x + p4.x) * 0.25,
                (p1.y + p2.y + p3.y + p4.y) * 0.25,
                (p1.z + p2.z + p3.z + p4.z) * 0.25
        );

        capture.include(p1);
        capture.include(p2);
        capture.include(p3);
        capture.include(p4);
        capture.vfx.snapshotQuads.add(new SnapshotQuad(p1, p2, p3, p4, center));
    }

    private static Vec3 transformSnapshotPoint(Matrix4f pose, Vec3 offset, float x, float y, float z) {
        Vector3f transformed = pose.transformPosition(x, y, z, new Vector3f());
        return new Vec3(transformed.x + offset.x, transformed.y + offset.y, transformed.z + offset.z);
    }

    static Vector3f volumeWorldToLocal(DarkBallVolumeBuildResult volume, Vec3 world) {
        Vec3 rel = world.subtract(volume.root());
        return new Vector3f(
                (float) rel.dot(volume.axis()),
                (float) rel.dot(volume.side()),
                (float) rel.dot(volume.up())
        );
    }

    private static int countVoxelCandidates(DarkBallVolumeBuildResult volume) {
        if (volume == null || volume.signedDistanceField() == null) return 0;
        int count = 0;
        for (float distance : volume.signedDistanceField()) if (distance <= 0) count++;
        return count;
    }

    private void logVoxelSdfStats(DarkBallVolumeBuildResult volume) {
        if (volume == null || volume.signedDistanceField() == null || volume.signedDistanceField().length == 0) {
            return;
        }

        int inside = 0;
        int outside = 0;
        float deepestInside = 0f;
        float farthestOutside = 0f;
        for (float d : volume.signedDistanceField()) {
            if (d < 0f) {
                inside++;
                deepestInside = Math.min(deepestInside, d);
            } else {
                outside++;
                farthestOutside = Math.max(farthestOutside, d);
            }
        }

        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball SDF for pokemon {} (insideVoxels={}, outsideVoxels={}, deepestInside={}, farthestOutside={}, voxelX={}, voxelYZ={})",
                pokemonId,
                inside,
                outside,
                String.format(java.util.Locale.ROOT, "%.4f", deepestInside),
                String.format(java.util.Locale.ROOT, "%.4f", farthestOutside),
                String.format(java.util.Locale.ROOT, "%.4f", volume.sdfVoxelX()),
                String.format(java.util.Locale.ROOT, "%.4f", volume.sdfVoxelYz())
        );
    }

    private void logVoxelBuild(String result, int vertexCount, int triangleCount, int candidateCount) {
        if (voxelBuildLogged) {
            return;
        }
        voxelBuildLogged = true;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball voxel source for pokemon {} {} (vertices={}, triangles={}, candidates={})",
                pokemonId,
                result,
                vertexCount,
                triangleCount,
                candidateCount
        );
    }

    private static int packMaskColor(float strength) {
        int alpha = Math.round(Mth.clamp(strength, 0.0f, 1.0f) * 255.0f);
        return (alpha << 24) | 0x00FFFFFF;
    }

    private static void setupMaskUniforms(PokemonEntity entity, DarkBallCaptureVfx vfx) {
        ShaderInstance shader = ModShaders.DARK_BALL_MASK;
        if (shader == null) {
            return;
        }
        RenderSystem.setupShaderLights(shader);

        float modelSize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float siphon = remapClamped(vfx.age, SIPHON_START, SIPHON_END);
        float expand = Mth.clamp(modelSize * Mth.lerp(siphon, 0.018f, 0.085f), 0.018f, 0.18f);
        float screenExpand = Mth.clamp(0.0028f + modelSize * 0.00045f, 0.0028f, 0.0075f);
        screenExpand *= 1.0f - smoothstep(0.55f, 0.92f, siphon) * 0.65f;

        Uniform uExpand = shader.getUniform("MaskExpand");
        if (uExpand != null) {
            uExpand.set(expand);
        }
        Uniform uScreenExpand = shader.getUniform("MaskScreenExpand");
        if (uScreenExpand != null) {
            uScreenExpand.set(screenExpand);
        }
    }

    private static long currentFrameToken(Minecraft mc, float partialTicks) {
        long frameToken = mc.getFrameTimeNs();
        if (frameToken == 0L && mc.level != null) {
            frameToken = (mc.level.getGameTime() << 20) ^ (long) (partialTicks * 1000.0f);
        }
        return frameToken;
    }

    private static float remapClamped(float x, float a, float b) {
        return Mth.clamp((x - a) / (b - a), 0f, 1f);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static final class ModelSnapshotCapture {
        private final DarkBallCaptureVfx vfx;
        private final PokemonEntity entity;
        private final Vec3 poseToWorldOffset;
        private final boolean captureGeometry;
        private final SnapshotBounds bounds = new SnapshotBounds();

        private ModelSnapshotCapture(DarkBallCaptureVfx vfx,
                                     PokemonEntity entity,
                                     Vec3 poseToWorldOffset,
                                     boolean captureGeometry) {
            this.vfx = vfx;
            this.entity = entity;
            this.poseToWorldOffset = poseToWorldOffset;
            this.captureGeometry = captureGeometry;
        }

        private void include(Vec3 point) {
            bounds.include(point);
        }
    }

    private static final class TexturedSnapshotCapture {
        private final DarkBallCaptureVfx vfx;
        private final PokemonEntity entity;
        private final DarkBallFieldMaskBufferSource bufferSource;

        private TexturedSnapshotCapture(DarkBallCaptureVfx vfx,
                                        PokemonEntity entity,
                                        DarkBallFieldMaskBufferSource bufferSource) {
            this.vfx = vfx;
            this.entity = entity;
            this.bufferSource = bufferSource;
        }
    }

    private static final class SnapshotBounds {
        private boolean hasBounds = false;
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(Vec3 point) {
            hasBounds = true;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        private Vec3 center() {
            return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }

        private float extent() {
            double sx = maxX - minX;
            double sy = maxY - minY;
            double sz = maxZ - minZ;
            return Math.max(0.18f, (float) (Math.max(sx, Math.max(sy, sz)) * 0.62));
        }

        private AABB toAabb() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private record SnapshotQuad(Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, Vec3 center) {
    }

    private record VoxelBuildOutput(int generation, DarkBallVolumeBuildResult volume,
            int vertexCount, int triangleCount, long buildMillis) {}

    private record SurfaceMeshBuildOutput(int generation, DarkBallSurfaceMesh mesh,
            DarkBallAnimeMotion.Binding animeBinding, DarkBallPreparationTimings timings, long buildMillis) {}

    private enum PreparationState {
        PENDING,
        READY,
        FAILED
    }

}
