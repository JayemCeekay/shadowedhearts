package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Persistent, topology-independent presentation of an extracted Dark Ball
 * surface.
 *
 * <p>Each retained surface sample is uploaded once as two explicit triangles
 * forming one camera-facing quad. The vertex shader owns turbulence, ordered
 * collapse, and view-facing expansion, so the CPU does no per-frame splat
 * simulation or tessellation. Triangle connectivity is intentionally ignored:
 * boundary, winding, and collapse-authority failures cannot reject otherwise
 * finite samples.</p>
 */
final class DarkBallSurfaceSplatRenderer implements AutoCloseable {
    private static final VertexFormat FORMAT =
            ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT;
    private static final int POSITION_OFFSET =
            FORMAT.getOffset(VertexFormatElement.POSITION);
    private static final int COLOR_OFFSET =
            FORMAT.getOffset(VertexFormatElement.COLOR);
    private static final int UV0_OFFSET =
            FORMAT.getOffset(VertexFormatElement.UV0);
    private static final int UV1_OFFSET =
            FORMAT.getOffset(VertexFormatElement.UV1);
    private static final int UV2_OFFSET =
            FORMAT.getOffset(VertexFormatElement.UV2);
    private static final int NORMAL_OFFSET =
            FORMAT.getOffset(VertexFormatElement.NORMAL);
    /**
     * Explicit triangle-list order for the four billboard corners. This avoids
     * Minecraft's generated QUADS index buffer and does not ask Iris to
     * preserve a particular {@code gl_VertexID} convention.
     */
    private static final int[] TRIANGLE_CORNERS = {0, 1, 2, 0, 2, 3};
    /**
     * Conservatively covers the widest tangent ellipse plus its connected
     * boundary-hook lobe. Projected resolve bounds use this value to keep the
     * curved silhouette from being scissored away.
     */
    private static final float MAX_PROJECTED_SPLAT_REACH = 12.75f;
    private static final float MAX_THICKNESS_RADIUS_SCALE = 1.08f;
    static final float SURFACE_INSET_VOXEL_FACTOR = 0.45f;
    static final float SURFACE_INSET_THICKNESS_FRACTION = 0.15f;
    private static final float MIN_COLLAPSE_LOCALITY = 0.50f;
    private static final double MIN_SAMPLE_TRIANGLE_AREA = 1.0e-12;
    static final float FEATURE_SAMPLE_FRACTION = 0.30f;
    static final int BLUE_NOISE_CANDIDATE_MULTIPLIER = 3;
    static final int MAX_MANUAL_SURFEL_BUDGET = 16_000;
    static final float FEATURE_THINNESS_SHARE = 0.72f;
    static final float FEATURE_CURVATURE_SHARE =
            1.0f - FEATURE_THINNESS_SHARE;
    static final float COMPONENT_FEATURE_BALANCE = 0.72f;
    static final float MAX_COMPONENT_FEATURE_BOOST = 8.0f;
    /**
     * Temporary hard-on investigation aid for the persistent Iris stretching
     * fault. Logging is bounded to activation and phase transitions for each
     * uploaded capture, so it must never become a per-frame render log.
     */
    private static final boolean ENABLE_PROJECTION_DIAGNOSTIC_LOGGING = true;
    private static final int DIAGNOSTIC_ACTIVATION = 1;
    private static final int DIAGNOSTIC_TURBULENCE = 1 << 1;
    private static final int DIAGNOSTIC_COLLAPSE = 1 << 2;

    private VertexBuffer vertexBuffer;
    private DarkBallSurfaceMesh uploadedMesh;
    private DarkBallVolumeBuildResult uploadedVolume;
    private DarkBallVfxQuality uploadedQuality;
    private SurfaceSamplePlan uploadedSamplePlan;
    private SurfaceSamplePlan uploadedDiagnosticSamplePlan;
    private float thicknessScale = 1.0f;
    private float splatRadius = 0.05f;
    private float uploadedMeanSurfaceInset;
    private float uploadedMaximumSurfaceInset;
    private float uploadedVoxelSurfaceInsetLimit;
    private int uploadedSampleCount;
    private long lastPreparationUploadCpuMicros = -1L;
    private long lastDrawCpuMicros = -1L;
    private long uploadGeneration;
    private boolean uploadOccurredLastRender;
    private boolean uploadFailureLogged;
    private boolean diagnosticFailureLogged;
    private boolean frontDepthFailureLogged;
    private boolean projectionDiagnosticFailureLogged;
    private long projectionDiagnosticUploadGeneration = -1L;
    private int projectionDiagnosticStageMask;
    private ShaderInstance validatedVertexLayoutShader;
    private long validatedVertexLayoutUploadGeneration = -1L;
    private boolean validatedVertexLayoutCompatible;
    private boolean vertexLayoutFailureLogged;

    record Parameters(
            Vector3f inletLocal,
            Vector3f spikeTransportOriginLocal,
            Vector3f siphonTangentLocal,
            float releaseFront,
            float collapseLocality,
            float turbulenceBlend,
            float turbulenceComplexityBlend,
            float spikeFlowTime,
            float deformationFlowTime,
            float effectFade,
            int pokemonId,
            int ballId,
            float presentationAge,
            SurfaceSamplePlan preparedSamples
    ) {
        Parameters {
            inletLocal = inletLocal == null
                    ? new Vector3f()
                    : new Vector3f(inletLocal);
            spikeTransportOriginLocal =
                    spikeTransportOriginLocal == null
                            ? new Vector3f(inletLocal)
                            : new Vector3f(spikeTransportOriginLocal);
            siphonTangentLocal = siphonTangentLocal == null
                    ? new Vector3f(1.0f, 0.0f, 0.0f)
                    : new Vector3f(siphonTangentLocal);
        }
    }

    boolean render(DarkBallSurfaceMesh mesh,
                   DarkBallVolumeBuildResult volume,
                   DarkBallVfxQuality quality,
                   Camera camera,
                   Parameters parameters) {
        uploadOccurredLastRender = false;
        lastDrawCpuMicros = -1L;
        ShaderInstance shader = ModShaders.DARK_BALL_SURFACE_SPLAT;
        if (!RenderSystem.isOnRenderThread()
                || shader == null
                || mesh == null
                || mesh.vertexCount() < 1
                || volume == null
                || camera == null
                || parameters == null) {
            return false;
        }

        DarkBallVfxQuality resolvedQuality = quality == null
                ? DarkBallVfxQuality.MEDIUM
                : quality;
        boolean shouldLogAppliedGpuState;
        try {
            if (!uploadIfNeeded(
                    mesh,
                    volume,
                    resolvedQuality,
                    parameters.preparedSamples())) {
                return false;
            }
            configureShader(shader, volume, camera, parameters);
            shouldLogAppliedGpuState = logProjectionDiagnosticsIfNeeded(
                    volume, camera, parameters);
            setFloat(shader, "SplatDiagnosticMode",
                    DarkBallDeformationSettings
                            .spikeIndentAmplitudeDebugEnabled()
                            ? -1.0f : 0.0f);
        } catch (Throwable failure) {
            logUploadFailureOnce(mesh, failure);
            return false;
        }

        RenderState previous = RenderState.capture();
        try {
            // Surface splats write an intermediate weighted field. The
            // bounded resolve pass decodes the final material only after
            // overlapping surfels have fused.
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE);
            GL20.glBlendEquationSeparate(
                    GL14.GL_FUNC_ADD,
                    GL14.GL_FUNC_ADD);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            // Keep the pristine scene-depth copy as the occlusion authority;
            // splats must accumulate through one another rather than
            // self-occluding into visible particle discs.
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            vertexBuffer.bind();
            // Resolve the installed pair once and reuse those exact objects
            // for both the applied-state probe and the production draw. This
            // avoids letting an Iris callback observe two independently
            // fetched matrix snapshots within one surfel submission.
            Matrix4f modelView = DarkBallRenderContext.modelView(camera);
            Matrix4f projection = DarkBallRenderContext.projection();
            if (!validateBoundVertexLayout(
                    shader,
                    modelView,
                    projection,
                    parameters)) {
                return false;
            }
            if (shouldLogAppliedGpuState) {
                logAppliedGpuState(
                        shader,
                        modelView,
                        projection,
                        parameters);
            }
            long drawStarted = System.nanoTime();
            try {
                vertexBuffer.drawWithShader(
                        modelView,
                        projection,
                        shader);
            } finally {
                // CPU submission time only. This intentionally excludes the
                // immutable sample preparation/upload above and never waits
                // for GPU completion.
                lastDrawCpuMicros = elapsedMicros(drawStarted);
            }
            return true;
        } catch (Throwable failure) {
            logUploadFailureOnce(mesh, failure);
            return false;
        } finally {
            VertexBuffer.unbind();
            previous.restore();
        }
    }

    /**
     * Redraws the already-uploaded splats into the isolated diagnostic target.
     * The fragment shader substitutes stable identity colors for the
     * production accumulation payload; no production timing or upload state is
     * modified by this inspection-only submission.
     */
    boolean renderDiagnostic(Camera camera) {
        ShaderInstance shader = ModShaders.DARK_BALL_SURFACE_SPLAT;
        if (!RenderSystem.isOnRenderThread()
                || shader == null
                || camera == null
                || vertexBuffer == null
                || vertexBuffer.isInvalid()) {
            return false;
        }

        RenderState previous = RenderState.capture();
        try {
            setFloat(shader, "SplatDiagnosticMode", 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE);
            GL20.glBlendEquationSeparate(
                    GL14.GL_FUNC_ADD,
                    GL14.GL_FUNC_ADD);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            vertexBuffer.bind();
            Matrix4f modelView =
                    DarkBallRenderContext.modelView(camera);
            Matrix4f projection =
                    DarkBallRenderContext.projection();
            if (!validateBoundVertexLayout(
                    shader, modelView, projection, null)) {
                return false;
            }
            vertexBuffer.drawWithShader(
                    modelView,
                    projection,
                    shader);
            return true;
        } catch (Throwable failure) {
            logDiagnosticFailureOnce(failure);
            return false;
        } finally {
            setFloat(shader, "SplatDiagnosticMode", 0.0f);
            VertexBuffer.unbind();
            previous.restore();
        }
    }

    int uploadedSampleCount() {
        return uploadedSampleCount;
    }

    long lastPreparationUploadCpuMicros() {
        return lastPreparationUploadCpuMicros;
    }

    long lastDrawCpuMicros() {
        return lastDrawCpuMicros;
    }

    long uploadGeneration() {
        return uploadGeneration;
    }

    boolean uploadOccurredLastRender() {
        return uploadOccurredLastRender;
    }

    float uploadedSplatRadius() {
        return splatRadius;
    }

    float uploadedProjectedReachRadius() {
        return splatRadius
                * MAX_PROJECTED_SPLAT_REACH
                * MAX_THICKNESS_RADIUS_SCALE;
    }

    float uploadedMeanSurfaceInset() {
        return uploadedMeanSurfaceInset;
    }

    float uploadedMaximumSurfaceInset() {
        return uploadedMaximumSurfaceInset;
    }

    float uploadedVoxelSurfaceInsetLimit() {
        return uploadedVoxelSurfaceInsetLimit;
    }

    private boolean uploadIfNeeded(DarkBallSurfaceMesh mesh,
                                   DarkBallVolumeBuildResult volume,
                                   DarkBallVfxQuality quality,
                                   SurfaceSamplePlan preparedSamples) {
        if (uploadedMesh == mesh
                && uploadedVolume == volume
                && uploadedQuality == quality
                && uploadedSamplePlan == preparedSamples
                && vertexBuffer != null
                && !vertexBuffer.isInvalid()) {
            return true;
        }
        // The extracted mesh is immutable. Do the O(vertexCount) finite-data
        // audit only when a new VBO upload is actually required, never on
        // every render-thread frame.
        if (!mesh.hasFiniteSplatSamples()) {
            return false;
        }
        int preparedSampleCount = preparedSamples == null
                ? 0
                : preparedSamples.positions().length / 3;
        int sampleCount = preparedSampleCount > 0
                ? preparedSampleCount
                : Math.min(mesh.vertexCount(), sampleBudget(quality));
        if (sampleCount < 1) {
            return false;
        }
        int uploadedVertexCount = Math.multiplyExact(sampleCount, 6);
        int uploadedIndexCount = uploadedVertexCount;
        int vertexStride = FORMAT.getVertexSize();
        int vertexBytes = Math.multiplyExact(
                uploadedVertexCount,
                vertexStride);
        float maximumVoxel = maximumVoxelSize(volume);
        long preparationUploadStarted = System.nanoTime();
        SurfaceSamplePlan samples = preparedSamples;
        if (samples == null
                || samples.positions().length / 3 != sampleCount) {
            samples = buildAreaStratifiedSamples(
                    mesh, sampleCount, maximumVoxel);
        }
        ByteBufferBuilder vertexBuilder =
                new ByteBufferBuilder(Math.max(vertexBytes, 1));
        VertexBuffer replacement =
                new VertexBuffer(VertexBuffer.Usage.STATIC);
        SurfaceInsetStatistics insetStatistics;
        boolean uploaded = false;
        try (vertexBuilder) {
            long vertices = vertexBuilder.reserve(vertexBytes);
            MemoryUtil.memSet(vertices, 0, vertexBytes);
            insetStatistics = writeSamples(
                    vertices,
                    vertexStride,
                    mesh,
                    samples,
                    maximumVoxel);
            ByteBufferBuilder.Result vertexResult = vertexBuilder.build();
            if (vertexResult == null) {
                throw new IllegalStateException(
                        "surface splat upload buffer was empty");
            }
            VertexFormat.IndexType indexType =
                    VertexFormat.IndexType.least(uploadedVertexCount);
            MeshData.DrawState drawState = new MeshData.DrawState(
                    FORMAT,
                    uploadedVertexCount,
                    uploadedIndexCount,
                    VertexFormat.Mode.TRIANGLES,
                    indexType);
            replacement.bind();
            replacement.upload(new MeshData(vertexResult, drawState));
            VertexBuffer.unbind();
            uploaded = true;
        } finally {
            if (!uploaded) {
                VertexBuffer.unbind();
                replacement.close();
            }
        }

        VertexBuffer previous = vertexBuffer;
        vertexBuffer = replacement;
        uploadedMesh = mesh;
        uploadedVolume = volume;
        uploadedQuality = quality;
        uploadedSamplePlan = preparedSamples;
        uploadedDiagnosticSamplePlan = samples;
        uploadedSampleCount = sampleCount;
        lastPreparationUploadCpuMicros =
                elapsedMicros(preparationUploadStarted);
        uploadGeneration++;
        uploadOccurredLastRender = true;
        splatRadius = calculateSplatRadius(
                mesh,
                volume,
                sampleCount);
        uploadedMeanSurfaceInset =
                insetStatistics.meanInset();
        uploadedMaximumSurfaceInset =
                insetStatistics.maximumInset();
        uploadedVoxelSurfaceInsetLimit =
                insetStatistics.voxelLimit();
        uploadFailureLogged = false;
        projectionDiagnosticFailureLogged = false;
        validatedVertexLayoutShader = null;
        validatedVertexLayoutUploadGeneration = -1L;
        validatedVertexLayoutCompatible = false;
        vertexLayoutFailureLogged = false;
        Shadowedhearts.LOGGER.info(
                "[ShadowedHearts] Dark Ball surface-splat upload tightened "
                        + "the captured silhouette (samples={}, "
                        + "insetMean={}, insetMax={}, insetVoxelLimit={}, "
                        + "insetThicknessFraction={}, splatRadius={})",
                sampleCount,
                uploadedMeanSurfaceInset,
                uploadedMaximumSurfaceInset,
                uploadedVoxelSurfaceInsetLimit,
                SURFACE_INSET_THICKNESS_FRACTION,
                splatRadius);
        if (previous != null) {
            previous.close();
        }
        return true;
    }

    private static long elapsedMicros(long startedNanos) {
        return Math.max(
                (System.nanoTime() - startedNanos) / 1_000L,
                0L);
    }

    private boolean logProjectionDiagnosticsIfNeeded(
            DarkBallVolumeBuildResult volume,
            Camera camera,
            Parameters parameters) {
        if (!ENABLE_PROJECTION_DIAGNOSTIC_LOGGING) {
            return false;
        }
        try {
            if (projectionDiagnosticUploadGeneration != uploadGeneration) {
                projectionDiagnosticUploadGeneration = uploadGeneration;
                projectionDiagnosticStageMask = 0;
            }
            int phaseBit = diagnosticPhaseBit(parameters);
            boolean activation =
                    (projectionDiagnosticStageMask
                            & DIAGNOSTIC_ACTIVATION) == 0;
            if (!activation
                    && (projectionDiagnosticStageMask & phaseBit) != 0) {
                return false;
            }
            projectionDiagnosticStageMask |= DIAGNOSTIC_ACTIVATION | phaseBit;

            Matrix4f modelView = DarkBallRenderContext.modelView(camera);
            Matrix4f projection = DarkBallRenderContext.projection();
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int drawFramebuffer = GL11.glGetInteger(
                    GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int readFramebuffer = GL11.glGetInteger(
                    GL30.GL_READ_FRAMEBUFFER_BINDING);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            ProjectionStatistics projectionStatistics =
                    collectProjectionStatistics(
                            volume,
                            camera,
                            modelView,
                            projection,
                            viewport);
            String phase = diagnosticPhaseName(parameters);
            String stage = activation ? "activation/" + phase : phase;
            Matrix4f renderSystemModelView = new Matrix4f(
                    RenderSystem.getModelViewMatrix());
            Matrix4f renderSystemProjection = new Matrix4f(
                    RenderSystem.getProjectionMatrix());

            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat projection trace "
                            + "pokemon={} ball={} stage={} age={} "
                            + "irisContext={} upload={} samples={} "
                            + "viewport=({},{} {}x{}) drawFbo={} readFbo={} "
                            + "program={}",
                    parameters.pokemonId(),
                    parameters.ballId(),
                    stage,
                    parameters.presentationAge(),
                    DarkBallRenderContext.hasInstalledTransform(),
                    uploadGeneration,
                    uploadedSampleCount,
                    viewport[0], viewport[1], viewport[2], viewport[3],
                    drawFramebuffer, readFramebuffer, program);
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat projection inputs "
                            + "pokemon={} ball={} releaseFront={} "
                            + "turbulence={} complexity={} spikeTime={} "
                            + "deformationTime={} fade={} rootCamera={} "
                            + "inletLocal={} tangentLocal={} bodyRadius={} "
                            + "voxel=({}, {}) splatRadius={} "
                            + "projectedReach={} basis={}",
                    parameters.pokemonId(),
                    parameters.ballId(),
                    parameters.releaseFront(),
                    parameters.turbulenceBlend(),
                    parameters.turbulenceComplexityBlend(),
                    parameters.spikeFlowTime(),
                    parameters.deformationFlowTime(),
                    parameters.effectFade(),
                    formatVec3(volume.root().subtract(camera.getPosition())),
                    formatVector3f(parameters.inletLocal()),
                    formatVector3f(parameters.siphonTangentLocal()),
                    volume.bodyRadius(),
                    volume.sdfVoxelX(), volume.sdfVoxelYz(),
                    splatRadius,
                    uploadedProjectedReachRadius(),
                    basisSummary(volume));
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat base projection "
                            + "pokemon={} ball={} {} root={} inlet={}",
                    parameters.pokemonId(),
                    parameters.ballId(),
                    projectionStatistics.summary(),
                    projectLocalPoint(
                            new Vector3f(),
                            volume,
                            camera,
                            modelView,
                            projection),
                    projectLocalPoint(
                            parameters.inletLocal(),
                            volume,
                            camera,
                            modelView,
                            projection));
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat matrices pokemon={} "
                            + "ball={} modelViewDet={} projectionDet={} "
                            + "renderSystemDelta=({}, {}) modelView={} "
                            + "projection={}",
                    parameters.pokemonId(),
                    parameters.ballId(),
                    modelView.determinant(),
                    projection.determinant(),
                    maximumMatrixDelta(modelView, renderSystemModelView),
                    maximumMatrixDelta(projection,
                            renderSystemProjection),
                    formatMatrix(modelView),
                    formatMatrix(projection));
            return true;
        } catch (Throwable failure) {
            if (!projectionDiagnosticFailureLogged) {
                projectionDiagnosticFailureLogged = true;
                Shadowedhearts.LOGGER.warn(
                        "[ShadowedHearts] Dark Ball splat projection trace "
                                + "failed; production rendering is unchanged",
                        failure);
            }
            return false;
        }
    }

    /**
     * Samples the state OpenGL actually sees after {@link ShaderInstance} has
     * applied its pending uniforms and while the surfel VAO is bound. The CPU
     * projection trace cannot expose Iris program substitution, a stale scalar
     * uniform, or a mismatched vertex-array layout. The production draw
     * reapplies this shader immediately afterward.
     */
    private void logAppliedGpuState(ShaderInstance shader,
                                    Matrix4f modelView,
                                    Matrix4f projection,
                                    Parameters parameters) {
        boolean applied = false;
        try {
            setMatrix(shader, "ModelViewMat", modelView);
            setMatrix(shader, "ProjMat", projection);
            shader.apply();
            applied = true;

            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int arrayBuffer = GL11.glGetInteger(
                    GL15.GL_ARRAY_BUFFER_BINDING);
            int elementBuffer = GL11.glGetInteger(
                    GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            float[] appliedModelView = readAppliedUniform(
                    program, "ModelViewMat", 16);
            float[] appliedProjection = readAppliedUniform(
                    program, "ProjMat", 16);

            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat applied GPU state "
                            + "pokemon={} ball={} upload={} program={} "
                            + "vao={} arrayBuffer={} elementBuffer={} "
                            + "formatStride={} attributes=[{}]",
                    parameters.pokemonId(),
                    parameters.ballId(),
                    uploadGeneration,
                    program,
                    vao,
                    arrayBuffer,
                    elementBuffer,
                    FORMAT.getVertexSize(),
                    appliedAttributeSummary(program));
            Shadowedhearts.LOGGER.info(
                    "[ShadowedHearts] Dark Ball splat applied uniforms "
                            + "pokemon={} ball={} splatRadius={}/{} "
                            + "voxelSize={} thicknessScale={}/{} "
                            + "bodyRadius={} turbulence={} complexity={} "
                            + "modelViewDelta={} projectionDelta={}",
                    parameters.pokemonId(),
                    parameters.ballId(),
                    splatRadius,
                    formatAppliedUniform(program, "SplatRadius"),
                    formatAppliedUniform(program, "VoxelSize"),
                    thicknessScale,
                    formatAppliedUniform(program, "ThicknessScale"),
                    formatAppliedUniform(program, "BodyRadius"),
                    formatAppliedUniform(program, "TurbulenceBlend"),
                    formatAppliedUniform(
                            program, "TurbulenceComplexityBlend"),
                    maximumUniformDelta(appliedModelView, modelView),
                    maximumUniformDelta(appliedProjection, projection));
        } catch (Throwable failure) {
            if (!projectionDiagnosticFailureLogged) {
                projectionDiagnosticFailureLogged = true;
                Shadowedhearts.LOGGER.warn(
                        "[ShadowedHearts] Dark Ball splat applied GPU state "
                                + "trace failed; production rendering is "
                                + "unchanged",
                        failure);
            }
        } finally {
            if (applied) {
                shader.clear();
            }
        }
    }

    /**
     * Fails closed if a shader-pack compatibility layer ever installs a
     * physical VAO layout that disagrees with the manually packed 36-byte
     * records. The successful result is cached for this VBO generation and
     * shader instance, so the OpenGL attribute queries do not become a
     * per-frame cost. Returning {@code false} leaves the captured exact-mask
     * presentation in control instead of submitting corrupted splats.
     */
    private boolean validateBoundVertexLayout(
            ShaderInstance shader,
            Matrix4f modelView,
            Matrix4f projection,
            Parameters parameters) {
        if (validatedVertexLayoutShader == shader
                && validatedVertexLayoutUploadGeneration
                == uploadGeneration) {
            return validatedVertexLayoutCompatible;
        }

        boolean applied = false;
        try {
            setMatrix(shader, "ModelViewMat", modelView);
            setMatrix(shader, "ProjMat", projection);
            shader.apply();
            applied = true;

            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            boolean compatible = vertexBuffer != null
                    && vertexBuffer.getFormat() == FORMAT
                    && appliedAttributeLayoutCompatible(program);
            validatedVertexLayoutShader = shader;
            validatedVertexLayoutUploadGeneration = uploadGeneration;
            validatedVertexLayoutCompatible = compatible;
            if (!compatible && !vertexLayoutFailureLogged) {
                vertexLayoutFailureLogged = true;
                Shadowedhearts.LOGGER.error(
                        "[ShadowedHearts] Dark Ball surfel VAO layout "
                                + "mismatch for pokemon={} ball={} "
                                + "upload={} packedStride={} "
                                + "attributes=[{}]; retaining the captured "
                                + "exact-mask presentation",
                        parameters == null ? -1 : parameters.pokemonId(),
                        parameters == null ? -1 : parameters.ballId(),
                        uploadGeneration,
                        FORMAT.getVertexSize(),
                        appliedAttributeSummary(program));
            }
            return compatible;
        } catch (Throwable failure) {
            validatedVertexLayoutShader = shader;
            validatedVertexLayoutUploadGeneration = uploadGeneration;
            validatedVertexLayoutCompatible = false;
            if (!vertexLayoutFailureLogged) {
                vertexLayoutFailureLogged = true;
                Shadowedhearts.LOGGER.error(
                        "[ShadowedHearts] Dark Ball surfel VAO layout "
                                + "validation failed; retaining the captured "
                                + "exact-mask presentation",
                        failure);
            }
            return false;
        } finally {
            if (applied) {
                shader.clear();
            }
        }
    }

    private static boolean appliedAttributeLayoutCompatible(int program) {
        if (program <= 0) {
            return false;
        }
        String[] attributes = {
                "Position", "Color", "UV0", "UV1", "UV2", "Normal"
        };
        int expectedStride = FORMAT.getVertexSize();
        int vertexBuffer = 0;
        for (String attribute : attributes) {
            int location = GL20.glGetAttribLocation(program, attribute);
            if (location < 0
                    || GL20.glGetVertexAttribi(
                    location,
                    GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == 0
                    || GL20.glGetVertexAttribi(
                    location,
                    GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE) != expectedStride) {
                return false;
            }
            int attributeBuffer = GL20.glGetVertexAttribi(
                    location,
                    GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
            if (attributeBuffer <= 0) {
                return false;
            }
            if (vertexBuffer == 0) {
                vertexBuffer = attributeBuffer;
            } else if (attributeBuffer != vertexBuffer) {
                return false;
            }
        }
        return true;
    }

    private static String appliedAttributeSummary(int program) {
        return String.join(", ",
                appliedAttributeSummary(program, "Position"),
                appliedAttributeSummary(program, "Color"),
                appliedAttributeSummary(program, "UV0"),
                appliedAttributeSummary(program, "UV1"),
                appliedAttributeSummary(program, "UV2"),
                appliedAttributeSummary(program, "Normal"));
    }

    private static String appliedAttributeSummary(int program,
                                                  String name) {
        int location = GL20.glGetAttribLocation(program, name);
        if (location < 0) {
            return name + "=missing";
        }
        return String.format(
                Locale.ROOT,
                "%s=%d(enabled=%d,size=%d,type=0x%X,norm=%d,stride=%d,"
                        + "buffer=%d)",
                name,
                location,
                GL20.glGetVertexAttribi(
                        location, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED),
                GL20.glGetVertexAttribi(
                        location, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE),
                GL20.glGetVertexAttribi(
                        location, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE),
                GL20.glGetVertexAttribi(
                        location, GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED),
                GL20.glGetVertexAttribi(
                        location, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE),
                GL20.glGetVertexAttribi(
                        location,
                        GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
    }

    private static String formatAppliedUniform(int program, String name) {
        float[] values = readAppliedUniform(program, name, 1);
        if (values == null) {
            return "missing";
        }
        return String.format(Locale.ROOT, "%.9g", values[0]);
    }

    private static float[] readAppliedUniform(int program,
                                              String name,
                                              int count) {
        if (program <= 0 || count < 1) {
            return null;
        }
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            return null;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count);
            GL20.glGetUniformfv(program, location, values);
            float[] result = new float[count];
            for (int component = 0; component < count; component++) {
                result[component] = values.get(component);
            }
            return result;
        }
    }

    private static String maximumUniformDelta(float[] actual,
                                              Matrix4f expected) {
        if (actual == null || actual.length < 16) {
            return "missing";
        }
        float[] expectedValues = new float[16];
        expected.get(expectedValues);
        float maximum = 0.0f;
        for (int component = 0; component < 16; component++) {
            maximum = Math.max(
                    maximum,
                    Math.abs(actual[component] - expectedValues[component]));
        }
        return String.format(Locale.ROOT, "%.9g", maximum);
    }

    private ProjectionStatistics collectProjectionStatistics(
            DarkBallVolumeBuildResult volume,
            Camera camera,
            Matrix4f modelView,
            Matrix4f projection,
            int[] viewport) {
        SurfaceSamplePlan samples = uploadedDiagnosticSamplePlan;
        if (samples == null || samples.releaseOrder().length == 0) {
            return ProjectionStatistics.EMPTY;
        }
        Vec3 rootCamera = volume.root().subtract(camera.getPosition());
        Vec3 axis = volume.axis();
        Vec3 side = volume.side();
        Vec3 up = volume.up();
        float maximumVoxel = maximumVoxelSize(volume);
        float projectedReach = uploadedProjectedReachRadius();
        float projectionScale = Math.max(
                Math.abs(projection.m00()),
                Math.abs(projection.m11()));
        float modelViewScale = maximumLinearScale(modelView);

        int finiteCount = 0;
        int nonFiniteCount = 0;
        int badWCount = 0;
        int outsideViewportCount = 0;
        int shaderExtremeCount = 0;
        float minimumW = Float.POSITIVE_INFINITY;
        float maximumW = Float.NEGATIVE_INFINITY;
        float minimumNdcX = Float.POSITIVE_INFINITY;
        float maximumNdcX = Float.NEGATIVE_INFINITY;
        float minimumNdcY = Float.POSITIVE_INFINITY;
        float maximumNdcY = Float.NEGATIVE_INFINITY;
        float maximumAbsNdc = 0.0f;
        float maximumEstimatedReachPixels = 0.0f;
        float minimumViewZ = Float.POSITIVE_INFINITY;
        float maximumViewZ = Float.NEGATIVE_INFINITY;
        float maximumCameraDistance = 0.0f;

        for (int sample = 0;
             sample < samples.releaseOrder().length;
             sample++) {
            int triple = sample * 3;
            float sheetScore = Mth.clamp(
                    samples.thinSheetScore()[sample], 0.0f, 1.0f);
            float inset = surfaceInsetDistance(
                    maximumVoxel,
                    samples.localThickness()[sample])
                    * (1.0f - smoothstep(
                    DarkBallSplatThinSheetPlan.MIN_PAIR_SCORE,
                    0.82f,
                    sheetScore));
            float localX = samples.positions()[triple]
                    - samples.normals()[triple] * inset;
            float localY = samples.positions()[triple + 1]
                    - samples.normals()[triple + 1] * inset;
            float localZ = samples.positions()[triple + 2]
                    - samples.normals()[triple + 2] * inset;
            float cameraX = (float) (rootCamera.x
                    + axis.x * localX
                    + side.x * localY
                    + up.x * localZ);
            float cameraY = (float) (rootCamera.y
                    + axis.y * localX
                    + side.y * localY
                    + up.y * localZ);
            float cameraZ = (float) (rootCamera.z
                    + axis.z * localX
                    + side.z * localY
                    + up.z * localZ);
            maximumCameraDistance = Math.max(
                    maximumCameraDistance,
                    (float) Math.sqrt(cameraX * cameraX
                            + cameraY * cameraY
                            + cameraZ * cameraZ));
            Vector4f view = new Vector4f(
                    cameraX, cameraY, cameraZ, 1.0f);
            modelView.transform(view);
            Vector4f clip = new Vector4f(view);
            projection.transform(clip);
            if (!finiteVector(view) || !finiteVector(clip)) {
                nonFiniteCount++;
                continue;
            }
            finiteCount++;
            minimumViewZ = Math.min(minimumViewZ, view.z);
            maximumViewZ = Math.max(maximumViewZ, view.z);
            minimumW = Math.min(minimumW, clip.w);
            maximumW = Math.max(maximumW, clip.w);
            if (clip.w <= 0.0001f) {
                badWCount++;
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
                nonFiniteCount++;
                continue;
            }
            minimumNdcX = Math.min(minimumNdcX, ndcX);
            maximumNdcX = Math.max(maximumNdcX, ndcX);
            minimumNdcY = Math.min(minimumNdcY, ndcY);
            maximumNdcY = Math.max(maximumNdcY, ndcY);
            float absNdc = Math.max(Math.abs(ndcX), Math.abs(ndcY));
            maximumAbsNdc = Math.max(maximumAbsNdc, absNdc);
            if (absNdc > 1.0f) {
                outsideViewportCount++;
            }
            if (absNdc > 8.0f) {
                shaderExtremeCount++;
            }
            float estimatedReachNdc = projectedReach
                    * projectionScale
                    * modelViewScale
                    / clip.w;
            float estimatedReachPixels = estimatedReachNdc * 0.5f
                    * Math.max(viewport[2], viewport[3]);
            if (Float.isFinite(estimatedReachPixels)) {
                maximumEstimatedReachPixels = Math.max(
                        maximumEstimatedReachPixels,
                        estimatedReachPixels);
            }
        }
        return new ProjectionStatistics(
                samples.releaseOrder().length,
                finiteCount,
                nonFiniteCount,
                badWCount,
                outsideViewportCount,
                shaderExtremeCount,
                minimumW,
                maximumW,
                minimumNdcX,
                maximumNdcX,
                minimumNdcY,
                maximumNdcY,
                maximumAbsNdc,
                maximumEstimatedReachPixels,
                minimumViewZ,
                maximumViewZ,
                maximumCameraDistance);
    }

    private static String projectLocalPoint(
            Vector3f local,
            DarkBallVolumeBuildResult volume,
            Camera camera,
            Matrix4f modelView,
            Matrix4f projection) {
        Vec3 rootCamera = volume.root().subtract(camera.getPosition());
        Vec3 axis = volume.axis();
        Vec3 side = volume.side();
        Vec3 up = volume.up();
        Vector4f view = new Vector4f(
                (float) (rootCamera.x + axis.x * local.x
                        + side.x * local.y + up.x * local.z),
                (float) (rootCamera.y + axis.y * local.x
                        + side.y * local.y + up.y * local.z),
                (float) (rootCamera.z + axis.z * local.x
                        + side.z * local.y + up.z * local.z),
                1.0f);
        modelView.transform(view);
        Vector4f clip = new Vector4f(view);
        projection.transform(clip);
        if (!finiteVector(view) || !finiteVector(clip)) {
            return "non-finite";
        }
        if (clip.w <= 0.0001f) {
            return String.format(
                    Locale.ROOT,
                    "behind(w=%.6g viewZ=%.6g)",
                    clip.w,
                    view.z);
        }
        return String.format(
                Locale.ROOT,
                "ndc=(%.6g,%.6g,%.6g) w=%.6g viewZ=%.6g",
                clip.x / clip.w,
                clip.y / clip.w,
                clip.z / clip.w,
                clip.w,
                view.z);
    }

    private static int diagnosticPhaseBit(Parameters parameters) {
        if (parameters.releaseFront() > -0.5f) {
            return DIAGNOSTIC_COLLAPSE;
        }
        if (parameters.turbulenceBlend() > 0.001f) {
            return DIAGNOSTIC_TURBULENCE;
        }
        return DIAGNOSTIC_ACTIVATION;
    }

    private static String diagnosticPhaseName(Parameters parameters) {
        return switch (diagnosticPhaseBit(parameters)) {
            case DIAGNOSTIC_COLLAPSE -> "collapse";
            case DIAGNOSTIC_TURBULENCE -> "turbulence";
            default -> "formation";
        };
    }

    private static String basisSummary(DarkBallVolumeBuildResult volume) {
        Vec3 axis = volume.axis();
        Vec3 side = volume.side();
        Vec3 up = volume.up();
        return String.format(
                Locale.ROOT,
                "lengths=(%.6g,%.6g,%.6g) dots=(%.6g,%.6g,%.6g) "
                        + "det=%.6g",
                axis.length(), side.length(), up.length(),
                axis.dot(side), axis.dot(up), side.dot(up),
                axis.dot(side.cross(up)));
    }

    private static String formatVec3(Vec3 value) {
        return String.format(
                Locale.ROOT,
                "(%.6g,%.6g,%.6g)",
                value.x, value.y, value.z);
    }

    private static String formatVector3f(Vector3f value) {
        return String.format(
                Locale.ROOT,
                "(%.6g,%.6g,%.6g)",
                value.x, value.y, value.z);
    }

    private static String formatMatrix(Matrix4f matrix) {
        return String.format(
                Locale.ROOT,
                "[[%.6g,%.6g,%.6g,%.6g],"
                        + "[%.6g,%.6g,%.6g,%.6g],"
                        + "[%.6g,%.6g,%.6g,%.6g],"
                        + "[%.6g,%.6g,%.6g,%.6g]]",
                matrix.m00(), matrix.m10(), matrix.m20(), matrix.m30(),
                matrix.m01(), matrix.m11(), matrix.m21(), matrix.m31(),
                matrix.m02(), matrix.m12(), matrix.m22(), matrix.m32(),
                matrix.m03(), matrix.m13(), matrix.m23(), matrix.m33());
    }

    private static float maximumMatrixDelta(Matrix4f first,
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

    private static float maximumLinearScale(Matrix4f matrix) {
        float first = (float) Math.sqrt(
                matrix.m00() * matrix.m00()
                        + matrix.m01() * matrix.m01()
                        + matrix.m02() * matrix.m02());
        float second = (float) Math.sqrt(
                matrix.m10() * matrix.m10()
                        + matrix.m11() * matrix.m11()
                        + matrix.m12() * matrix.m12());
        float third = (float) Math.sqrt(
                matrix.m20() * matrix.m20()
                        + matrix.m21() * matrix.m21()
                        + matrix.m22() * matrix.m22());
        return Math.max(first, Math.max(second, third));
    }

    private static boolean finiteVector(Vector4f value) {
        return Float.isFinite(value.x)
                && Float.isFinite(value.y)
                && Float.isFinite(value.z)
                && Float.isFinite(value.w);
    }

    private SurfaceInsetStatistics writeSamples(
            long vertices,
            int vertexStride,
            DarkBallSurfaceMesh mesh,
            SurfaceSamplePlan samples,
            float maximumVoxel) {
        float[] localThickness = mesh.localThickness();
        thicknessScale = findThicknessScale(localThickness);
        int sampleCount = samples.releaseOrder().length;
        DarkBallSplatBoneRoutePlan.Result boneRoute =
                samples.boneRoutePlan();
        if (boneRoute != null
                && (boneRoute.attachmentNodes().length != sampleCount
                || boneRoute.attachmentT().length != sampleCount
                || boneRoute.routeConfidence().length != sampleCount
                || boneRoute.normalizedPathLength().length
                != sampleCount)) {
            throw new IllegalArgumentException(
                    "surface-splat bone route does not match samples");
        }
        float insetSum = 0.0f;
        float maximumInset = 0.0f;

        for (int sample = 0; sample < sampleCount; sample++) {
            int triple = sample * 3;
            float sheetScore = Mth.clamp(
                    samples.thinSheetScore()[sample],
                    0.0f,
                    1.0f);
            float inset = surfaceInsetDistance(
                    maximumVoxel,
                    samples.localThickness()[sample])
                    * (1.0f - smoothstep(
                    DarkBallSplatThinSheetPlan.MIN_PAIR_SCORE,
                    0.82f,
                    sheetScore));
            insetSum += inset;
            maximumInset = Math.max(maximumInset, inset);
            float centerX = samples.positions()[triple]
                    - samples.normals()[triple] * inset;
            float centerY = samples.positions()[triple + 1]
                    - samples.normals()[triple + 1] * inset;
            float centerZ = samples.positions()[triple + 2]
                    - samples.normals()[triple + 2] * inset;
            for (int triangleVertex = 0;
                 triangleVertex < TRIANGLE_CORNERS.length;
                 triangleVertex++) {
                int corner = TRIANGLE_CORNERS[triangleVertex];
                int uploadedVertex = sample * TRIANGLE_CORNERS.length
                        + triangleVertex;
                long address =
                        vertices + (long) uploadedVertex * vertexStride;
                putFiniteFloat(address + POSITION_OFFSET,
                        centerX, "position");
                putFiniteFloat(address + POSITION_OFFSET + 4L,
                        centerY, "position");
                putFiniteFloat(address + POSITION_OFFSET + 8L,
                        centerZ, "position");

                int encodedThickness = Math.round(Mth.clamp(
                        samples.localThickness()[sample] / thicknessScale,
                        0.0f,
                        1.0f) * 255.0f);
                int encodedPresentationRelease = Math.round(Mth.clamp(
                        samples.presentationReleaseOrder()[sample],
                        0.0f,
                        1.0f) * 255.0f);
                MemoryUtil.memPutByte(address + COLOR_OFFSET,
                        (byte) encodedThickness);
                MemoryUtil.memPutByte(address + COLOR_OFFSET + 1L,
                        (byte) encodedPresentationRelease);
                int attachmentNode = boneRoute == null
                        ? -1
                        : boneRoute.attachmentNodes()[sample];
                int encodedAttachmentNode =
                        encodeAttachmentAndThinSheetMetadata(
                                attachmentNode,
                                sheetScore);
                int encodedAttachmentT = encodeAttachmentTAndCorner(
                        boneRoute == null
                                ? 0.0f
                                : boneRoute.attachmentT()[sample],
                        corner);
                MemoryUtil.memPutByte(address + COLOR_OFFSET + 2L,
                        (byte) encodedAttachmentNode);
                MemoryUtil.memPutByte(address + COLOR_OFFSET + 3L,
                        (byte) encodedAttachmentT);

                putFiniteFloat(address + UV0_OFFSET,
                        Mth.clamp(samples.releaseOrder()[sample],
                                0.0f,
                                0.9999f),
                        "release order");
                putFiniteFloat(address + UV0_OFFSET + 4L,
                        Mth.clamp(samples.transportOrder()[sample],
                                0.0f,
                                1.0f),
                        "transport order");

                // The direct splat path no longer consumes the mesh-sweep
                // payload. Reuse the same four packed shorts for stable,
                // one-time neighborhood coverage and principal-direction
                // metadata without increasing the persistent vertex stride.
                MemoryUtil.memPutShort(
                        address + UV1_OFFSET,
                        encodeRange(
                                samples.spacingScale()[sample],
                                DarkBallSplatNeighborhoodPlan
                                        .MIN_SPACING_SCALE,
                                DarkBallSplatNeighborhoodPlan
                                        .MAX_SPACING_SCALE));
                MemoryUtil.memPutShort(
                        address + UV1_OFFSET + 2L,
                        encodeUnit(samples.coherence()[sample]));
                MemoryUtil.memPutShort(
                        address + UV2_OFFSET,
                        encodeTangentFrameMetadata(
                                samples.tangentAngle()[sample],
                                samples.tangentConfidence()[sample]));
                MemoryUtil.memPutShort(
                        address + UV2_OFFSET + 2L,
                        encodeBoneRouteMetadata(
                                attachmentNode,
                                boneRoute == null
                                        ? 0.0f
                                        : boneRoute.routeConfidence()[sample],
                                boneRoute == null
                                        ? 0.0f
                                        : boneRoute.normalizedPathLength()[
                                        sample]));

                putNormal(address + NORMAL_OFFSET,
                        samples.normals()[triple]);
                putNormal(address + NORMAL_OFFSET + 1L,
                        samples.normals()[triple + 1]);
                putNormal(address + NORMAL_OFFSET + 2L,
                        samples.normals()[triple + 2]);
            }
        }
        return new SurfaceInsetStatistics(
                sampleCount > 0 ? insetSum / sampleCount : 0.0f,
                maximumInset,
                maximumVoxel * SURFACE_INSET_VOXEL_FACTOR);
    }

    /**
     * Pulls the uploaded surfel center just inside the conservative binary-SDF
     * surface. The voxel limit removes the rasterization half-cell bias while
     * the thickness limit protects narrow appendages from crossing through
     * their local medial region.
     */
    static float surfaceInsetDistance(float maximumVoxel,
                                      float localThickness) {
        float finiteVoxel = Float.isFinite(maximumVoxel)
                ? Math.max(maximumVoxel, 0.0f)
                : 0.0f;
        float finiteThickness = Float.isFinite(localThickness)
                ? Math.max(localThickness, 0.0f)
                : 0.0f;
        return Math.min(
                finiteVoxel * SURFACE_INSET_VOXEL_FACTOR,
                finiteThickness * SURFACE_INSET_THICKNESS_FRACTION);
    }

    /**
     * Returns the exact immutable carrier centers uploaded to the splat VBO.
     * Route preparation uses this copy so its first-segment lengths match the
     * shader's undeformed {@code Position} attribute.
     */
    static float[] insetSamplePositions(
            SurfaceSamplePlan samples,
            float maximumVoxel) {
        if (samples == null) {
            return new float[0];
        }
        float[] result = samples.positions().clone();
        for (int sample = 0;
             sample < samples.releaseOrder().length;
             sample++) {
            int triple = sample * 3;
            float inset = surfaceInsetDistance(
                    maximumVoxel,
                    samples.localThickness()[sample]);
            result[triple] -= samples.normals()[triple] * inset;
            result[triple + 1] -=
                    samples.normals()[triple + 1] * inset;
            result[triple + 2] -=
                    samples.normals()[triple + 2] * inset;
        }
        return result;
    }

    private static float maximumVoxelSize(
            DarkBallVolumeBuildResult volume) {
        return Math.max(
                Math.max(volume.sdfVoxelX(), volume.sdfVoxelYz()),
                0.0001f);
    }

    private void configureShader(ShaderInstance shader,
                                 DarkBallVolumeBuildResult volume,
                                 Camera camera,
                                 Parameters parameters) {
        Vec3 cameraRelativeRoot =
                volume.root().subtract(camera.getPosition());
        setVec3(shader, "VolumeRootCameraRelative",
                cameraRelativeRoot);
        setVec3(shader, "VolumeAxis", volume.axis());
        setVec3(shader, "VolumeSide", volume.side());
        setVec3(shader, "VolumeUp", volume.up());
        setVec3(shader, "InletLocal", parameters.inletLocal());
        setVec3(shader, "SpikeTransportOrigin",
                parameters.spikeTransportOriginLocal());
        setVec3(shader, "SiphonTangentLocal",
                parameters.siphonTangentLocal());
        setFloat(shader, "BodyRadius", Math.max(
                volume.bodyRadius(),
                0.0001f));
        setFloat(shader, "VoxelSize", Math.max(
                Math.min(volume.sdfVoxelX(), volume.sdfVoxelYz()),
                0.0001f));
        setFloat(shader, "ThicknessScale", thicknessScale);
        setFloat(shader, "SplatRadius", splatRadius);
        setFloat(shader, "ReleaseFront",
                finiteOr(parameters.releaseFront(), -1.0f));
        DarkBallSplatTransportMath.Parameters transport =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        setFloat(shader, "TransportActivationHalfWidth",
                transport.activationHalfWidth());
        setFloat(shader, "TransportFrontSpan",
                transport.travelFrontSpan());
        setFloat(shader, "TransportMinimumCrossSection",
                transport.minimumCrossSectionScale());
        setFloat(shader, "TransportThroatCoordinate",
                transport.throatCoordinate());
        setFloat(shader, "TransportTerminalCoordinate",
                transport.terminalCoordinate());
        setFloat(shader, "TransportRetirementWidth",
                transport.retirementWidth());
        setFloat(shader, "TransportCollarExitCoordinate",
                transport.collarExitCoordinate());
        setFloat(shader, "TransportCollarExitWidth",
                transport.collarExitWidth());
        setFloat(shader, "CollapseLocality", Mth.clamp(
                finiteOr(parameters.collapseLocality(), 1.0f),
                MIN_COLLAPSE_LOCALITY,
                1.0f));
        setFloat(shader, "TurbulenceBlend", Mth.clamp(
                finiteOr(parameters.turbulenceBlend(), 0.0f),
                0.0f,
                1.0f));
        setFloat(shader, "TurbulenceComplexityBlend", Mth.clamp(
                finiteOr(parameters.turbulenceComplexityBlend(), 0.0f),
                0.0f,
                1.0f));
        setFloat(shader, "SpikeFlowTime", Math.max(
                finiteOr(parameters.spikeFlowTime(), 0.0f),
                0.0f));
        setFloat(shader, "DeformationFlowTime", Math.max(
                finiteOr(parameters.deformationFlowTime(), 0.0f),
                0.0f));
        setFloat(shader, "EffectFade", Mth.clamp(
                finiteOr(parameters.effectFade(), 1.0f),
                0.0f,
                1.0f));
        configureBoneRouteShader(
                shader,
                parameters.preparedSamples());
    }

    private static void configureBoneRouteShader(
            ShaderInstance shader,
            SurfaceSamplePlan samples) {
        DarkBallSplatBoneRoutePlan.Result route =
                samples == null ? null : samples.boneRoutePlan();
        boolean enabled = route != null
                && route.paletteParents().length > 1
                && route.paletteParents().length <= 32
                && route.maximumPathLength() > 1.0e-6f;
        setFloat(shader, "BoneRouteEnabled",
                enabled ? 1.0f : 0.0f);
        setFloat(shader, "BoneRoutePathScale",
                enabled ? route.maximumPathLength() : 1.0f);
        if (!enabled) {
            // BoneRouteEnabled is authoritative. Retain the previous palette
            // while inactive instead of replacing eight matrices every
            // formation/turbulence frame.
            return;
        }
        for (int matrix = 0; matrix < 8; matrix++) {
            setMatrix(
                    shader,
                    "BoneRouteNode" + matrix,
                    boneRoutePaletteMatrix(route, matrix));
        }
    }

    /**
     * Uses eight matrix uniforms as a compact array of thirty-two route
     * nodes. Each matrix column is one
     * {@code vec4(center.xyz, parentIndex)}; it is data, not a transform.
     */
    private static Matrix4f boneRoutePaletteMatrix(
            DarkBallSplatBoneRoutePlan.Result route,
            int matrixIndex) {
        Matrix4f result = new Matrix4f().zero();
        float[] positions = route.palettePositions();
        int[] parents = route.paletteParents();
        for (int column = 0; column < 4; column++) {
            int node = matrixIndex * 4 + column;
            if (node >= parents.length) {
                break;
            }
            int triple = node * 3;
            float x = positions[triple];
            float y = positions[triple + 1];
            float z = positions[triple + 2];
            float parent = parents[node];
            switch (column) {
                case 0 -> {
                    result.m00(x);
                    result.m01(y);
                    result.m02(z);
                    result.m03(parent);
                }
                case 1 -> {
                    result.m10(x);
                    result.m11(y);
                    result.m12(z);
                    result.m13(parent);
                }
                case 2 -> {
                    result.m20(x);
                    result.m21(y);
                    result.m22(z);
                    result.m23(parent);
                }
                case 3 -> {
                    result.m30(x);
                    result.m31(y);
                    result.m32(z);
                    result.m33(parent);
                }
                default -> throw new AssertionError(column);
            }
        }
        return result;
    }

    static int sampleBudget(DarkBallVfxQuality quality) {
        DarkBallVfxQuality resolvedQuality = quality == null
                ? DarkBallVfxQuality.MEDIUM
                : quality;
        return switch (resolvedQuality) {
            case LOW -> 1350;
            case MEDIUM -> 2200;
            case HIGH -> 3200;
        };
    }

    static int resolveSampleBudget(DarkBallVfxQuality quality,
                                   int manualBudget) {
        if (manualBudget <= 0) {
            return sampleBudget(quality);
        }
        return Mth.clamp(
                manualBudget,
                1,
                MAX_MANUAL_SURFEL_BUDGET);
    }

    static SurfaceSamplePlan prepareSamples(
            DarkBallSurfaceMesh mesh,
            DarkBallVfxQuality quality) {
        return prepareSamples(mesh, quality, 0.0f);
    }

    static SurfaceSamplePlan prepareSamples(
            DarkBallSurfaceMesh mesh,
            DarkBallVfxQuality quality,
            float maximumVoxel) {
        return prepareSamples(
                mesh,
                sampleBudget(quality),
                maximumVoxel);
    }

    static SurfaceSamplePlan prepareSamples(
            DarkBallSurfaceMesh mesh,
            int requestedSampleBudget,
            float maximumVoxel) {
        if (mesh == null || !mesh.hasFiniteSplatSamples()) {
            return null;
        }
        int sampleCount = Math.min(
                mesh.vertexCount(),
                Mth.clamp(
                        requestedSampleBudget,
                        1,
                        MAX_MANUAL_SURFEL_BUDGET));
        return sampleCount > 0
                ? buildAreaStratifiedSamples(
                mesh, sampleCount, maximumVoxel)
                : null;
    }

    /**
     * Samples triangle area instead of marching-cubes vertex order. The
     * extractor's vertex order follows voxel traversal and can leave broad
     * screen regions undersampled even when the total point count is high.
     * Equal cumulative-area targets plus a deterministic low-discrepancy
     * barycentric sequence first produce an oversubscribed candidate set.
     * A surface-aware farthest-point pass then retains a blue-noise-like
     * subset. This work runs as part of asynchronous capture preparation, not
     * on the render thread. A fixed part of the final sample budget is
     * distributed independently over thin and high-curvature triangles,
     * preventing large torso surfaces from consuming nearly every sample
     * needed by fins, fingers, ears, and narrow appendages.
     */
    private static SurfaceSamplePlan buildAreaStratifiedSamples(
            DarkBallSurfaceMesh mesh,
            int sampleCount,
            float maximumVoxel) {
        int[] indices = mesh.indices();
        float[] positions = mesh.positions();
        if (indices.length < 3 || indices.length % 3 != 0) {
            return buildVertexFallbackSamples(
                    mesh, sampleCount, maximumVoxel);
        }

        float maximumThickness = maximumFiniteThickness(
                mesh.localThickness());
        double totalArea = 0.0;
        double[] rawFeatureWeights =
                new double[indices.length / 3];
        for (int offset = 0; offset < indices.length; offset += 3) {
            double area = triangleArea(
                    positions,
                    indices[offset],
                    indices[offset + 1],
                    indices[offset + 2]);
            if (!Double.isFinite(area)) {
                return buildVertexFallbackSamples(
                        mesh, sampleCount, maximumVoxel);
            }
            if (area > MIN_SAMPLE_TRIANGLE_AREA) {
                totalArea += area;
                rawFeatureWeights[offset / 3] =
                        area * triangleFeatureSignal(
                        mesh,
                        indices[offset],
                        indices[offset + 1],
                        indices[offset + 2],
                        maximumThickness);
            }
        }
        ComponentFeatureWeights componentFeatures =
                balanceFeatureWeightsByComponent(
                        mesh.vertexCount(),
                        indices,
                        rawFeatureWeights);
        double totalFeatureWeight =
                componentFeatures.totalWeight();
        if (!Double.isFinite(totalArea)
                || totalArea <= MIN_SAMPLE_TRIANGLE_AREA) {
            return buildVertexFallbackSamples(
                    mesh, sampleCount, maximumVoxel);
        }

        int featureSamples = totalFeatureWeight > MIN_SAMPLE_TRIANGLE_AREA
                ? Math.min(
                sampleCount,
                Math.max(
                        0,
                        Math.round(sampleCount
                                * FEATURE_SAMPLE_FRACTION)))
                : 0;
        int areaSamples = sampleCount - featureSamples;
        int areaCandidateCount = Math.multiplyExact(
                areaSamples,
                BLUE_NOISE_CANDIDATE_MULTIPLIER);
        int featureCandidateCount = Math.multiplyExact(
                featureSamples,
                BLUE_NOISE_CANDIDATE_MULTIPLIER);
        int candidateCount = Math.addExact(
                areaCandidateCount,
                featureCandidateCount);
        SurfaceSamplePlan candidates =
                new SurfaceSamplePlan(candidateCount);
        int generatedArea = writeStratifiedDistribution(
                candidates,
                0,
                areaCandidateCount,
                mesh,
                totalArea,
                null,
                false);
        int generatedFeatures = writeStratifiedDistribution(
                candidates,
                areaCandidateCount,
                featureCandidateCount,
                mesh,
                totalFeatureWeight,
                componentFeatures.weights(),
                true);
        if (generatedArea != areaCandidateCount
                || generatedFeatures != featureCandidateCount) {
            return buildVertexFallbackSamples(
                    mesh, sampleCount, maximumVoxel);
        }
        DarkBallSplatThinSheetPlan.stabilize(
                candidates, maximumVoxel);

        SurfaceSamplePlan plan = new SurfaceSamplePlan(sampleCount);
        int selected = selectBlueNoiseSubset(
                candidates,
                0,
                areaCandidateCount,
                plan,
                0,
                areaSamples);
        selected += selectPatchAwareBlueNoiseSubset(
                candidates,
                areaCandidateCount,
                featureCandidateCount,
                plan,
                selected,
                featureSamples,
                maximumVoxel);
        if (selected != sampleCount) {
            return buildVertexFallbackSamples(
                    mesh, sampleCount, maximumVoxel);
        }
        DarkBallSplatThinSheetPlan.stabilize(
                plan, maximumVoxel);
        addNeighborhoodMetadata(plan);
        DarkBallSplatThinSheetPatchPlan.apply(
                plan, maximumVoxel);
        return plan;
    }

    /**
     * Deterministically retains a dispersed subset from one weighted
     * candidate distribution. Samples already written before
     * {@code outputOffset} seed the distance field, so the feature reserve
     * supplements rather than duplicates the area distribution.
     */
    static int selectBlueNoiseSubset(
            SurfaceSamplePlan candidates,
            int candidateOffset,
            int candidateCount,
            SurfaceSamplePlan output,
            int outputOffset,
            int selectionCount) {
        if (selectionCount <= 0) {
            return 0;
        }
        if (candidateOffset < 0
                || candidateCount < selectionCount
                || candidateOffset + candidateCount
                > candidates.releaseOrder().length
                || outputOffset < 0
                || outputOffset + selectionCount
                > output.releaseOrder().length) {
            throw new IllegalArgumentException(
                    "invalid blue-noise candidate or output range");
        }

        boolean[] selected = new boolean[candidateCount];
        double[] nearestDistanceSquared =
                new double[candidateCount];
        Arrays.fill(
                nearestDistanceSquared,
                Double.POSITIVE_INFINITY);

        for (int localCandidate = 0;
             localCandidate < candidateCount;
             localCandidate++) {
            int candidate = candidateOffset + localCandidate;
            for (int retained = 0;
                 retained < outputOffset;
                 retained++) {
                double distanceSquared =
                        compatibleSampleDistanceSquared(
                                candidates,
                                candidate,
                                output,
                                retained);
                if (distanceSquared
                        < nearestDistanceSquared[localCandidate]) {
                    nearestDistanceSquared[localCandidate] =
                            distanceSquared;
                }
            }
        }

        int firstCandidate = outputOffset == 0
                ? farthestCandidateFromCentroid(
                candidates,
                candidateOffset,
                candidateCount)
                : -1;
        for (int retained = 0;
             retained < selectionCount;
             retained++) {
            int selectedLocal = retained == 0
                    && firstCandidate >= 0
                    ? firstCandidate - candidateOffset
                    : farthestUnselectedCandidate(
                    nearestDistanceSquared,
                    selected);
            if (selectedLocal < 0) {
                return retained;
            }
            int selectedCandidate =
                    candidateOffset + selectedLocal;
            copySurfaceSample(
                    output,
                    outputOffset + retained,
                    candidates,
                    selectedCandidate);
            selected[selectedLocal] = true;

            for (int localCandidate = 0;
                 localCandidate < candidateCount;
                 localCandidate++) {
                if (selected[localCandidate]) {
                    continue;
                }
                double distanceSquared =
                        compatibleSampleDistanceSquared(
                                candidates,
                                candidateOffset + localCandidate,
                                candidates,
                                selectedCandidate);
                if (distanceSquared
                        < nearestDistanceSquared[localCandidate]) {
                    nearestDistanceSquared[localCandidate] =
                            distanceSquared;
                }
            }
        }
        return selectionCount;
    }

    /**
     * Retains a dispersed feature subset while reserving a small, bounded
     * quota for every detected thin planar patch. Inside a patch, distance is
     * measured in its logical mid-surface plane, so front/back SDF faces do
     * not consume the fixed budget as if they were separate geometry.
     */
    static int selectPatchAwareBlueNoiseSubset(
            SurfaceSamplePlan candidates,
            int candidateOffset,
            int candidateCount,
            SurfaceSamplePlan output,
            int outputOffset,
            int selectionCount,
            float maximumVoxel) {
        if (selectionCount <= 0) {
            return 0;
        }
        if (candidateOffset < 0
                || candidateCount < selectionCount
                || candidateOffset + candidateCount
                > candidates.releaseOrder().length
                || outputOffset < 0
                || outputOffset + selectionCount
                > output.releaseOrder().length) {
            throw new IllegalArgumentException(
                    "invalid patch-aware candidate or output range");
        }

        DarkBallSplatThinSheetPatchPlan.Layout layout =
                DarkBallSplatThinSheetPatchPlan.analyze(
                        candidates,
                        candidateOffset,
                        candidateCount,
                        maximumVoxel);
        if (layout.patchCount() == 0) {
            return selectBlueNoiseSubset(
                    candidates,
                    candidateOffset,
                    candidateCount,
                    output,
                    outputOffset,
                    selectionCount);
        }
        for (int local = 0; local < candidateCount; local++) {
            int candidate = candidateOffset + local;
            candidates.thinSheetPatchId()[candidate] =
                    layout.patchId(candidate);
        }

        boolean[] selected = new boolean[candidateCount];
        double[] nearestDistanceSquared =
                new double[candidateCount];
        Arrays.fill(
                nearestDistanceSquared,
                Double.POSITIVE_INFINITY);
        for (int localCandidate = 0;
             localCandidate < candidateCount;
             localCandidate++) {
            int candidate = candidateOffset + localCandidate;
            for (int retained = 0;
                 retained < outputOffset;
                 retained++) {
                double distanceSquared =
                        compatibleSampleDistanceSquared(
                                candidates,
                                candidate,
                                output,
                                retained);
                if (distanceSquared
                        < nearestDistanceSquared[localCandidate]) {
                    nearestDistanceSquared[localCandidate] =
                            distanceSquared;
                }
            }
        }

        int patchQuota = Math.min(
                DarkBallSplatThinSheetPatchPlan
                        .MIN_PATCH_FEATURE_QUOTA,
                Math.max(
                        1,
                        selectionCount
                                / Math.max(
                                layout.patchCount() * 2,
                                1)));
        int retainedCount = 0;
        for (int quotaRound = 0;
             quotaRound < patchQuota
                     && retainedCount < selectionCount;
             quotaRound++) {
            for (int patch = 0;
                 patch < layout.patchCount()
                         && retainedCount < selectionCount;
                 patch++) {
                if (quotaRound >= layout.patchSize(patch)) {
                    continue;
                }
                int selectedLocal =
                        farthestUnselectedCandidateInPatch(
                                candidates,
                                candidateOffset,
                                candidateCount,
                                layout,
                                patch,
                                nearestDistanceSquared,
                                selected);
                if (selectedLocal < 0) {
                    continue;
                }
                retainPatchCandidate(
                        candidates,
                        candidateOffset,
                        candidateCount,
                        output,
                        outputOffset + retainedCount,
                        layout,
                        selectedLocal,
                        selected,
                        nearestDistanceSquared);
                retainedCount++;
            }
        }

        while (retainedCount < selectionCount) {
            int selectedLocal = farthestUnselectedCandidate(
                    nearestDistanceSquared, selected);
            if (selectedLocal < 0) {
                return retainedCount;
            }
            retainPatchCandidate(
                    candidates,
                    candidateOffset,
                    candidateCount,
                    output,
                    outputOffset + retainedCount,
                    layout,
                    selectedLocal,
                    selected,
                    nearestDistanceSquared);
            retainedCount++;
        }
        return retainedCount;
    }

    private static int farthestUnselectedCandidateInPatch(
            SurfaceSamplePlan candidates,
            int candidateOffset,
            int candidateCount,
            DarkBallSplatThinSheetPatchPlan.Layout layout,
            int patch,
            double[] nearestDistanceSquared,
            boolean[] selected) {
        int farthestFinite = -1;
        double farthestDistance = -1.0;
        for (int local = 0; local < candidateCount; local++) {
            int candidate = candidateOffset + local;
            if (selected[local]
                    || layout.patchId(candidate) != patch
                    || !Double.isFinite(
                    nearestDistanceSquared[local])) {
                continue;
            }
            if (nearestDistanceSquared[local]
                    > farthestDistance) {
                farthestDistance =
                        nearestDistanceSquared[local];
                farthestFinite = local;
            }
        }
        if (farthestFinite >= 0) {
            return farthestFinite;
        }

        double centerX = 0.0;
        double centerY = 0.0;
        double centerZ = 0.0;
        int memberCount = 0;
        for (int local = 0; local < candidateCount; local++) {
            int candidate = candidateOffset + local;
            if (selected[local]
                    || layout.patchId(candidate) != patch) {
                continue;
            }
            int triple = candidate * 3;
            centerX += candidates.positions()[triple];
            centerY += candidates.positions()[triple + 1];
            centerZ += candidates.positions()[triple + 2];
            memberCount++;
        }
        if (memberCount == 0) {
            return -1;
        }
        centerX /= memberCount;
        centerY /= memberCount;
        centerZ /= memberCount;

        int farthest = -1;
        double farthestFromCenter = -1.0;
        for (int local = 0; local < candidateCount; local++) {
            int candidate = candidateOffset + local;
            if (selected[local]
                    || layout.patchId(candidate) != patch) {
                continue;
            }
            int triple = candidate * 3;
            double dx =
                    candidates.positions()[triple] - centerX;
            double dy =
                    candidates.positions()[triple + 1] - centerY;
            double dz =
                    candidates.positions()[triple + 2] - centerZ;
            double distanceSquared =
                    dx * dx + dy * dy + dz * dz;
            if (distanceSquared > farthestFromCenter) {
                farthestFromCenter = distanceSquared;
                farthest = local;
            }
        }
        return farthest;
    }

    private static void retainPatchCandidate(
            SurfaceSamplePlan candidates,
            int candidateOffset,
            int candidateCount,
            SurfaceSamplePlan output,
            int outputSample,
            DarkBallSplatThinSheetPatchPlan.Layout layout,
            int selectedLocal,
            boolean[] selected,
            double[] nearestDistanceSquared) {
        int selectedCandidate =
                candidateOffset + selectedLocal;
        copySurfaceSample(
                output,
                outputSample,
                candidates,
                selectedCandidate);
        selected[selectedLocal] = true;
        for (int local = 0; local < candidateCount; local++) {
            if (selected[local]) {
                continue;
            }
            int candidate = candidateOffset + local;
            double distanceSquared;
            if (layout.patchId(candidate) >= 0
                    && layout.patchId(candidate)
                    == layout.patchId(selectedCandidate)) {
                distanceSquared =
                        layout.inPlaneDistanceSquared(
                                candidates,
                                candidate,
                                selectedCandidate);
            } else {
                distanceSquared =
                        compatibleSampleDistanceSquared(
                                candidates,
                                candidate,
                                candidates,
                                selectedCandidate);
            }
            if (distanceSquared
                    < nearestDistanceSquared[local]) {
                nearestDistanceSquared[local] =
                        distanceSquared;
            }
        }
    }

    /**
     * Replays the uploaded surfels into a depth-writing target. Coverage still
     * comes exclusively from the fused accumulation/resolve path; this pass
     * records only the nearest supported oriented footprint distance.
     */
    boolean renderFrontDepth(Camera camera) {
        ShaderInstance shader = ModShaders.DARK_BALL_SURFACE_SPLAT;
        if (!RenderSystem.isOnRenderThread()
                || shader == null
                || camera == null
                || vertexBuffer == null
                || vertexBuffer.isInvalid()) {
            return false;
        }

        RenderState previous = RenderState.capture();
        try {
            setFloat(shader, "SplatDiagnosticMode", 2.0f);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();

            vertexBuffer.bind();
            Matrix4f modelView =
                    DarkBallRenderContext.modelView(camera);
            Matrix4f projection =
                    DarkBallRenderContext.projection();
            if (!validateBoundVertexLayout(
                    shader, modelView, projection, null)) {
                return false;
            }
            vertexBuffer.drawWithShader(
                    modelView,
                    projection,
                    shader);
            return true;
        } catch (Throwable failure) {
            logFrontDepthFailureOnce(failure);
            return false;
        } finally {
            setFloat(shader, "SplatDiagnosticMode", 0.0f);
            VertexBuffer.unbind();
            previous.restore();
        }
    }

    private static int farthestCandidateFromCentroid(
            SurfaceSamplePlan candidates,
            int candidateOffset,
            int candidateCount) {
        double centerX = 0.0;
        double centerY = 0.0;
        double centerZ = 0.0;
        for (int localCandidate = 0;
             localCandidate < candidateCount;
             localCandidate++) {
            int triple =
                    (candidateOffset + localCandidate) * 3;
            centerX += candidates.positions()[triple];
            centerY += candidates.positions()[triple + 1];
            centerZ += candidates.positions()[triple + 2];
        }
        centerX /= candidateCount;
        centerY /= candidateCount;
        centerZ /= candidateCount;

        int farthest = candidateOffset;
        double farthestDistanceSquared = -1.0;
        for (int localCandidate = 0;
             localCandidate < candidateCount;
             localCandidate++) {
            int candidate =
                    candidateOffset + localCandidate;
            int triple = candidate * 3;
            double dx =
                    candidates.positions()[triple] - centerX;
            double dy =
                    candidates.positions()[triple + 1] - centerY;
            double dz =
                    candidates.positions()[triple + 2] - centerZ;
            double distanceSquared =
                    dx * dx + dy * dy + dz * dz;
            if (distanceSquared > farthestDistanceSquared) {
                farthestDistanceSquared = distanceSquared;
                farthest = candidate;
            }
        }
        return farthest;
    }

    private static int farthestUnselectedCandidate(
            double[] nearestDistanceSquared,
            boolean[] selected) {
        int farthest = -1;
        double farthestDistanceSquared = -1.0;
        for (int candidate = 0;
             candidate < nearestDistanceSquared.length;
             candidate++) {
            if (selected[candidate]) {
                continue;
            }
            double distanceSquared =
                    nearestDistanceSquared[candidate];
            if (distanceSquared > farthestDistanceSquared) {
                farthestDistanceSquared = distanceSquared;
                farthest = candidate;
            }
        }
        return farthest;
    }

    private static double compatibleSampleDistanceSquared(
            SurfaceSamplePlan firstPlan,
            int first,
            SurfaceSamplePlan secondPlan,
            int second) {
        int firstTriple = first * 3;
        int secondTriple = second * 3;
        float normalDot =
                firstPlan.normals()[firstTriple]
                        * secondPlan.normals()[secondTriple]
                        + firstPlan.normals()[firstTriple + 1]
                        * secondPlan.normals()[secondTriple + 1]
                        + firstPlan.normals()[firstTriple + 2]
                        * secondPlan.normals()[secondTriple + 2];
        if (!Float.isFinite(normalDot)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx =
                firstPlan.positions()[firstTriple]
                        - secondPlan.positions()[secondTriple];
        double dy =
                firstPlan.positions()[firstTriple + 1]
                        - secondPlan.positions()[secondTriple + 1];
        double dz =
                firstPlan.positions()[firstTriple + 2]
                        - secondPlan.positions()[secondTriple + 2];
        if (normalDot
                < DarkBallSplatNeighborhoodPlan
                .MIN_SURFACE_NEIGHBOR_NORMAL_DOT
                && Math.min(
                firstPlan.thinSheetScore()[first],
                secondPlan.thinSheetScore()[second])
                < DarkBallSplatThinSheetPlan.MIN_PAIR_SCORE) {
            return Double.POSITIVE_INFINITY;
        }
        return dx * dx + dy * dy + dz * dz;
    }

    private static void copySurfaceSample(
            SurfaceSamplePlan target,
            int targetSample,
            SurfaceSamplePlan source,
            int sourceSample) {
        System.arraycopy(
                source.positions(),
                sourceSample * 3,
                target.positions(),
                targetSample * 3,
                3);
        System.arraycopy(
                source.normals(),
                sourceSample * 3,
                target.normals(),
                targetSample * 3,
                3);
        target.localThickness()[targetSample] =
                source.localThickness()[sourceSample];
        target.releaseOrder()[targetSample] =
                source.releaseOrder()[sourceSample];
        target.presentationReleaseOrder()[targetSample] =
                source.presentationReleaseOrder()[sourceSample];
        target.transportOrder()[targetSample] =
                source.transportOrder()[sourceSample];
        target.thinSheetScore()[targetSample] =
                source.thinSheetScore()[sourceSample];
        target.thinSheetPatchId()[targetSample] =
                source.thinSheetPatchId()[sourceSample];
    }

    private static int writeStratifiedDistribution(
            SurfaceSamplePlan plan,
            int sampleOffset,
            int sampleCount,
            DarkBallSurfaceMesh mesh,
            double totalWeight,
            double[] featureWeights,
            boolean featureDistribution) {
        if (sampleCount <= 0) {
            return 0;
        }
        int[] indices = mesh.indices();
        float[] positions = mesh.positions();
        int generated = 0;
        double accumulatedArea = 0.0;
        for (int offset = 0;
             offset < indices.length && generated < sampleCount;
             offset += 3) {
            int first = indices[offset];
            int second = indices[offset + 1];
            int third = indices[offset + 2];
            double area = triangleArea(
                    positions,
                    first,
                    second,
                    third);
            if (area <= MIN_SAMPLE_TRIANGLE_AREA) {
                continue;
            }
            double weightedArea = featureDistribution
                    ? featureWeights[offset / 3]
                    : area;
            double nextArea = accumulatedArea + weightedArea;
            while (generated < sampleCount) {
                double targetArea = (generated + 0.5)
                        * totalWeight / sampleCount;
                if (targetArea > nextArea) {
                    break;
                }
                writeInterpolatedSample(
                        plan,
                        sampleOffset + generated,
                        mesh,
                        first,
                        second,
                        third);
                generated++;
            }
            accumulatedArea = nextArea;
        }
        return generated;
    }

    /**
     * A fixed global feature reserve can still starve disconnected ribbons,
     * fins, and model-part spines because their total triangle area is tiny
     * beside a torso. Blend the raw feature distribution toward equal
     * connected-component shares, with a finite boost cap so microscopic SDF
     * debris cannot consume the whole reserve.
     */
    static ComponentFeatureWeights balanceFeatureWeightsByComponent(
            int vertexCount,
            int[] indices,
            double[] rawWeights) {
        int triangleCount = indices.length / 3;
        if (vertexCount <= 0
                || indices.length % 3 != 0
                || rawWeights.length != triangleCount) {
            throw new IllegalArgumentException(
                    "invalid component feature-weight inputs");
        }

        int[] parent = new int[vertexCount];
        byte[] rank = new byte[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            parent[vertex] = vertex;
        }
        for (int triangle = 0;
             triangle < triangleCount;
             triangle++) {
            int offset = triangle * 3;
            int first = indices[offset];
            int second = indices[offset + 1];
            int third = indices[offset + 2];
            requireVertexIndex(first, vertexCount);
            requireVertexIndex(second, vertexCount);
            requireVertexIndex(third, vertexCount);
            union(parent, rank, first, second);
            union(parent, rank, first, third);
        }

        Map<Integer, Integer> componentByRoot = new HashMap<>();
        int[] triangleComponent = new int[triangleCount];
        double[] componentRaw = new double[
                Math.max(1, triangleCount)];
        int componentCount = 0;
        for (int triangle = 0;
             triangle < triangleCount;
             triangle++) {
            int root = find(parent, indices[triangle * 3]);
            Integer component = componentByRoot.get(root);
            if (component == null) {
                component = componentCount++;
                componentByRoot.put(root, component);
            }
            triangleComponent[triangle] = component;
            double raw = rawWeights[triangle];
            if (Double.isFinite(raw) && raw > 0.0) {
                componentRaw[component] += raw;
            }
        }

        double totalRaw = 0.0;
        int activeComponents = 0;
        for (int component = 0;
             component < componentCount;
             component++) {
            totalRaw += componentRaw[component];
            if (componentRaw[component]
                    > MIN_SAMPLE_TRIANGLE_AREA) {
                activeComponents++;
            }
        }
        if (activeComponents <= 1
                || totalRaw <= MIN_SAMPLE_TRIANGLE_AREA) {
            return new ComponentFeatureWeights(
                    rawWeights.clone(),
                    totalRaw,
                    componentCount,
                    activeComponents);
        }

        double equalComponentTarget =
                totalRaw / activeComponents;
        double[] multipliers = new double[componentCount];
        for (int component = 0;
             component < componentCount;
             component++) {
            double raw = componentRaw[component];
            if (raw <= MIN_SAMPLE_TRIANGLE_AREA) {
                continue;
            }
            double equalizingBoost =
                    equalComponentTarget / raw;
            multipliers[component] = Math.min(
                    MAX_COMPONENT_FEATURE_BOOST,
                    (1.0 - COMPONENT_FEATURE_BALANCE)
                            + COMPONENT_FEATURE_BALANCE
                            * equalizingBoost);
        }

        double[] balanced = new double[triangleCount];
        double totalBalanced = 0.0;
        for (int triangle = 0;
             triangle < triangleCount;
             triangle++) {
            double raw = rawWeights[triangle];
            if (!Double.isFinite(raw) || raw <= 0.0) {
                continue;
            }
            balanced[triangle] =
                    raw * multipliers[triangleComponent[triangle]];
            totalBalanced += balanced[triangle];
        }
        return new ComponentFeatureWeights(
                balanced,
                totalBalanced,
                componentCount,
                activeComponents);
    }

    private static void requireVertexIndex(
            int vertex,
            int vertexCount) {
        if (vertex < 0 || vertex >= vertexCount) {
            throw new IllegalArgumentException(
                    "component triangle index is out of range");
        }
    }

    private static int find(int[] parent, int value) {
        int root = value;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[value] != value) {
            int next = parent[value];
            parent[value] = root;
            value = next;
        }
        return root;
    }

    private static void union(
            int[] parent,
            byte[] rank,
            int first,
            int second) {
        int firstRoot = find(parent, first);
        int secondRoot = find(parent, second);
        if (firstRoot == secondRoot) {
            return;
        }
        if (rank[firstRoot] < rank[secondRoot]) {
            parent[firstRoot] = secondRoot;
        } else if (rank[firstRoot] > rank[secondRoot]) {
            parent[secondRoot] = firstRoot;
        } else {
            parent[secondRoot] = firstRoot;
            rank[firstRoot]++;
        }
    }

    record ComponentFeatureWeights(
            double[] weights,
            double totalWeight,
            int componentCount,
            int activeComponents
    ) {
    }

    private static float maximumFiniteThickness(float[] thickness) {
        float maximum = 0.0f;
        for (float value : thickness) {
            if (Float.isFinite(value)) {
                maximum = Math.max(maximum, Math.max(value, 0.0f));
            }
        }
        return Math.max(maximum, 0.0001f);
    }

    private static double triangleFeatureSignal(
            DarkBallSurfaceMesh mesh,
            int first,
            int second,
            int third,
            float maximumThickness) {
        float[] normals = mesh.normals();
        float firstSecond = normalDot(normals, first, second);
        float secondThird = normalDot(normals, second, third);
        float thirdFirst = normalDot(normals, third, first);
        float normalVariation = Mth.clamp(
                1.0f - Math.min(
                        firstSecond,
                        Math.min(secondThird, thirdFirst)),
                0.0f,
                1.0f);
        float[] thickness = mesh.localThickness();
        float averageThickness = (
                Math.max(thickness[first], 0.0f)
                        + Math.max(thickness[second], 0.0f)
                        + Math.max(thickness[third], 0.0f)) / 3.0f;
        float thinness = 1.0f - Mth.clamp(
                averageThickness / maximumThickness,
                0.0f,
                1.0f);
        float thinFeatureSignal =
                thinness * thinness * (3.0f - 2.0f * thinness);
        return FEATURE_THINNESS_SHARE * thinFeatureSignal
                + FEATURE_CURVATURE_SHARE
                * Math.sqrt(normalVariation);
    }

    private static float normalDot(float[] normals,
                                   int first,
                                   int second) {
        int firstOffset = first * 3;
        int secondOffset = second * 3;
        return Mth.clamp(
                normals[firstOffset] * normals[secondOffset]
                        + normals[firstOffset + 1]
                        * normals[secondOffset + 1]
                        + normals[firstOffset + 2]
                        * normals[secondOffset + 2],
                -1.0f,
                1.0f);
    }

    private static SurfaceSamplePlan buildVertexFallbackSamples(
            DarkBallSurfaceMesh mesh,
            int sampleCount,
            float maximumVoxel) {
        SurfaceSamplePlan plan = new SurfaceSamplePlan(sampleCount);
        for (int sample = 0; sample < sampleCount; sample++) {
            int sourceVertex = Math.min(
                    mesh.vertexCount() - 1,
                    (int) (((long) sample * mesh.vertexCount())
                            / sampleCount));
            copyVertexSample(
                    plan,
                    sample,
                    mesh,
                    sourceVertex);
        }
        DarkBallSplatThinSheetPlan.stabilize(
                plan, maximumVoxel);
        addNeighborhoodMetadata(plan);
        DarkBallSplatThinSheetPatchPlan.apply(
                plan, maximumVoxel);
        return plan;
    }

    private static SurfaceSamplePlan addNeighborhoodMetadata(
            SurfaceSamplePlan plan) {
        DarkBallSplatNeighborhoodPlan.Metadata metadata =
                DarkBallSplatNeighborhoodPlan.build(
                        plan.positions(),
                        plan.normals(),
                        plan.releaseOrder());
        System.arraycopy(
                metadata.smoothedReleaseOrder(),
                0,
                plan.releaseOrder(),
                0,
                plan.releaseOrder().length);
        System.arraycopy(
                metadata.spacingScale(),
                0,
                plan.spacingScale(),
                0,
                plan.spacingScale().length);
        System.arraycopy(
                metadata.areaScale(),
                0,
                plan.areaScale(),
                0,
                plan.areaScale().length);
        System.arraycopy(
                metadata.coherence(),
                0,
                plan.coherence(),
                0,
                plan.coherence().length);
        System.arraycopy(
                metadata.tangentAngle(),
                0,
                plan.tangentAngle(),
                0,
                plan.tangentAngle().length);
        System.arraycopy(
                metadata.tangentConfidence(),
                0,
                plan.tangentConfidence(),
                0,
                plan.tangentConfidence().length);
        return plan;
    }

    private static void writeInterpolatedSample(
            SurfaceSamplePlan target,
            int sample,
            DarkBallSurfaceMesh mesh,
            int first,
            int second,
            int third) {
        float sequenceU = fract(
                (sample + 0.5f) * 0.754877666f);
        float sequenceV = fract(
                (sample + 0.5f) * 0.569840296f);
        float rootU = (float) Math.sqrt(sequenceU);
        float firstWeight = 1.0f - rootU;
        float secondWeight = rootU * (1.0f - sequenceV);
        float thirdWeight = rootU * sequenceV;

        interpolateTriple(
                target.positions(),
                sample,
                mesh.positions(),
                first,
                second,
                third,
                firstWeight,
                secondWeight,
                thirdWeight);
        interpolateTriple(
                target.normals(),
                sample,
                mesh.normals(),
                first,
                second,
                third,
                firstWeight,
                secondWeight,
                thirdWeight);
        normalizeSampleNormal(target.normals(), sample);
        target.localThickness()[sample] = interpolateScalar(
                mesh.localThickness(),
                first,
                second,
                third,
                firstWeight,
                secondWeight,
                thirdWeight);
        target.releaseOrder()[sample] = interpolateScalar(
                mesh.releaseOrder(),
                first,
                second,
                third,
                firstWeight,
                secondWeight,
                thirdWeight);
        target.presentationReleaseOrder()[sample] = interpolateScalar(
                mesh.presentationReleaseOrder(),
                first,
                second,
                third,
                firstWeight,
                secondWeight,
                thirdWeight);
        target.transportOrder()[sample] = interpolateScalar(
                mesh.transportOrder(),
                first,
                second,
                third,
                firstWeight,
                secondWeight,
                thirdWeight);
    }

    private static void copyVertexSample(
            SurfaceSamplePlan target,
            int sample,
            DarkBallSurfaceMesh mesh,
            int sourceVertex) {
        int targetTriple = sample * 3;
        int sourceTriple = sourceVertex * 3;
        System.arraycopy(
                mesh.positions(),
                sourceTriple,
                target.positions(),
                targetTriple,
                3);
        System.arraycopy(
                mesh.normals(),
                sourceTriple,
                target.normals(),
                targetTriple,
                3);
        target.localThickness()[sample] =
                mesh.localThickness()[sourceVertex];
        target.releaseOrder()[sample] =
                mesh.releaseOrder()[sourceVertex];
        target.presentationReleaseOrder()[sample] =
                mesh.presentationReleaseOrder()[sourceVertex];
        target.transportOrder()[sample] =
                mesh.transportOrder()[sourceVertex];
    }

    private static void interpolateTriple(
            float[] target,
            int targetIndex,
            float[] source,
            int first,
            int second,
            int third,
            float firstWeight,
            float secondWeight,
            float thirdWeight) {
        int targetTriple = targetIndex * 3;
        int firstTriple = first * 3;
        int secondTriple = second * 3;
        int thirdTriple = third * 3;
        for (int component = 0; component < 3; component++) {
            target[targetTriple + component] =
                    source[firstTriple + component] * firstWeight
                            + source[secondTriple + component]
                            * secondWeight
                            + source[thirdTriple + component]
                            * thirdWeight;
        }
    }

    private static float interpolateScalar(
            float[] source,
            int first,
            int second,
            int third,
            float firstWeight,
            float secondWeight,
            float thirdWeight) {
        return source[first] * firstWeight
                + source[second] * secondWeight
                + source[third] * thirdWeight;
    }

    private static void normalizeSampleNormal(
            float[] normals,
            int sample) {
        int triple = sample * 3;
        float x = normals[triple];
        float y = normals[triple + 1];
        float z = normals[triple + 2];
        float lengthSquared = x * x + y * y + z * z;
        if (lengthSquared <= 1.0e-12f
                || !Float.isFinite(lengthSquared)) {
            normals[triple] = 1.0f;
            normals[triple + 1] = 0.0f;
            normals[triple + 2] = 0.0f;
            return;
        }
        float inverseLength =
                (float) (1.0 / Math.sqrt(lengthSquared));
        normals[triple] = x * inverseLength;
        normals[triple + 1] = y * inverseLength;
        normals[triple + 2] = z * inverseLength;
    }

    private static double triangleArea(
            float[] positions,
            int first,
            int second,
            int third) {
        int vertexCount = positions.length / 3;
        if (first < 0 || first >= vertexCount
                || second < 0 || second >= vertexCount
                || third < 0 || third >= vertexCount) {
            return Double.NaN;
        }
        int a = first * 3;
        int b = second * 3;
        int c = third * 3;
        double abX = positions[b] - positions[a];
        double abY = positions[b + 1] - positions[a + 1];
        double abZ = positions[b + 2] - positions[a + 2];
        double acX = positions[c] - positions[a];
        double acY = positions[c + 1] - positions[a + 1];
        double acZ = positions[c + 2] - positions[a + 2];
        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;
        return 0.5 * Math.sqrt(
                crossX * crossX
                        + crossY * crossY
                        + crossZ * crossZ);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static short encodeRange(
            float value,
            float minimum,
            float maximum) {
        float unit = Mth.clamp(
                (value - minimum) / Math.max(maximum - minimum, 1.0e-6f),
                0.0f,
                1.0f);
        return (short) Math.round((unit * 2.0f - 1.0f) * 32767.0f);
    }

    private static short encodeUnit(float value) {
        return (short) Math.round(
                Mth.clamp(value, 0.0f, 1.0f) * 32767.0f);
    }

    static int encodeAttachmentAndThinSheetMetadata(
            int attachmentNode,
            float thinSheetScore) {
        int encodedAttachmentNode =
                attachmentNode >= 0 && attachmentNode < 32
                        ? attachmentNode
                        : 0;
        int encodedSheetScore = Math.round(
                Mth.clamp(thinSheetScore, 0.0f, 1.0f) * 7.0f);
        return encodedAttachmentNode | encodedSheetScore << 5;
    }

    static int encodeAttachmentTAndCorner(float attachmentT, int corner) {
        int encodedAttachmentT = Math.round(
                Mth.clamp(attachmentT, 0.0f, 1.0f) * 63.0f);
        return encodedAttachmentT << 2 | corner & 3;
    }

    static short encodeTangentFrameMetadata(
            float angle,
            float confidence) {
        float normalizedAngle = Mth.clamp(
                angle / (float) Math.PI,
                0.0f,
                0.999999f);
        int encodedAngle = Math.round(normalizedAngle * 4095.0f);
        int encodedConfidence = Math.round(
                Mth.clamp(confidence, 0.0f, 1.0f) * 15.0f);
        return (short) ((encodedConfidence << 12) | encodedAngle);
    }

    private static float smoothstep(
            float edge0,
            float edge1,
            float value) {
        float t = Mth.clamp(
                (value - edge0) / Math.max(edge1 - edge0, 1.0e-6f),
                0.0f,
                1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float calculateSplatRadius(
            DarkBallSurfaceMesh mesh,
            DarkBallVolumeBuildResult volume,
            int sampleCount) {
        float maximumVoxel = Math.max(
                Math.max(volume.sdfVoxelX(), volume.sdfVoxelYz()),
                0.0001f);
        float retainedSpacing = maximumVoxel * 0.70f
                * (float) Math.sqrt(
                Math.max((double) mesh.vertexCount() / sampleCount, 1.0));
        float projectedCoverage = Math.max(volume.bodyRadius(), maximumVoxel)
                * 1.62f
                / (float) Math.sqrt(sampleCount);
        float radius = Math.max(
                maximumVoxel * 0.96f,
                Math.max(retainedSpacing, projectedCoverage));
        float maximumRadius = Math.max(
                volume.bodyRadius() * 0.18f,
                maximumVoxel * 5.5f);
        return Mth.clamp(
                radius,
                maximumVoxel * 0.96f,
                Math.max(maximumRadius, maximumVoxel * 0.96f));
    }

    private void logUploadFailureOnce(DarkBallSurfaceMesh mesh,
                                      Throwable failure) {
        if (uploadFailureLogged) {
            return;
        }
        uploadFailureLogged = true;
        Shadowedhearts.LOGGER.error(
                "Dark Ball surface-splat draw failed ({}); "
                        + "discarding this capture's body draw",
                mesh.summary(),
                failure);
    }

    private void logDiagnosticFailureOnce(Throwable failure) {
        if (diagnosticFailureLogged) {
            return;
        }
        diagnosticFailureLogged = true;
        Shadowedhearts.LOGGER.warn(
                "Dark Ball pre-merge splat diagnostic draw failed; "
                        + "the production surfel presentation remains active",
                failure);
    }

    private void logFrontDepthFailureOnce(Throwable failure) {
        if (frontDepthFailureLogged) {
            return;
        }
        frontDepthFailureLogged = true;
        Shadowedhearts.LOGGER.warn(
                "Dark Ball frontmost surfel depth draw failed; "
                        + "the fused coverage and harmonic depth fallback "
                        + "remain active",
                failure);
    }

    private static float findThicknessScale(float[] thickness) {
        float maximum = 0.0f;
        for (float value : thickness) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "surface splat thickness is not finite");
            }
            maximum = Math.max(maximum, Math.max(value, 0.0f));
        }
        return Math.max(maximum, 0.0001f);
    }

    static short encodeBoneRouteMetadata(
            int attachmentNode,
            float confidence,
            float normalizedPathLength) {
        if (attachmentNode < 0 || attachmentNode >= 32) {
            return 0;
        }
        // Zero is reserved for an invalid route in the shader. An assigned
        // route must survive byte quantization even when its confidence or
        // normalized length is extremely small.
        int encodedConfidence = Math.max(1, Math.round(Mth.clamp(
                confidence, 0.0f, 1.0f) * 255.0f));
        int encodedPathLength = Math.max(2, Math.round(Mth.clamp(
                normalizedPathLength, 0.0f, 1.0f) * 255.0f));
        return (short) (encodedConfidence
                | (encodedPathLength << 8));
    }

    private static void putFiniteFloat(long address,
                                       float value,
                                       String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    "surface splat " + field + " is not finite");
        }
        MemoryUtil.memPutFloat(address, value);
    }

    private static void putNormal(long address, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    "surface splat normal is not finite");
        }
        int encoded = Math.round(
                Mth.clamp(value, -1.0f, 1.0f) * 127.0f);
        MemoryUtil.memPutByte(address, (byte) encoded);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static void setFloat(ShaderInstance shader,
                                 String name,
                                 float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setVec3(ShaderInstance shader,
                                String name,
                                Vec3 value) {
        setVec3(shader, name,
                (float) value.x,
                (float) value.y,
                (float) value.z);
    }

    private static void setVec3(ShaderInstance shader,
                                String name,
                                Vector3f value) {
        setVec3(shader, name, value.x, value.y, value.z);
    }

    private static void setVec3(ShaderInstance shader,
                                String name,
                                float x,
                                float y,
                                float z) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private static void setMatrix(
            ShaderInstance shader,
            String name,
            Matrix4f value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    @Override
    public void close() {
        VertexBuffer current = vertexBuffer;
        vertexBuffer = null;
        uploadedMesh = null;
        uploadedVolume = null;
        uploadedQuality = null;
        uploadedSamplePlan = null;
        uploadedDiagnosticSamplePlan = null;
        uploadedSampleCount = 0;
        uploadedMeanSurfaceInset = 0.0f;
        uploadedMaximumSurfaceInset = 0.0f;
        uploadedVoxelSurfaceInsetLimit = 0.0f;
        lastPreparationUploadCpuMicros = -1L;
        lastDrawCpuMicros = -1L;
        uploadGeneration = 0L;
        uploadOccurredLastRender = false;
        diagnosticFailureLogged = false;
        frontDepthFailureLogged = false;
        projectionDiagnosticFailureLogged = false;
        projectionDiagnosticUploadGeneration = -1L;
        projectionDiagnosticStageMask = 0;
        validatedVertexLayoutShader = null;
        validatedVertexLayoutUploadGeneration = -1L;
        validatedVertexLayoutCompatible = false;
        vertexLayoutFailureLogged = false;
        if (current == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            current.close();
        } else {
            RenderSystem.recordRenderCall(current::close);
        }
    }

    private record RenderState(
            boolean blendEnabled,
            boolean depthTestEnabled,
            boolean cullEnabled,
            boolean depthMask,
            int depthFunction,
            int cullFace,
            int blendSourceRgb,
            int blendDestinationRgb,
            int blendSourceAlpha,
            int blendDestinationAlpha,
            int blendEquationRgb,
            int blendEquationAlpha
    ) {
        static RenderState capture() {
            return new RenderState(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA));
        }

        void restore() {
            GlStateManager._blendFuncSeparate(
                    blendSourceRgb,
                    blendDestinationRgb,
                    blendSourceAlpha,
                    blendDestinationAlpha);
            GL20.glBlendEquationSeparate(
                    blendEquationRgb,
                    blendEquationAlpha);
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
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(depthMask);
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            GL11.glCullFace(cullFace);
        }
    }

    private record SurfaceInsetStatistics(
            float meanInset,
            float maximumInset,
            float voxelLimit
    ) {
    }

    private record ProjectionStatistics(
            int totalCount,
            int finiteCount,
            int nonFiniteCount,
            int badWCount,
            int outsideViewportCount,
            int shaderExtremeCount,
            float minimumW,
            float maximumW,
            float minimumNdcX,
            float maximumNdcX,
            float minimumNdcY,
            float maximumNdcY,
            float maximumAbsNdc,
            float maximumEstimatedReachPixels,
            float minimumViewZ,
            float maximumViewZ,
            float maximumCameraDistance
    ) {
        private static final ProjectionStatistics EMPTY =
                new ProjectionStatistics(
                        0, 0, 0, 0, 0, 0,
                        Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN,
                        Float.NaN);

        String summary() {
            return String.format(
                    Locale.ROOT,
                    "centers(total=%d finite=%d nonFinite=%d badW=%d "
                            + "outsideViewport=%d shaderExtreme=%d "
                            + "w=[%.6g,%.6g] ndcX=[%.6g,%.6g] "
                            + "ndcY=[%.6g,%.6g] maxAbsNdc=%.6g "
                            + "estimatedReachMax=%.3fpx "
                            + "viewZ=[%.6g,%.6g] maxCameraDistance=%.6g)",
                    totalCount,
                    finiteCount,
                    nonFiniteCount,
                    badWCount,
                    outsideViewportCount,
                    shaderExtremeCount,
                    minimumW,
                    maximumW,
                    minimumNdcX,
                    maximumNdcX,
                    minimumNdcY,
                    maximumNdcY,
                    maximumAbsNdc,
                    maximumEstimatedReachPixels,
                    minimumViewZ,
                    maximumViewZ,
                    maximumCameraDistance);
        }
    }

    record SurfaceSamplePlan(
            float[] positions,
            float[] normals,
            float[] localThickness,
            float[] releaseOrder,
            float[] presentationReleaseOrder,
            float[] transportOrder,
            float[] spacingScale,
            float[] areaScale,
            float[] coherence,
            float[] tangentAngle,
            float[] tangentConfidence,
            float[] thinSheetScore,
            int[] thinSheetPatchId,
            DarkBallSplatBoneRoutePlan.Result boneRoutePlan
    ) {
        SurfaceSamplePlan(int sampleCount) {
            this(
                    new float[Math.multiplyExact(sampleCount, 3)],
                    new float[Math.multiplyExact(sampleCount, 3)],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    new float[sampleCount],
                    emptyPatchIds(sampleCount),
                    null);
        }

        private static int[] emptyPatchIds(int sampleCount) {
            int[] patchIds = new int[sampleCount];
            Arrays.fill(patchIds, -1);
            return patchIds;
        }

        SurfaceSamplePlan withBoneRoutePlan(
                DarkBallSplatBoneRoutePlan.Result routePlan) {
            if (routePlan != null
                    && routePlan.attachmentNodes().length
                    != releaseOrder.length) {
                throw new IllegalArgumentException(
                        "bone route sample count does not match splats");
            }
            return new SurfaceSamplePlan(
                    positions,
                    normals,
                    localThickness,
                    releaseOrder,
                    presentationReleaseOrder,
                    transportOrder,
                    spacingScale,
                    areaScale,
                    coherence,
                    tangentAngle,
                    tangentConfidence,
                    thinSheetScore,
                    thinSheetPatchId,
                    routePlan);
        }
    }
}
