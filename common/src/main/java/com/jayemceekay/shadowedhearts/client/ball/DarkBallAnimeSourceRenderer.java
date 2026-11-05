package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

/** Captured source mesh used only during conversion into the anime energy body. */
final class DarkBallAnimeSourceRenderer implements AutoCloseable {
    private VertexBuffer buffer;
    private Object uploaded;

    boolean render(DarkBallSurfaceMesh mesh, DarkBallVolumeBuildResult volume,
                   Camera camera, float fade, DarkBallAnimeMotion.Frame animeFrame,
                   boolean energyHandoff) {
        ShaderInstance shader = ModShaders.DARK_BALL_ANIME_SOURCE;
        if (shader == null || mesh == null || mesh.triangleCount() == 0)
            return false;
        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            Object identity = animeFrame.binding().coreMesh();
            if (uploaded != identity || buffer == null || buffer.isInvalid()) {
                upload(animeFrame.binding().coreMesh());
            }
            vector(shader, "VolumeRootCameraRelative", volume.root().subtract(camera.getPosition()));
            vector(shader, "VolumeAxis", volume.axis());
            vector(shader, "VolumeSide", volume.side());
            vector(shader, "VolumeUp", volume.up());
            scalar(shader, "CoreCoverage", Math.max(0, Math.min(1, fade)));
            DarkBallAnimeMotion.apply(shader, animeFrame);
            Uniform handoff = shader.getUniform("EnergySourceHandoff");
            if (handoff == null && energyHandoff) return false;
            if (handoff != null) handoff.set(energyHandoff ? 1 : 0);
            DarkBallDensityFBO.applyBodyDepthUniforms(shader);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            // Captured model sheets are intentionally two-sided; no watertight requirement.
            RenderSystem.disableCull();
            buffer.bind();
            buffer.drawWithShader(DarkBallRenderContext.modelView(camera),
                    DarkBallRenderContext.projection(), shader);
            return true;
        }
    }

    private void upload(DarkBallAnimeCoreMesh mesh) {
        upload(mesh, mesh.positions(), mesh.normals(), null, mesh.indices());
    }

    private void upload(Object identity, float[] p, float[] n, float[] release, int[] indices) {
        close();
        int bytes = Math.multiplyExact(indices.length, ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT.getVertexSize());
        VertexBuffer next = new VertexBuffer(VertexBuffer.Usage.STATIC);
        boolean success = false;
        try (ByteBufferBuilder storage = new ByteBufferBuilder(Math.max(1, bytes))) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.TRIANGLES,
                    ModShaders.DARK_BALL_MANUAL_ENTITY_FORMAT);
            for (int index : indices) {
                if (index < 0 || index >= p.length / 3) throw new IllegalArgumentException("invalid core index");
                int offset = index * 3;
                builder.addVertex(p[offset], p[offset + 1], p[offset + 2])
                        .setColor(255, 255, 255, 255).setUv(release == null ? 0 : release[index], 0)
                        .setUv1(0, 0).setUv2(0, 0).setNormal(n[offset], n[offset + 1], n[offset + 2]);
            }
            MeshData data = builder.build();
            if (data == null) throw new IllegalStateException("empty anime source");
            next.bind();
            next.upload(data);
            buffer = next;
            uploaded = identity;
            success = true;
        } finally {
            VertexBuffer.unbind();
            if (!success) next.close();
        }
    }

    private static void scalar(ShaderInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }
    private static void vector(ShaderInstance shader, String name, Vec3 value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set((float) value.x, (float) value.y, (float) value.z);
    }
    @Override public void close() {
        VertexBuffer old = buffer;
        buffer = null;
        uploaded = null;
        if (old != null) {
            if (RenderSystem.isOnRenderThread()) old.close();
            else RenderSystem.recordRenderCall(old::close);
        }
    }
}
