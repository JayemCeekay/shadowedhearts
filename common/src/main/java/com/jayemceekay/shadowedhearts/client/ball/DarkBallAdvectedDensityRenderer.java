package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/** Renders the anchored reservoir and mobile atlas as one implicit medium. */
final class DarkBallAdvectedDensityRenderer {
    /** Capture-relative speed of body-surface warping and depletion-front noise. */
    private static final float DEFORMATION_TIME_SCALE = 3.2f;
    private static final int MAX_BODY_RAYMARCH_SAMPLES = 96;
    private static final int LOW_TURBULENT_BODY_SAMPLES = 48;
    private static final int MEDIUM_TURBULENT_BODY_SAMPLES = 88;
    private static final int HIGH_TURBULENT_BODY_SAMPLES = 96;

    /**
     * Deliberately non-harmonic angular rates keep the four domains from
     * repeatedly lining up during the two-second turbulent hold. Supplying
     * each unwrapped phase as a sine/cosine pair lets the shader use genuine
     * continuous rotation instead of deriving an angle from a rocking scalar.
     */
    private static final float[] VORTEX_PHASE_RATES = {
            1.113f, -1.367f, 1.619f, -1.239f
    };
    private static final float[] VORTEX_PHASE_OFFSETS = {
            0.35f, 2.10f, 4.05f, 5.20f
    };
    /**
     * Developer-only diagnostics. Supply these as JVM properties when launching
     * a client; normal players retain mode zero and capture-relative time.
     */
    private static final int DEFORMATION_DEBUG_MODE = Mth.clamp(
            Integer.getInteger("shadowedhearts.darkBallDeformationDebug", 0), 0, 12);
    private static final boolean FREEZE_DEFORMATION_TIME = false;/*Boolean.getBoolean(
            "shadowedhearts.darkBallFreezeDeformation");*/
    private static final float FROZEN_DEFORMATION_TIME = systemFloat(
            "shadowedhearts.darkBallFrozenDeformationTime", 4.25f);

    private DarkBallAdvectedDensityRenderer() {
    }

    static int deformationDebugMode() {
        return DEFORMATION_DEBUG_MODE;
    }

    static boolean render(DarkBallAdvectedDensityField field,
                          DarkBallAnalyticalVolume shape,
                          DarkBallVolumeBuildResult volume,
                          Vec3 ballWorld,
                          Camera camera,
                          float age) {
        ShaderInstance shader = ModShaders.DARK_BALL_ADVECTED_VOLUME;
        if (field == null || shape == null || volume == null || shader == null) {
            return false;
        }

        int shapeTexture;
        int surfaceAttributeTexture;
        try {
            shapeTexture = shape.uploadShapeTexture();
            surfaceAttributeTexture = shape.uploadSurfaceAttributeTexture();
        } catch (Throwable failure) {
            field.disableForCapture("shape atlas upload failed during volume render", failure);
            return false;
        }
        int densityTexture = field.siphonDensityTextureId();
        int bodyDensityTexture = field.bodyDensityTextureId();
        int sceneDepthTexture = DarkBallDensityFBO.getSceneDepthTextureId();
        if (shapeTexture == 0 || surfaceAttributeTexture == 0
                || densityTexture == 0 || bodyDensityTexture == 0
                || sceneDepthTexture == 0) {
            return false;
        }

        RenderState previous = null;
        boolean shaderSelected = false;
        try {
            Vector3f ballLocal = DarkBallCaptureVfx.volumeWorldToLocal(volume, ballWorld);
            if (!field.updateBallLocal(ballLocal)) {
                return false;
            }
            Vector3f outlet = field.outletLocal();
            Vector3f curveRoot = field.siphonRootLocal();
            Vector3f controlA = field.siphonControlALocal();
            Vector3f controlB = field.siphonControlBLocal();
            field.updateSiphonBoltPath(controlA, controlB, age);

            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f cameraToWorld = new Matrix4f().rotation(camera.rotation());
            Vec3 cameraPosition = camera.getPosition();

            RenderSystem.setShaderTexture(0, shapeTexture);
            RenderSystem.setShaderTexture(1, densityTexture);
            RenderSystem.setShaderTexture(2, sceneDepthTexture);
            RenderSystem.setShaderTexture(3, bodyDensityTexture);
            RenderSystem.setShaderTexture(4, surfaceAttributeTexture);
            shader.setSampler("ShapeSampler", shapeTexture);
            shader.setSampler("DensitySampler", densityTexture);
            shader.setSampler("SceneDepthSampler", sceneDepthTexture);
            shader.setSampler("BodyDensitySampler", bodyDensityTexture);
            shader.setSampler("SurfaceAttributeSampler", surfaceAttributeTexture);

            setMatrix(shader, "InvProjMat", inverseProjection);
            setMatrix(shader, "CameraToWorldMat", cameraToWorld);
            setVec3(shader, "CameraPos", cameraPosition);
            setVec3(shader, "VolumeRoot", volume.root());
            setVec3(shader, "VolumeAxis", volume.axis());
            setVec3(shader, "VolumeSide", volume.side());
            setVec3(shader, "VolumeUp", volume.up());
            setVec3(shader, "VolumeSize", volume.captureLength(), volume.radius(), volume.radius());
            setVec3(shader, "SiphonP0", curveRoot);
            setVec3(shader, "SiphonP3", ballLocal);
            setSiphonBoltUniforms(shader, field);
            setVec3(shader, "SpikeTransportOrigin", outlet);

            float formation = smoothstep(DarkBallCaptureVfx.CONVERSION_START,
                    DarkBallCaptureVfx.CONVERSION_SOLID, age);
            float fade = 1.0f - smoothstep(DarkBallCaptureVfx.BALL_ABSORB_END,
                    DarkBallCaptureVfx.VFX_END, age);
            float turbulenceBlend = DarkBallCaptureVfx.turbulenceBlendAt(age);
            float destabilization = turbulenceBlend
                    * (1.0f - field.visualCollapse() * 0.72f);
            float voxelSize = Math.min(volume.captureLength() / DarkBallAdvectedDensityField.X_SIZE,
                    volume.radius() * 2.0f / DarkBallAdvectedDensityField.Y_SIZE);
            float safeVoxelSize = Math.max(voxelSize, 0.0001f);
            float siphonEndRadius = DarkBallCaptureMath.siphonEndRadius();
            DarkBallProjectedEffectBounds.UvBounds geometryBounds =
                    projectedEffectBounds(
                            field, shape, volume, projection,
                            camera, cameraPosition, turbulenceBlend,
                            safeVoxelSize, siphonEndRadius);
            DarkBallProjectedEffectBounds.UvBounds directDrawBounds =
                    geometryBounds.expandByPixels(
                            DarkBallProjectedEffectBounds
                                    .DIRECT_PADDING_PIXELS,
                            DarkBallDensityFBO.getTargetWidth(),
                            DarkBallDensityFBO.getTargetHeight());

            setFloat(shader, "BodyRadius", volume.bodyRadius());
            setFloat(shader, "BodyMaxX", shape.occupiedBodyMaxX());
            setFloat(shader, "VoxelSize", safeVoxelSize);
            setFloat(shader, "SiphonEndRadius", siphonEndRadius);
            setFloat(shader, "SiphonSinkFeather",
                    DarkBallCaptureMath.SIPHON_SINK_FEATHER);
            setFloat(shader, "Formation", formation);
            setFloat(shader, "Destabilization", destabilization);
            setFloat(shader, "TurbulenceBlend", turbulenceBlend);
            setFloat(shader, "SiphonProgress", field.visualSiphon());
            setFloat(shader, "FinalCollapse", field.visualCollapse());
            setFloat(shader, "EffectFade", fade);
            Minecraft minecraft = Minecraft.getInstance();
            float gameTimeSeconds = age;
            if (minecraft.level != null) {
                gameTimeSeconds = (minecraft.level.getGameTime()
                        + minecraft.getTimer().getGameTimeDeltaPartialTick(true)) * 0.05f;
            }
            setFloat(shader, "GameTime", gameTimeSeconds);
            float deformationTime = FREEZE_DEFORMATION_TIME
                    ? FROZEN_DEFORMATION_TIME
                    : deformationTimeAt(age);
            setFloat(shader, "DeformationTime", deformationTime);
            // The legacy deformation phase runs at 3.2 units per capture
            // second. Recover real seconds and start spike transport at the
            // turbulence transition so the approved static field is phase zero
            // when it first appears. Do not clamp at siphoning: the rising
            // surface motion must continue while depletion cuts it away.
            float spikeFlowTime = Math.max(deformationTime
                    / DEFORMATION_TIME_SCALE
                    - DarkBallCaptureVfx.TURBULENCE_START, 0.0f);
            setFloat(shader, "SpikeFlowTime", spikeFlowTime);
            setInt(shader, "DeformationDebugMode", DEFORMATION_DEBUG_MODE);
            setInt(shader, "VortexCellCount", field.quality().vortexCells());
            setVortexPhases(shader, deformationTime);
            setInt(shader, "BodyRaymarchSamples",
                    bodyRaymarchSamples(field.quality(), turbulenceBlend));
            setInt(shader, "SiphonRaymarchSamples",
                    Math.min(24, field.quality().raymarchSamples() + 8));

            previous = RenderState.capture();
            // The density target is cleared and direct rendering is restricted
            // to one capture, so store straight RGBA by replacement. The
            // shader JSON uses the matching ONE/ZERO blend state in case
            // ShaderInstance.apply() enables blending during the draw.
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.setShader(() -> shader);
            shaderSelected = true;

            drawScreenQuad(directDrawBounds);

            DarkBallDensityFBO.markDirectVolumeRendered(geometryBounds);
            return true;
        } catch (Throwable failure) {
            if (shaderSelected) {
                try {
                    shader.clear();
                } catch (Throwable ignored) {
                    // Preserve the original render failure for diagnostics.
                }
                shaderSelected = false;
            }
            field.disableForCapture("advected volume render failed", failure);
            return false;
        } finally {
            try {
                if (shaderSelected) {
                    shader.clear();
                }
            } finally {
                if (previous != null) {
                    previous.restore();
                }
            }
        }
    }

    private static DarkBallProjectedEffectBounds.UvBounds projectedEffectBounds(
            DarkBallAdvectedDensityField field,
            DarkBallAnalyticalVolume shape,
            DarkBallVolumeBuildResult volume,
            Matrix4f projection,
            Camera camera,
            Vec3 cameraPosition,
            float turbulenceBlend,
            float voxelSize,
            float siphonEndRadius) {
        Vector3d worldRoot = toVector3d(volume.root());
        Vector3d worldAxis = toVector3d(volume.axis());
        Vector3d worldSide = toVector3d(volume.side());
        Vector3d worldUp = toVector3d(volume.up());
        DarkBallProjectedEffectBounds.Projection bounds =
                DarkBallProjectedEffectBounds.begin(
                        projection,
                        camera.rotation(),
                        toVector3d(cameraPosition));

        float baseMargin = Math.max(
                voxelSize * 2.0f, volume.bodyRadius() * 0.090f);
        float auraMargin = volume.bodyRadius() * 0.36f
                * Mth.clamp(turbulenceBlend, 0.0f, 1.0f);
        bounds.includeOrientedBox(
                worldRoot, worldAxis, worldSide, worldUp,
                -baseMargin - auraMargin,
                -volume.radius() - auraMargin,
                -volume.radius() - auraMargin,
                shape.occupiedBodyMaxX() + baseMargin + auraMargin,
                volume.radius() + auraMargin,
                volume.radius() + auraMargin);

        // Before the siphon starts its density atlas is empty. Avoid letting
        // the dormant body-to-ball path enlarge the expensive body raymarch;
        // include it on the very first non-zero visual progress instead.
        if (field.visualSiphon() > 0.0f
                || field.visualCollapse() > 0.0f) {
            Vector3f[] nodes =
                    new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
            for (int node = 0; node < nodes.length; node++) {
                nodes[node] = new Vector3f(field.siphonBoltNode(node));
            }
            bounds.includeSiphonPrisms(
                    worldRoot, worldAxis, worldSide, worldUp,
                    nodes, volume.bodyRadius(), voxelSize,
                    siphonEndRadius);
        }

        return bounds.finish(
                DarkBallDensityFBO.getTargetWidth(),
                DarkBallDensityFBO.getTargetHeight(),
                0);
    }

    private static Vector3d toVector3d(Vec3 vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    private static void drawScreenQuad(
            DarkBallProjectedEffectBounds.UvBounds bounds) {
        float minimumX = bounds.minU() * 2.0f - 1.0f;
        float minimumY = bounds.minV() * 2.0f - 1.0f;
        float maximumX = bounds.maxU() * 2.0f - 1.0f;
        float maximumY = bounds.maxV() * 2.0f - 1.0f;
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(minimumX, minimumY, 0.0f)
                .setUv(bounds.minU(), bounds.minV());
        buffer.addVertex(maximumX, minimumY, 0.0f)
                .setUv(bounds.maxU(), bounds.minV());
        buffer.addVertex(maximumX, maximumY, 0.0f)
                .setUv(bounds.maxU(), bounds.maxV());
        buffer.addVertex(minimumX, maximumY, 0.0f)
                .setUv(bounds.minU(), bounds.maxV());
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static float remap(float value, float start, float end) {
        return Mth.clamp((value - start) / Math.max(end - start, 0.0001f), 0.0f, 1.0f);
    }

    private static float smoothstep(float start, float end, float value) {
        float t = remap(value, start, end);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float systemFloat(String name, float fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static float deformationTimeAt(float captureAge) {
        return captureAge * DEFORMATION_TIME_SCALE;
    }

    static int bodyRaymarchSamples(DarkBallVfxQuality quality, float turbulenceBlend) {
        DarkBallVfxQuality effectiveQuality = quality == null
                ? DarkBallVfxQuality.MEDIUM
                : quality;
        // The body test grid has twice the resolution on every axis. Preserve
        // the former samples-per-voxel ratio without changing the quality
        // enum's budget, because that same value still controls the independent
        // 48x12x12 siphon march.
        int baseSamples = effectiveQuality.raymarchSamples() * 2;
        int turbulentSamples = switch (effectiveQuality) {
            case LOW -> LOW_TURBULENT_BODY_SAMPLES;
            case MEDIUM -> MEDIUM_TURBULENT_BODY_SAMPLES;
            case HIGH -> HIGH_TURBULENT_BODY_SAMPLES;
        };
        float blend = Mth.clamp(turbulenceBlend, 0.0f, 1.0f);
        return Mth.clamp(Math.round(Mth.lerp(blend, baseSamples, turbulentSamples)),
                1, MAX_BODY_RAYMARCH_SAMPLES);
    }

    static float vortexPhaseRadians(int cell, float deformationTime) {
        if (cell < 0 || cell >= VORTEX_PHASE_RATES.length) {
            throw new IllegalArgumentException("vortex cell must be between 0 and 3");
        }
        return deformationTime * VORTEX_PHASE_RATES[cell]
                + VORTEX_PHASE_OFFSETS[cell];
    }

    private static void setVortexPhases(ShaderInstance shader, float time) {
        float phase0 = vortexPhaseRadians(0, time);
        float phase1 = vortexPhaseRadians(1, time);
        float phase2 = vortexPhaseRadians(2, time);
        float phase3 = vortexPhaseRadians(3, time);
        setVec4(shader, "VortexPhaseSin",
                (float) Math.sin(phase0), (float) Math.sin(phase1),
                (float) Math.sin(phase2), (float) Math.sin(phase3));
        setVec4(shader, "VortexPhaseCos",
                (float) Math.cos(phase0), (float) Math.cos(phase1),
                (float) Math.cos(phase2), (float) Math.cos(phase3));
    }

    private static void setSiphonBoltUniforms(
            ShaderInstance shader,
            DarkBallAdvectedDensityField field) {
        for (int node = 1; node < DarkBallSiphonBoltPath.NODE_COUNT - 1;
             node++) {
            setVec3(shader, "SiphonBolt" + node,
                    field.siphonBoltNode(node));
        }
    }

    private static void setMatrix(ShaderInstance shader, String name, Matrix4f value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setFloat(ShaderInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setInt(ShaderInstance shader, String name, int value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setVec3(ShaderInstance shader, String name, Vec3 value) {
        setVec3(shader, name, (float) value.x, (float) value.y, (float) value.z);
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

    private static void setVec4(ShaderInstance shader, String name,
                                float x, float y, float z, float w) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z, w);
        }
    }

    private record RenderState(boolean blendEnabled, boolean depthTestEnabled,
                               boolean scissorEnabled, boolean depthMask,
                               int blendSourceRgb, int blendDestinationRgb,
                               int blendSourceAlpha, int blendDestinationAlpha,
                               int blendEquationRgb, int blendEquationAlpha) {
        static RenderState capture() {
            return new RenderState(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
            );
        }

        void restore() {
            GlStateManager._blendFuncSeparate(
                    blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
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
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            RenderSystem.depthMask(depthMask);
        }
    }
}
