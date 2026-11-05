package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded posed mesh cache. Never retains or renders the shared old Cobblemon poser. */
final class VoidShadowSnapshotBufferSource implements MultiBufferSource {
    static final int MAX_VERTICES = 8192;
    private final MultiBufferSource delegate;
    private final Matrix4f inversePose;
    private final Matrix3f inverseNormal;
    private final Map<RenderType, RecordingConsumer> layers = new LinkedHashMap<>();
    private int count;
    private boolean overflow;

    VoidShadowSnapshotBufferSource(MultiBufferSource delegate, PoseStack.Pose root) {
        this.delegate = delegate;
        inversePose = new Matrix4f(root.pose()).invertAffine();
        inverseNormal = new Matrix3f(root.normal()).invert();
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) {
        if (type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS
                || (VoidShadowRenderTypes.material(type) == null && VoidShadowBufferSource.material(type) == null)) {
            return delegate.getBuffer(type);
        }
        // BufferSource can flush between layers, so refresh the delegate on each request.
        return recordingLayer(type, delegate.getBuffer(type));
    }

    VertexConsumer recordingLayer(RenderType type, VertexConsumer sink) {
        RecordingConsumer consumer = layers.computeIfAbsent(type, key -> new RecordingConsumer());
        consumer.delegate = sink;
        return consumer;
    }

    MultiBufferSource nativeBuffers() { return delegate; }

    Snapshot finish() {
        for (RecordingConsumer layer : layers.values()) layer.finishVertex();
        if (overflow || count == 0) return null;
        List<Layer> result = new ArrayList<>(layers.size());
        for (var entry : layers.entrySet()) {
            if (entry.getValue().vertices.size() % 4 != 0) return null;
            result.add(new Layer(entry.getKey(), List.copyOf(entry.getValue().vertices)));
        }
        return new Snapshot(List.copyOf(result));
    }

    record Layer(RenderType type, List<Vertex> vertices) { }
    record Snapshot(List<Layer> layers) {
        void render(PoseStack.Pose pose, MultiBufferSource buffers, float opacity) {
            if (opacity <= 0.001f) return;
            MultiBufferSource fade = new VoidShadowFadeBufferSource(buffers, opacity);
            for (Layer layer : layers) {
                VertexConsumer consumer = fade.getBuffer(layer.type());
                for (Vertex v : layer.vertices()) {
                    v.emit(pose, consumer);
                }
            }
        }
    }
    record Vertex(float x, float y, float z, int r, int g, int b, int a, float u, float v,
                  int overlayU, int overlayV, int lightU, int lightV, float nx, float ny, float nz) {
        void emit(PoseStack.Pose pose, VertexConsumer consumer) {
            consumer.addVertex(pose.pose(), x, y, z).setColor(r, g, b, a)
                    .setUv(u, v).setUv1(overlayU, overlayV).setUv2(lightU, lightV)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private final class RecordingConsumer implements VertexConsumer {
        private VertexConsumer delegate;
        private final List<Vertex> vertices = new ArrayList<>();
        private boolean pending;
        private float x, y, z, u, v, nx, ny, nz;
        private int r, g, b, a, overlayU, overlayV, lightU, lightV;

        private void finishVertex() {
            if (!pending) return;
            pending = false;
            if (++count > MAX_VERTICES) { overflow = true; return; }
            Vector3f position = inversePose.transformPosition(new Vector3f(x, y, z));
            Vector3f normal = inverseNormal.transform(new Vector3f(nx, ny, nz)).normalize();
            if (!position.isFinite() || !normal.isFinite()) { overflow = true; return; }
            vertices.add(new Vertex(position.x, position.y, position.z, r, g, b, a, u, v,
                    overlayU, overlayV, lightU, lightV, normal.x, normal.y, normal.z));
        }
        public VertexConsumer addVertex(float x, float y, float z) {
            finishVertex(); this.x = x; this.y = y; this.z = z; pending = true;
            delegate.addVertex(x, y, z); return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.r = r; this.g = g; this.b = b; this.a = a; delegate.setColor(r, g, b, a); return this;
        }
        public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; delegate.setUv(u, v); return this; }
        public VertexConsumer setUv1(int u, int v) {
            overlayU = u; overlayV = v; delegate.setUv1(u, v); return this;
        }
        public VertexConsumer setUv2(int u, int v) { lightU = u; lightV = v; delegate.setUv2(u, v); return this; }
        public VertexConsumer setNormal(float x, float y, float z) {
            nx = x; ny = y; nz = z; delegate.setNormal(x, y, z); return this;
        }
    }
}
