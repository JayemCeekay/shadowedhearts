package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/** Bounded implicit energy surface. The caller owns density and scene-depth rollback. */
final class DarkBallAnimeEnergyRenderer implements AutoCloseable {
    private VertexBuffer screenTriangle;
    private int restTexture;
    private DarkBallAnimeRestVolume uploadedRest;

    boolean render(DarkBallAnimeEnergyBody.Frame frame, DarkBallVolumeBuildResult volume,
                   Camera camera, float coverage) {
        if (frame == null || !Float.isFinite(coverage)) return false;
        if ((frame.primitiveCount() == 0 && frame.rest() == null) || coverage <= 0.0f) return true;
        ShaderInstance shader = ModShaders.DARK_BALL_ANIME_ENERGY;
        if (!RenderSystem.isOnRenderThread() || shader == null || volume == null || camera == null
                || !valid(frame) || !finite(volume.root()) || !finite(volume.axis())
                || !finite(volume.side()) || !finite(volume.up()) || !finite(camera.getPosition())) {
            return false;
        }

        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            Vec3 root = volume.root().subtract(camera.getPosition());
            Matrix4f localToCamera = new Matrix4f()
                    .m00((float) volume.axis().x).m01((float) volume.axis().y).m02((float) volume.axis().z)
                    .m10((float) volume.side().x).m11((float) volume.side().y).m12((float) volume.side().z)
                    .m20((float) volume.up().x).m21((float) volume.up().y).m22((float) volume.up().z)
                    .m30((float) root.x).m31((float) root.y).m32((float) root.z);
            Matrix4f modelView = DarkBallRenderContext.modelView(camera);
            Matrix4f projection = DarkBallRenderContext.projection();
            Matrix4f localToClip = new Matrix4f(projection).mul(modelView).mul(localToCamera);
            if (!localToClip.isFinite() || !Float.isFinite(localToClip.determinant())
                    || Math.abs(localToClip.determinant()) < 1.0e-20f) return false;
            Matrix4f clipToLocal = new Matrix4f(localToClip).invert();
            if (!clipToLocal.isFinite()) return false;

            required(shader, "EnergyPrimitives").set(frame.primitives());
            required(shader, "EnergyNotches").set(frame.notches());
            required(shader, "EnergyPrimitiveCount").set(frame.primitiveCount());
            required(shader, "EnergyNotchCount").set(frame.notchCount());
            required(shader, "EnergySmoothness").set(frame.smoothness());
            required(shader, "EnergyCoverage").set(Math.min(1.0f, coverage));
            required(shader, "EnergyTraceScale").set((float) frame.maximum().subtract(frame.minimum()).length());
            vector(shader, "EnergyMinimum", frame.minimum());
            vector(shader, "EnergyMaximum", frame.maximum());
            required(shader, "EnergyLocalToCamera").set(localToCamera);
            required(shader, "EnergyLocalToClip").set(localToClip);
            required(shader, "EnergyClipToLocal").set(clipToLocal);
            uploadRestUniforms(shader, frame.rest());
            required(shader, "EnergyTailAvailable").set(frame.tail() == null ? 0 : 1);
            if (frame.tail() != null) {
                vector(shader, "EnergyTailTip", frame.tail().tip());
                vector(shader, "EnergyTailDirection", frame.tail().direction());
                required(shader, "EnergyTailShape").set(frame.tail().slope(), frame.tail().length(),
                        frame.tail().flareSlope(), frame.tail().flareOffset());
            }

            if (screenTriangle == null || screenTriangle.isInvalid()) uploadScreenTriangle();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            screenTriangle.bind();
            screenTriangle.drawWithShader(modelView, projection, shader);
            return true;
        }
    }

    private static boolean valid(DarkBallAnimeEnergyBody.Frame frame) {
        if (frame.primitiveCount() < 0 || frame.primitiveCount() > 24
                || frame.notchCount() < 0 || frame.notchCount() > 4
                || !finite(frame.minimum()) || !finite(frame.maximum())
                || !(frame.maximum().x > frame.minimum().x)
                || !(frame.maximum().y > frame.minimum().y)
                || !(frame.maximum().z > frame.minimum().z)
                || !Float.isFinite(frame.smoothness()) || frame.smoothness() < 0.0f) return false;
        return validPrimitives(frame.primitives(), 384, frame.primitiveCount())
                && validPrimitives(frame.notches(), 64, frame.notchCount());
    }

    private static boolean validPrimitives(float[] packed, int size, int count) {
        if (packed == null || packed.length != size) return false;
        for (float value : packed) {
            if (!Float.isFinite(value) || Math.abs(value) > 1.0e20f) return false;
        }
        for (int index = 0; index < count; index++) {
            int offset = index * 16;
            if (!(packed[offset + 3] > 0.0f)) return false;
            for (int row = 1; row <= 3; row++) {
                int start = offset + row * 4;
                if (packed[start] == 0.0f && packed[start + 1] == 0.0f && packed[start + 2] == 0.0f)
                    return false;
            }
        }
        return true;
    }

    private void uploadScreenTriangle() {
        closeScreenTriangle();
        VertexBuffer next = new VertexBuffer(VertexBuffer.Usage.STATIC);
        boolean uploaded = false;
        try (ByteBufferBuilder storage = new ByteBufferBuilder(3 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(-1, -1, 0).setUv(0, 0);
            builder.addVertex(3, -1, 0).setUv(2, 0);
            builder.addVertex(-1, 3, 0).setUv(0, 2);
            MeshData mesh = builder.build();
            if (mesh == null) throw new IllegalStateException("empty anime energy screen triangle");
            next.bind();
            next.upload(mesh);
            screenTriangle = next;
            uploaded = true;
        } finally {
            VertexBuffer.unbind();
            if (!uploaded) next.close();
        }
    }

    private static Uniform required(ShaderInstance shader, String name) {
        Uniform uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("missing anime energy uniform " + name);
        return uniform;
    }

    private void uploadRestUniforms(ShaderInstance shader, DarkBallAnimeRestMotion.Frame rest) {
        required(shader, "EnergyRestAvailable").set(rest == null ? 0 : 1);
        if (rest == null) {
            shader.setSampler("EnergyRestSampler", 0);
            return;
        }
        DarkBallAnimeRestVolume volume = rest.volume();
        shader.setSampler("EnergyRestSampler", ensureRestTexture(volume));
        vector(shader, "EnergyRestGridMinimum", volume.gridMinimum());
        vector(shader, "EnergyRestGridMaximum", volume.gridMaximum());
        vector(shader, "EnergyRestMinimum", rest.minimum());
        vector(shader, "EnergyRestMaximum", rest.maximum());
        required(shader, "EnergyRestAxialMinimum").set((float) rest.restAxialMinimum());
        required(shader, "EnergyRestGridSize").set((float) volume.xSize(), (float) volume.ySize(), (float) volume.zSize());
        required(shader, "EnergyRestDistanceLipschitz").set((float) volume.distanceLipschitz());
        required(shader, "EnergyRestDistanceBand").set((float) volume.distanceBand(),
                (float) volume.distanceBandGradient());
        vector(shader, "EnergyRestOuterBands", volume.distanceOuterBands());
        vector(shader, "EnergyRestOuterBandGradients", volume.distanceOuterBandGradients());
        vector(shader, "EnergyRestCenter", rest.center());
        vector(shader, "EnergyRestDirection", rest.direction());
        vector(shader, "EnergyRestSide", rest.side());
        vector(shader, "EnergyRestUp", rest.up());
        vector(shader, "EnergyRestTarget", rest.target());
        vector(shader, "EnergyRestBend", rest.bend());
        required(shader, "EnergyRestShape").set((float) rest.radius(), (float) rest.axialMinimum(),
                (float) rest.axialSpan(), (float) rest.inverseLipschitz());
        required(shader, "EnergyRestClock").set((float) rest.pull(), (float) rest.strength(),
                (float) rest.time(), (float) rest.squash());
        var phases = rest.phases();
        required(shader, "EnergyRestShearPhases").set(phases.uAxial(), phases.uCross(),
                phases.vAxial(), phases.vCross());
        required(shader, "EnergyRestSqueeze").set(phases.squeezeAngle(), phases.strain());
        required(shader, "EnergyRestWarp").set((float) rest.inflation(), (float) rest.travelScale(),
                (float) rest.radialDomainRadius(), (float) rest.restAxialMaximum());
        required(shader, "EnergyRestTransport").set(rest.derivativeMinimum(), rest.minimumWidth(),
                rest.sideSlope(), rest.upSlope());
    }

    /** Upload once per immutable capture, with no persistent staging buffer. */
    private int ensureRestTexture(DarkBallAnimeRestVolume rest) {
        if (restTexture != 0 && uploadedRest == rest) return restTexture;
        int maximumSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (rest.atlasWidth() > maximumSize || rest.atlasHeight() > maximumSize)
            throw new IllegalStateException("Energy rest volume exceeds texture support");
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int unpackSwapBytes = GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES);
        float[] data = rest.atlas();
        FloatBuffer staging = MemoryUtil.memAllocFloat(data.length);
        int texture = 0;
        boolean uploaded = false;
        try {
            texture = GL11.glGenTextures();
            staging.put(data).flip();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, rest.atlasWidth(), rest.atlasHeight(),
                    0, GL11.GL_RED, GL11.GL_FLOAT, staging);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            if (width != rest.atlasWidth()) throw new IllegalStateException("Energy rest texture upload failed");
            uploaded = true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, unpackSwapBytes);
            MemoryUtil.memFree(staging);
            if (!uploaded) GL11.glDeleteTextures(texture);
        }
        if (restTexture != 0) GL11.glDeleteTextures(restTexture);
        restTexture = texture;
        uploadedRest = rest;
        return texture;
    }

    private static void vector(ShaderInstance shader, String name, Vec3 value) {
        required(shader, name).set((float) value.x, (float) value.y, (float) value.z);
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y)
                && Double.isFinite(vector.z) && Math.abs(vector.x) <= 1.0e20
                && Math.abs(vector.y) <= 1.0e20 && Math.abs(vector.z) <= 1.0e20;
    }

    @Override public void close() {
        closeScreenTriangle();
        int texture = restTexture;
        restTexture = 0;
        uploadedRest = null;
        if (texture != 0) {
            if (RenderSystem.isOnRenderThread()) GL11.glDeleteTextures(texture);
            else RenderSystem.recordRenderCall(() -> GL11.glDeleteTextures(texture));
        }
    }

    private void closeScreenTriangle() {
        VertexBuffer old = screenTriangle;
        screenTriangle = null;
        if (old != null) {
            if (RenderSystem.isOnRenderThread()) old.close();
            else RenderSystem.recordRenderCall(old::close);
        }
    }
}
