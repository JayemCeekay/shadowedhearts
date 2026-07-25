package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Split mobile-density simulation: a static body-space field feeds a moving
 * curvilinear siphon field. The recognizable body remains an anchored
 * reservoir in the presentation shader, preserving its exact depletion wave.
 */
final class DarkBallAdvectedDensityField {
    // Synchronous GPU texture readbacks are developer-only and default off.
    private static final boolean ENABLE_DENSITY_DIAGNOSTICS =
            Boolean.getBoolean("shadowedhearts.darkBallDensityDiagnostics");

    static final int X_SIZE = DarkBallAnalyticalVolume.X_SIZE;
    static final int Y_SIZE = DarkBallAnalyticalVolume.Y_SIZE;
    static final int Z_SIZE = DarkBallAnalyticalVolume.Z_SIZE;
    static final int ATLAS_WIDTH = X_SIZE * Z_SIZE;
    static final int ATLAS_HEIGHT = Y_SIZE;
    static final int SIPHON_X_SIZE = 48;
    static final int SIPHON_Y_SIZE = 12;
    static final int SIPHON_Z_SIZE = 12;
    static final int SIPHON_ATLAS_WIDTH = SIPHON_X_SIZE * SIPHON_Z_SIZE;
    static final int SIPHON_ATLAS_HEIGHT = SIPHON_Y_SIZE;

    private final DarkBallVolumeBuildResult volume;
    private final DarkBallAnalyticalVolume shape;
    private final DarkBallVfxQuality quality;
    private final Vector3f ballLocal = new Vector3f();
    private final Vector3f siphonRootLocal = new Vector3f();
    private final Vector3f siphonRootTangent = new Vector3f(1.0f, 0.0f, 0.0f);
    private final Vector3f[] siphonBoltNodes =
            new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
    private final int[] densityTextures = new int[2];
    private final int[] densityFramebuffers = new int[2];
    private final int[] siphonDensityTextures = new int[2];
    private final int[] siphonDensityFramebuffers = new int[2];

    private int readIndex;
    private int siphonReadIndex;
    private float accumulator;
    private float simulatedSiphon;
    private float simulatedCollapse;
    private float visualSiphon;
    private float visualCollapse;
    private boolean targetsReady;
    private boolean permanentlyUnsupported;
    private boolean failureLogged;
    private boolean densityDiagnosticsLogged;

    private DarkBallAdvectedDensityField(DarkBallVolumeBuildResult volume,
                                         DarkBallAnalyticalVolume shape,
                                         DarkBallVfxQuality quality) {
        this.volume = volume;
        this.shape = shape;
        this.quality = quality == null ? DarkBallVfxQuality.MEDIUM : quality;
    }

    static DarkBallAdvectedDensityField build(DarkBallVolumeBuildResult volume,
                                               DarkBallAnalyticalVolume shape,
                                               Vector3f initialBallLocal,
                                               DarkBallVfxQuality quality) {
        if (volume == null || shape == null || !isFinite(initialBallLocal)) {
            return null;
        }
        DarkBallAdvectedDensityField result = new DarkBallAdvectedDensityField(
                volume, shape, quality);
        result.ballLocal.set(initialBallLocal);
        result.initializeFrozenSiphonRoot();
        return result;
    }

    DarkBallVfxQuality quality() {
        return quality;
    }

    Vector3f outletLocal() {
        return shape.outletLocal();
    }

    Vector3f siphonRootLocal() {
        return new Vector3f(siphonRootLocal);
    }

    Vector3f siphonControlALocal() {
        float handleLength = Math.max(volume.bodyRadius() * 0.32f,
                siphonRootLocal.distance(ballLocal) * 0.34f);
        return new Vector3f(siphonRootLocal).fma(handleLength, siphonRootTangent);
    }

    Vector3f siphonControlBLocal() {
        return DarkBallCaptureMath.siphonCurveControlBLocal(
                siphonRootLocal, ballLocal, volume.bodyRadius());
    }

    void updateSiphonBoltPath(Vector3f controlA,
                              Vector3f controlB,
                              float captureTime) {
        DarkBallSiphonBoltPath.populate(
                siphonRootLocal, controlA, controlB, ballLocal,
                volume.bodyRadius(), captureTime, siphonBoltNodes);
    }

    Vector3f siphonBoltNode(int index) {
        if (index < 0 || index >= siphonBoltNodes.length
                || siphonBoltNodes[index] == null) {
            throw new IllegalArgumentException("invalid uninitialized bolt node "
                    + index);
        }
        return siphonBoltNodes[index];
    }

    float simulatedSiphon() {
        return simulatedSiphon;
    }

    float simulatedCollapse() {
        return simulatedCollapse;
    }

    float visualSiphon() {
        return visualSiphon;
    }

    float visualCollapse() {
        return visualCollapse;
    }

    boolean available() {
        return !permanentlyUnsupported;
    }

    int siphonDensityTextureId() {
        return targetsReady ? siphonDensityTextures[siphonReadIndex] : 0;
    }

    int bodyDensityTextureId() {
        return targetsReady ? densityTextures[readIndex] : 0;
    }

    boolean updateBallLocal(Vector3f value) {
        if (!isFinite(value) || permanentlyUnsupported) {
            return false;
        }
        ballLocal.set(value);
        return true;
    }

    /**
     * Advances toward the supplied visual phase. Progress is tracked from the
     * last completed GPU step, so a dropped catch-up step cannot skip reservoir
     * release; the next pass injects the full outstanding difference once.
     */
    boolean advance(float targetSiphon, float targetCollapse,
                    float destabilization, float age, float dt) {
        visualSiphon = Mth.clamp(targetSiphon, 0.0f, 1.0f);
        visualCollapse = Mth.clamp(targetCollapse, 0.0f, 1.0f);
        if (!ensureTargets() || ModShaders.DARK_BALL_DENSITY_ADVECT == null
                || ModShaders.DARK_BALL_SIPHON_ADVECT == null) {
            return false;
        }

        accumulator += Mth.clamp(dt, 0.0f, 0.12f);
        float step = quality.shellStepSeconds();
        int availableSteps = Mth.floor(accumulator / step);
        int maxSteps = quality == DarkBallVfxQuality.HIGH ? 3 : 2;
        int steps = Math.min(availableSteps, maxSteps);
        if (steps <= 0) {
            return true;
        }

        float startSiphon = simulatedSiphon;
        float startCollapse = simulatedCollapse;
        float clampedSiphon = visualSiphon;
        float clampedCollapse = visualCollapse;
        for (int i = 0; i < steps; i++) {
            float fraction = (i + 1.0f) / steps;
            float nextSiphon = Mth.lerp(fraction, startSiphon, clampedSiphon);
            float nextCollapse = Mth.lerp(fraction, startCollapse, clampedCollapse);
            if (!runAdvectionStep(simulatedSiphon, nextSiphon,
                    simulatedCollapse, nextCollapse,
                    Mth.clamp(destabilization, 0.0f, 1.0f), age, step)) {
                return false;
            }
            simulatedSiphon = nextSiphon;
            simulatedCollapse = nextCollapse;
            accumulator -= step;
        }

        if (accumulator >= step) {
            accumulator = step * 0.95f;
        }
        return true;
    }

    void destroy() {
        destroyTargets();
    }

    void disableForCapture(String message, Throwable failure) {
        permanentlyUnsupported = true;
        destroyTargets();
        logFailure(message, failure);
    }

    String summary() {
        return "grid=" + X_SIZE + "x" + Y_SIZE + "x" + Z_SIZE
                + ", atlas=" + ATLAS_WIDTH + "x" + ATLAS_HEIGHT
                + ", siphonGrid=" + SIPHON_X_SIZE + "x" + SIPHON_Y_SIZE + "x" + SIPHON_Z_SIZE
                + ", stepHz=" + Math.round(1.0f / quality.shellStepSeconds())
                + ", anchoredRelease=analytical-parity"
                + ", domain=static-body+moving-curve"
                + ", rootSdf=" + String.format(java.util.Locale.ROOT, "%.4f",
                shape.signedDistanceLocal(siphonRootLocal));
    }

    static float releaseRemaining(float releaseOrder, float siphonProgress, float finalCollapse) {
        if (siphonProgress <= 0.001f) {
            return 1.0f;
        }
        float front = Mth.clamp(siphonProgress * 1.16f - 0.045f + finalCollapse * 0.16f,
                0.0f, 1.30f);
        float width = 0.130f + (1.0f - Math.min(siphonProgress, 1.0f)) * 0.045f;
        return smoothstep(front - width, front + width, releaseOrder);
    }

    static float deformationAttachment(float releaseOrder,
                                       float siphonProgress,
                                       float finalCollapse) {
        float remaining = releaseRemaining(
                releaseOrder, siphonProgress, finalCollapse);
        return smoothstep(0.58f, 0.92f, remaining);
    }

    static float releasedFraction(float releaseOrder,
                                  float previousSiphon, float siphon,
                                  float previousCollapse, float collapse) {
        float previous = releaseRemaining(releaseOrder, previousSiphon, previousCollapse);
        float current = releaseRemaining(releaseOrder, siphon, collapse);
        return Math.max(0.0f, previous - current);
    }

    static int atlasTexelX(int x, int z) {
        return z * X_SIZE + x;
    }

    static int siphonAtlasTexelX(int x, int z) {
        return z * SIPHON_X_SIZE + x;
    }

    private void initializeFrozenSiphonRoot() {
        Vector3f outlet = shape.outletLocal();
        Vector3f towardInitialBall = new Vector3f(ballLocal).sub(outlet);
        if (towardInitialBall.lengthSquared() < 0.000001f) {
            towardInitialBall.set(1.0f, 0.0f, 0.0f);
        } else {
            towardInitialBall.normalize();
        }
        // Bury the throat far enough inside the source SDF that the rendered
        // body/siphon union has no separate cap sitting on top of the model.
        // Thin appendages still snap to a valid primary-component cell in
        // interiorSiphonRootLocal().
        siphonRootLocal.set(shape.interiorSiphonRootLocal(
                ballLocal, volume.bodyRadius() * 0.22f));
        Vector3f initialControl = DarkBallCaptureMath.siphonCurveControlALocal(
                siphonRootLocal, ballLocal, volume.bodyRadius());
        siphonRootTangent.set(initialControl).sub(siphonRootLocal);
        if (siphonRootTangent.lengthSquared() < 0.000001f) {
            siphonRootTangent.set(towardInitialBall);
        } else {
            siphonRootTangent.normalize();
        }
    }

    private static boolean isFinite(Vector3f value) {
        return value != null
                && Float.isFinite(value.x)
                && Float.isFinite(value.y)
                && Float.isFinite(value.z);
    }

    private boolean ensureTargets() {
        if (permanentlyUnsupported) {
            return false;
        }
        if (targetsReady) {
            return true;
        }

        FramebufferState previous = FramebufferState.capture();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            int maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            int requiredWidth = Math.max(ATLAS_WIDTH, SIPHON_ATLAS_WIDTH);
            int requiredHeight = Math.max(ATLAS_HEIGHT, SIPHON_ATLAS_HEIGHT);
            if (requiredWidth > maxTextureSize || requiredHeight > maxTextureSize) {
                throw new IllegalStateException("density atlases require up to "
                        + requiredWidth + "x" + requiredHeight
                        + " but GL_MAX_TEXTURE_SIZE=" + maxTextureSize);
            }
            IntBuffer maximumViewport = BufferUtils.createIntBuffer(2);
            GL11.glGetIntegerv(GL11.GL_MAX_VIEWPORT_DIMS, maximumViewport);
            int maxViewportWidth = maximumViewport.get(0);
            int maxViewportHeight = maximumViewport.get(1);
            if (requiredWidth > maxViewportWidth
                    || requiredHeight > maxViewportHeight) {
                throw new IllegalStateException("density atlases require a "
                        + requiredWidth + "x" + requiredHeight
                        + " viewport but GL_MAX_VIEWPORT_DIMS="
                        + maxViewportWidth + "x" + maxViewportHeight);
            }

            FloatBuffer zero = BufferUtils.createFloatBuffer(4);
            zero.put(0.0f).put(0.0f).put(0.0f).put(0.0f).flip();
            for (int i = 0; i < 2; i++) {
                densityTextures[i] = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, densityTextures[i]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG16F,
                        ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL30.GL_RG, GL11.GL_FLOAT, 0);

                densityFramebuffers[i] = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, densityFramebuffers[i]);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, densityTextures[i], 0);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("RG16F body density framebuffer incomplete: 0x"
                            + Integer.toHexString(status));
                }
                zero.rewind();
                GL30.glClearBufferfv(GL11.GL_COLOR, 0, zero);

                siphonDensityTextures[i] = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, siphonDensityTextures[i]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16F,
                        SIPHON_ATLAS_WIDTH, SIPHON_ATLAS_HEIGHT,
                        0, GL11.GL_RED, GL11.GL_FLOAT, 0);

                siphonDensityFramebuffers[i] = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, siphonDensityFramebuffers[i]);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, siphonDensityTextures[i], 0);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("R16F siphon framebuffer incomplete: 0x"
                            + Integer.toHexString(status));
                }
                zero.rewind();
                GL30.glClearBufferfv(GL11.GL_COLOR, 0, zero);
            }
            readIndex = 0;
            siphonReadIndex = 0;
            targetsReady = true;
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball split density ready "
                            + "(bodyRG16F={}x{}, siphonR16F={}x{}, textures={}/{}/{}/{})",
                    ATLAS_WIDTH,
                    ATLAS_HEIGHT,
                    SIPHON_ATLAS_WIDTH,
                    SIPHON_ATLAS_HEIGHT,
                    densityTextures[0],
                    densityTextures[1],
                    siphonDensityTextures[0],
                    siphonDensityTextures[1]
            );
            return true;
        } catch (Throwable failure) {
            destroyTargets();
            permanentlyUnsupported = true;
            logFailure("could not allocate R16F ping-pong atlas", failure);
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            previous.restore();
        }
    }

    private boolean runAdvectionStep(float previousSiphon, float siphon,
                                     float previousCollapse, float collapse,
                                     float destabilization, float age, float step) {
        ShaderInstance bodyShader = ModShaders.DARK_BALL_DENSITY_ADVECT;
        ShaderInstance siphonShader = ModShaders.DARK_BALL_SIPHON_ADVECT;
        int shapeTexture;
        try {
            shapeTexture = shape.uploadShapeTexture();
        } catch (Throwable failure) {
            disableForCapture("shape atlas upload failed", failure);
            return false;
        }
        int sourceTexture = densityTextures[readIndex];
        int writeIndex = 1 - readIndex;
        int siphonSourceTexture = siphonDensityTextures[siphonReadIndex];
        int siphonWriteIndex = 1 - siphonReadIndex;
        if (bodyShader == null || siphonShader == null
                || shapeTexture == 0 || sourceTexture == 0 || siphonSourceTexture == 0
                || densityFramebuffers[writeIndex] == 0
                || siphonDensityFramebuffers[siphonWriteIndex] == 0) {
            return false;
        }

        Vector3f outlet = shape.outletLocal();
        Vector3f curveRoot = siphonRootLocal();
        Vector3f controlA = siphonControlALocal();
        Vector3f controlB = siphonControlBLocal();
        updateSiphonBoltPath(controlA, controlB, age);
        float voxelSize = Math.min(volume.captureLength() / X_SIZE,
                volume.radius() * 2.0f / Y_SIZE);

        FramebufferState previous = FramebufferState.capture();
        ShaderInstance activeShader = null;
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, densityFramebuffers[writeIndex]);
            RenderSystem.viewport(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            RenderSystem.setShaderTexture(0, sourceTexture);
            RenderSystem.setShaderTexture(1, shapeTexture);
            bodyShader.setSampler("PreviousDensitySampler", sourceTexture);
            bodyShader.setSampler("ShapeSampler", shapeTexture);
            setVec3(bodyShader, "VolumeSize", volume.captureLength(), volume.radius(), volume.radius());
            setVec3(bodyShader, "OutletLocal", outlet);
            setVec3(bodyShader, "SiphonP0", curveRoot);
            setVec3(bodyShader, "SiphonP1", controlA);
            setFloat(bodyShader, "BodyRadius", volume.bodyRadius());
            setFloat(bodyShader, "VoxelSize", Math.max(voxelSize, 0.0001f));
            setFloat(bodyShader, "DeltaTime", step);
            setFloat(bodyShader, "PreviousSiphonProgress", previousSiphon);
            setFloat(bodyShader, "SiphonProgress", siphon);
            setFloat(bodyShader, "PreviousFinalCollapse", previousCollapse);
            setFloat(bodyShader, "FinalCollapse", collapse);

            RenderSystem.setShader(() -> bodyShader);
            activeShader = bodyShader;
            drawFullScreenQuad();
            bodyShader.clear();
            activeShader = null;

            // The moving curvilinear grid is independent of the static body
            // atlas. Reinterpreting normalized t/radial coordinates against
            // the current curve lets the siphon follow a bouncing ball without
            // stretching the body voxels or rebuilding the captured SDF.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                    siphonDensityFramebuffers[siphonWriteIndex]);
            RenderSystem.viewport(0, 0, SIPHON_ATLAS_WIDTH, SIPHON_ATLAS_HEIGHT);
            RenderSystem.setShaderTexture(0, siphonSourceTexture);
            // The body result is no longer attached once the siphon FBO is
            // bound. Its G channel contains this step's exact P0 transfer flux.
            int bodyResultTexture = densityTextures[writeIndex];
            RenderSystem.setShaderTexture(1, bodyResultTexture);
            siphonShader.setSampler("PreviousSiphonDensitySampler", siphonSourceTexture);
            siphonShader.setSampler("BodyDensitySampler", bodyResultTexture);
            setVec3(siphonShader, "VolumeSize",
                    volume.captureLength(), volume.radius(), volume.radius());
            setVec3(siphonShader, "SiphonP0", curveRoot);
            setVec3(siphonShader, "SiphonP3", ballLocal);
            setSiphonBoltUniforms(siphonShader);
            setFloat(siphonShader, "BodyRadius", volume.bodyRadius());
            setFloat(siphonShader, "VoxelSize", Math.max(voxelSize, 0.0001f));
            setFloat(siphonShader, "SiphonEndRadius",
                    DarkBallCaptureMath.siphonEndRadius());
            setFloat(siphonShader, "DeltaTime", step);
            setFloat(siphonShader, "SiphonProgress", siphon);
            setFloat(siphonShader, "FinalCollapse", collapse);
            setFloat(siphonShader, "Destabilization", destabilization);
            setFloat(siphonShader, "GameTime", age);

            RenderSystem.setShader(() -> siphonShader);
            activeShader = siphonShader;
            drawFullScreenQuad();
            siphonShader.clear();
            activeShader = null;

            if (ENABLE_DENSITY_DIAGNOSTICS
                    && !densityDiagnosticsLogged
                    && siphon >= 0.35f) {
                logDensityDiagnostics(densityTextures[writeIndex],
                        siphonDensityTextures[siphonWriteIndex], siphon);
                densityDiagnosticsLogged = true;
            }

            readIndex = writeIndex;
            siphonReadIndex = siphonWriteIndex;
            return true;
        } catch (Throwable failure) {
            disableForCapture("advection pass failed", failure);
            return false;
        } finally {
            if (activeShader != null) {
                try {
                    activeShader.clear();
                } catch (Throwable ignored) {
                    // Preserve the original pass failure.
                }
            }
            previous.restore();
        }
    }

    private void setSiphonBoltUniforms(ShaderInstance shader) {
        for (int node = 1; node < DarkBallSiphonBoltPath.NODE_COUNT - 1;
             node++) {
            setVec3(shader, "SiphonBolt" + node, siphonBoltNode(node));
        }
    }

    private void destroyTargets() {
        for (int i = 0; i < 2; i++) {
            if (densityFramebuffers[i] != 0) {
                GL30.glDeleteFramebuffers(densityFramebuffers[i]);
                densityFramebuffers[i] = 0;
            }
            if (densityTextures[i] != 0) {
                GL11.glDeleteTextures(densityTextures[i]);
                densityTextures[i] = 0;
            }
            if (siphonDensityFramebuffers[i] != 0) {
                GL30.glDeleteFramebuffers(siphonDensityFramebuffers[i]);
                siphonDensityFramebuffers[i] = 0;
            }
            if (siphonDensityTextures[i] != 0) {
                GL11.glDeleteTextures(siphonDensityTextures[i]);
                siphonDensityTextures[i] = 0;
            }
        }
        targetsReady = false;
        readIndex = 0;
        siphonReadIndex = 0;
    }

    private void logFailure(String message, Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        if (failure == null) {
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball advected density disabled for this capture: {}",
                    message
            );
        } else {
            Shadowedhearts.LOGGER.warn(
                    "[ShadowedHearts] Dark Ball advected density disabled for this capture: {}",
                    message,
                    failure
            );
        }
    }

    private void logDensityDiagnostics(int bodyTexture, int siphonTexture, float progress) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            FloatBuffer body = BufferUtils.createFloatBuffer(ATLAS_WIDTH * ATLAS_HEIGHT * 2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bodyTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG, GL11.GL_FLOAT, body);
            float bodyMobileSum = 0.0f;
            float bodyFluxSum = 0.0f;
            float bodyFluxMaximum = 0.0f;
            int bodyFluxCells = 0;
            for (int i = 0; i < ATLAS_WIDTH * ATLAS_HEIGHT; i++) {
                float mobile = body.get(i * 2);
                float flux = body.get(i * 2 + 1);
                bodyMobileSum += Math.max(0.0f, mobile);
                bodyFluxSum += Math.max(0.0f, flux);
                bodyFluxMaximum = Math.max(bodyFluxMaximum, flux);
                if (flux > 0.00001f) {
                    bodyFluxCells++;
                }
            }

            FloatBuffer siphon = BufferUtils.createFloatBuffer(
                    SIPHON_ATLAS_WIDTH * SIPHON_ATLAS_HEIGHT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, siphonTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RED, GL11.GL_FLOAT, siphon);
            float siphonSum = 0.0f;
            float siphonMaximum = 0.0f;
            int siphonCells = 0;
            for (int i = 0; i < SIPHON_ATLAS_WIDTH * SIPHON_ATLAS_HEIGHT; i++) {
                float density = siphon.get(i);
                siphonSum += Math.max(0.0f, density);
                siphonMaximum = Math.max(siphonMaximum, density);
                if (density > 0.00001f) {
                    siphonCells++;
                }
            }

            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball density handoff at siphon={}: "
                            + "bodyMobileSum={}, bodyFluxSum={}, bodyFluxMax={}, bodyFluxCells={}, "
                            + "siphonSum={}, siphonMax={}, siphonCells={}",
                    String.format(java.util.Locale.ROOT, "%.3f", progress),
                    String.format(java.util.Locale.ROOT, "%.3f", bodyMobileSum),
                    String.format(java.util.Locale.ROOT, "%.5f", bodyFluxSum),
                    String.format(java.util.Locale.ROOT, "%.5f", bodyFluxMaximum),
                    bodyFluxCells,
                    String.format(java.util.Locale.ROOT, "%.5f", siphonSum),
                    String.format(java.util.Locale.ROOT, "%.5f", siphonMaximum),
                    siphonCells
            );
        } catch (Throwable failure) {
            Shadowedhearts.LOGGER.debug(
                    "[ShadowedHearts] Could not read Dark Ball density diagnostics", failure);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static void drawFullScreenQuad() {
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(-1.0f, -1.0f, 0.0f).setUv(0.0f, 0.0f);
        buffer.addVertex(1.0f, -1.0f, 0.0f).setUv(1.0f, 0.0f);
        buffer.addVertex(1.0f, 1.0f, 0.0f).setUv(1.0f, 1.0f);
        buffer.addVertex(-1.0f, 1.0f, 0.0f).setUv(0.0f, 1.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void setFloat(ShaderInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setVec3(ShaderInstance shader, String name, Vector3f value) {
        setVec3(shader, name, value.x, value.y, value.z);
    }

    private static void setVec3(ShaderInstance shader, String name, float x, float y, float z) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0f : 1.0f;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private record FramebufferState(int drawFramebuffer, int readFramebuffer,
                                    int viewportX, int viewportY,
                                    int viewportWidth, int viewportHeight,
                                    boolean blendEnabled, boolean depthTestEnabled,
                                    boolean cullEnabled, boolean scissorEnabled,
                                    boolean depthMask) {
        static FramebufferState capture() {
            IntBuffer viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return new FramebufferState(
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            );
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthTestEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            RenderSystem.depthMask(depthMask);
        }
    }
}
