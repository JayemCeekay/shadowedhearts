package com.jayemceekay.shadowedhearts.client.ball;

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
    private static boolean directVolumeRendered;
    private static DarkBallProjectedEffectBounds.UvBounds
            directVolumeGeometryBounds =
            DarkBallProjectedEffectBounds.UvBounds.fullScreen();

    private DarkBallDensityFBO() {
    }

    public static boolean beginDensityPass() {
        return beginDensityPass(true, true);
    }

    public static boolean beginDensityPass(boolean clearTarget, boolean copyMainDepth) {
        DarkBallVfxQuality quality = DarkBallVfxQuality.parse(
                com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                        .getInstance().getClientConfig().darkBallVfxQuality());
        // The direct SDF silhouette needs pixel-accurate boundary bands.
        // Preserve half resolution only for the explicitly low-cost profile.
        PIPELINE.setResolutionDivisor(quality == DarkBallVfxQuality.LOW ? 2 : 1);
        boolean ready = PIPELINE.beginDensityPass(clearTarget, copyMainDepth);
        if (!ready) {
            return false;
        }
        if (clearTarget) {
            directVolumeRendered = false;
            directVolumeGeometryBounds =
                    DarkBallProjectedEffectBounds.UvBounds.fullScreen();
        }
        // Preserve an untouched depth copy while the direct fullscreen pass
        // writes color into the density target.
        PIPELINE.beginProxyDepthPass(false, true, true);
        PIPELINE.resumeDensityPass();
        return true;
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    public static boolean beginMaskPass(boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginMaskPass(clearTarget, copyMainDepth);
    }

    public static boolean beginProxyDepthPass(boolean backFaces, boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginProxyDepthPass(backFaces, clearTarget, copyMainDepth);
    }

    /**
     * Restores the proxy-front depth attachment to pristine world-scene depth
     * immediately before a direct fullscreen volume pass.
     *
     * <p>The captured Pokemon proxy writes its own surface depth into this
     * attachment while building the reconstruction inputs. Without this
     * depth-only refresh, that invisible static mesh truncates the deformed SDF
     * raymarch. The proxy color attachment is deliberately not cleared: its R
     * channel still contains the captured front-surface depth for the exact
     * core resolve and depth-based surface lighting.
     */
    static void refreshSceneDepthForDirectVolume() {
        if (PIPELINE.beginProxyDepthPass(false, false, true)) {
            PIPELINE.resumeDensityPass();
        }
    }

    public static void resumeDensityPass() {
        PIPELINE.resumeDensityPass();
    }

    public static boolean composite() {
        if (!DarkBallCaptureVfx.isCompositeExactMaskReady()) {
            return false;
        }
        int debugMode = DarkBallAdvectedDensityRenderer.deformationDebugMode();
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
                        : DarkBallProjectedEffectBounds.UvBounds.fullScreen();
        if (directVolumeRendered) {
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
        return PIPELINE.composite(ModShaders.DARK_BALL_VOLUME_COMPOSITE,
                DarkBallDensityFBO::applyDirectVolumeCompositeUniforms,
                useEdgeTongueMaterial,
                compositeBounds.minU(), compositeBounds.minV(),
                compositeBounds.maxU(), compositeBounds.maxV());
    }

    static int getSceneDepthTextureId() {
        return PIPELINE.getProxyFrontDepthTextureId();
    }

    static int getTargetWidth() {
        return PIPELINE.getTargetWidth();
    }

    static int getTargetHeight() {
        return PIPELINE.getTargetHeight();
    }

    static void markDirectVolumeRendered(
            DarkBallProjectedEffectBounds.UvBounds geometryBounds) {
        directVolumeRendered = true;
        directVolumeGeometryBounds = geometryBounds == null
                ? DarkBallProjectedEffectBounds.UvBounds.fullScreen()
                : geometryBounds;
    }

    private static void applyEdgeTongueUniforms(ShaderInstance shader) {
        int sceneDepthTexture = PIPELINE.getProxyFrontDepthTextureId();
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
            deformationDebugMode.set(DarkBallAdvectedDensityRenderer.deformationDebugMode());
        }
    }

    private static void applyDirectVolumeCompositeUniforms(ShaderInstance shader) {
        int maskTexture = PIPELINE.getMaskTextureId();
        if (maskTexture != 0) {
            RenderSystem.setShaderTexture(1, maskTexture);
            shader.setSampler("MaskSampler", maskTexture);
        }
        Uniform deformationBlend = shader.getUniform("DeformationBlend");
        if (deformationBlend != null) {
            deformationBlend.set(DarkBallCaptureVfx.getCompositeDeformationBlend());
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
            deformationDebugMode.set(DarkBallAdvectedDensityRenderer.deformationDebugMode());
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

    public static void destroy() {
        PIPELINE.destroy();
        directVolumeRendered = false;
        directVolumeGeometryBounds =
                DarkBallProjectedEffectBounds.UvBounds.fullScreen();
    }
}
