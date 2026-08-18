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
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.network.chat.Component;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dark Ball capture VFX: a cohesive black conversion mass that keeps the
 * target silhouette briefly, then collapses into a twisting Bezier siphon.
 */
public final class DarkBallCaptureVfx {

    public static final float TIMING_SCALE = DarkBallCaptureTimings.TIMING_SCALE;
    public static final float VFX_END = DarkBallCaptureTimings.VFX_DURATION_SECONDS;
    private static final float CONVERSION_START_FRACTION = 0.021621622f;
    private static final float CONVERSION_SOLID_FRACTION = 0.15135135f;
    private static final float VISIBLE_EXPANSION_FULL_FRACTION = 0.20383784f;
    private static final float TURBULENCE_START_FRACTION = 0.05383784f;
    private static final float SIPHON_START_FRACTION = 0.45383784f;
    private static final float SIPHON_END_FRACTION = 0.970f;
    private static final float SIPHON_COLLAPSE_START_FRACTION = 0.895f;
    /*
     * Keep the last locally transported surfels alive for the opening beat of
     * siphon retraction. The transport retirement feather consumes most of
     * this interval internally, leaving only a narrow visible overlap instead
     * of the former post-sink pause.
     */
    private static final float SPLAT_RETRACTION_OVERLAP_FRACTION = 0.040f;
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
    public static final float VISIBLE_EXPANSION_RAMP_SECONDS =
            VFX_END * VISIBLE_EXPANSION_FULL_FRACTION
                    - FROZEN_MODEL_CROSSFADE_END;
    public static final float VISIBLE_EXPANSION_FULL =
            VFX_END * VISIBLE_EXPANSION_FULL_FRACTION;
    public static final float TURBULENCE_RAMP_SECONDS =
            VFX_END * (VISIBLE_EXPANSION_FULL_FRACTION
                    - TURBULENCE_START_FRACTION);
    public static final float TURBULENCE_FULL = VISIBLE_EXPANSION_FULL;
    public static final float TURBULENCE_START =
            VFX_END * TURBULENCE_START_FRACTION;
    public static final float MAX_TURBULENCE_HOLD_SECONDS =
            VFX_END * (SIPHON_START_FRACTION
                    - VISIBLE_EXPANSION_FULL_FRACTION);
    public static final float SIPHON_START =
            VFX_END * SIPHON_START_FRACTION;
    public static final float SIPHON_END =
            VFX_END * SIPHON_END_FRACTION;
    public static final float SIPHON_DRAIN_LEAD_SECONDS =
            VFX_END * (SIPHON_END_FRACTION
                    - SIPHON_COLLAPSE_START_FRACTION);
    public static final float SIPHON_COLLAPSE_START =
            VFX_END * SIPHON_COLLAPSE_START_FRACTION;
    static final float SPLAT_RETRACTION_OVERLAP_SECONDS =
            VFX_END * SPLAT_RETRACTION_OVERLAP_FRACTION;
    static final float SPLAT_SINK_COMPLETE_AGE =
            SIPHON_COLLAPSE_START
                    + SPLAT_RETRACTION_OVERLAP_SECONDS;
    public static final float BALL_ABSORB_END =
            VFX_END * BALL_ABSORB_END_FRACTION;
    static final float PROXY_DEPTH_FADE_START =
            SIPHON_END + (BALL_ABSORB_END - SIPHON_END) * 0.5f;
    static final float BODY_COLLAPSE_RETAINED_SPAN = 0.08f;
    static final float MATERIAL_COVERAGE_DEPTH_CUTOFF = 0.08f;
    // Mirrors the splat accumulation weight floor, not the final material
    // coverage cutoff: overlapping low-weight splats can still fuse visibly.
    static final float SPLAT_BODY_ACTIVITY_EPSILON = 0.0005f;
    static final float SIPHON_LEADING_EASE_START = 0.003f;
    static final float SIPHON_LEADING_EASE_END = 0.18f;
    static final float SIPHON_TRAILING_EASE_START = 0.015f;
    static final float SIPHON_TRAILING_EASE_END = 0.96f;
    static final float BODY_RETIRE_EASE_END = 0.20f;
    static final float BODY_RELEASE_RETIRE_FRONT_START = 0.76f;
    static final float BODY_RELEASE_RETIRE_FRONT_END = 0.86f;
    static final float SIPHON_INLET_CONTRACTION_FRONT_START = 0.78f;
    static final float SIPHON_INLET_CONTRACTION_FRONT_END = 1.04f;
    static final float SIPHON_CONTRACTED_INLET_RADIUS_SCALE = 0.38f;
    private static final int MAX_SNAPSHOT_QUADS = 12000;
    private static final float MIN_CHAINABLE_CUBE_AXIS = 3.25f;
    private static final double MIN_ROUTING_GEOMETRY_MEASURE = 1.0e-4;
    private static final int FULLBRIGHT = 0x00F000F0;
    private static final boolean ENABLE_VISIBLE_FIELD_MASK = false;
    /**
     * Shader maximum: 0.04R uniform expansion plus 0.22R outward spike.
     * Keep a small conservative pad so the projected sweep scissor never
     * clips a peak at full turbulence.
     */
    private static final float SURFACE_SPLAT_MAX_OUTWARD_TURBULENCE = 0.27f;
    /**
     * One delayed render/simulation-step budget reserved by activation
     * scheduling. This is not a cap on the visual choreography clock.
     */
    static final float MAX_TICK_DELTA_SECONDS = 0.10f;
    static final float SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS =
            VFX_END * 0.045f;
    static final float SURFACE_SPLAT_MIN_LATE_ACTIVATION_RAMP_SECONDS =
            Math.min(0.020f, VFX_END * 0.010f);
    /**
     * Threshold between the unchanged schedule and late-ready remapping. It is
     * no longer a rejection deadline: a valid result after this point receives
     * a compressed remaining presentation schedule.
     */
    static final float SURFACE_SPLAT_SELECTION_DEADLINE =
            SIPHON_START
                    - SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS
                    - MAX_TICK_DELTA_SECONDS;
    private static final AtomicInteger PREPARATION_THREAD_SEQUENCE =
            new AtomicInteger();
    /**
     * Isolates capture preparation from Minecraft's shared background pool.
     * Two daemon workers preserve simultaneous-capture progress without
     * allowing voxel/SDF work to fan out across every processor.
     */
    private static final ExecutorService PREPARATION_EXECUTOR =
            Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(
                        task,
                        "ShadowedHearts-DarkBall-Prep-"
                                + PREPARATION_THREAD_SEQUENCE
                                .incrementAndGet());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });
    private static final String STAGE_CHAT_MESSAGES_PROPERTY =
            "shadowedhearts.darkBallStageMessages";
    private static final boolean STAGE_CHAT_MESSAGES_ENABLED =
            Boolean.parseBoolean(System.getProperty(
                    STAGE_CHAT_MESSAGES_PROPERTY, "true"));
    private static final List<StageChatMarker> STAGE_CHAT_MARKERS = List.of(
            new StageChatMarker(
                    CONVERSION_START,
                    "Stage 1/6: formation / silhouette conversion"),
            new StageChatMarker(
                    TURBULENCE_START,
                    "Stage 2/6: turbulence ramp"),
            new StageChatMarker(
                    TURBULENCE_FULL,
                    "Stage 3/6: maximum turbulence hold"),
            new StageChatMarker(
                    SIPHON_START,
                    "Stage 4/6: bone-routed collapse / siphon extension"),
            new StageChatMarker(
                    SIPHON_COLLAPSE_START,
                    "Stage 5/6: overlapping terminal drain / siphon contraction"),
            new StageChatMarker(
                    BALL_ABSORB_END,
                    "Stage 6/6: final absorption fade"),
            new StageChatMarker(
                    VFX_END,
                    "Complete: visible Dark Ball VFX ended"));
    private static final MultiBufferSource.BufferSource MASK_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private static final Map<Integer, DarkBallCaptureVfx> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_CAPTURE_GENERATION =
            new AtomicLong();
    private static long lastTickNanos = 0L;
    private static float lastChoreographyDeltaSeconds;
    private static long lastMaskFrameToken = Long.MIN_VALUE;
    private static boolean maskCapturedThisFrame;
    private static boolean renderingModelMask;
    private static float compositeMaskClipStrength;
    private static float compositeMaskVisibleStrength;
    private static float compositeProxyDepthStrength;
    private static float compositeProxyDepthAvailable;
    private static boolean compositeExactMaskReady;
    private static float compositeDeformationBlend;
    private static float compositeDepthHighlightBlend;
    private static float compositeSignedBodyAuthority;
    private static float compositePreCollapseInteriorGuard;
    private static float compositeTurbulenceBlend;
    private static float compositeDeformationTime;
    private static float compositeSiphonRootU = 0.5f;
    private static float compositeSiphonRootV = 0.5f;
    private static float compositeBallU = 0.5f;
    private static float compositeBallV = 0.5f;
    private static float compositeSiphonProgress;
    private static float compositeBodyCollapseEnvelopeScale = 1.0f;
    private static final Vector2f compositeProjectedUp = new Vector2f(0f, 1f);
    private static final Vector4f compositeBodyUvBounds = new Vector4f(0f, 0f, 1f, 1f);
    private static int compositeMassTextureId;
    private static int compositeSiphonTextureId;
    private static float compositeBodyVolumeStrength;
    private static float compositeAnalyticSiphonStrength;
    private static final Matrix4f compositeInvProjection = new Matrix4f();
    private static final Matrix4f compositeProjection = new Matrix4f();
    private static final Matrix4f compositeCameraToWorld = new Matrix4f();
    private static final Vector3f compositeCameraPos = new Vector3f();
    private static final Vector3f compositeVolumeRoot = new Vector3f();
    private static final Vector3f compositeVolumeAxis = new Vector3f(1f, 0f, 0f);
    private static final Vector3f compositeVolumeSide = new Vector3f(0f, 1f, 0f);
    private static final Vector3f compositeVolumeUp = new Vector3f(0f, 0f, 1f);
    private static final Vector3f compositeVolumeSize = new Vector3f(1f, 1f, 1f);
    private static final Vector3f compositeSiphonP0 = new Vector3f();
    private static final Vector3f compositeSiphonP1 = new Vector3f();
    private static final Vector3f compositeSiphonP2 = new Vector3f();
    private static final Vector3f compositeSiphonP3 = new Vector3f();
    private static boolean reconstructionAuxiliaryTargetsCleared;
    private static ModelSnapshotCapture activeSnapshotCapture;
    private static TexturedSnapshotCapture activeTexturedSnapshotCapture;

    public static void start(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        installCapture(ball, pokemon);
    }

    public static void startAtHit(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
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
        int ballId = ball.getId();
        int pokemonId = pokemon.getId();
        DarkBallCaptureVfx current = ACTIVE.get(ballId);
        if (current != null && current.pokemonId == pokemonId) {
            return current;
        }

        for (Map.Entry<Integer, DarkBallCaptureVfx> entry
                : List.copyOf(ACTIVE.entrySet())) {
            DarkBallCaptureVfx active = entry.getValue();
            if ((entry.getKey() == ballId || active.pokemonId == pokemonId)
                    && ACTIVE.remove(entry.getKey(), active)) {
                active.destroy();
            }
        }

        DarkBallCaptureVfx installed = new DarkBallCaptureVfx(ball, pokemon);
        ACTIVE.put(ballId, installed);
        return installed;
    }

    public static void stop(int ballId) {
        DarkBallCaptureVfx removed = ACTIVE.remove(ballId);
        if (removed != null) {
            removed.destroy();
        }
    }

    public static void clearAll() {
        for (DarkBallCaptureVfx vfx : ACTIVE.values()) {
            vfx.destroy();
        }
        ACTIVE.clear();
        lastTickNanos = 0L;
        lastChoreographyDeltaSeconds = 0.0f;
        activeSnapshotCapture = null;
        activeTexturedSnapshotCapture = null;
        maskCapturedThisFrame = false;
        renderingModelMask = false;
    }

    public static DarkBallCaptureVfx get(int ballId) {
        return ACTIVE.get(ballId);
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

    static FboPreviewCapture fboPreviewCapture(DarkBallCaptureVfx vfx) {
        if (vfx == null || ACTIVE.get(vfx.ballId) != vfx) {
            return null;
        }
        String renderPath = switch (vfx.surfaceMeshPath) {
            case SPLAT -> "splat";
            case PENDING -> "pending";
            case FAILED -> vfx.forceExactMaskOnly
                    ? "forced-exact-mask"
                    : "failed";
        };
        float activationBlend =
                vfx.currentDirectBodyActivationBlend();
        String presentationAuthority = switch (vfx.surfaceMeshPath) {
            case PENDING -> "exact-mask";
            case SPLAT -> activationBlend < 0.999f
                    ? "splat-activation"
                    : "splat";
            case FAILED -> vfx.forceExactMaskOnly
                    ? "exact-mask-forced"
                    : "exact-mask";
        };
        DarkBallSplatBoneRoutePlan.Result boneRoute =
                vfx.surfaceSplatSamplePlan == null
                        ? null
                        : vfx.surfaceSplatSamplePlan
                        .boneRoutePlan();
        int routeSampleCount = boneRoute == null
                ? 0
                : boneRoute.assignedSamples()
                + boneRoute.fallbackSamples();
        float routedFraction = routeSampleCount <= 0
                ? 0.0f
                : boneRoute.assignedSamples()
                / (float) routeSampleCount;
        DarkBallDensityFBO.PreviewFrame previewFrame =
                DarkBallDensityFBO.previewFrame();
        boolean previewFrameCurrent =
                DarkBallDensityFBO
                        .isPreviewFrameCurrent(previewFrame);
        Float projectedPixelAreaFraction = previewFrameCurrent
                ? projectedUvAreaFraction(
                previewFrame.effectMinU(),
                previewFrame.effectMinV(),
                previewFrame.effectMaxU(),
                previewFrame.effectMaxV())
                : null;
        Boolean reducedCompositeUsed = previewFrameCurrent
                ? previewFrame.reducedCompositeUsed()
                : null;
        Integer splatSampleCount =
                vfx.surfaceSplatRenderer != null
                        && vfx.surfaceSplatRenderer.uploadedSampleCount() > 0
                        ? vfx.surfaceSplatRenderer.uploadedSampleCount()
                        : null;
        float presentationAge = vfx.currentStagePresentationAge();
        return new FboPreviewCapture(
                vfx.captureGeneration,
                vfx.ballId,
                vfx.pokemonId,
                vfx.age,
                lastChoreographyDeltaSeconds,
                vfx.currentDirectBodyTurbulenceBlend(),
                vfx.currentDirectBodyTurbulenceComplexityBlend(),
                vfx.currentDirectBodySpikeFlowTime(),
                siphonProgressAt(presentationAge),
                vfx.surfaceMeshPath == SurfaceMeshPath.SPLAT
                        ? splatBodyReleaseFrontAt(presentationAge)
                        : bodyReleaseFrontAt(presentationAge),
                bodyPresentationGateAt(presentationAge),
                finalCollapseAt(presentationAge),
                siphonTrailingGateAt(presentationAge, 0.0f),
                siphonTrailingGateAt(presentationAge, 1.0f),
                Float.isFinite(vfx.voxelReadyAge)
                        ? vfx.voxelReadyAge
                        : null,
                vfx.voxelBuildMillis >= 0L
                        ? vfx.voxelBuildMillis
                        : null,
                Float.isFinite(vfx.surfaceReadyAge)
                        ? vfx.surfaceReadyAge
                        : null,
                vfx.surfaceBuildMillis >= 0L
                        ? vfx.surfaceBuildMillis
                        : null,
                Float.isFinite(vfx.directBodyActivationAge)
                        ? vfx.directBodyActivationAge
                        : null,
                vfx.surfaceSplatRequestedBudget > 0
                        ? vfx.surfaceSplatRequestedBudget
                        : null,
                splatSampleCount,
                vfx.surfaceMeshPath == SurfaceMeshPath.SPLAT
                        ? vfx.surfaceSplatBodySubmittedThisFrame
                        : null,
                vfx.surfaceMeshPath == SurfaceMeshPath.SPLAT
                        ? splatLastSectionBodyOwnershipAt(presentationAge)
                        : null,
                vfx.surfaceMeshPath == SurfaceMeshPath.SPLAT
                        ? vfx.surfaceSplatUploadOccurredThisFrame
                        : null,
                vfx.surfaceSplatRenderer != null
                        ? vfx.surfaceSplatRenderer.uploadGeneration()
                        : null,
                vfx.surfaceSplatRenderer != null
                        && vfx.surfaceSplatRenderer
                        .lastPreparationUploadCpuMicros() >= 0L
                        ? vfx.surfaceSplatRenderer
                        .lastPreparationUploadCpuMicros()
                        : null,
                vfx.surfaceSplatDrawCpuMicros >= 0L
                        ? vfx.surfaceSplatDrawCpuMicros
                        : null,
                vfx.surfaceSplatResolveCpuMicros >= 0L
                        ? vfx.surfaceSplatResolveCpuMicros
                        : null,
                vfx.siphonBuildCpuMicros >= 0L
                        ? vfx.siphonBuildCpuMicros
                        : null,
                vfx.siphonDrawCpuMicros >= 0L
                        ? vfx.siphonDrawCpuMicros
                        : null,
                vfx.depthRestoreCpuMicros >= 0L
                        ? vfx.depthRestoreCpuMicros
                        : null,
                nullableGpuMicros(
                        vfx.gpuTimer,
                        DarkBallGpuTimer.Stage.SPLAT_DRAW),
                nullableGpuMicros(
                        vfx.gpuTimer,
                        DarkBallGpuTimer.Stage.SPLAT_RESOLVE),
                nullableGpuMicros(
                        vfx.gpuTimer,
                        DarkBallGpuTimer.Stage.SIPHON_DRAW),
                nullableGpuMicros(
                        vfx.gpuTimer,
                        DarkBallGpuTimer.Stage.DEPTH_RESTORE),
                vfx.analyticalQuality == null
                        ? null
                        : vfx.analyticalQuality.name().toLowerCase(
                        java.util.Locale.ROOT),
                previewFrameCurrent ? previewFrame.width() : null,
                previewFrameCurrent ? previewFrame.height() : null,
                renderPath,
                presentationAuthority,
                activationBlend,
                "bone-tree",
                boneRoute != null && boneRoute.guided()
                        ? "parent-chain"
                        : "retire-on-release",
                routedFraction,
                boneRoute == null
                        ? 0
                        : boneRoute.paletteCount(),
                DarkBallSplatBoneRoutePlan.MAX_PARENT_HOPS,
                Mth.clamp(
                        splatBodyReleaseFrontAt(presentationAge),
                        0.0f,
                        1.0f),
                0.0f,
                1.0f,
                1.0f,
                boneRoute == null
                        ? "route plan unavailable"
                        : boneRoute.summary(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                projectedPixelAreaFraction,
                reducedCompositeUsed);
    }

    private static Long nullableGpuMicros(
            DarkBallGpuTimer timer,
            DarkBallGpuTimer.Stage stage) {
        long micros = timer.latestMicros(stage);
        return micros >= 0L ? micros : null;
    }

    private static Float projectedUvAreaFraction(
            float minU,
            float minV,
            float maxU,
            float maxV) {
        if (!Float.isFinite(minU)
                || !Float.isFinite(minV)
                || !Float.isFinite(maxU)
                || !Float.isFinite(maxV)
                || maxU <= minU
                || maxV <= minV) {
            return null;
        }
        return Mth.clamp(maxU - minU, 0.0f, 1.0f)
                * Mth.clamp(maxV - minV, 0.0f, 1.0f);
    }

    record FboPreviewCapture(
            long captureKey,
            int ballId,
            int pokemonId,
            float age,
            float choreographyDeltaSeconds,
            float turbulenceAmplitudeBlend,
            float turbulenceComplexityBlend,
            float spikeFlowTime,
            float siphonProgress,
            float bodyReleaseFront,
            float bodyPresentationGate,
            float finalCollapse,
            float siphonRootGate,
            float siphonTipGate,
            Float voxelReadyAge,
            Long voxelBuildMillis,
            Float surfaceReadyAge,
            Long surfaceBuildMillis,
            Float directRendererActivationAge,
            Integer splatRequestedBudget,
            Integer splatSampleCount,
            Boolean splatBodySubmittedCurrentFrame,
            Float splatLastSectionBodyOwnership,
            Boolean splatUploadOccurredCurrentFrame,
            Long splatUploadGeneration,
            Long splatPreparationUploadCpuMicros,
            Long splatDrawCpuMicros,
            Long splatResolveCpuMicros,
            Long siphonBuildCpuMicros,
            Long siphonDrawCpuMicros,
            Long depthRestoreCpuMicros,
            Long splatDrawGpuMicros,
            Long splatResolveGpuMicros,
            Long siphonDrawGpuMicros,
            Long depthRestoreGpuMicros,
            String quality,
            Integer rawTargetWidth,
            Integer rawTargetHeight,
            String renderPath,
            String presentationAuthority,
            float presentationActivationBlend,
            String sweepMode,
            String sweepPath,
            float sweepStrength,
            int sweepNodeCount,
            int sweepSliceCount,
            float sweepPinchCenter,
            float sweepPinchStrength,
            float sweepMinimumSectionScale,
            float sweepMaximumSectionScale,
            String sweepAuthority,
            Boolean bridgePresentationActive,
            Float bridgeMaterialActivation,
            Float bridgeRetractionProgress,
            Float bridgeRetractionFront,
            Integer bridgeFirstActiveRing,
            Integer bridgeLastActiveRing,
            Integer bridgeActiveRingCount,
            Float bridgePathLength,
            Float bridgePathStartRadius,
            Float bridgePathShoulderRadius,
            Float bridgeInletRadius,
            Float bridgeProjectedPixelAreaFraction,
            Float projectedPixelAreaFraction,
            Boolean reducedCompositeUsed) {
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
        vfx.capturedBoneNodes = List.of();
        vfx.voxelVolume = null;
        vfx.voxelReadyAge = Float.NaN;
        vfx.voxelBuildMillis = -1L;
        vfx.surfaceReadyAge = Float.NaN;
        vfx.surfaceBuildMillis = -1L;
        vfx.cancelPendingVoxelBuild();
        vfx.destroyCapturedVolumeResources();
        vfx.siphonDrainRootValid = false;
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
        long now = System.nanoTime();
        float choreographyDt;
        if (lastTickNanos == 0L || ACTIVE.isEmpty()) {
            choreographyDt = 0f;
        } else {
            choreographyDt =
                    choreographyDeltaSeconds(lastTickNanos, now);
        }
        lastTickNanos = now;
        lastChoreographyDeltaSeconds = choreographyDt;

        ACTIVE.entrySet().removeIf(entry -> {
            entry.getValue().tick(choreographyDt);
            boolean expired = entry.getValue().age
                    > CAPTURE_CLEANUP_AGE_SECONDS;
            if (expired) {
                entry.getValue().destroy();
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
     * slow render frame must not stretch the ten-second visual sequence.
     */
    static float choreographyDeltaSeconds(long previousNanos,
                                          long currentNanos) {
        if (previousNanos == 0L) {
            return 0.0f;
        }
        long elapsedNanos = currentNanos - previousNanos;
        if (elapsedNanos <= 0L) {
            return 0.0f;
        }
        return elapsedNanos / 1_000_000_000.0f;
    }

    /**
     * Returns an immutable, deterministic back-to-front render queue.
     *
     * <p>Each entry is composited in its own complete FBO transaction. That
     * keeps exact masks, proxy depth, surfel accumulation, and silhouette
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
        compositeMaskClipStrength = 0.0f;
        compositeMaskVisibleStrength = 0.0f;
        compositeProxyDepthStrength = 0.0f;
        compositeProxyDepthAvailable = 0.0f;
        compositeExactMaskReady = false;
        compositeDeformationBlend = 0.0f;
        compositeDepthHighlightBlend = 0.0f;
        compositeSignedBodyAuthority = 0.0f;
        compositePreCollapseInteriorGuard = 0.0f;
        compositeTurbulenceBlend = 0.0f;
        compositeDeformationTime = 0.0f;
        compositeSiphonRootU = 0.5f;
        compositeSiphonRootV = 0.5f;
        compositeBallU = 0.5f;
        compositeBallV = 0.5f;
        compositeSiphonProgress = 0.0f;
        compositeBodyCollapseEnvelopeScale = 1.0f;
        compositeProjectedUp.set(0.0f, 1.0f);
        compositeBodyUvBounds.set(0.0f, 0.0f, 1.0f, 1.0f);
        compositeMassTextureId = 0;
        compositeSiphonTextureId = 0;
        compositeBodyVolumeStrength = 0.0f;
        compositeAnalyticSiphonStrength = 0.0f;
        compositeVolumeRoot.zero();
        compositeVolumeAxis.set(1.0f, 0.0f, 0.0f);
        compositeVolumeSide.set(0.0f, 1.0f, 0.0f);
        compositeVolumeUp.set(0.0f, 0.0f, 1.0f);
        compositeVolumeSize.set(1.0f, 1.0f, 1.0f);
        compositeSiphonP0.zero();
        compositeSiphonP1.zero();
        compositeSiphonP2.zero();
        compositeSiphonP3.zero();
        compositeCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        compositeProjection.set(RenderSystem.getProjectionMatrix());
        compositeInvProjection.set(compositeProjection).invert();
        compositeCameraToWorld.identity().rotation(camera.rotation());
        reconstructionAuxiliaryTargetsCleared = false;
    }

    public static float getCompositeMaskClipStrength() {
        return compositeMaskClipStrength;
    }

    public static float getCompositeMaskVisibleStrength() {
        return ENABLE_VISIBLE_FIELD_MASK ? compositeMaskVisibleStrength : 0.0f;
    }

    public static float getCompositeProxyDepthStrength() {
        return compositeProxyDepthStrength;
    }

    public static float getCompositeProxyDepthAvailable() {
        return compositeProxyDepthAvailable;
    }

    public static boolean isCompositeExactMaskReady() {
        return compositeExactMaskReady;
    }

    public static float getCompositeDeformationBlend() {
        return compositeDeformationBlend;
    }

    public static float getCompositeDepthHighlightBlend() {
        return compositeDepthHighlightBlend;
    }

    public static float getCompositeSignedBodyAuthority() {
        return compositeSignedBodyAuthority;
    }

    public static float getCompositePreCollapseInteriorGuard() {
        return compositePreCollapseInteriorGuard;
    }

    public static float getCompositeTurbulenceBlend() {
        return compositeTurbulenceBlend;
    }

    public static float getCompositeDeformationTime() {
        return compositeDeformationTime;
    }

    public static float getCompositeSiphonRootU() {
        return compositeSiphonRootU;
    }

    public static float getCompositeSiphonRootV() {
        return compositeSiphonRootV;
    }

    public static float getCompositeBallU() {
        return compositeBallU;
    }

    public static float getCompositeBallV() {
        return compositeBallV;
    }

    public static float getCompositeSiphonProgress() {
        return compositeSiphonProgress;
    }

    public static float getCompositeBodyCollapseEnvelopeScale() {
        return compositeBodyCollapseEnvelopeScale;
    }

    public static Vector2f getCompositeProjectedUp() {
        return compositeProjectedUp;
    }

    public static Vector4f getCompositeBodyUvBounds() {
        return compositeBodyUvBounds;
    }

    public static int getCompositeMassTextureId() {
        return compositeMassTextureId;
    }

    public static int getCompositeSiphonTextureId() {
        return compositeSiphonTextureId;
    }

    static int getPreviewBodyTransportTextureId() {
        return 0;
    }

    static int getPreviewSiphonTransportTextureId() {
        return 0;
    }

    public static float getCompositeBodyVolumeStrength() {
        return compositeBodyVolumeStrength;
    }

    public static float getCompositeAnalyticSiphonStrength() {
        return compositeAnalyticSiphonStrength;
    }

    public static Matrix4f getCompositeInvProjection() {
        return compositeInvProjection;
    }

    public static Matrix4f getCompositeProjection() {
        return compositeProjection;
    }

    public static Matrix4f getCompositeCameraToWorld() {
        return compositeCameraToWorld;
    }

    public static Vector3f getCompositeCameraPos() {
        return compositeCameraPos;
    }

    public static Vector3f getCompositeVolumeRoot() {
        return compositeVolumeRoot;
    }

    public static Vector3f getCompositeVolumeAxis() {
        return compositeVolumeAxis;
    }

    public static Vector3f getCompositeVolumeSide() {
        return compositeVolumeSide;
    }

    public static Vector3f getCompositeVolumeUp() {
        return compositeVolumeUp;
    }

    public static Vector3f getCompositeVolumeSize() {
        return compositeVolumeSize;
    }

    public static Vector3f getCompositeSiphonP0() {
        return compositeSiphonP0;
    }

    public static Vector3f getCompositeSiphonP1() {
        return compositeSiphonP1;
    }

    public static Vector3f getCompositeSiphonP2() {
        return compositeSiphonP2;
    }

    public static Vector3f getCompositeSiphonP3() {
        return compositeSiphonP3;
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

        // The textured capture is the normal successful path, but it cannot
        // retain ModelPart ownership after its triangles are voxelized into an
        // SDF. Snapshot the fully posed Cobblemon bone hierarchy here, before
        // that path's early return, and keep only immutable numeric data for
        // the later asynchronous mesh-order build.
        try {
            vfx.capturedBoneNodes = capturePosedBoneHierarchy(
                    rootPart, renderStack, poseToWorldOffset);
        } catch (Throwable failure) {
            vfx.capturedBoneNodes = List.of();
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball posed bone capture failed "
                            + "for pokemon {}; using radial-only mesh "
                            + "compression order",
                    entity.getId(),
                    failure);
        }
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

    private static List<CapturedBoneNode> capturePosedBoneHierarchy(
            Bone rootPart,
            PoseStack renderStack,
            Vec3 poseToWorldOffset) {
        PoseStack posedStack = new PoseStack();
        posedStack.last().pose().set(renderStack.last().pose());
        posedStack.last().normal().set(renderStack.last().normal());
        List<CapturedBoneNode> nodes = new ArrayList<>();
        Set<Bone> visited = Collections.newSetFromMap(
                new IdentityHashMap<>());
        capturePosedBoneNode(
                rootPart,
                posedStack,
                poseToWorldOffset,
                -1,
                "root",
                0,
                visited,
                nodes);
        return List.copyOf(nodes);
    }

    private static void capturePosedBoneNode(
            Bone bone,
            PoseStack stack,
            Vec3 poseToWorldOffset,
            int parentIndex,
            String path,
            int depth,
            Set<Bone> visited,
            List<CapturedBoneNode> nodes) {
        if (bone == null
                || depth > 24
                || !visited.add(bone)
                || bone instanceof ModelPart part && !part.visible) {
            return;
        }

        stack.pushPose();
        try {
            bone.transform(stack);
            Matrix4f pose = stack.last().pose();
            boolean ownsGeometry =
                    bone instanceof ModelPart part
                            && ownsRoutingGeometry(part);
            boolean chainable =
                    bone instanceof ModelPart part
                            && isChainableModelPart(part);
            Vec3 anchorWorld = posedBoneAnchorWorld(
                    bone, pose, poseToWorldOffset, ownsGeometry);
            int nodeIndex = nodes.size();
            nodes.add(new CapturedBoneNode(
                    anchorWorld,
                    parentIndex,
                    path,
                    ownsGeometry,
                    chainable));

            Map<String, Bone> children = bone.getChildren();
            if (children == null || children.isEmpty()) {
                return;
            }
            List<Map.Entry<String, Bone>> orderedChildren =
                    new ArrayList<>(children.entrySet());
            orderedChildren.sort(Map.Entry.comparingByKey(
                    Comparator.nullsFirst(String::compareTo)));
            int unnamedIndex = 0;
            for (Map.Entry<String, Bone> child : orderedChildren) {
                String childName = child.getKey();
                if (childName == null || childName.isBlank()) {
                    childName = "child" + unnamedIndex;
                }
                unnamedIndex++;
                capturePosedBoneNode(
                        child.getValue(),
                        stack,
                        poseToWorldOffset,
                        nodeIndex,
                        path + "/" + childName,
                        depth + 1,
                        visited,
                        nodes);
            }
        } finally {
            stack.popPose();
        }
    }

    private static Vec3 posedBoneAnchorWorld(
            Bone bone,
            Matrix4f pose,
            Vec3 poseToWorldOffset,
            boolean ownsGeometry) {
        if (ownsGeometry && bone instanceof ModelPart part) {
            double weightedX = 0.0;
            double weightedY = 0.0;
            double weightedZ = 0.0;
            double totalWeight = 0.0;
            for (ModelPart.Cube cube : part.cubes) {
                double sizeX = Math.abs(cube.maxX - cube.minX);
                double sizeY = Math.abs(cube.maxY - cube.minY);
                double sizeZ = Math.abs(cube.maxZ - cube.minZ);
                double volume = sizeX * sizeY * sizeZ;
                double surfaceArea = 2.0 * (
                        sizeX * sizeY
                                + sizeX * sizeZ
                                + sizeY * sizeZ);
                double weight = Math.max(
                        volume,
                        surfaceArea * 0.18);
                if (weight <= MIN_ROUTING_GEOMETRY_MEASURE) {
                    continue;
                }
                Vector3f center = pose.transformPosition(
                        (cube.minX + cube.maxX) / 32.0f,
                        (cube.minY + cube.maxY) / 32.0f,
                        (cube.minZ + cube.maxZ) / 32.0f,
                        new Vector3f());
                weightedX += center.x * weight;
                weightedY += center.y * weight;
                weightedZ += center.z * weight;
                totalWeight += weight;
            }
            if (totalWeight > 0.0) {
                return new Vec3(
                        weightedX / totalWeight + poseToWorldOffset.x,
                        weightedY / totalWeight + poseToWorldOffset.y,
                        weightedZ / totalWeight + poseToWorldOffset.z);
            }
        }

        Vector3f origin = pose.transformPosition(
                0.0f, 0.0f, 0.0f, new Vector3f());
        return new Vec3(
                origin.x + poseToWorldOffset.x,
                origin.y + poseToWorldOffset.y,
                origin.z + poseToWorldOffset.z);
    }

    public static void captureRenderedModelPart(ModelPart part, PoseStack stack) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        if (capture == null || part == null || stack == null) {
            return;
        }

        Matrix4f pose = stack.last().pose();
        boolean ownsGeometry = ownsRoutingGeometry(part);
        boolean chainable = isChainableModelPart(part);
        int parentIndex = capture.renderedPartStack.isEmpty()
                ? -1
                : capture.renderedPartStack
                .get(capture.renderedPartStack.size() - 1)
                .nodeIndex();
        Vec3 geometryCenter = renderedPartGeometryCenterWorld(
                part,
                pose,
                capture.poseToWorldOffset,
                ownsGeometry);
        int nodeIndex = capture.renderedBoneNodes.size();
        capture.renderedBoneNodes.add(new CapturedBoneNode(
                geometryCenter,
                parentIndex,
                "rendered/" + nodeIndex,
                ownsGeometry,
                chainable));
        capture.renderedPartStack.add(
                new RenderedCapturedPart(part, nodeIndex));

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

    /**
     * Mirrors the rendered {@link ModelPart} recursion rather than the logical
     * Cobblemon bone hierarchy. This sees the final transforms that actually
     * produced the frozen snapshot and gives every captured node a stable
     * rendered parent for later surfel routing.
     */
    public static void endRenderedModelPart(ModelPart part) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        if (capture == null
                || part == null
                || capture.renderedPartStack.isEmpty()) {
            return;
        }

        int last = capture.renderedPartStack.size() - 1;
        if (capture.renderedPartStack.get(last).part() == part) {
            capture.renderedPartStack.remove(last);
            return;
        }

        // Be defensive around renderers that return through an unexpected
        // nested path. Discard the unmatched suffix so one malformed part
        // cannot assign all later surfels to the wrong anatomical branch.
        for (int index = last - 1; index >= 0; index--) {
            if (capture.renderedPartStack.get(index).part() == part) {
                capture.renderedPartStack.subList(
                        index,
                        capture.renderedPartStack.size()).clear();
                return;
            }
        }
    }

    private static Vec3 renderedPartGeometryCenterWorld(
            ModelPart part,
            Matrix4f pose,
            Vec3 poseToWorldOffset,
            boolean ownsGeometry) {
        if (ownsGeometry) {
            double weightedX = 0.0;
            double weightedY = 0.0;
            double weightedZ = 0.0;
            double totalWeight = 0.0;
            for (ModelPart.Cube cube : part.cubes) {
                double sizeX = Math.abs(cube.maxX - cube.minX);
                double sizeY = Math.abs(cube.maxY - cube.minY);
                double sizeZ = Math.abs(cube.maxZ - cube.minZ);
                double volume = sizeX * sizeY * sizeZ;
                double surfaceArea = 2.0 * (
                        sizeX * sizeY
                                + sizeX * sizeZ
                                + sizeY * sizeZ);
                double weight = Math.max(
                        volume,
                        surfaceArea * 0.18);
                Vector3f center = pose.transformPosition(
                        (cube.minX + cube.maxX) / 32.0f,
                        (cube.minY + cube.maxY) / 32.0f,
                        (cube.minZ + cube.maxZ) / 32.0f,
                        new Vector3f());
                weightedX += center.x * weight;
                weightedY += center.y * weight;
                weightedZ += center.z * weight;
                totalWeight += weight;
            }
            if (totalWeight > 0.0) {
                return new Vec3(
                        weightedX / totalWeight + poseToWorldOffset.x,
                        weightedY / totalWeight + poseToWorldOffset.y,
                        weightedZ / totalWeight + poseToWorldOffset.z);
            }
        }

        Vector3f origin = pose.transformPosition(
                0.0f, 0.0f, 0.0f, new Vector3f());
        return new Vec3(
                origin.x + poseToWorldOffset.x,
                origin.y + poseToWorldOffset.y,
                origin.z + poseToWorldOffset.z);
    }

    /**
     * Uses the same conservative body-chain cutoff as the Shadow Aura. Thin
     * decorative planes may own captured surface, but do not create long
     * anatomical transport branches.
     */
    private static boolean isChainableModelPart(ModelPart part) {
        if (part == null || part.skipDraw) {
            return false;
        }
        for (ModelPart.Cube cube : part.cubes) {
            float sizeX = Math.abs(cube.maxX - cube.minX);
            float sizeY = Math.abs(cube.maxY - cube.minY);
            float sizeZ = Math.abs(cube.maxZ - cube.minZ);
            if (sizeX >= MIN_CHAINABLE_CUBE_AXIS
                    && sizeY >= MIN_CHAINABLE_CUBE_AXIS
                    && sizeZ >= MIN_CHAINABLE_CUBE_AXIS) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsRoutingGeometry(ModelPart part) {
        if (part == null || part.skipDraw) {
            return false;
        }
        for (ModelPart.Cube cube : part.cubes) {
            double sizeX = Math.abs(cube.maxX - cube.minX);
            double sizeY = Math.abs(cube.maxY - cube.minY);
            double sizeZ = Math.abs(cube.maxZ - cube.minZ);
            double volume = sizeX * sizeY * sizeZ;
            double surfaceArea = 2.0 * (
                    sizeX * sizeY
                            + sizeX * sizeZ
                            + sizeY * sizeZ);
            if (Math.max(volume, surfaceArea)
                    > MIN_ROUTING_GEOMETRY_MEASURE) {
                return true;
            }
        }
        return false;
    }

    public static void endModelSnapshotCapture(PokemonEntity entity) {
        ModelSnapshotCapture capture = activeSnapshotCapture;
        activeSnapshotCapture = null;
        if (capture == null || capture.entity != entity) {
            return;
        }
        if (capture.renderedBoneNodes.size() >= 2) {
            capture.vfx.capturedBoneNodes =
                    List.copyOf(capture.renderedBoneNodes);
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
    private List<CapturedBoneNode> capturedBoneNodes = List.of();

    boolean isMediumReducedCompositeLatched() {
        return mediumReducedCompositeLatched;
    }

    void setMediumReducedCompositeLatched(boolean latched) {
        mediumReducedCompositeLatched = latched;
    }

    public float age = 0f;
    private boolean hitStageSeen = false;
    private int nextStageChatMarker;
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
    private DarkBallAnalyticalVolume analyticalVolume;
    private DarkBallVfxQuality analyticalQuality;
    private float voxelReadyAge = Float.NaN;
    private long voxelBuildMillis = -1L;
    private boolean analyticalVolumeLogged;
    private DarkBallAdvectedDensityField advectedDensityField;
    private DarkBallAnalyticalVolume advectedDensityShape;
    private DarkBallVfxQuality advectedDensityQuality;
    private boolean advectedDensityLogged;
    private CompletableFuture<VoxelBuildOutput> voxelBuildFuture;
    private CompletableFuture<SurfaceMeshBuildOutput> surfaceMeshBuildFuture;
    private AtomicBoolean surfaceMeshBuildCancellation;
    private float directBodyActivationAge = Float.NaN;
    private float surfaceReadyAge = Float.NaN;
    private long surfaceBuildMillis = -1L;
    private int surfaceSplatRequestedBudget;
    private boolean forceExactMaskOnly;
    private DarkBallSurfaceMesh surfaceMesh;
    private DarkBallSurfaceSplatRenderer.SurfaceSamplePlan
            surfaceSplatSamplePlan;
    private DarkBallSurfaceSplatRenderer surfaceSplatRenderer;
    private DarkBallSiphonSurfaceMeshRenderer siphonSurfaceMeshRenderer;
    private final DarkBallGpuTimer gpuTimer = new DarkBallGpuTimer();
    private boolean surfaceSplatBodySubmittedThisFrame;
    private boolean surfaceSplatUploadOccurredThisFrame;
    private long surfaceSplatDrawCpuMicros = -1L;
    private long surfaceSplatResolveCpuMicros = -1L;
    private long siphonBuildCpuMicros = -1L;
    private long siphonDrawCpuMicros = -1L;
    private long depthRestoreCpuMicros = -1L;
    private SurfaceMeshPath surfaceMeshPath = initialSurfaceMeshPath();
    private boolean surfaceMeshPathLogged;
    private int voxelBuildGeneration;
    private final Vector3f siphonDrainRootLocal = new Vector3f(0f, 0f, 0f);
    private boolean siphonDrainRootValid = false;

    private DarkBallCaptureVfx(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        this.ballId = ball.getId();
        this.pokemonId = pokemon.getId();
    }

    private void markHitStage() {
        if (!hitStageSeen) {
            hitStageSeen = true;
            age = 0f;
            nextStageChatMarker = 0;
            announceStageChatMessage(
                    "Capture started: snapshot and surfel preparation");
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

    private float getSnapshotMaskStrength() {
        float seed = getMaskStrength();
        float handedOff = smoothstep(CONVERSION_SOLID * 0.62f,
                SIPHON_START + 0.10f * TIMING_SCALE, age);
        return seed * Mth.lerp(handedOff, 1.0f, 0.16f);
    }

    private float getFieldMaskClipStrength() {
        if (usesSdfContainment()) {
            return 0.0f;
        }
        return age < VFX_END ? 1.0f : 0.0f;
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

    static float depthHighlightBlendAt(float captureAge,
                                       float directBodyActivation) {
        // Proxy-front depth remains available from the first exact-mask frame
        // for ordering, but it must not light the captured model's individual
        // planar faces during the clean black hit. Reveal depth styling only
        // after the texture-exact handoff and only when the fused surfel body
        // can actually own the presented surface.
        return smoothstep(
                FROZEN_MODEL_CROSSFADE_END,
                VISIBLE_EXPANSION_FULL,
                captureAge)
                * Mth.clamp(directBodyActivation, 0.0f, 1.0f);
    }

    static float turbulenceBlendAt(float captureAge) {
        // Square a smoothstep rather than advancing linearly. The silhouette
        // stays restrained through the texture-exact handoff, escalates hard
        // near the end of the ramp, then arrives with zero slope for a stable
        // full-strength hold before depletion begins.
        float eased = turbulenceComplexityBlendAt(captureAge);
        return eased * eased;
    }

    static float turbulenceComplexityBlendAt(float captureAge) {
        // Complexity reveals weaker phase-locked spikes and finer indentations
        // over the full turbulence interval. Keep this unsquared so feature
        // density can mature before the late-accelerating amplitude envelope.
        return smoothstep(TURBULENCE_START, TURBULENCE_FULL, captureAge);
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
            return SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS;
        }
        float remaining = Math.max(VFX_END - activationAge, 0.0f);
        if (remaining <= 0.0f) {
            return 0.0f;
        }
        return Math.min(
                SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS,
                Math.max(
                        SURFACE_SPLAT_MIN_LATE_ACTIVATION_RAMP_SECONDS,
                        remaining * 0.30f));
    }

    /**
     * Keeps a late but valid surfel result recoverable. The exact-mask
     * presentation owns the pending interval; after activation, the remaining
     * conceptual choreography is compressed so collapse begins only after the
     * activation ramp and still reaches the terminal sink at {@link #VFX_END}.
     */
    static float surfacePresentationAgeAt(float captureAge,
                                          float activationAge) {
        if (!Float.isFinite(activationAge)
                || activationAge <= SURFACE_SPLAT_SELECTION_DEADLINE
                || captureAge <= activationAge) {
            return captureAge;
        }
        float remaining = VFX_END - activationAge;
        if (remaining <= 1.0e-5f) {
            return VFX_END;
        }
        float progress = remapClamped(
                captureAge,
                activationAge,
                VFX_END);
        if (activationAge >= SIPHON_START) {
            // Preparation completed after collapse had conceptually begun.
            // Never rewind the presentation to turbulence: enter at the
            // corresponding collapse state and compress only what remains.
            return Mth.lerp(progress, activationAge, VFX_END);
        }
        float ramp = Math.min(
                lateActivationRampSecondsAt(activationAge),
                remaining * 0.90f);
        float denominator = Math.max(remaining - ramp, 1.0e-5f);
        float logicalStart =
                (remaining * SIPHON_START - ramp * VFX_END)
                        / denominator;
        logicalStart = Mth.clamp(
                logicalStart,
                TURBULENCE_START,
                SIPHON_START);
        return Mth.lerp(progress, logicalStart, VFX_END);
    }

    static float directBodyTurbulenceBlendAt(float captureAge,
                                             float activationAge) {
        return turbulenceBlendAt(captureAge)
                * directBodyActivationBlendAt(
                captureAge, activationAge);
    }

    static float directBodyTurbulenceComplexityBlendAt(
            float captureAge,
            float activationAge) {
        return turbulenceComplexityBlendAt(captureAge)
                * directBodyActivationBlendAt(
                captureAge, activationAge);
    }

    static float directBodySpikeFlowTimeAt(float captureAge,
                                           float activationAge) {
        if (!Float.isFinite(activationAge)) {
            return 0.0f;
        }
        return Math.max(captureAge - TURBULENCE_START, 0.0f);
    }

    static float directBodyAuthorityAt(float captureAge,
                                       float activationAge) {
        return signedBodyAuthorityAt(captureAge)
                * directBodyActivationBlendAt(
                captureAge, activationAge);
    }

    static float signedBodyAuthorityAt(float captureAge) {
        // Color first: the frozen model must finish fading onto the opaque,
        // texture-exact black mask before signed SDF displacement can own the
        // contour. Give the visible expansion its own art-directed ramp rather
        // than compressing it into the short remainder of the turbulence ramp.
        // The exact mask still releases completely so inward cuts are not
        // permanently filled back in.
        return smoothstep(FROZEN_MODEL_CROSSFADE_END,
                VISIBLE_EXPANSION_FULL, captureAge);
    }

    static float finalCollapseAt(float captureAge) {
        return remapClamped(captureAge, SIPHON_COLLAPSE_START,
                BALL_ABSORB_END);
    }

    static float siphonProgressAt(float captureAge) {
        return remapClamped(captureAge, SIPHON_START, SIPHON_END);
    }

    static float bodyCollapseEnvelopeScale(float siphonProgress) {
        float releaseFront = Mth.clamp(siphonProgress * 1.16f - 0.045f,
                0.0f, 1.0f);
        return Mth.lerp(releaseFront, 1.0f,
                BODY_COLLAPSE_RETAINED_SPAN);
    }

    static float bodyCollapseEnvelopeScaleAt(float captureAge) {
        return bodyCollapseEnvelopeScale(siphonProgressAt(captureAge));
    }

    static float bodyReleaseFrontAt(float captureAge) {
        float siphon = siphonProgressAt(captureAge);
        if (siphon <= 0.001f) {
            return -1.0f;
        }
        return Mth.clamp(siphon * 1.16f - 0.045f
                + finalCollapseAt(captureAge) * 0.16f, 0.0f, 1.30f);
    }

    static float splatBodyReleaseFrontAt(float captureAge) {
        if (captureAge <= SIPHON_START) {
            return -1.0f;
        }
        float sinkProgress = smoothstep(
                SIPHON_START,
                SPLAT_SINK_COMPLETE_AGE,
                captureAge);
        return Mth.lerp(
                sinkProgress,
                -0.045f,
                1.30f);
    }

    static float bodyPresentationGateAt(float captureAge) {
        float trailingProgress = smoothstep(
                SIPHON_TRAILING_EASE_START,
                BODY_RETIRE_EASE_END,
                finalCollapseAt(captureAge));
        float trailingFront =
                Mth.lerp(trailingProgress, -0.06f, 1.08f);
        float rootCollarRetirement = smoothstep(
                trailingFront - 0.045f,
                trailingFront + 0.045f,
                0.0f);
        float releaseRetirement = 1.0f - smoothstep(
                BODY_RELEASE_RETIRE_FRONT_START,
                BODY_RELEASE_RETIRE_FRONT_END,
                bodyReleaseFrontAt(captureAge));
        return Math.min(rootCollarRetirement, releaseRetirement);
    }

    /**
     * The inlet section is the final splat section to cross the transport
     * throat. Its ownership is therefore a conservative CPU-side answer to
     * whether any locally transported body material can still survive.
     */
    static float splatLastSectionBodyOwnershipAt(float captureAge) {
        return DarkBallSplatTransportMath.sample(
                1.0f,
                splatBodyReleaseFrontAt(captureAge))
                .bodyOwnership();
    }

    static boolean splatBodyPresentationRequiredAt(float captureAge) {
        return captureAge < SIPHON_START
                || splatLastSectionBodyOwnershipAt(captureAge)
                > SPLAT_BODY_ACTIVITY_EPSILON;
    }

    static float bodyEffectFadeAt(float captureAge) {
        float formation =
                smoothstep(CONVERSION_START, CONVERSION_SOLID, captureAge);
        float finalFade =
                1.0f - smoothstep(BALL_ABSORB_END, VFX_END, captureAge);
        return formation * finalFade
                * bodyPresentationGateAt(captureAge);
    }

    static boolean bodyPresentationRequiredAt(float captureAge) {
        // Before siphoning, the texture-exact edge resolve can still source the
        // visible formation silhouette even if the indexed coverage is small.
        // During contraction the body is intentionally retired with the root
        // collar once no body fragment can survive the material cutoff.
        return captureAge < SIPHON_START
                || bodyEffectFadeAt(captureAge)
                >= MATERIAL_COVERAGE_DEPTH_CUTOFF;
    }

    static float siphonInletRadiusScaleAt(float captureAge) {
        float contraction = smoothstep(
                SIPHON_COLLAPSE_START,
                Mth.lerp(0.55f,
                        SIPHON_COLLAPSE_START,
                        BALL_ABSORB_END),
                captureAge);
        return Mth.lerp(
                contraction,
                1.0f,
                SIPHON_CONTRACTED_INLET_RADIUS_SCALE);
    }

    static float siphonTrailingGateAt(float captureAge, float curveT) {
        float trailingProgress = smoothstep(
                SIPHON_TRAILING_EASE_START,
                SIPHON_TRAILING_EASE_END,
                finalCollapseAt(captureAge));
        float trailingFront = Mth.lerp(trailingProgress, -0.06f, 1.08f);
        return smoothstep(trailingFront - 0.045f,
                trailingFront + 0.045f, curveT);
    }

    private boolean usesSdfContainment() {
        return voxelVolume != null
                && siphonDrainRootValid
                && voxelVolume.signedDistanceField() != null
                && voxelVolume.sdfGradientX() != null
                && voxelVolume.sdfGradientY() != null
                && voxelVolume.sdfGradientZ() != null;
    }

    private void tick(float choreographyDt) {
        installVoxelBuildIfReady();
        installSurfaceMeshBuildIfReady();
        age += choreographyDt;
        announceCrossedStageChatMarkers();
    }

    private void announceCrossedStageChatMarkers() {
        if (!STAGE_CHAT_MESSAGES_ENABLED) {
            nextStageChatMarker = STAGE_CHAT_MARKERS.size();
            return;
        }
        float stageAge = currentStagePresentationAge();
        while (nextStageChatMarker < STAGE_CHAT_MARKERS.size()) {
            StageChatMarker marker =
                    STAGE_CHAT_MARKERS.get(nextStageChatMarker);
            if (stageAge < marker.startAge()) {
                return;
            }
            announceStageChatMessage(marker.message());
            nextStageChatMarker++;
        }
    }

    private void announceStageChatMessage(String message) {
        if (!STAGE_CHAT_MESSAGES_ENABLED || message == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String timedMessage = String.format(
                java.util.Locale.ROOT,
                "[Dark Ball] %s (%.3f s)",
                message,
                age);
        minecraft.player.displayClientMessage(
                Component.literal(timedMessage),
                false);
    }

    private void destroy() {
        cancelPendingVoxelBuild();
        destroyCapturedVolumeResources();
        gpuTimer.close();
    }

    private void cancelPendingVoxelBuild() {
        voxelBuildGeneration++;
        CompletableFuture<VoxelBuildOutput> pending = voxelBuildFuture;
        voxelBuildFuture = null;
        if (pending != null) {
            pending.cancel(false);
        }
        cancelPendingSurfaceMeshBuild();
        surfaceMesh = null;
        surfaceSplatSamplePlan = null;
        surfaceSplatRequestedBudget = 0;
        directBodyActivationAge = Float.NaN;
        destroySurfaceMeshRenderer();
        surfaceMeshPath = initialSurfaceMeshPath();
        surfaceMeshPathLogged = false;
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
        surfaceSplatSamplePlan = null;
        directBodyActivationAge = Float.NaN;
    }

    private void destroyCapturedVolumeResources() {
        if (advectedDensityField != null) {
            advectedDensityField.destroy();
            advectedDensityField = null;
        }
        advectedDensityShape = null;
        advectedDensityQuality = null;
        advectedDensityLogged = false;
        if (analyticalVolume != null) {
            analyticalVolume.destroy();
            analyticalVolume = null;
        }
        analyticalQuality = null;
        analyticalVolumeLogged = false;
    }

    private void destroySurfaceMeshRenderer() {
        if (surfaceSplatRenderer != null) {
            surfaceSplatRenderer.close();
            surfaceSplatRenderer = null;
        }
        if (siphonSurfaceMeshRenderer != null) {
            siphonSurfaceMeshRenderer.close();
            siphonSurfaceMeshRenderer = null;
        }
    }

    private void startDirectBodyActivationIfNeeded() {
        if (surfaceMeshPath != SurfaceMeshPath.SPLAT
                || Float.isFinite(directBodyActivationAge)) {
            return;
        }
        // Record the first frame that can actually submit the fused surfel
        // field. Preserving this timestamp across a failed direct transaction
        // keeps exact-mask recovery from changing the choreography clock.
        directBodyActivationAge = age;
    }

    private float currentSurfacePresentationAge() {
        return surfacePresentationAgeAt(
                age,
                directBodyActivationAge);
    }

    private float currentStagePresentationAge() {
        if (surfaceMeshPath == SurfaceMeshPath.PENDING) {
            return Math.min(
                    age,
                    SIPHON_START
                            - SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS);
        }
        if (surfaceMeshPath == SurfaceMeshPath.SPLAT) {
            return currentSurfacePresentationAge();
        }
        return age;
    }

    private float currentDirectBodyActivationBlend() {
        if (surfaceMeshPath != SurfaceMeshPath.SPLAT) {
            return 0.0f;
        }
        return directBodyActivationBlendAt(
                age, directBodyActivationAge);
    }

    private float currentDirectBodyTurbulenceBlend() {
        return turbulenceBlendAt(currentSurfacePresentationAge())
                * currentDirectBodyActivationBlend();
    }

    private float currentDirectBodyTurbulenceComplexityBlend() {
        return turbulenceComplexityBlendAt(
                currentSurfacePresentationAge())
                * currentDirectBodyActivationBlend();
    }

    private float currentDirectBodySpikeFlowTime() {
        return Math.max(
                currentSurfacePresentationAge()
                        - TURBULENCE_START,
                0.0f);
    }

    private float currentSurfaceSplatSpikeFlowTime() {
        // Freeze the captured deformation carrier when local transport begins.
        // The advancing release front must be the only authority that changes
        // a splat's remaining inlet distance during collapse.
        return Math.max(
                Math.min(
                        currentSurfacePresentationAge(),
                        SIPHON_START)
                        - TURBULENCE_START,
                0.0f);
    }

    private void renderDensityPass(Camera camera, Vec3 camPos, float partialTick,
                                   boolean allowDirectVolume) {
        surfaceSplatBodySubmittedThisFrame = false;
        surfaceSplatUploadOccurredThisFrame = false;
        surfaceSplatDrawCpuMicros = -1L;
        surfaceSplatResolveCpuMicros = -1L;
        siphonBuildCpuMicros = -1L;
        siphonDrawCpuMicros = -1L;
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
            // The direct advected renderer returns immediately on success, so
            // all screen-space material inputs must be captured before it is
            // attempted. This is intentionally independent of the auxiliary
            // reconstruction mask's lifetime; the exact volume may outlive it.
            // The legacy fallback consumes the same values later.
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
            ensureAnalyticalVolume(voxelVolume, ballPos);
            ensureAdvectedDensityField(voxelVolume, ballPos);
            if (surfaceMeshPath == SurfaceMeshPath.PENDING) {
                // Preparation wall time does not scale with a shortened visual
                // profile. Keep the exact captured mask while pending and
                // accept the immutable surfel result whenever it completes;
                // the instance schedule will compress only the remaining
                // turbulence/collapse choreography.
                return;
            }
            if (surfaceMeshPath == SurfaceMeshPath.SPLAT) {
                renderSurfaceSplatPath(ballPos, camera, camPos);
                return;
            }
        }
        // If surfels are not ready, the captured exact mask remains
        // authoritative. The live Pokemon stays suppressed.
    }

    private boolean renderSurfaceSplatPath(Vec3 ballPos,
                                           Camera camera,
                                           Vec3 cameraPosition) {
        surfaceSplatBodySubmittedThisFrame = false;
        surfaceSplatUploadOccurredThisFrame = false;
        surfaceSplatDrawCpuMicros = -1L;
        surfaceSplatResolveCpuMicros = -1L;
        siphonBuildCpuMicros = -1L;
        siphonDrawCpuMicros = -1L;
        depthRestoreCpuMicros = -1L;
        if (surfaceMesh == null
                || analyticalVolume == null
                || advectedDensityField == null
                || voxelVolume == null) {
            failSurfaceSplatPath(
                    "exact-mask safety path: surface-splat inputs unavailable");
            return false;
        }

        Vector3f ballLocal = volumeWorldToLocal(voxelVolume, ballPos);
        if (!advectedDensityField.updateBallLocal(ballLocal)) {
            failSurfaceSplatPath(
                    "exact-mask safety path: splat ball-local update failed");
            return false;
        }

        startDirectBodyActivationIfNeeded();
        float presentationAge = currentSurfacePresentationAge();
        float siphonProgress = siphonProgressAt(presentationAge);
        boolean bodyRendered = true;
        boolean bodyPresentationActive = true;
        boolean siphonRendered = true;
        boolean depthWriteSubmitted = false;
        String splatFailure = null;
        try {
            Vector3f controlA =
                    advectedDensityField.siphonControlALocal();
            Vector3f controlB =
                    advectedDensityField.siphonControlBLocal();
            advectedDensityField.updateSiphonBoltPath(
                    controlA,
                    controlB,
                    presentationAge);

            float formation =
                    smoothstep(CONVERSION_START, CONVERSION_SOLID, age);
            float fade =
                    1.0f - smoothstep(
                            BALL_ABSORB_END,
                            VFX_END,
                            presentationAge);
            float releaseFront =
                    splatBodyReleaseFrontAt(presentationAge);
            // Local transport owns body retirement in the splat shader. Keep
            // EffectFade global only for formation/final fade, and use the
            // final inlet section merely to decide when no splat can survive.
            float bodyEffectFade = formation * fade;
            float lastSectionBodyOwnership =
                    DarkBallSplatTransportMath.sample(
                    1.0f,
                    releaseFront)
                    .bodyOwnership();
            bodyPresentationActive = presentationAge < SIPHON_START
                    || lastSectionBodyOwnership
                    > SPLAT_BODY_ACTIVITY_EPSILON;
            Vector3f inletLocal =
                    advectedDensityField.siphonRootLocal();
            if (bodyPresentationActive) {
                if (surfaceSplatRenderer == null) {
                    surfaceSplatRenderer =
                            new DarkBallSurfaceSplatRenderer();
                }
                DarkBallSurfaceSplatRenderer.Parameters parameters =
                        new DarkBallSurfaceSplatRenderer.Parameters(
                                inletLocal,
                                advectedDensityField.outletLocal(),
                                new Vector3f(controlA).sub(inletLocal),
                                releaseFront,
                                1.0f,
                                currentDirectBodyTurbulenceBlend(),
                                currentDirectBodyTurbulenceComplexityBlend(),
                                 currentSurfaceSplatSpikeFlowTime(),
                                 currentDirectBodySpikeFlowTime(),
                                 bodyEffectFade,
                                 pokemonId,
                                 ballId,
                                 presentationAge,
                                 surfaceSplatSamplePlan);
                gpuTimer.begin(DarkBallGpuTimer.Stage.SPLAT_DRAW);
                try {
                    bodyRendered = surfaceSplatRenderer.render(
                            surfaceMesh,
                            voxelVolume,
                            analyticalQuality,
                            camera,
                            parameters);
                } finally {
                    gpuTimer.end(DarkBallGpuTimer.Stage.SPLAT_DRAW);
                }
                surfaceSplatUploadOccurredThisFrame =
                        surfaceSplatRenderer
                                .uploadOccurredLastRender();
                surfaceSplatDrawCpuMicros =
                        surfaceSplatRenderer.lastDrawCpuMicros();
                surfaceSplatBodySubmittedThisFrame =
                        surfaceSplatDrawCpuMicros >= 0L;
                if (bodyRendered) {
                    if (DarkBallDensityFBO
                            .beginSurfaceSplatDiagnosticPass()) {
                        try {
                            if (surfaceSplatRenderer
                                    .renderDiagnostic(camera)) {
                                DarkBallDensityFBO
                                        .markSurfaceSplatDiagnosticRendered();
                            }
                        } finally {
                            DarkBallDensityFBO.resumeDensityPass();
                        }
                    }
                    if (DarkBallDensityFBO
                            .beginSurfaceSplatFrontDepthPass()) {
                        try {
                            if (surfaceSplatRenderer
                                    .renderFrontDepth(camera)) {
                                DarkBallDensityFBO
                                        .markSurfaceSplatFrontDepthRendered();
                            }
                        } finally {
                            DarkBallDensityFBO.resumeDensityPass();
                            restoreDensityPassDrawState();
                        }
                    }
                    float bodyVoxelSize = Math.max(Math.min(
                            voxelVolume.sdfVoxelX(),
                            voxelVolume.sdfVoxelYz()), 0.0001f);
                    Matrix4f bodyProjection =
                            new Matrix4f(
                                    RenderSystem.getProjectionMatrix());
                    DarkBallProjectedEffectBounds.UvBounds bodyBounds =
                            DarkBallProjectedEffectBounds
                                    .forSurfelAndSiphon(
                                            advectedDensityField,
                                            analyticalVolume,
                                            voxelVolume,
                                            bodyProjection,
                                            camera,
                                            cameraPosition,
                                            currentDirectBodyTurbulenceBlend(),
                                            bodyVoxelSize,
                                            DarkBallCaptureMath
                                                    .siphonEndRadius(),
                                            true,
                                            false,
                                            siphonInletRadiusScaleAt(
                                                    presentationAge),
                                            surfaceSplatRenderer
                                                    .uploadedProjectedReachRadius());
                    long splatResolveStarted = System.nanoTime();
                    gpuTimer.begin(
                            DarkBallGpuTimer.Stage.SPLAT_RESOLVE);
                    try {
                        bodyRendered =
                                DarkBallDensityFBO
                                        .resolveSurfaceSplatField(bodyBounds);
                    } finally {
                        gpuTimer.end(
                                DarkBallGpuTimer.Stage.SPLAT_RESOLVE);
                    }
                    surfaceSplatResolveCpuMicros =
                            elapsedMicros(splatResolveStarted);
                    if (!bodyRendered) {
                        splatFailure =
                                "surface-splat field resolve failed";
                    }
                } else {
                    splatFailure = "surface-splat body draw failed";
                }
            }

            if (bodyRendered && siphonProgress > 0.001f) {
                long siphonBuildStarted = System.nanoTime();
                DarkBallSiphonSurfaceMesh siphonMesh =
                        buildSiphonSurfaceMesh(siphonProgress);
                siphonBuildCpuMicros =
                        elapsedMicros(siphonBuildStarted);
                if (hasVisibleSiphonMaterial(siphonMesh)) {
                    if (siphonSurfaceMeshRenderer == null) {
                        siphonSurfaceMeshRenderer =
                                new DarkBallSiphonSurfaceMeshRenderer();
                    }
                    long siphonDrawStarted = System.nanoTime();
                    gpuTimer.begin(DarkBallGpuTimer.Stage.SIPHON_DRAW);
                    try {
                        siphonRendered = siphonSurfaceMeshRenderer.render(
                                siphonMesh,
                                voxelVolume,
                                camera,
                                fade);
                    } finally {
                        gpuTimer.end(DarkBallGpuTimer.Stage.SIPHON_DRAW);
                    }
                    siphonDrawCpuMicros =
                            elapsedMicros(siphonDrawStarted);
                    depthWriteSubmitted |= siphonRendered;
                    if (!siphonRendered) {
                        splatFailure = "siphon indexed draw failed";
                    }
                }
            }
        } catch (Throwable failure) {
            splatFailure = "surface-splat transaction threw "
                    + failure.getClass().getSimpleName();
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Dark Ball surface-splat transaction "
                            + "failed for pokemon {}; resetting the material "
                            + "target before exact-mask recovery",
                    pokemonId,
                    failure);
        }

        boolean drawsSucceeded = splatFailure == null
                && bodyRendered
                && siphonRendered;
        boolean depthRestored = false;
        if (drawsSucceeded) {
            long depthRestoreStarted = System.nanoTime();
            gpuTimer.begin(DarkBallGpuTimer.Stage.DEPTH_RESTORE);
            try {
                depthRestored = !depthWriteSubmitted
                        || DarkBallDensityFBO
                        .restoreSceneDepthAfterDirectDraw();
            } finally {
                gpuTimer.end(DarkBallGpuTimer.Stage.DEPTH_RESTORE);
            }
            if (depthWriteSubmitted) {
                depthRestoreCpuMicros =
                        elapsedMicros(depthRestoreStarted);
            }
            if (!depthRestored) {
                DarkBallDensityFBO
                        .resetDensityPassAfterFailedDirectDraw();
            }
        } else {
            DarkBallDensityFBO
                    .resetDensityPassAfterFailedDirectDraw();
        }
        if (!drawsSucceeded || !depthRestored) {
            destroySurfaceMeshRenderer();
            if (splatFailure == null) {
                splatFailure = "scene-depth restore failed";
            }
            failSurfaceSplatPath(
                    "exact-mask safety path: " + splatFailure);
            return false;
        }

        if (bodyPresentationActive
                && surfaceSplatBodySubmittedThisFrame) {
            // This authority belongs only to a successfully submitted splat
            // body. It reaches zero at the exact siphon boundary so the
            // texture-exact reference can repair enclosed pre-collapse
            // pinholes without ever surviving as a collapse ghost.
            compositePreCollapseInteriorGuard = Math.max(
                    compositePreCollapseInteriorGuard,
                    preCollapseInteriorGuardAt(presentationAge)
                            * currentDirectBodyActivationBlend());
        }

        float voxelSize = Math.max(Math.min(
                voxelVolume.sdfVoxelX(),
                voxelVolume.sdfVoxelYz()), 0.0001f);
        Matrix4f projection =
                new Matrix4f(RenderSystem.getProjectionMatrix());
        DarkBallProjectedEffectBounds.UvBounds geometryBounds =
                DarkBallProjectedEffectBounds.forSurfelAndSiphon(
                        advectedDensityField,
                        analyticalVolume,
                        voxelVolume,
                        projection,
                        camera,
                        cameraPosition,
                        currentDirectBodyTurbulenceBlend(),
                        voxelSize,
                        DarkBallCaptureMath.siphonEndRadius(),
                        bodyPresentationActive,
                        siphonProgress > 0.0f
                                || finalCollapseAt(presentationAge)
                                > 0.0f,
                        siphonInletRadiusScaleAt(presentationAge),
                        surfaceSplatRenderer == null
                                ? 0.0f
                                : surfaceSplatRenderer
                                .uploadedProjectedReachRadius());
        DarkBallDensityFBO.markDirectVolumeRendered(
                geometryBounds,
                bodyPresentationActive);
        int sampleCount = surfaceSplatRenderer == null
                ? 0
                : surfaceSplatRenderer.uploadedSampleCount();
        logSurfaceMeshPath(
                "fused topology-independent surface splats (samples="
                        + sampleCount
                        + ", resolve=3x3, transport=bone-tree-routed"
                        + ") + siphon mesh");
        return true;
    }

    private static long elapsedMicros(long startedNanos) {
        return Math.max(
                (System.nanoTime() - startedNanos) / 1_000L,
                0L);
    }

    private DarkBallSiphonSurfaceMesh buildSiphonSurfaceMesh(
            float siphonProgress) {
        float presentationAge = currentSurfacePresentationAge();
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        float[] trailingGates = new float[nodes.length];
        for (int node = 0; node < nodes.length; node++) {
            nodes[node] = new Vector3f(
                    advectedDensityField.siphonBoltNode(node));
            float curveT = node / (nodes.length - 1.0f);
            trailingGates[node] =
                    siphonTrailingGateAt(presentationAge, curveT);
        }

        float voxelSize = Math.max(Math.min(
                voxelVolume.sdfVoxelX(),
                voxelVolume.sdfVoxelYz()), 0.0001f);
        float inletRadiusScale =
                siphonInletRadiusScaleAt(presentationAge);
        float rootRadius = Math.max(
                voxelVolume.bodyRadius() * 0.055f * inletRadiusScale,
                voxelSize * 0.90f);
        float shoulderRadius = Math.max(
                voxelVolume.bodyRadius() * 0.070f * inletRadiusScale,
                voxelSize * 1.10f);
        float endRadius = Math.max(
                DarkBallCaptureMath.siphonEndRadius() * 0.72f,
                voxelSize * 0.90f);
        return DarkBallSiphonSurfaceMesh.build(
                nodes,
                rootRadius,
                shoulderRadius,
                endRadius,
                siphonProgress,
                trailingGates);
    }

    private static boolean hasVisibleSiphonMaterial(
            DarkBallSiphonSurfaceMesh mesh) {
        if (mesh == null) {
            return false;
        }
        for (float window : mesh.materialWindow()) {
            if (window >= MATERIAL_COVERAGE_DEPTH_CUTOFF) {
                return true;
            }
        }
        return false;
    }

    private void logSurfaceMeshPath(String description) {
        if (surfaceMeshPathLogged) {
            return;
        }
        surfaceMeshPathLogged = true;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball render path for pokemon {}: {} "
                        + "(duration={}s)",
                pokemonId,
                description,
                VFX_END);
    }

    private void failSurfaceSplatPath(String description) {
        boolean firstFailure =
                surfaceMeshPath != SurfaceMeshPath.FAILED;
        surfaceMeshPath = SurfaceMeshPath.FAILED;
        if (!firstFailure) {
            return;
        }
        Shadowedhearts.LOGGER.warn(
                "[ShadowedHearts] Dark Ball surfel path for pokemon {} "
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

    private boolean hasSnapshotMask() {
        return snapshotApplied && !snapshotQuads.isEmpty() && getSnapshotMaskStrength() > 0.002f;
    }

    private boolean shouldWaitForSnapshotBeforeSplats() {
        return snapshotRequested && !snapshotApplied
                && age < CONVERSION_SOLID + 0.20f * TIMING_SCALE;
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
        if (DarkBallDensityFBO.beginMaskPass(clearAuxiliaryTargets, true)) {
            setupSnapshotMaskUniforms();
            exactMaskRendered = renderTexturedSnapshotMask(camPos);
        }

        float proxyStrength = getProxyDepthStrength();
        if (proxyStrength > 0.002f && ModShaders.DARK_BALL_PROXY_DEPTH != null) {
            boolean frontRendered = renderTexturedSnapshotProxyDepth(
                    camPos, proxyStrength, clearAuxiliaryTargets);
            if (frontRendered) {
                compositeProxyDepthAvailable = 1.0f;
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
        RenderType maskType = BallRenderTypes.darkBallMask(snapshotTexture);
        VertexConsumer consumer = MASK_BUFFERS.getBuffer(maskType);
        Matrix4f identity = new Matrix4f();
        int color = packMaskColor(strength);

        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            addTexturedSnapshotVertex(consumer, identity, quad.p1(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p2(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p3(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p4(), camPos, color);
        }

        MASK_BUFFERS.endBatch(maskType);
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

        RenderType proxyType = BallRenderTypes.darkBallProxyDepth(snapshotTexture);
        VertexConsumer consumer = MASK_BUFFERS.getBuffer(proxyType);
        Matrix4f identity = new Matrix4f();
        int color = packMaskColor(1.0f);

        for (DarkBallFieldMaskBufferSource.CapturedQuad quad : texturedSnapshotQuads) {
            addTexturedSnapshotVertex(consumer, identity, quad.p1(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p2(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p3(), camPos, color);
            addTexturedSnapshotVertex(consumer, identity, quad.p4(), camPos, color);
        }

        MASK_BUFFERS.endBatch(proxyType);
        GL11.glCullFace(GL11.GL_BACK);
        return true;
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

    private void renderSnapshotMask(Vec3 camPos, Camera camera, BufferBuilder buffer) {
        float strength = getSnapshotMaskStrength();
        if (strength <= 0.002f || snapshotQuads.isEmpty()) {
            return;
        }

        Vec3 viewDir = snapshotCenter.subtract(camPos);
        if (viewDir.lengthSqr() < 1e-7) {
            viewDir = new Vec3(0, 0, 1);
        } else {
            viewDir = viewDir.normalize();
        }

        float siphon = remapClamped(age, SIPHON_START, SIPHON_END);
        float surfaceDensity = Mth.clamp(strength * Mth.lerp(siphon, 0.28f, 0.14f), 0.0f, 0.34f);
        float extent = Math.max(0.18f, snapshotExtent);
        Matrix4f identity = new Matrix4f();

        for (SnapshotQuad quad : snapshotQuads) {
            float depth01 = normalizedViewDepth(quad.center, snapshotCenter, viewDir, extent);
            emitSnapshotQuad(buffer, identity, quad, camPos, surfaceDensity, depth01);
        }
    }

    private static void emitSnapshotQuad(BufferBuilder buffer,
                                         Matrix4f pose,
                                         SnapshotQuad quad,
                                         Vec3 camPos,
                                         float density,
                                         float depth01) {
        float d = Mth.clamp(density, 0f, 1f);
        float z = Mth.clamp(depth01, 0f, 1f);
        addSnapshotVertex(buffer, pose, quad.p1, camPos, 0f, 1f, d, z);
        addSnapshotVertex(buffer, pose, quad.p2, camPos, 1f, 1f, d, z);
        addSnapshotVertex(buffer, pose, quad.p3, camPos, 1f, 0f, d, z);
        addSnapshotVertex(buffer, pose, quad.p4, camPos, 0f, 0f, d, z);
    }

    private static void addSnapshotVertex(BufferBuilder buffer,
                                          Matrix4f pose,
                                          Vec3 world,
                                          Vec3 camPos,
                                          float u,
                                          float v,
                                          float density,
                                          float depth01) {
        buffer.addVertex(pose,
                        (float) (world.x - camPos.x),
                        (float) (world.y - camPos.y),
                        (float) (world.z - camPos.z))
                .setUv(u, v)
                .setColor(density, depth01, 1.0f, 1.0f)
                .setLight(FULLBRIGHT);
    }

    private static void setDensitySnapshotMode(ShaderInstance shader, float mode) {
        Uniform uniform = shader.getUniform("SnapshotMode");
        if (uniform != null) {
            uniform.set(mode);
        }
    }

    private static void setDensityProxyDepthMode(ShaderInstance shader, float mode) {
        Uniform uniform = shader.getUniform("ProxyDepthMode");
        if (uniform != null) {
            uniform.set(mode);
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

    static float preCollapseInteriorGuardAt(float captureAge) {
        // SiphonProgress is zero both immediately before and exactly at the
        // collapse boundary. Keep this age-domain gate discrete so repairs
        // remain available for the final stable-body frame but are guaranteed
        // absent from the first transport frame.
        return captureAge < SIPHON_START ? 1.0f : 0.0f;
    }

    private void updateCompositeReconstructionInputs(Camera camera, Vec3 camPos,
                                                      Vec3 ballPos, Vec3 pokemonCenter,
                                                      AABB bodyBounds,
                                                      boolean allowDirectVolume) {
        if (allowDirectVolume
                && voxelVolume != null
                && analyticalVolume != null
                && advectedDensityField != null) {
            startDirectBodyActivationIfNeeded();
        }
        float directBodyActivation =
                currentDirectBodyActivationBlend();
        if (voxelVolume != null) {
            // These components supply depth continuity and pseudo-surface
            // scale to the fused surfel and exact-mask composite passes.
            compositeVolumeSize.x = Math.max(voxelVolume.captureLength(), 0.0001f);
            compositeVolumeSize.y = Math.max(voxelVolume.radius(), 0.0001f);
        } else {
            // The texture-exact handoff can render before the asynchronous
            // volume build completes. Give its proxy-depth lighting a stable
            // model-scale fallback without making that volume visible.
            compositeVolumeSize.x = Math.max(snapshotExtent * 2.0f, 0.05f);
        compositeVolumeSize.y = Math.max(snapshotExtent, 0.05f);
        }
        compositeDeformationBlend = Math.max(compositeDeformationBlend,
                getDeformationBlend());
        compositeDepthHighlightBlend = Math.max(
                compositeDepthHighlightBlend,
                depthHighlightBlendAt(age, directBodyActivation));
        compositeSignedBodyAuthority = Math.max(
                compositeSignedBodyAuthority,
                signedBodyAuthorityAt(age) * directBodyActivation);
        compositeTurbulenceBlend = Math.max(compositeTurbulenceBlend,
                turbulenceBlendAt(age) * directBodyActivation);
        compositeDeformationTime = Math.max(compositeDeformationTime,
                DarkBallDeformationSettings.timeAt(age));
        Vector2f centerUv = projectWorldToScreenUv(pokemonCenter, camera, camPos);
        Vector2f worldUpUv = projectWorldToScreenUv(
                pokemonCenter.add(0.0, 1.0, 0.0), camera, camPos);
        if (centerUv != null && worldUpUv != null) {
            float upX = worldUpUv.x - centerUv.x;
            float upY = worldUpUv.y - centerUv.y;
            float upLengthSquared = upX * upX + upY * upY;
            if (upLengthSquared > 1.0e-8f) {
                float inverseLength = Mth.invSqrt(upLengthSquared);
                compositeProjectedUp.set(upX * inverseLength, upY * inverseLength);
            }
        }

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

        Vector3f rootLocal = voxelVolume != null && siphonDrainRootValid
                ? new Vector3f(siphonDrainRootLocal)
                : null;
        Vec3 rootWorld = rootLocal != null ? volumeLocalToWorld(voxelVolume, rootLocal) : pokemonCenter;

        compositeSiphonProgress = Math.max(compositeSiphonProgress,
                remapClamped(age, SIPHON_START, SIPHON_END));
        float bodyEnvelopeScale = bodyCollapseEnvelopeScaleAt(age);
        compositeBodyCollapseEnvelopeScale = Math.min(
                compositeBodyCollapseEnvelopeScale,
                bodyEnvelopeScale);
        Vector2f rootUv = projectWorldToScreenUv(rootWorld, camera, camPos);
        Vector2f ballUv = projectWorldToScreenUv(ballPos, camera, camPos);
        if (rootUv == null || ballUv == null) {
            return;
        }

        compositeSiphonRootU = Mth.clamp(rootUv.x, 0.0f, 1.0f);
        compositeSiphonRootV = Mth.clamp(rootUv.y, 0.0f, 1.0f);
        compositeBallU = Mth.clamp(ballUv.x, 0.0f, 1.0f);
        compositeBallV = Mth.clamp(ballUv.y, 0.0f, 1.0f);
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
        voxelVolume = null;
        cancelPendingVoxelBuild();
        destroyCapturedVolumeResources();
        siphonDrainRootValid = false;
        if (capturedModelVertices.size() < 12 && capturedModelTriangles.size() < 6) {
            logVoxelBuild("insufficient captured mesh", capturedModelVertices.size(), capturedModelTriangles.size(), 0);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity ballEnt = mc.level.getEntity(ballId);
        if (!(ballEnt instanceof EmptyPokeBallEntity ball)) {
            logVoxelBuild("missing ball entity", capturedModelVertices.size(), capturedModelTriangles.size(), 0);
            return;
        }

        Vec3 ballPos = new Vec3(ball.getX(), ball.getY(), ball.getZ()).add(0, 0.08, 0);
        Vec3 capturedCenter = snapshotCenter;
        AABB capturedBounds = bounds.toAabb();
        List<Vec3> capturedVertices = List.copyOf(capturedModelVertices);
        List<DarkBallFieldMaskBufferSource.CapturedTriangle> capturedTriangles =
                List.copyOf(capturedModelTriangles);
        NativeImage textureImage = loadSnapshotTextureImage();
        DarkBallVfxQuality quality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality()
        );
        forceExactMaskOnly =
                com.jayemceekay.shadowedhearts.config
                        .ShadowedHeartsConfigs
                        .getInstance()
                        .getClientConfig()
                        .darkBallForceExactMaskOnly();
        int surfelBudget =
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        quality,
                        com.jayemceekay.shadowedhearts.config
                                .ShadowedHeartsConfigs
                                .getInstance()
                                .getClientConfig()
                                .darkBallSurfelBudget());
        surfaceSplatRequestedBudget =
                forceExactMaskOnly ? 0 : surfelBudget;
        List<CapturedBoneNode> capturedBoneNodeSnapshot =
                List.copyOf(capturedBoneNodes);
        int generation = voxelBuildGeneration;
        AtomicBoolean preparationCancellation = new AtomicBoolean();
        surfaceMeshBuildCancellation = preparationCancellation;

        // Triangle rasterization, interior fill, SDF construction, gradients,
        // component labeling, and release-order propagation are all CPU-only.
        // Running them here used to stall the render thread exactly when the
        // ball hit. Keep GL texture allocation deferred to the normal renderer.
        voxelBuildFuture = CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            try (NativeImage image = textureImage) {
                DarkBallVolumeBuildResult volume = DarkBallVolumeVoxelizer.build(
                        ballPos,
                        capturedCenter,
                        capturedBounds,
                        capturedVertices,
                        capturedTriangles,
                        image
                );
                Vector3f ballLocal = volume == null
                        ? new Vector3f()
                        : volumeWorldToLocal(volume, ballPos);
                DarkBallAnalyticalVolume analytical = volume == null
                        ? null
                        : DarkBallAnalyticalVolume.build(volume, ballLocal, quality);
                long buildMillis = (System.nanoTime() - started) / 1_000_000L;
                return new VoxelBuildOutput(
                        generation,
                        volume,
                        analytical,
                        quality,
                        capturedVertices.size(),
                        capturedTriangles.size(),
                        buildMillis
                );
            }
        }, PREPARATION_EXECUTOR);
        if (forceExactMaskOnly) {
            selectForcedExactMaskPath();
        } else {
            surfaceMeshBuildFuture = voxelBuildFuture.thenApplyAsync(result -> {
                        List<DarkBallSplatBoneRoutePlan.Node> routeNodes =
                                capturedBoneNodesToSplatLocal(
                                        result.volume(),
                                        capturedBoneNodeSnapshot);
                        return buildSurfaceMeshOutput(
                                result.generation(),
                                result.analyticalVolume(),
                                result.volume(),
                                surfelBudget,
                                routeNodes,
                                preparationCancellation);
                    }, PREPARATION_EXECUTOR);
        }
    }

    private void installVoxelBuildIfReady() {
        CompletableFuture<VoxelBuildOutput> pending = voxelBuildFuture;
        if (pending == null || !pending.isDone()) {
            return;
        }
        voxelBuildFuture = null;

        VoxelBuildOutput result;
        try {
            result = pending.join();
        } catch (CompletionException failure) {
            Shadowedhearts.LOGGER.error(
                    "[ShadowedHearts] Dark Ball asynchronous voxel build failed for pokemon {}",
                    pokemonId,
                    failure.getCause() == null ? failure : failure.getCause()
            );
            return;
        }
        if (result == null || result.generation() != voxelBuildGeneration) {
            return;
        }

        voxelVolume = result.volume();
        analyticalVolume = result.analyticalVolume();
        analyticalQuality = result.quality();
        voxelReadyAge = age;
        voxelBuildMillis = result.buildMillis();
        logVoxelBuild("built asynchronously in " + result.buildMillis() + " ms",
                result.vertexCount(), result.triangleCount(),
                countVoxelCandidates(voxelVolume));
        logVoxelSdfStats(voxelVolume);

        if (analyticalVolume != null) {
            siphonDrainRootLocal.set(analyticalVolume.outletLocal());
            siphonDrainRootValid = true;
            analyticalVolumeLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball captured shape atlas for pokemon {} active ({})",
                    pokemonId,
                    analyticalVolume.summary()
            );
        }
    }

    private void startSurfaceMeshBuild(
            int generation,
            DarkBallAnalyticalVolume source) {
        if (forceExactMaskOnly) {
            selectForcedExactMaskPath();
            return;
        }
        cancelPendingSurfaceMeshBuild();
        surfaceMesh = null;
        surfaceSplatSamplePlan = null;
        surfaceMeshPath = SurfaceMeshPath.PENDING;
        surfaceMeshPathLogged = false;
        if (source == null) {
            surfaceMeshPath = SurfaceMeshPath.FAILED;
            return;
        }

        // Extraction, area-stratified sampling, neighborhood smoothing, and
        // bone-route assignment are immutable one-time preparation stages.
        // Keep all of them off the render thread.
        AtomicBoolean cancellation = new AtomicBoolean();
        DarkBallVfxQuality preparationQuality = analyticalQuality;
        int preparationSurfelBudget =
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        preparationQuality,
                        com.jayemceekay.shadowedhearts.config
                                .ShadowedHeartsConfigs
                                .getInstance()
                                .getClientConfig()
                                .darkBallSurfelBudget());
        surfaceSplatRequestedBudget = preparationSurfelBudget;
        DarkBallVolumeBuildResult preparationVolume = voxelVolume;
        List<DarkBallSplatBoneRoutePlan.Node> routeNodes =
                capturedBoneNodesToSplatLocal(
                        preparationVolume,
                        capturedBoneNodes);
        surfaceMeshBuildCancellation = cancellation;
        surfaceMeshBuildFuture = CompletableFuture.supplyAsync(
                () -> buildSurfaceMeshOutput(
                        generation,
                        source,
                        preparationVolume,
                        preparationSurfelBudget,
                        routeNodes,
                        cancellation),
                PREPARATION_EXECUTOR);
    }

    private void selectForcedExactMaskPath() {
        cancelPendingSurfaceMeshBuild();
        surfaceMesh = null;
        surfaceSplatSamplePlan = null;
        destroySurfaceMeshRenderer();
        surfaceMeshPath = SurfaceMeshPath.FAILED;
        if (surfaceMeshPathLogged) {
            return;
        }
        surfaceMeshPathLogged = true;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball render path for pokemon {}: "
                        + "intentional exact-mask-only diagnostic; surfel "
                        + "preparation and submission are bypassed "
                        + "(duration={}s)",
                pokemonId,
                VFX_END);
    }

    private static SurfaceMeshBuildOutput buildSurfaceMeshOutput(
            int generation,
            DarkBallAnalyticalVolume source,
            DarkBallVolumeBuildResult preparationVolume,
            int preparationSurfelBudget,
            List<DarkBallSplatBoneRoutePlan.Node> routeNodes,
            AtomicBoolean cancellation) {
            if (source == null || cancellation.get()) {
                return new SurfaceMeshBuildOutput(
                        generation, source, null, null, "unavailable", 0L);
            }
            Vector3f routeInletLocal = source.outletLocal();
            long started = System.nanoTime();
            DarkBallSurfaceMesh mesh =
                    source.buildSurfaceMesh(cancellation::get);
            if (mesh == null || !mesh.hasFiniteSplatSamples()) {
                return new SurfaceMeshBuildOutput(
                        generation, source, mesh, null, "unavailable",
                        (System.nanoTime() - started) / 1_000_000L);
            }

            float maximumVoxel = preparationVolume == null
                    ? 0.0f
                    : Math.max(
                    preparationVolume.sdfVoxelX(),
                    preparationVolume.sdfVoxelYz());
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan splatSamplePlan =
                    DarkBallSurfaceSplatRenderer.prepareSamples(
                            mesh,
                            preparationSurfelBudget,
                            maximumVoxel);
            if (splatSamplePlan == null) {
                return new SurfaceMeshBuildOutput(
                        generation, source, mesh, null,
                        "sample preparation unavailable",
                        (System.nanoTime() - started) / 1_000_000L);
            }

            float[] routedCarrierPositions =
                    DarkBallSurfaceSplatRenderer.insetSamplePositions(
                            splatSamplePlan,
                            maximumVoxel);
            DarkBallSplatBoneRoutePlan.Result routePlan =
                    DarkBallSplatBoneRoutePlan.build(
                            routedCarrierPositions,
                            routeInletLocal,
                            routeNodes,
                            cancellation::get);
            splatSamplePlan =
                    splatSamplePlan.withBoneRoutePlan(routePlan);
            int confidentPrincipalTangents = 0;
            float principalConfidenceSum = 0.0f;
            int thinSheetSamples = 0;
            float thinSheetScoreSum = 0.0f;
            int thinSheetPatchCount = 0;
            int patchedThinSheetSamples = 0;
            int largestThinSheetPatch = 0;
            for (float confidence
                    : splatSamplePlan.tangentConfidence()) {
                principalConfidenceSum += confidence;
                if (confidence >= 0.50f) {
                    confidentPrincipalTangents++;
                }
            }
            for (float sheetScore
                    : splatSamplePlan.thinSheetScore()) {
                thinSheetScoreSum += sheetScore;
                if (sheetScore
                        >= DarkBallSplatThinSheetPlan.MIN_PAIR_SCORE) {
                    thinSheetSamples++;
                }
            }
            for (int patchId
                    : splatSamplePlan.thinSheetPatchId()) {
                if (patchId >= 0) {
                    thinSheetPatchCount = Math.max(
                            thinSheetPatchCount,
                            patchId + 1);
                    patchedThinSheetSamples++;
                }
            }
            if (thinSheetPatchCount > 0) {
                int[] patchSizes =
                        new int[thinSheetPatchCount];
                for (int patchId
                        : splatSamplePlan.thinSheetPatchId()) {
                    if (patchId >= 0
                            && patchId < patchSizes.length) {
                        patchSizes[patchId]++;
                    }
                }
                for (int patchSize : patchSizes) {
                    largestThinSheetPatch = Math.max(
                            largestThinSheetPatch, patchSize);
                }
            }
            float meanPrincipalConfidence =
                    splatSamplePlan.tangentConfidence().length > 0
                            ? principalConfidenceSum
                            / splatSamplePlan
                            .tangentConfidence().length
                            : 0.0f;
            String preparationSummary =
                    "surfelBudget=" + preparationSurfelBudget
                            + ", samples="
                            + splatSamplePlan.releaseOrder().length
                            + ", meshVertices=" + mesh.vertexCount()
                            + ", blueNoiseCandidates="
                            + Math.multiplyExact(
                            splatSamplePlan.releaseOrder().length,
                            DarkBallSurfaceSplatRenderer
                                    .BLUE_NOISE_CANDIDATE_MULTIPLIER)
                            + ", selection=patch-aware-surface-blue-noise"
                            + ", featureReserve="
                            + Math.round(
                            splatSamplePlan.releaseOrder().length
                                    * DarkBallSurfaceSplatRenderer
                                    .FEATURE_SAMPLE_FRACTION)
                            + ", principalTangents="
                            + confidentPrincipalTangents
                            + "/"
                            + splatSamplePlan.releaseOrder().length
                            + ", principalConfidenceMean="
                            + String.format(
                            java.util.Locale.ROOT,
                            "%.3f",
                            meanPrincipalConfidence)
                            + ", thinSheetSamples="
                            + thinSheetSamples
                            + ", thinSheetScoreMean="
                            + String.format(
                            java.util.Locale.ROOT,
                            "%.3f",
                            splatSamplePlan.thinSheetScore().length > 0
                                    ? thinSheetScoreSum
                                    / splatSamplePlan
                                    .thinSheetScore().length
                                    : 0.0f)
                            + ", thinSheetPatches="
                            + thinSheetPatchCount
                            + ", patchedThinSheetSamples="
                            + patchedThinSheetSamples
                            + ", largestThinSheetPatch="
                            + largestThinSheetPatch
                            + ", neighbors="
                            + DarkBallSplatNeighborhoodPlan.NEIGHBOR_COUNT
                            + ", route=" + routePlan.summary();
            return new SurfaceMeshBuildOutput(
                    generation,
                    source,
                    mesh,
                    splatSamplePlan,
                    preparationSummary,
                    (System.nanoTime() - started) / 1_000_000L);
    }

    private void installSurfaceMeshBuildIfReady() {
        CompletableFuture<SurfaceMeshBuildOutput> pending =
                surfaceMeshBuildFuture;
        if (pending == null || !pending.isDone()) {
            return;
        }
        surfaceMeshBuildFuture = null;
        surfaceMeshBuildCancellation = null;

        SurfaceMeshBuildOutput result;
        try {
            result = pending.join();
        } catch (CancellationException ignored) {
            return;
        } catch (CompletionException failure) {
            surfaceMeshPath = SurfaceMeshPath.FAILED;
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball surfel preparation failed "
                            + "for pokemon {}; retaining the exact captured mask",
                    pokemonId,
                    failure.getCause() == null
                            ? failure
                            : failure.getCause());
            return;
        }
        if (result == null
                || result.generation() != voxelBuildGeneration
                || result.source() != analyticalVolume) {
            return;
        }

        DarkBallSurfaceMesh candidate = result.mesh();
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan samplePlan =
                result.splatSamplePlan();
        surfaceReadyAge = age;
        surfaceBuildMillis = result.buildMillis();
        if (candidate == null
                || !candidate.hasFiniteSplatSamples()
                || samplePlan == null) {
            surfaceMeshPath = SurfaceMeshPath.FAILED;
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball surfel source for pokemon {} "
                            + "was unavailable; retaining the exact captured "
                            + "mask ({}, preparation={})",
                    pokemonId,
                    candidate == null ? "mesh=missing" : candidate.summary(),
                    result.splatPreparationSummary());
            return;
        }
        boolean lateReadiness =
                age >= SURFACE_SPLAT_SELECTION_DEADLINE;
        surfaceMesh = candidate;
        surfaceSplatSamplePlan = samplePlan;
        surfaceMeshPath = SurfaceMeshPath.SPLAT;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball topology-independent bone-routed "
                        + "surfel source for pokemon {} built asynchronously "
                        + "in {} ms with {} readiness; topology and "
                        + "collapse-authority guards are not part of this path "
                        + "({}, preparation={})",
                pokemonId,
                result.buildMillis(),
                lateReadiness ? "late-remapped" : "on-time",
                candidate.summary(),
                result.splatPreparationSummary());
    }

    private static SurfaceMeshPath initialSurfaceMeshPath() {
        return SurfaceMeshPath.PENDING;
    }

    private NativeImage loadSnapshotTextureImage() {
        if (snapshotTexture == null) {
            return null;
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(snapshotTexture);
            if (resource.isEmpty()) {
                return null;
            }
            try (var stream = resource.get().open()) {
                return NativeImage.read(stream);
            }
        } catch (Exception failure) {
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Could not read Dark Ball snapshot texture {}; "
                            + "falling back to geometry-only voxelization",
                    snapshotTexture,
                    failure
            );
            return null;
        }
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

    private static Vec3 volumeLocalToWorld(DarkBallVolumeBuildResult volume, Vector3f local) {
        return volume.root()
                .add(volume.axis().scale(local.x))
                .add(volume.side().scale(local.y))
                .add(volume.up().scale(local.z));
    }

    static Vector3f volumeWorldToLocal(DarkBallVolumeBuildResult volume, Vec3 world) {
        Vec3 rel = world.subtract(volume.root());
        return new Vector3f(
                (float) rel.dot(volume.axis()),
                (float) rel.dot(volume.side()),
                (float) rel.dot(volume.up())
        );
    }

    private static List<DarkBallSplatBoneRoutePlan.Node>
    capturedBoneNodesToSplatLocal(
            DarkBallVolumeBuildResult volume,
            List<CapturedBoneNode> capturedNodes) {
        if (volume == null
                || capturedNodes == null
                || capturedNodes.isEmpty()) {
            return List.of();
        }
        List<Vector3f> capturedPositions =
                new ArrayList<>(capturedNodes.size());
        Vector3f syntheticRoot = new Vector3f();
        int rootContributors = 0;
        for (CapturedBoneNode captured : capturedNodes) {
            Vector3f local = captured == null
                    || captured.worldPosition() == null
                    ? null
                    : volumeWorldToLocal(
                    volume,
                    captured.worldPosition());
            capturedPositions.add(local);
            if (local != null
                    && Float.isFinite(local.x)
                    && Float.isFinite(local.y)
                    && Float.isFinite(local.z)
                    && captured.ownsGeometry()) {
                syntheticRoot.add(local);
                rootContributors++;
            }
        }
        if (rootContributors == 0) {
            for (Vector3f local : capturedPositions) {
                if (local != null
                        && Float.isFinite(local.x)
                        && Float.isFinite(local.y)
                        && Float.isFinite(local.z)) {
                    syntheticRoot.add(local);
                    rootContributors++;
                }
            }
        }
        if (rootContributors > 0) {
            syntheticRoot.div(rootContributors);
        }

        // Rendered ModelPart recursion may expose several top-level roots.
        // Join them through one model-space center so every component follows
        // its own parent chain instead of being projected onto a different
        // limb's surviving route.
        List<DarkBallSplatBoneRoutePlan.Node> localNodes =
                new ArrayList<>(capturedNodes.size() + 1);
        localNodes.add(new DarkBallSplatBoneRoutePlan.Node(
                syntheticRoot,
                -1,
                "rendered-root",
                false,
                true));
        for (int index = 0; index < capturedNodes.size(); index++) {
            CapturedBoneNode captured = capturedNodes.get(index);
            int capturedParent = captured == null
                    ? -1
                    : captured.parentIndex();
            localNodes.add(new DarkBallSplatBoneRoutePlan.Node(
                    capturedPositions.get(index),
                    capturedParent < 0
                            ? 0
                            : capturedParent + 1,
                    captured == null
                            ? ""
                            : captured.path(),
                    captured != null
                            && captured.ownsGeometry(),
                    captured != null
                            && captured.chainable()));
        }
        return List.copyOf(localNodes);
    }

    private void ensureAnalyticalVolume(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        // Despite the historical name, this object now owns the captured
        // shape/cohesive atlases consumed by the direct advected renderer.
        if (volume == null) {
            return;
        }

        DarkBallVfxQuality requestedQuality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality()
        );
        if (analyticalVolume != null && analyticalQuality == requestedQuality) {
            return;
        }
        if (analyticalVolume != null) {
            analyticalVolume.destroy();
        }

        Vector3f ballLocal = volumeWorldToLocal(volume, ballPos);
        analyticalVolume = DarkBallAnalyticalVolume.build(volume, ballLocal, requestedQuality);
        analyticalQuality = requestedQuality;
        startSurfaceMeshBuild(voxelBuildGeneration, analyticalVolume);
        if (analyticalVolume != null && !analyticalVolumeLogged) {
            siphonDrainRootLocal.set(analyticalVolume.outletLocal());
            siphonDrainRootValid = true;
            analyticalVolumeLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball captured shape atlas for pokemon {} active ({})",
                    pokemonId,
                    analyticalVolume.summary()
            );
        }
    }

    private void ensureAdvectedDensityField(DarkBallVolumeBuildResult volume, Vec3 ballPos) {
        if (volume == null || analyticalVolume == null) {
            return;
        }

        DarkBallVfxQuality requestedQuality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality()
        );
        if (advectedDensityField != null
                && advectedDensityQuality == requestedQuality
                && advectedDensityShape == analyticalVolume) {
            return;
        }
        if (advectedDensityField != null) {
            advectedDensityField.destroy();
        }

        Vector3f ballLocal = volumeWorldToLocal(volume, ballPos);
        advectedDensityField = DarkBallAdvectedDensityField.build(
                volume, analyticalVolume, ballLocal, requestedQuality);
        advectedDensityShape = analyticalVolume;
        advectedDensityQuality = requestedQuality;
        if (advectedDensityField != null && !advectedDensityLogged) {
            siphonDrainRootLocal.set(advectedDensityField.outletLocal());
            siphonDrainRootValid = true;
            advectedDensityLogged = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball siphon path state for pokemon {} active ({})",
                    pokemonId,
                    advectedDensityField.summary()
            );
        }
    }

    private static int countVoxelCandidates(DarkBallVolumeBuildResult volume) {
        if (volume == null || volume.coreField() == null || volume.envelopeField() == null
                || volume.coreField().length != volume.envelopeField().length) {
            return 0;
        }

        int candidates = 0;
        for (int i = 0; i < volume.coreField().length; i++) {
            if (volume.coreField()[i] > 0.018f || volume.envelopeField()[i] > 0.052f) {
                candidates++;
            }
        }
        return candidates;
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

    private static float normalizedViewDepth(Vec3 pos, Vec3 center, Vec3 viewDir, float extent) {
        float projected = (float) pos.subtract(center).dot(viewDir);
        return Mth.clamp(projected / (extent * 2f) + 0.5f, 0f, 1f);
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
        private final List<CapturedBoneNode> renderedBoneNodes =
                new ArrayList<>();
        private final List<RenderedCapturedPart> renderedPartStack =
                new ArrayList<>();

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

    private record StageChatMarker(float startAge, String message) {
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

    private record CapturedBoneNode(
            Vec3 worldPosition,
            int parentIndex,
            String path,
            boolean ownsGeometry,
            boolean chainable
    ) {
    }

    private record RenderedCapturedPart(
            ModelPart part,
            int nodeIndex
    ) {
    }

    private record VoxelBuildOutput(
            int generation,
            DarkBallVolumeBuildResult volume,
            DarkBallAnalyticalVolume analyticalVolume,
            DarkBallVfxQuality quality,
            int vertexCount,
            int triangleCount,
            long buildMillis
    ) {
    }

    private record SurfaceMeshBuildOutput(
            int generation,
            DarkBallAnalyticalVolume source,
            DarkBallSurfaceMesh mesh,
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan
                    splatSamplePlan,
            String splatPreparationSummary,
            long buildMillis
    ) {
        private SurfaceMeshBuildOutput {
            splatPreparationSummary = splatPreparationSummary == null
                    ? "disabled"
                    : splatPreparationSummary;
        }
    }

    private enum SurfaceMeshPath {
        PENDING,
        SPLAT,
        FAILED
    }

}
