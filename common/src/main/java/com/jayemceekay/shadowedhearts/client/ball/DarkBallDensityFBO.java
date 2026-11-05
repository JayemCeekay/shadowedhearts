package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Offscreen framebuffer wrapper for the Dark Ball anime capture material.
 */
public final class DarkBallDensityFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(1, 0.95f);
    private static final float MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD =
            0.22f;
    private static final float CLEAN_BODY_GRAY_TRANSITION_FRACTION = 0.10f;
    private static final float MAX_SILHOUETTE_DISTANCE_OUTPUT_PIXELS = 104.0f;
    private static boolean bodyMaterialRendered;
    private static boolean bodyMaterialExactBodyBoundsRequired;
    private static boolean reducedCompositeRequested;
    private static boolean reducedCompositeFailureLogged;
    private static boolean bodyDepthSnapshotReady;
    private static boolean silhouetteDistanceRendered;
    private static boolean silhouetteDistanceSupported = true;
    private static boolean silhouetteDistanceFailureLogged;
    private static DarkBallProjectedEffectBounds.UvBounds
            bodyMaterialGeometryBounds =
            DarkBallProjectedEffectBounds.UvBounds.fullScreen();

    private DarkBallDensityFBO() {
    }

    public static boolean beginDensityPass() {
        return beginDensityPass(true, true);
    }

    public static boolean beginDensityPass(boolean clearTarget, boolean copyMainDepth) {
        return beginDensityPass(null, clearTarget, copyMainDepth);
    }

    static boolean beginDensityPass(DarkBallCaptureVfx capture,
                                    boolean clearTarget,
                                    boolean copyMainDepth) {
        DarkBallVfxQuality quality = capture != null
                ? capture.quality() : DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality());
        // Raw material/depth stays full resolution on MEDIUM/HIGH. LOW keeps
        // its established half-resolution source; the independent styled
        // target may also engage for a large MEDIUM screen footprint.
        PIPELINE.setResolutionDivisor(quality == DarkBallVfxQuality.LOW ? 2 : 1);
        float projectedBodyAreaFraction =
                projectedBodyScreenAreaFraction();
        float mediumReleaseThreshold =
                MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD * 0.75f;
        boolean mediumReducedCompositeLatched = capture != null
                && capture.isMediumReducedCompositeLatched();
        boolean mediumLargeScreenEffect =
                quality == DarkBallVfxQuality.MEDIUM
                        && projectedBodyAreaFraction
                        >= (mediumReducedCompositeLatched
                        ? mediumReleaseThreshold
                        : MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD);
        if (capture != null) {
            capture.setMediumReducedCompositeLatched(
                    mediumLargeScreenEffect);
        }
        reducedCompositeRequested =
                (quality == DarkBallVfxQuality.LOW
                        || mediumLargeScreenEffect)
                        && ModShaders.DARK_BALL_REDUCED_UPSAMPLE != null;
        PIPELINE.setReducedCompositeEnabled(
                reducedCompositeRequested);
        PIPELINE.setSilhouetteDistanceEnabled(silhouetteDistanceSupported);
        bodyDepthSnapshotReady = false;
        silhouetteDistanceRendered = false;
        boolean ready = PIPELINE.beginDensityPass(clearTarget, copyMainDepth);
        if (!ready) {
            return false;
        }
        if (reducedCompositeRequested
                && !PIPELINE.isReducedCompositeReady()) {
            reducedCompositeRequested = false;
        }
        if (clearTarget) {
            bodyMaterialRendered = false;
            bodyMaterialExactBodyBoundsRequired = false;
            bodyMaterialGeometryBounds =
                    DarkBallProjectedEffectBounds.UvBounds.fullScreen();
        }
        return true;
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    /**
     * Restores pristine scene depth after the energy/source draw while
     * preserving material written to the density color attachment.
     */
    static boolean restoreSceneDepthAfterDirectDraw() {
        return PIPELINE.restoreSceneDepthAfterDirectDraw();
    }

    /**
     * Clears a partial energy/source submission before exact-mask recovery.
     */
    static boolean resetDensityPassAfterFailedDirectDraw() {
        bodyDepthSnapshotReady = false;
        return PIPELINE.resetDensityPassAfterFailedDirectDraw();
    }

    /** Immutable body material input for the following captured-source draw. */
    static boolean prepareBodyDepthSnapshot(DarkBallCaptureVfx capture) {
        bodyDepthSnapshotReady = false;
        try (DarkBallGpuTimer.Scope ignored = DarkBallGpuTimer.measure(
                capture != null ? capture.gpuTimer() : null,
                DarkBallGpuTimer.Stage.BODY_DEPTH_SNAPSHOT)) {
            bodyDepthSnapshotReady = PIPELINE.snapshotDensityToTemp();
            return bodyDepthSnapshotReady;
        }
    }

    static void applyBodyDepthUniforms(ShaderInstance shader) {
        int bodyMaterial = PIPELINE.getProcessedTextureId();
        boolean available = bodyDepthSnapshotReady && bodyMaterial != 0;
        RenderSystem.setShaderTexture(1, available ? bodyMaterial : 0);
        shader.setSampler("BodyMaterialSampler", available ? bodyMaterial : 0);
        Uniform bodyAvailable = shader.getUniform("BodyDepthAvailable");
        if (bodyAvailable != null) bodyAvailable.set(available ? 1 : 0);
    }

    public static boolean beginMaskPass(boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginMaskPass(clearTarget, copyMainDepth);
    }

    public static boolean beginProxyDepthPass(boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginProxyDepthPass(clearTarget, copyMainDepth);
    }

    public static void resumeDensityPass() {
        PIPELINE.resumeDensityPass();
    }

    public static boolean composite() {
        return composite(null);
    }

    static boolean composite(DarkBallCaptureVfx capture) {
        // Auxiliary passes mutate shaders and samplers before the final
        // composite takes its own snapshot. Preserve the original caller
        // across all of them, including fallbacks and early returns.
        try (DensityFboPipeline.RenderStateScope ignored =
                     DensityFboPipeline.preserveRenderState()) {
            RenderSystem.colorMask(true, true, true, true);
            GL11.glColorMask(true, true, true, true);
            return compositeWithinState(capture != null ? capture.gpuTimer() : null);
        } finally {
            if (capture != null) {
                try {
                    capture.logFrameTimings();
                } catch (RuntimeException timingFailure) {
                    // Diagnostics run after GL state restoration and must
                    // never turn a successful draw into a failed capture or
                    // replace the original rendering exception.
                    Shadowedhearts.LOGGER.debug("Dark Ball frame timing log failed", timingFailure);
                }
            }
        }
    }

    private static boolean compositeWithinState(DarkBallGpuTimer compositeGpuTimer) {
        if (!DarkBallCaptureVfx.isCompositeExactMaskReady()) {
            return false;
        }
        boolean useResolvedMaterial = false;
        DarkBallProjectedEffectBounds.UvBounds geometryBounds =
                bodyMaterialRendered
                        ? bodyMaterialGeometryBounds
                        : exactBodyGeometryBounds()
                        .expandByPixels(
                                DarkBallProjectedEffectBounds
                                        .DIRECT_PADDING_PIXELS,
                                PIPELINE.getTargetWidth(),
                                PIPELINE.getTargetHeight());
        if (bodyMaterialRendered
                && bodyMaterialExactBodyBoundsRequired) {
            // The edge resolve can restore texture-exact mask pixels that are
            // outside the initial body bounds. Union the captured
            // model's projected AABB so those extremities are not excluded
            // merely because the body material produced zero there.
            Vector4f bodyBounds =
                    DarkBallCaptureVfx.getCompositeBodyUvBounds();
            geometryBounds = geometryBounds.union(
                    DarkBallProjectedEffectBounds.UvBounds.conservative(
                            bodyBounds.x, bodyBounds.y,
                            bodyBounds.z, bodyBounds.w));
        }
        DarkBallProjectedEffectBounds.UvBounds edgeBounds =
                geometryBounds.expandByPixels(
                        DarkBallProjectedEffectBounds.EDGE_PADDING_PIXELS,
                        PIPELINE.getTargetWidth(),
                        PIPELINE.getTargetHeight());
        // Resolve the moving anime material or the exact-mask recovery.
        try (DarkBallGpuTimer.Scope ignored = DarkBallGpuTimer.measure(
                compositeGpuTimer, DarkBallGpuTimer.Stage.EDGE_RESOLVE)) {
            useResolvedMaterial = PIPELINE.processDensityToTemp(
                    ModShaders.DARK_BALL_MATERIAL_RESOLVE,
                    DarkBallDensityFBO::applyMaterialResolveUniforms,
                    edgeBounds.minU(), edgeBounds.minV(),
                    edgeBounds.maxU(), edgeBounds.maxV());
        }
        if (!useResolvedMaterial) {
            return false;
        }
        DarkBallProjectedEffectBounds.UvBounds compositeBounds =
                edgeBounds
                        .expandByPixels(
                                DarkBallProjectedEffectBounds
                                        .COMPOSITE_PADDING_PIXELS,
                                PIPELINE.getTargetWidth(),
                                PIPELINE.getTargetHeight());
        if (silhouetteDistanceSupported) {
            try (DarkBallGpuTimer.Scope ignored = DarkBallGpuTimer.measure(
                    compositeGpuTimer, DarkBallGpuTimer.Stage.SILHOUETTE_DISTANCE)) {
                silhouetteDistanceRendered =
                        PIPELINE.buildSilhouetteDistanceField(
                                ModShaders
                                        .DARK_BALL_SILHOUETTE_DISTANCE_SEED,
                                ModShaders
                                        .DARK_BALL_SILHOUETTE_DISTANCE_JUMP,
                                MAX_SILHOUETTE_DISTANCE_OUTPUT_PIXELS,
                                compositeBounds.minU(),
                                compositeBounds.minV(),
                                compositeBounds.maxU(),
                                compositeBounds.maxV());
            } catch (RuntimeException distanceFieldFailure) {
                silhouetteDistanceRendered = false;
                silhouetteDistanceSupported = false;
                PIPELINE.setSilhouetteDistanceEnabled(false);
                if (!silhouetteDistanceFailureLogged) {
                    silhouetteDistanceFailureLogged = true;
                    Shadowedhearts.LOGGER.warn(
                            "[ShadowedHearts] Dark Ball silhouette-distance "
                                    + "field failed; retaining the directional "
                                    + "composite fallback for this session",
                            distanceFieldFailure);
                }
            }
        }
        boolean compositeRendered = false;
        try (DarkBallGpuTimer.Scope ignored = DarkBallGpuTimer.measure(
                compositeGpuTimer, DarkBallGpuTimer.Stage.COMPOSITE)) {
            if (reducedCompositeRequested) {
                try {
                    boolean reducedResolved =
                            PIPELINE.processProcessedToReducedComposite(
                                    ModShaders.DARK_BALL_VOLUME_COMPOSITE,
                                    DarkBallDensityFBO
                                            ::applyBodyMaterialCompositeUniforms,
                                    compositeBounds.minU(),
                                    compositeBounds.minV(),
                                    compositeBounds.maxU(),
                                    compositeBounds.maxV());
                    if (reducedResolved) {
                        compositeRendered = PIPELINE.compositeReduced(
                                ModShaders.DARK_BALL_REDUCED_UPSAMPLE,
                                DarkBallDensityFBO
                                        ::applyReducedCompositeUpsampleUniforms,
                                compositeBounds.minU(),
                                compositeBounds.minV(),
                                compositeBounds.maxU(),
                                compositeBounds.maxV());
                    }
                } catch (RuntimeException reducedPassFailure) {
                    PIPELINE.disableReducedCompositeAfterFailure();
                    reducedCompositeRequested = false;
                    if (!reducedCompositeFailureLogged) {
                        reducedCompositeFailureLogged = true;
                        Shadowedhearts.LOGGER.warn(
                                "[ShadowedHearts] Dark Ball reduced composite "
                                        + "failed; retaining the direct composite "
                                        + "for this and later frames",
                                reducedPassFailure);
                    }
                    compositeRendered = false;
                }
            }
            if (!compositeRendered) {
                // Medium/High, disabled optimization,
                // or any unavailable/failed reduced target retain the established
                // full-output resolve in the same frame.
                compositeRendered = PIPELINE.composite(
                        ModShaders.DARK_BALL_VOLUME_COMPOSITE,
                        DarkBallDensityFBO
                                ::applyBodyMaterialCompositeUniforms,
                        useResolvedMaterial,
                        compositeBounds.minU(),
                        compositeBounds.minV(),
                        compositeBounds.maxU(),
                        compositeBounds.maxV());
            }
        }
        return compositeRendered;
    }

    private static DarkBallProjectedEffectBounds.UvBounds
    exactBodyGeometryBounds() {
        Vector4f bodyBounds =
                DarkBallCaptureVfx.getCompositeBodyUvBounds();
        // conservative() deliberately returns full-screen for non-finite,
        // inverted, collapsed, or fully clipped input. That keeps this
        // formation-path optimization incapable of clipping the exact mask.
        return DarkBallProjectedEffectBounds.UvBounds.conservative(
                bodyBounds.x, bodyBounds.y,
                bodyBounds.z, bodyBounds.w);
    }

    private static float projectedBodyScreenAreaFraction() {
        Vector4f bodyBounds =
                DarkBallCaptureVfx.getCompositeBodyUvBounds();
        if (!Float.isFinite(bodyBounds.x)
                || !Float.isFinite(bodyBounds.y)
                || !Float.isFinite(bodyBounds.z)
                || !Float.isFinite(bodyBounds.w)
                || bodyBounds.z <= bodyBounds.x
                || bodyBounds.w <= bodyBounds.y) {
            return 0.0f;
        }
        float minimumU = Math.max(0.0f, Math.min(1.0f, bodyBounds.x));
        float minimumV = Math.max(0.0f, Math.min(1.0f, bodyBounds.y));
        float maximumU = Math.max(0.0f, Math.min(1.0f, bodyBounds.z));
        float maximumV = Math.max(0.0f, Math.min(1.0f, bodyBounds.w));
        return Math.max(maximumU - minimumU, 0.0f)
                * Math.max(maximumV - minimumV, 0.0f);
    }

    static int getSceneDepthTextureId() {
        return PIPELINE.getDensityDepthTextureId();
    }

    static int getTargetWidth() {
        return PIPELINE.getTargetWidth();
    }

    static int getTargetHeight() {
        return PIPELINE.getTargetHeight();
    }

    static void markBodyMaterialRendered(
            DarkBallProjectedEffectBounds.UvBounds geometryBounds) {
        markBodyMaterialRendered(geometryBounds, true);
    }

    static void markBodyMaterialRendered(
            DarkBallProjectedEffectBounds.UvBounds geometryBounds,
            boolean includeExactBodyBounds) {
        bodyMaterialRendered = true;
        bodyMaterialExactBodyBoundsRequired = includeExactBodyBounds;
        bodyMaterialGeometryBounds = geometryBounds == null
                ? DarkBallProjectedEffectBounds.UvBounds.fullScreen()
                : geometryBounds;
    }

    private static void applyMaterialResolveUniforms(ShaderInstance shader) {
        int maskTexture = PIPELINE.getMaskTextureId();
        RenderSystem.setShaderTexture(1, maskTexture);
        shader.setSampler("MaskSampler", maskTexture);
        int proxyFrontTexture = PIPELINE.getProxyFrontTextureId();
        RenderSystem.setShaderTexture(2, proxyFrontTexture);
        shader.setSampler("ProxyFrontSampler", proxyFrontTexture);
        Uniform proxyAvailable = shader.getUniform("ProxyFrontAvailable");
        if (proxyAvailable != null) {
            proxyAvailable.set(proxyFrontTexture != 0
                    && DarkBallCaptureVfx.getCompositeProxyDepthAvailable() > 0.5f ? 1 : 0);
        }
        Uniform inverseProjection = shader.getUniform("InvProjMat");
        if (inverseProjection != null) {
            inverseProjection.set(DarkBallCaptureVfx.getCompositeInvProjection());
        }
        Uniform bodyAvailable = shader.getUniform("BodyMaterialAvailable");
        if (bodyAvailable != null) bodyAvailable.set(bodyMaterialRendered ? 1 : 0);
    }

    private static void applyBodyMaterialCompositeUniforms(ShaderInstance shader) {
        applyAnimeUniforms(shader);
        int maskTexture = PIPELINE.getMaskTextureId();
        if (maskTexture != 0) {
            RenderSystem.setShaderTexture(1, maskTexture);
            shader.setSampler("MaskSampler", maskTexture);
        }
        int silhouetteDistanceTexture =
                PIPELINE.getSilhouetteDistanceTextureId();
        boolean distanceFieldAvailable = silhouetteDistanceRendered
                && silhouetteDistanceTexture != 0;
        if (distanceFieldAvailable) {
            RenderSystem.setShaderTexture(2, silhouetteDistanceTexture);
            shader.setSampler(
                    "DistanceSampler", silhouetteDistanceTexture);
        }
        Uniform distanceFieldAvailableUniform =
                shader.getUniform("DistanceFieldAvailable");
        if (distanceFieldAvailableUniform != null) {
            distanceFieldAvailableUniform.set(
                    distanceFieldAvailable ? 1 : 0);
        }
        Uniform deformationBlend = shader.getUniform("DeformationBlend");
        if (deformationBlend != null) {
            deformationBlend.set(DarkBallCaptureVfx.getCompositeDeformationBlend());
        }
        Uniform bodyGrayTransitionFraction =
                shader.getUniform("BodyGrayTransitionFraction");
        if (bodyGrayTransitionFraction != null) {
            bodyGrayTransitionFraction.set(
                    CLEAN_BODY_GRAY_TRANSITION_FRACTION);
        }
        Uniform gameTime = shader.getUniform("GameTime");
        if (gameTime != null) {
            Minecraft minecraft = Minecraft.getInstance();
            float seconds = minecraft.level == null ? 0.0f
                    : (minecraft.level.getGameTime()
                    + minecraft.getTimer().getGameTimeDeltaPartialTick(true)) * 0.05f;
            gameTime.set(seconds);
        }
        Vector4f bodyBounds = DarkBallCaptureVfx.getCompositeBodyUvBounds();
        DarkBallProjectedEffectBounds.UvBounds projectedBodyBounds =
                DarkBallProjectedEffectBounds.UvBounds.conservative(
                        bodyBounds.x, bodyBounds.y,
                        bodyBounds.z, bodyBounds.w);
        Uniform projectedBodyRadius =
                shader.getUniform("ProjectedBodyRadiusPixels");
        if (projectedBodyRadius != null) {
            projectedBodyRadius.set(
                    projectedBodyBounds.inscribedRadiusPixels(
                            PIPELINE.getTargetWidth(),
                            PIPELINE.getTargetHeight()));
        }
    }

    private static void applyReducedCompositeUpsampleUniforms(
            ShaderInstance shader) {
        int sceneDepthTexture =
                PIPELINE.getDensityDepthTextureId();
        if (sceneDepthTexture != 0) {
            RenderSystem.setShaderTexture(
                    1, sceneDepthTexture);
            shader.setSampler(
                    "SceneDepthSampler", sceneDepthTexture);
        }
        Uniform inverseProjection = shader.getUniform("InvProjMat");
        if (inverseProjection != null) {
            inverseProjection.set(
                    DarkBallCaptureVfx.getCompositeInvProjection());
        }
    }

    private static void applyAnimeUniforms(ShaderInstance shader) {
        Uniform energyCoverage = shader.getUniform("AnimeEnergyCoverage");
        if (energyCoverage != null) energyCoverage.set(DarkBallCaptureVfx.isCompositeAnimeEnergyCoverage() ? 1 : 0);
        Uniform pulse = shader.getUniform("AnimeIntakePulse");
        if (pulse != null) pulse.set(DarkBallCaptureVfx.getCompositeAnimeIntakePulse());
        Uniform flow = shader.getUniform("AnimeFlowDirection");
        if (flow != null) {
            Vector2f direction = new Vector2f(DarkBallCaptureVfx.getCompositeAnimeFlowDirection());
            direction.mul(PIPELINE.getTargetWidth(), PIPELINE.getTargetHeight());
            if (!Float.isFinite(direction.x) || !Float.isFinite(direction.y)
                    || direction.lengthSquared() < 1.0e-8f) direction.set(0, 1);
            else direction.normalize();
            flow.set(direction.x, direction.y);
        }
    }

    public static void destroy() {
        PIPELINE.destroy();
        bodyMaterialRendered = false;
        bodyDepthSnapshotReady = false;
        bodyMaterialExactBodyBoundsRequired = false;
        reducedCompositeRequested = false;
        reducedCompositeFailureLogged = false;
        silhouetteDistanceRendered = false;
        silhouetteDistanceSupported = true;
        silhouetteDistanceFailureLogged = false;
        bodyMaterialGeometryBounds =
                DarkBallProjectedEffectBounds.UvBounds.fullScreen();
    }
}
