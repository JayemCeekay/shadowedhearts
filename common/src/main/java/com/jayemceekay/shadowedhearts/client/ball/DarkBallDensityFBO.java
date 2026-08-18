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

/**
 * Offscreen framebuffer wrapper for Dark Ball cohesive volume siphoning.
 */
public final class DarkBallDensityFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(1, 0.95f);
    private static final boolean ENABLE_EDGE_TONGUES = false;
    private static final boolean ENABLE_REDUCED_COMPOSITE =
            Boolean.parseBoolean(System.getProperty(
                    "shadowedhearts.darkBallReducedComposite", "true"));
    private static final float MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD =
            readMediumReducedCompositeAreaThreshold();
    private static final int SURFACE_SPLAT_RESOLVE_PADDING_PIXELS = 3;
    private static final float SURFACE_SPLAT_ACCUMULATION_GAIN = 1.60f;
    private static final float CLEAN_BODY_GRAY_TRANSITION_FRACTION = 0.10f;
    private static final float MAX_SILHOUETTE_DISTANCE_OUTPUT_PIXELS = 104.0f;
    private static boolean directVolumeRendered;
    private static boolean directVolumeExactBodyBoundsRequired;
    private static boolean reducedCompositeRequested;
    private static boolean reducedCompositeFailureLogged;
    private static boolean surfaceSplatDiagnosticRendered;
    private static boolean surfaceSplatFrontDepthRendered;
    private static boolean silhouetteDistanceRendered;
    private static boolean silhouetteDistanceSupported = true;
    private static boolean silhouetteDistanceFailureLogged;
    private static float projectedBodyAreaFraction;
    private static String reducedCompositePolicy = "direct";
    private static long previewFrameSerial;
    private static PreviewFrame previewFrame;
    private static DarkBallProjectedEffectBounds.UvBounds
            directVolumeGeometryBounds =
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
        previewFrame = null;
        previewFrameSerial++;
        DarkBallVfxQuality quality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality());
        // Raw material/depth stays full resolution on MEDIUM/HIGH. LOW keeps
        // its established half-resolution source; the independent styled
        // target may also engage for a large MEDIUM screen footprint.
        PIPELINE.setResolutionDivisor(quality == DarkBallVfxQuality.LOW ? 2 : 1);
        projectedBodyAreaFraction =
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
                ENABLE_REDUCED_COMPOSITE
                        && (quality == DarkBallVfxQuality.LOW
                        || mediumLargeScreenEffect)
                        && ModShaders.DARK_BALL_REDUCED_UPSAMPLE != null
                        && DarkBallDeformationSettings.debugMode() == 0;
        reducedCompositePolicy = reducedCompositeRequested
                ? (quality == DarkBallVfxQuality.LOW
                ? "low" : "medium-large")
                : "direct";
        PIPELINE.setReducedCompositeEnabled(
                reducedCompositeRequested);
        PIPELINE.setSplatDiagnosticEnabled(
                DarkBallFboDebugPreview.isEnabled());
        PIPELINE.setSurfaceSplatFrontDepthEnabled(true);
        PIPELINE.setSilhouetteDistanceEnabled(
                silhouetteDistanceSupported
                        && DarkBallDeformationSettings.debugMode() == 0);
        surfaceSplatDiagnosticRendered = false;
        surfaceSplatFrontDepthRendered = false;
        silhouetteDistanceRendered = false;
        boolean ready = PIPELINE.beginDensityPass(clearTarget, copyMainDepth);
        if (!ready) {
            return false;
        }
        if (reducedCompositeRequested
                && !PIPELINE.isReducedCompositeReady()) {
            reducedCompositeRequested = false;
            reducedCompositePolicy = "direct-fallback";
        }
        if (clearTarget) {
            directVolumeRendered = false;
            directVolumeExactBodyBoundsRequired = false;
            directVolumeGeometryBounds =
                    DarkBallProjectedEffectBounds.UvBounds.fullScreen();
        }
        return true;
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    /**
     * Restores pristine scene depth after the surfel/siphon direct draw while
     * preserving material written to the density color attachment.
     */
    static boolean restoreSceneDepthAfterDirectDraw() {
        return PIPELINE.restoreSceneDepthAfterDirectDraw();
    }

    /**
     * Clears a partial surfel/siphon submission before exact-mask recovery.
     */
    static boolean resetDensityPassAfterFailedDirectDraw() {
        return PIPELINE.resetDensityPassAfterFailedDirectDraw();
    }

    /**
     * Fuses the additive surface-splat accumulation into the established
     * direct-material payload before any siphon material is drawn.
     *
     * <p>The first bounded pass filters horizontally into the reusable
     * temporary target. The second filters vertically while decoding coverage,
     * remaining mass, and signed depth back into the density target. This
     * ordering prevents the later composite from styling each splat footprint
     * as an independent ball.</p>
     */
    static boolean resolveSurfaceSplatField(
            DarkBallProjectedEffectBounds.UvBounds bodyBounds) {
        ShaderInstance shader =
                ModShaders.DARK_BALL_SURFACE_SPLAT_RESOLVE;
        if (shader == null
                || PIPELINE.getTargetWidth() <= 0
                || PIPELINE.getTargetHeight() <= 0) {
            return false;
        }

        DarkBallProjectedEffectBounds.UvBounds resolveBounds =
                (bodyBounds == null
                        ? DarkBallProjectedEffectBounds.UvBounds.fullScreen()
                        : bodyBounds)
                        .expandByPixels(
                                SURFACE_SPLAT_RESOLVE_PADDING_PIXELS,
                                PIPELINE.getTargetWidth(),
                                PIPELINE.getTargetHeight());
        float texelWidth = 1.0f / PIPELINE.getTargetWidth();
        float texelHeight = 1.0f / PIPELINE.getTargetHeight();

        boolean horizontalResolved = false;
        boolean verticalResolved = false;
        try {
            horizontalResolved = PIPELINE.processDensityToTemp(
                    shader,
                    configuredShader -> applySurfaceSplatResolveUniforms(
                            configuredShader,
                            texelWidth,
                            0.0f,
                            0),
                    resolveBounds.minU(),
                    resolveBounds.minV(),
                    resolveBounds.maxU(),
                    resolveBounds.maxV());
            if (!horizontalResolved) {
                return false;
            }

            verticalResolved = PIPELINE.processTempToDensity(
                    shader,
                    configuredShader -> applySurfaceSplatResolveUniforms(
                            configuredShader,
                            0.0f,
                            texelHeight,
                            DarkBallDeformationSettings
                                    .spikeIndentAmplitudeDebugEnabled()
                                    ? 2 : 1),
                    resolveBounds.minU(),
                    resolveBounds.minV(),
                    resolveBounds.maxU(),
                    resolveBounds.maxV());
            return verticalResolved;
        } finally {
            // Both fullscreen helpers restore GL state, but explicitly resume
            // the density target so the following siphon draw cannot inherit
            // an auxiliary framebuffer after an early return or exception.
            PIPELINE.resumeDensityPass();
        }
    }

    private static void applySurfaceSplatResolveUniforms(
            ShaderInstance shader,
            float directionX,
            float directionY,
            int resolveMode) {
        Uniform direction = shader.getUniform("Direction");
        if (direction != null) {
            direction.set(directionX, directionY);
        }
        Uniform mode = shader.getUniform("ResolveMode");
        if (mode != null) {
            mode.set(resolveMode);
        }
        Uniform gain = shader.getUniform("AccumulationGain");
        if (gain != null) {
            gain.set(SURFACE_SPLAT_ACCUMULATION_GAIN);
        }
    }

    public static boolean beginMaskPass(boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginMaskPass(clearTarget, copyMainDepth);
    }

    public static boolean beginProxyDepthPass(boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginProxyDepthPass(clearTarget, copyMainDepth);
    }

    static boolean beginSurfaceSplatFrontDepthPass() {
        surfaceSplatFrontDepthRendered = false;
        return PIPELINE.beginSurfaceSplatFrontDepthPass(true, true);
    }

    static void markSurfaceSplatFrontDepthRendered() {
        surfaceSplatFrontDepthRendered = true;
    }

    static boolean beginSurfaceSplatDiagnosticPass() {
        surfaceSplatDiagnosticRendered = false;
        return DarkBallFboDebugPreview.isEnabled()
                && PIPELINE.beginSplatDiagnosticPass(true);
    }

    static void markSurfaceSplatDiagnosticRendered() {
        surfaceSplatDiagnosticRendered = true;
    }

    public static void resumeDensityPass() {
        PIPELINE.resumeDensityPass();
    }

    public static boolean composite() {
        if (!DarkBallCaptureVfx.isCompositeExactMaskReady()) {
            return false;
        }
        int debugMode = DarkBallDeformationSettings.debugMode();
        // Raw field diagnostics intentionally bypass the exact-mask material.
        // Until a real volume draw exists they have no meaningful source, so do
        // not publish replacement readiness and hide the live Pokemon for an
        // empty debug target.
        if (debugMode != 0 && !directVolumeRendered) {
            return false;
        }
        boolean useEdgeTongueMaterial = false;
        DarkBallProjectedEffectBounds.UvBounds geometryBounds =
                directVolumeRendered
                        ? directVolumeGeometryBounds
                        : exactBodyGeometryBounds()
                        .expandByPixels(
                                DarkBallProjectedEffectBounds
                                        .DIRECT_PADDING_PIXELS,
                                PIPELINE.getTargetWidth(),
                                PIPELINE.getTargetHeight());
        if (directVolumeRendered
                && directVolumeExactBodyBoundsRequired) {
            // The edge resolve can restore texture-exact mask pixels that are
            // thinner than the analytical volume atlas. Union the captured
            // model's projected AABB so those extremities are not excluded
            // merely because the raw volume shader produced zero there.
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
        // The dormant tongue bank can search far beyond the current local
        // contour. If it is re-enabled, retain full-screen processing until it
        // receives a separately bounded maximum reach.
        if (ENABLE_EDGE_TONGUES) {
            edgeBounds =
                    DarkBallProjectedEffectBounds.UvBounds.fullScreen();
        }
        if (debugMode == 0) {
            // This pass also resolves the exact texture-aware model core, so it
            // must run before turbulence begins even when no tongue is active.
            useEdgeTongueMaterial = PIPELINE.processDensityToTemp(
                    ModShaders.DARK_BALL_EDGE_TONGUES,
                    DarkBallDensityFBO::applyEdgeTongueUniforms,
                    edgeBounds.minU(), edgeBounds.minV(),
                    edgeBounds.maxU(), edgeBounds.maxV());
            if (!useEdgeTongueMaterial) {
                return false;
            }
        }
        DarkBallProjectedEffectBounds.UvBounds compositeBounds =
                (useEdgeTongueMaterial ? edgeBounds : geometryBounds)
                        .expandByPixels(
                                DarkBallProjectedEffectBounds
                                        .COMPOSITE_PADDING_PIXELS,
                                PIPELINE.getTargetWidth(),
                                PIPELINE.getTargetHeight());
        if (debugMode == 0 && useEdgeTongueMaterial
                && silhouetteDistanceSupported) {
            try {
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
        boolean reducedCompositeUsed = false;
        boolean useReducedComposite =
                reducedCompositeRequested
                        && debugMode == 0
                        && useEdgeTongueMaterial;
        if (useReducedComposite) {
            try {
                boolean reducedResolved =
                        PIPELINE.processProcessedToReducedComposite(
                                ModShaders.DARK_BALL_VOLUME_COMPOSITE,
                                DarkBallDensityFBO
                                        ::applyDirectVolumeCompositeUniforms,
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
                    reducedCompositeUsed = compositeRendered;
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
            // Medium/High, deformation diagnostics, disabled optimization,
            // or any unavailable/failed reduced target retain the established
            // full-output resolve in the same frame.
            compositeRendered = PIPELINE.composite(
                    ModShaders.DARK_BALL_VOLUME_COMPOSITE,
                    DarkBallDensityFBO
                            ::applyDirectVolumeCompositeUniforms,
                    useEdgeTongueMaterial,
                    compositeBounds.minU(),
                    compositeBounds.minV(),
                    compositeBounds.maxU(),
                    compositeBounds.maxV());
        }
        if (compositeRendered && DarkBallFboDebugPreview.isEnabled()) {
            DarkBallProjectedEffectBounds.UvBounds previewEffectBounds =
                    compositeBounds;
            if (!directVolumeRendered) {
                previewEffectBounds =
                        exactBodyGeometryBounds()
                                .expandByPixels(
                                        DarkBallProjectedEffectBounds
                                                .COMPOSITE_PADDING_PIXELS,
                                        PIPELINE.getTargetWidth(),
                                        PIPELINE.getTargetHeight());
            }
            previewFrame = new PreviewFrame(
                    previewFrameSerial,
                    PIPELINE.getTargetGeneration(),
                    PIPELINE.getDensityTextureId(),
                    PIPELINE.getProcessedTextureId(),
                    PIPELINE.getMaskTextureId(),
                    PIPELINE.getProxyFrontTextureId(),
                    getSceneDepthTextureId(),
                    PIPELINE.getSplatDiagnosticTextureId(),
                    surfaceSplatDiagnosticRendered,
                    DarkBallCaptureVfx
                            .getPreviewBodyTransportTextureId(),
                    DarkBallCaptureVfx
                            .getPreviewSiphonTransportTextureId(),
                    PIPELINE.getTargetWidth(),
                    PIPELINE.getTargetHeight(),
                    useEdgeTongueMaterial,
                    DarkBallCaptureVfx.getCompositeProxyDepthAvailable()
                            > 0.5f,
                    debugMode != 0,
                    reducedCompositeUsed,
                    reducedCompositePolicy,
                    projectedBodyAreaFraction,
                    MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD,
                    previewEffectBounds.minU(),
                    previewEffectBounds.minV(),
                    previewEffectBounds.maxU(),
                    previewEffectBounds.maxV());
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

    private static float readMediumReducedCompositeAreaThreshold() {
        String configured = System.getProperty(
                "shadowedhearts.darkBallReducedCompositeMediumArea",
                "0.22");
        try {
            float parsed = Float.parseFloat(configured);
            if (Float.isFinite(parsed)) {
                return Math.max(0.05f, Math.min(0.95f, parsed));
            }
        } catch (NumberFormatException ignored) {
            // Retain the conservative default below.
        }
        return 0.22f;
    }

    /**
     * Returns only a post-composite, current-frame view of the owned
     * attachments. Texture ids must never be cached beyond the supplied target
     * generation.
     */
    static PreviewFrame previewFrame() {
        return previewFrame;
    }

    static boolean isPreviewFrameCurrent(PreviewFrame candidate) {
        return candidate != null
                && candidate == previewFrame
                && candidate.targetGeneration()
                == PIPELINE.getTargetGeneration();
    }

    record PreviewFrame(
            long frameSerial,
            long targetGeneration,
            int rawMaterialTexture,
            int resolvedMaterialTexture,
            int exactMaskTexture,
            int proxyFrontTexture,
            int sceneDepthTexture,
            int splatDiagnosticTexture,
            boolean splatDiagnosticValid,
            int bodyTransportTexture,
            int siphonTransportTexture,
            int width,
            int height,
            boolean resolvedMaterialValid,
            boolean proxyFrontValid,
            boolean rawCompositeSource,
            boolean reducedCompositeUsed,
            String reducedCompositePolicy,
            float projectedBodyAreaFraction,
            float mediumReducedCompositeAreaThreshold,
            float effectMinU,
            float effectMinV,
            float effectMaxU,
            float effectMaxV) {

        boolean isUsable() {
            return rawMaterialTexture != 0
                    && exactMaskTexture != 0
                    && sceneDepthTexture != 0
                    && width > 0
                    && height > 0;
        }
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

    static void markDirectVolumeRendered(
            DarkBallProjectedEffectBounds.UvBounds geometryBounds) {
        markDirectVolumeRendered(geometryBounds, true);
    }

    static void markDirectVolumeRendered(
            DarkBallProjectedEffectBounds.UvBounds geometryBounds,
            boolean includeExactBodyBounds) {
        directVolumeRendered = true;
        directVolumeExactBodyBoundsRequired = includeExactBodyBounds;
        directVolumeGeometryBounds = geometryBounds == null
                ? DarkBallProjectedEffectBounds.UvBounds.fullScreen()
                : geometryBounds;
    }

    private static void applyEdgeTongueUniforms(ShaderInstance shader) {
        int sceneDepthTexture = PIPELINE.getDensityDepthTextureId();
        if (sceneDepthTexture != 0) {
            RenderSystem.setShaderTexture(1, sceneDepthTexture);
            shader.setSampler("SceneDepthSampler", sceneDepthTexture);
        }

        int maskTexture = PIPELINE.getMaskTextureId();
        if (maskTexture != 0) {
            RenderSystem.setShaderTexture(2, maskTexture);
            shader.setSampler("MaskSampler", maskTexture);
        }

        int proxyFrontTexture = PIPELINE.getProxyFrontTextureId();
        if (proxyFrontTexture != 0) {
            RenderSystem.setShaderTexture(3, proxyFrontTexture);
            shader.setSampler("ProxyFrontSampler", proxyFrontTexture);
        }

        int surfaceSplatFrontDepthTexture =
                PIPELINE.getSurfaceSplatFrontDepthTextureId();
        if (surfaceSplatFrontDepthTexture != 0) {
            RenderSystem.setShaderTexture(4, surfaceSplatFrontDepthTexture);
            shader.setSampler(
                    "SurfelFrontDepthSampler",
                    surfaceSplatFrontDepthTexture);
        }

        Uniform sceneDepthAvailable = shader.getUniform("SceneDepthAvailable");
        if (sceneDepthAvailable != null) {
            sceneDepthAvailable.set(sceneDepthTexture != 0 ? 1 : 0);
        }
        Uniform proxyFrontAvailable = shader.getUniform("ProxyFrontAvailable");
        if (proxyFrontAvailable != null) {
            proxyFrontAvailable.set(proxyFrontTexture != 0
                    && DarkBallCaptureVfx.getCompositeProxyDepthAvailable() > 0.5f
                    ? 1 : 0);
        }
        Uniform surfaceSplatFrontDepthAvailable =
                shader.getUniform("SurfelFrontDepthAvailable");
        if (surfaceSplatFrontDepthAvailable != null) {
            surfaceSplatFrontDepthAvailable.set(
                    surfaceSplatFrontDepthTexture != 0
                            && surfaceSplatFrontDepthRendered
                            ? 1
                            : 0);
        }
        Uniform inverseProjection = shader.getUniform("InvProjMat");
        if (inverseProjection != null) {
            inverseProjection.set(DarkBallCaptureVfx.getCompositeInvProjection());
        }
        Uniform turbulenceBlend = shader.getUniform("TurbulenceBlend");
        if (turbulenceBlend != null) {
            turbulenceBlend.set(
                    DarkBallCaptureVfx.getCompositeTurbulenceBlend());
        }
        Uniform signedBodyAuthority =
                shader.getUniform("SignedBodyAuthority");
        if (signedBodyAuthority != null) {
            signedBodyAuthority.set(
                    DarkBallCaptureVfx.getCompositeSignedBodyAuthority());
        }
        Uniform preCollapseInteriorGuard =
                shader.getUniform("PreCollapseInteriorGuard");
        if (preCollapseInteriorGuard != null) {
            preCollapseInteriorGuard.set(
                    DarkBallCaptureVfx
                            .getCompositePreCollapseInteriorGuard());
        }
        Uniform edgeTonguesEnabled = shader.getUniform("EdgeTonguesEnabled");
        if (edgeTonguesEnabled != null) {
            edgeTonguesEnabled.set(ENABLE_EDGE_TONGUES ? 1 : 0);
        }
        Uniform bodyVolumeAvailable = shader.getUniform("BodyVolumeAvailable");
        if (bodyVolumeAvailable != null) {
            bodyVolumeAvailable.set(directVolumeRendered ? 1 : 0);
        }
        Uniform siphonProgress = shader.getUniform("SiphonProgress");
        if (siphonProgress != null) {
            siphonProgress.set(DarkBallCaptureVfx.getCompositeSiphonProgress());
        }
        Uniform deformationTime = shader.getUniform("DeformationTime");
        if (deformationTime != null) {
            deformationTime.set(DarkBallCaptureVfx.getCompositeDeformationTime());
        }

        Vector2f projectedUp = DarkBallCaptureVfx.getCompositeProjectedUp();
        Uniform projectedUpUniform = shader.getUniform("ProjectedUp");
        if (projectedUpUniform != null) {
            projectedUpUniform.set(projectedUp.x, projectedUp.y);
        }
        Vector4f bodyBounds = DarkBallCaptureVfx.getCompositeBodyUvBounds();
        Uniform bodyBoundsUniform = shader.getUniform("BodyUvBounds");
        if (bodyBoundsUniform != null) {
            bodyBoundsUniform.set(bodyBounds.x, bodyBounds.y,
                    bodyBounds.z, bodyBounds.w);
        }
        Uniform ballUv = shader.getUniform("BallUv");
        if (ballUv != null) {
            ballUv.set(DarkBallCaptureVfx.getCompositeBallU(),
                    DarkBallCaptureVfx.getCompositeBallV());
        }
        Uniform surfaceDepthScale = shader.getUniform("SurfaceDepthScale");
        if (surfaceDepthScale != null) {
            surfaceDepthScale.set(Math.max(
                    DarkBallCaptureVfx.getCompositeVolumeSize().y(), 0.05f));
        }
        Uniform deformationDebugMode = shader.getUniform("DeformationDebugMode");
        if (deformationDebugMode != null) {
            deformationDebugMode.set(DarkBallDeformationSettings.debugMode());
        }
    }

    private static void applyDirectVolumeCompositeUniforms(ShaderInstance shader) {
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
        Uniform depthHighlightBlend =
                shader.getUniform("DepthHighlightBlend");
        if (depthHighlightBlend != null) {
            depthHighlightBlend.set(
                    DarkBallCaptureVfx.getCompositeDepthHighlightBlend());
        }
        Uniform bodyGrayTransitionFraction =
                shader.getUniform("BodyGrayTransitionFraction");
        if (bodyGrayTransitionFraction != null) {
            bodyGrayTransitionFraction.set(
                    CLEAN_BODY_GRAY_TRANSITION_FRACTION);
        }
        Uniform bodyContourSheenStrength =
                shader.getUniform("BodyContourSheenStrength");
        if (bodyContourSheenStrength != null) {
            bodyContourSheenStrength.set(
                    com.jayemceekay.shadowedhearts.config
                            .ShadowedHeartsConfigs
                            .getInstance()
                            .getClientConfig()
                            .darkBallBodyContourSheenStrength());
        }
        Uniform bodyDepthSpecularStrength =
                shader.getUniform("BodyDepthSpecularStrength");
        if (bodyDepthSpecularStrength != null) {
            bodyDepthSpecularStrength.set(
                    com.jayemceekay.shadowedhearts.config
                            .ShadowedHeartsConfigs
                            .getInstance()
                            .getClientConfig()
                            .darkBallBodyDepthSpecularStrength());
        }
        Uniform gameTime = shader.getUniform("GameTime");
        if (gameTime != null) {
            Minecraft minecraft = Minecraft.getInstance();
            float seconds = minecraft.level == null ? 0.0f
                    : (minecraft.level.getGameTime()
                    + minecraft.getTimer().getGameTimeDeltaPartialTick(true)) * 0.05f;
            gameTime.set(seconds);
        }
        Uniform deformationDebugMode = shader.getUniform("DeformationDebugMode");
        if (deformationDebugMode != null) {
            deformationDebugMode.set(DarkBallDeformationSettings.debugMode());
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
        Uniform inverseProjection = shader.getUniform("InvProjMat");
        if (inverseProjection != null) {
            inverseProjection.set(DarkBallCaptureVfx.getCompositeInvProjection());
        }
        Uniform surfaceDepthAvailable = shader.getUniform("SurfaceDepthAvailable");
        if (surfaceDepthAvailable != null) {
            surfaceDepthAvailable.set(1);
        }
        Uniform surfaceDepthScale = shader.getUniform("SurfaceDepthScale");
        if (surfaceDepthScale != null) {
            surfaceDepthScale.set(Math.max(
                    DarkBallCaptureVfx.getCompositeVolumeSize().y(), 0.05f));
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

    public static void destroy() {
        PIPELINE.destroy();
        DarkBallFboDebugPreview.destroy();
        previewFrame = null;
        previewFrameSerial++;
        directVolumeRendered = false;
        directVolumeExactBodyBoundsRequired = false;
        reducedCompositeRequested = false;
        reducedCompositeFailureLogged = false;
        silhouetteDistanceRendered = false;
        silhouetteDistanceSupported = true;
        silhouetteDistanceFailureLogged = false;
        projectedBodyAreaFraction = 0.0f;
        reducedCompositePolicy = "direct";
        directVolumeGeometryBounds =
                DarkBallProjectedEffectBounds.UvBounds.fullScreen();
    }
}
