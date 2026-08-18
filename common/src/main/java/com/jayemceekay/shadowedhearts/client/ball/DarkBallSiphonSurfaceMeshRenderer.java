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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

/**
 * Dynamic indexed raster owner for the CPU-generated Dark Ball siphon tube.
 *
 * <p>The current target is expected to contain copied pristine scene depth.
 * The tube uses {@code LEQUAL} and writes its winning surface depth so its
 * folds, caps, and overlap with the body resolve through conventional
 * rasterization. The caller remains responsible for restoring pristine depth
 * before later screen-space passes consume that attachment.</p>
 */
final class DarkBallSiphonSurfaceMeshRenderer implements AutoCloseable {
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

    private VertexBuffer vertexBuffer;
    private boolean drawFailureLogged;

    /**
     * Uploads and draws the current local-space siphon tube.
     *
     * @return {@code true} only after the indexed draw was submitted
     */
    boolean render(DarkBallSiphonSurfaceMesh mesh,
                   DarkBallVolumeBuildResult volume,
                   Camera camera,
                   float effectFade) {
        ShaderInstance shader =
                ModShaders.DARK_BALL_SIPHON_SURFACE_MESH;
        if (!RenderSystem.isOnRenderThread()
                || shader == null
                || mesh == null
                || mesh.vertexCount() < 3
                || mesh.triangleCount() < 1
                || volume == null
                || camera == null) {
            return false;
        }

        try {
            upload(mesh);
            configureShader(shader, volume, camera, effectFade);
        } catch (Throwable failure) {
            logDrawFailureOnce(mesh, failure);
            return false;
        }

        RenderState previous = RenderState.capture();
        try {
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_BACK);

            vertexBuffer.bind();
            vertexBuffer.drawWithShader(
                    DarkBallRenderContext.modelView(camera),
                    DarkBallRenderContext.projection(),
                    shader);
            drawFailureLogged = false;
            return true;
        } catch (Throwable failure) {
            logDrawFailureOnce(mesh, failure);
            return false;
        } finally {
            VertexBuffer.unbind();
            previous.restore();
        }
    }

    private void upload(DarkBallSiphonSurfaceMesh mesh) {
        UploadedBuffers buffers = buildUploadBuffers(mesh);
        VertexBuffer target = vertexBuffer;
        if (target == null || target.isInvalid()) {
            if (target != null) {
                target.close();
            }
            target = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            vertexBuffer = target;
        }

        boolean uploaded = false;
        try (ByteBufferBuilder vertexBuilder = buffers.vertexBuilder();
             ByteBufferBuilder indexBuilder = buffers.indexBuilder()) {
            ByteBufferBuilder.Result vertexResult = vertexBuilder.build();
            ByteBufferBuilder.Result indexResult = indexBuilder.build();
            if (vertexResult == null || indexResult == null) {
                closeResult(vertexResult);
                closeResult(indexResult);
                throw new IllegalStateException(
                        "siphon mesh upload buffers were empty");
            }

            VertexFormat.IndexType indexType =
                    VertexFormat.IndexType.least(mesh.vertexCount());
            MeshData.DrawState drawState = new MeshData.DrawState(
                    FORMAT,
                    mesh.vertexCount(),
                    mesh.indices().length,
                    VertexFormat.Mode.TRIANGLES,
                    indexType);

            target.bind();
            // A MeshData vertex upload installs a transient sequential index
            // binding. Replace it immediately with the tube's real ring/cap
            // topology so shared vertices remain genuinely indexed.
            target.upload(new MeshData(vertexResult, drawState));
            target.uploadIndexBuffer(indexResult);
            VertexBuffer.unbind();
            uploaded = true;
        } finally {
            if (!uploaded) {
                VertexBuffer.unbind();
                if (vertexBuffer == target) {
                    vertexBuffer = null;
                }
                target.close();
            }
        }
    }

    private UploadedBuffers buildUploadBuffers(
            DarkBallSiphonSurfaceMesh mesh) {
        int vertexStride = FORMAT.getVertexSize();
        int vertexBytes = Math.multiplyExact(
                mesh.vertexCount(), vertexStride);
        VertexFormat.IndexType indexType =
                VertexFormat.IndexType.least(mesh.vertexCount());
        int indexBytes = Math.multiplyExact(
                mesh.indices().length, indexType.bytes);

        ByteBufferBuilder vertexBuilder =
                new ByteBufferBuilder(Math.max(vertexBytes, 1));
        ByteBufferBuilder indexBuilder =
                new ByteBufferBuilder(Math.max(indexBytes, 1));
        boolean built = false;
        try {
            long vertices = vertexBuilder.reserve(vertexBytes);
            long indices = indexBuilder.reserve(indexBytes);
            MemoryUtil.memSet(vertices, 0, vertexBytes);
            MemoryUtil.memSet(indices, 0, indexBytes);

            float[] positions = mesh.positions();
            float[] normals = mesh.normals();
            float[] curveT = mesh.curveT();
            float[] localRadius = mesh.localRadius();
            float[] materialWindow = mesh.materialWindow();
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                long address = vertices + (long) vertex * vertexStride;
                int triple = vertex * 3;
                putFiniteFloat(address + POSITION_OFFSET,
                        positions[triple], "position");
                putFiniteFloat(address + POSITION_OFFSET + 4L,
                        positions[triple + 1], "position");
                putFiniteFloat(address + POSITION_OFFSET + 8L,
                        positions[triple + 2], "position");

                float window = requireFinite(
                        materialWindow[vertex], "material window");
                int encodedWindow = Math.round(
                        Mth.clamp(window, 0.0f, 1.0f) * 255.0f);
                MemoryUtil.memPutByte(address + COLOR_OFFSET,
                        (byte) encodedWindow);
                MemoryUtil.memPutByte(address + COLOR_OFFSET + 1L,
                        (byte) 0xFF);
                MemoryUtil.memPutByte(address + COLOR_OFFSET + 2L,
                        (byte) 0xFF);
                MemoryUtil.memPutByte(address + COLOR_OFFSET + 3L,
                        (byte) 0xFF);

                putFiniteFloat(address + UV0_OFFSET,
                        Mth.clamp(requireFinite(
                                curveT[vertex], "curve coordinate"),
                                0.0f, 1.0f),
                        "curve coordinate");
                putFiniteFloat(address + UV0_OFFSET + 4L,
                        Math.max(requireFinite(
                                localRadius[vertex], "local radius"),
                                0.0f),
                        "local radius");
                MemoryUtil.memPutShort(address + UV1_OFFSET, (short) 0);
                MemoryUtil.memPutShort(
                        address + UV1_OFFSET + 2L, (short) 0);
                MemoryUtil.memPutShort(address + UV2_OFFSET, (short) 0);
                MemoryUtil.memPutShort(
                        address + UV2_OFFSET + 2L, (short) 0);

                putNormal(address + NORMAL_OFFSET, normals[triple]);
                putNormal(address + NORMAL_OFFSET + 1L,
                        normals[triple + 1]);
                putNormal(address + NORMAL_OFFSET + 2L,
                        normals[triple + 2]);
            }

            int[] meshIndices = mesh.indices();
            for (int offset = 0; offset < meshIndices.length; offset++) {
                int index = meshIndices[offset];
                if (index < 0 || index >= mesh.vertexCount()) {
                    throw new IllegalArgumentException(
                            "siphon mesh index " + index
                                    + " is outside vertex count "
                                    + mesh.vertexCount());
                }
                long address = indices + (long) offset * indexType.bytes;
                if (indexType == VertexFormat.IndexType.SHORT) {
                    MemoryUtil.memPutShort(address, (short) index);
                } else {
                    MemoryUtil.memPutInt(address, index);
                }
            }
            built = true;
            return new UploadedBuffers(vertexBuilder, indexBuilder);
        } finally {
            if (!built) {
                vertexBuilder.close();
                indexBuilder.close();
            }
        }
    }

    private static void configureShader(
            ShaderInstance shader,
            DarkBallVolumeBuildResult volume,
            Camera camera,
            float effectFade) {
        Vec3 cameraRelativeRoot =
                volume.root().subtract(camera.getPosition());
        setVec3(shader, "VolumeRootCameraRelative",
                cameraRelativeRoot);
        setVec3(shader, "VolumeAxis", volume.axis());
        setVec3(shader, "VolumeSide", volume.side());
        setVec3(shader, "VolumeUp", volume.up());
        setFloat(shader, "EffectFade", Mth.clamp(
                finiteOr(effectFade, 0.0f), 0.0f, 1.0f));
    }

    private void logDrawFailureOnce(DarkBallSiphonSurfaceMesh mesh,
                                    Throwable failure) {
        if (drawFailureLogged) {
            return;
        }
        drawFailureLogged = true;
        Shadowedhearts.LOGGER.error(
                "Dark Ball siphon surface mesh draw failed "
                        + "(vertices={}, triangles={}); "
                        + "retain the analytical siphon fallback",
                mesh.vertexCount(), mesh.triangleCount(), failure);
    }

    private static float requireFinite(float value, String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(
                    "siphon mesh " + field + " is not finite");
        }
        return value;
    }

    private static void putFiniteFloat(long address,
                                       float value,
                                       String field) {
        MemoryUtil.memPutFloat(address, requireFinite(value, field));
    }

    private static void putNormal(long address, float value) {
        int encoded = Math.round(Mth.clamp(
                requireFinite(value, "normal"),
                -1.0f, 1.0f) * 127.0f);
        MemoryUtil.memPutByte(address, (byte) encoded);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static void closeResult(ByteBufferBuilder.Result result) {
        if (result != null) {
            result.close();
        }
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
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(
                    (float) value.x,
                    (float) value.y,
                    (float) value.z);
        }
    }

    @Override
    public void close() {
        VertexBuffer current = vertexBuffer;
        vertexBuffer = null;
        if (current == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            current.close();
        } else {
            RenderSystem.recordRenderCall(current::close);
        }
    }

    private record UploadedBuffers(
            ByteBufferBuilder vertexBuilder,
            ByteBufferBuilder indexBuilder
    ) {
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
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
            );
        }

        void restore() {
            GlStateManager._blendFuncSeparate(
                    blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            GL20.glBlendEquationSeparate(
                    blendEquationRgb, blendEquationAlpha);
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
}
