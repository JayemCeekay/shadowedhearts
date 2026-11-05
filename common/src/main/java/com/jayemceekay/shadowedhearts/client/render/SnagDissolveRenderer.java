package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.IrisHandler;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the current posed Snag dissolve mesh during entity rendering and
 * submits it at Iris' world-color seam. Captures never retain Cobblemon's poser
 * or mutate core-shader uniforms while Iris is rendering ordinary entities.
 */
public final class SnagDissolveRenderer {
    private static final int MAX_CAPTURES = 32;
    static final int MAX_VERTICES = 32768;
    private static final Map<Integer, Capture> CAPTURES = new LinkedHashMap<>();
    private static boolean acceptingCaptures;

    private SnagDissolveRenderer() { }

    /** Iris calls this once at the beginning of the world frame, including reloads. */
    public static void beginFrame() {
        CAPTURES.clear();
        acceptingCaptures = true;
    }

    public static void clear() {
        CAPTURES.clear();
        acceptingCaptures = false;
    }

    /** Returns native buffers when the inspected Iris submission seam is unavailable. */
    public static MultiBufferSource wrap(int entityId, MultiBufferSource nativeBuffers,
                                        ResourceLocation texture, float progress, float age) {
        IrisHandler iris = AuraReaderPulseRenderer.IRIS_HANDLER;
        if (!acceptingCaptures || iris == null || !iris.isShaderPackInUse()
                || iris.isShadowRenderActive() || ModShaders.SNAG_DISSOLVE == null
                || !isUsableFrame(iris.getIrisRenderingSnapshot())
                || !Float.isFinite(progress) || progress <= 0 || progress >= 1
                || !Float.isFinite(age)) {
            return nativeBuffers;
        }
        if (!CAPTURES.containsKey(entityId) && CAPTURES.size() >= MAX_CAPTURES) {
            return nativeBuffers;
        }
        Capture capture = new Capture(nativeBuffers, texture, progress, age);
        CAPTURES.put(entityId, capture);
        return capture;
    }

    /** TAIL seals the last vertex; only complete native renderer calls can be replayed. */
    public static void finish(int entityId) {
        Capture capture = CAPTURES.get(entityId);
        if (capture != null) capture.finish();
    }

    public static boolean hasQueuedMeshes() {
        return CAPTURES.values().stream().anyMatch(capture -> capture.complete
                && !capture.nativeFallback && capture.vertexCount > 0);
    }

    /** Support props such as Cobblemon's battle raft retain their ordinary material. */
    public static MultiBufferSource nativeBuffers(MultiBufferSource buffers) {
        return buffers instanceof Capture capture ? capture.nativeBuffers : buffers;
    }

    /**
     * Called with Iris' matched world matrices and world-color target installed,
     * while unknown core shaders are allowed to retain the caller's framebuffer.
     */
    public static void renderIris() {
        acceptingCaptures = false;
        try {
            for (Capture capture : CAPTURES.values()) {
                if (!capture.complete || capture.nativeFallback || capture.vertexCount == 0) continue;
                try (ByteBufferBuilder memory = new ByteBufferBuilder(8192)) {
                    MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(memory);
                    // Each capture flushes before the next one's uniforms are uploaded.
                    DissolveBufferSource material = new DissolveBufferSource(buffers,
                            capture.texture, capture.progress, capture.age);
                    material.uploadUniforms();
                    // Resource reload can remove the shader after admission.
                    // The target-preserving RenderType then draws its native
                    // entity-cutout fallback instead of dropping the body.
                    VertexConsumer sink = buffers.getBuffer(BallRenderTypes.dissolve(capture.texture));
                    capture.replay(sink);
                    buffers.endBatch();
                }
            }
        } finally {
            CAPTURES.clear();
        }
    }

    private static boolean isUsableFrame(IrisHandler.IrisRenderingSnapshot frame) {
        return frame != null && frame.depthTexture > 0
                && frame.renderWidth > 0 && frame.renderHeight > 0
                && frame.renderWidth <= 32768 && frame.renderHeight <= 32768
                && frame.modelViewMatrix.isFinite() && frame.projectionMatrix.isFinite()
                && Float.isFinite(frame.modelViewMatrix.determinant())
                && Float.isFinite(frame.projectionMatrix.determinant())
                && Math.abs(frame.modelViewMatrix.determinant()) > 1.0e-8f
                && Math.abs(frame.projectionMatrix.determinant()) > 1.0e-8f
                && Math.abs(frame.projectionMatrix.m23()) > 1.0e-6f;
    }

    static boolean isBodyLayer(String name) {
        return name.contains("entity_cutout") || name.contains("entity_translucent")
                || name.contains("entity_solid");
    }

    static final class Capture implements MultiBufferSource {
        private final MultiBufferSource nativeBuffers;
        private final ResourceLocation texture;
        private final float progress;
        private final float age;
        private final Map<RenderType, Layer> layers = new LinkedHashMap<>();
        private int vertexCount;
        private boolean nativeFallback;
        private boolean complete;

        Capture(MultiBufferSource nativeBuffers, ResourceLocation texture, float progress, float age) {
            this.nativeBuffers = nativeBuffers;
            this.texture = texture;
            this.progress = progress;
            this.age = age;
        }

        @Override
        public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) {
            if (nativeFallback || type.format() != DefaultVertexFormat.NEW_ENTITY
                    || type.mode() != VertexFormat.Mode.QUADS || !isBodyLayer(type.toString())) {
                return nativeBuffers.getBuffer(type);
            }
            return recordingLayer(type);
        }

        VertexConsumer recordingLayer(RenderType type) {
            return layers.computeIfAbsent(type, Layer::new).consumer;
        }

        void replay(VertexConsumer sink) {
            for (Layer layer : layers.values()) {
                for (Vertex vertex : layer.vertices) vertex.emit(sink);
            }
        }

        void finish() {
            for (Layer layer : layers.values()) layer.consumer.finishVertex();
            if (!nativeFallback && layers.values().stream().anyMatch(l -> l.vertices.size() % 4 != 0)) {
                fallback();
            }
            complete = true;
        }

        /** Recover the withheld body before forwarding an oversized/invalid mesh natively. */
        private void fallback() {
            if (nativeFallback) return;
            nativeFallback = true;
            // Layer requests can switch while each consumer still holds its
            // final vertex. Complete those records before native buffers flush.
            for (Layer layer : layers.values()) {
                layer.consumer.recoverPendingVertex();
            }
            for (Layer layer : layers.values()) {
                VertexConsumer sink = nativeBuffers.getBuffer(layer.type);
                for (Vertex vertex : layer.vertices) vertex.emit(sink);
                layer.vertices.clear();
            }
        }

        private final class Layer {
            final RenderType type;
            final List<Vertex> vertices = new ArrayList<>();
            final RecordingConsumer consumer = new RecordingConsumer();

            Layer(RenderType type) { this.type = type; }

            private final class RecordingConsumer implements VertexConsumer {
                private boolean pending;
                private VertexConsumer nativeSink;
                private float x, y, z, u, v, nx, ny, nz;
                private int r, g, b, a, overlayU, overlayV, lightU, lightV;

                private void finishVertex() {
                    if (!pending) return;
                    pending = false;
                    Vertex vertex = new Vertex(x, y, z, r, g, b, a, u, v,
                            overlayU, overlayV, lightU, lightV, nx, ny, nz);
                    if (nativeFallback) {
                        if (nativeSink == null) vertex.emit(nativeBuffers.getBuffer(type));
                    } else {
                        vertices.add(vertex);
                        if (++vertexCount > MAX_VERTICES || !vertex.isFinite()) fallback();
                    }
                    nativeSink = null;
                }

                private void recoverPendingVertex() {
                    if (!pending || nativeSink != null) return;
                    vertices.add(new Vertex(x, y, z, r, g, b, a, u, v,
                            overlayU, overlayV, lightU, lightV, nx, ny, nz));
                    pending = false;
                }

                @Override
                public VertexConsumer addVertex(float x, float y, float z) {
                    finishVertex();
                    this.x = x; this.y = y; this.z = z;
                    r = g = b = a = 255;
                    u = v = nx = ny = nz = 0;
                    overlayU = overlayV = lightU = lightV = 0;
                    pending = true;
                    if (nativeFallback) {
                        nativeSink = nativeBuffers.getBuffer(type);
                        nativeSink.addVertex(x, y, z);
                    }
                    return this;
                }

                @Override
                public VertexConsumer setColor(int r, int g, int b, int a) {
                    this.r = r; this.g = g; this.b = b; this.a = a;
                    if (nativeSink != null) nativeSink.setColor(r, g, b, a);
                    return this;
                }

                @Override
                public VertexConsumer setUv(float u, float v) {
                    this.u = u; this.v = v;
                    if (nativeSink != null) nativeSink.setUv(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setUv1(int u, int v) {
                    overlayU = u; overlayV = v;
                    if (nativeSink != null) nativeSink.setUv1(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setUv2(int u, int v) {
                    lightU = u; lightV = v;
                    if (nativeSink != null) nativeSink.setUv2(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setNormal(float x, float y, float z) {
                    nx = x; ny = y; nz = z;
                    if (nativeSink != null) nativeSink.setNormal(x, y, z);
                    return this;
                }
            }
        }
    }

    record Vertex(float x, float y, float z, int r, int g, int b, int a, float u, float v,
                  int overlayU, int overlayV, int lightU, int lightV, float nx, float ny, float nz) {
        boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    && Float.isFinite(u) && Float.isFinite(v)
                    && Float.isFinite(nx) && Float.isFinite(ny) && Float.isFinite(nz);
        }

        void emit(VertexConsumer sink) {
            sink.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v)
                    .setUv1(overlayU, overlayV).setUv2(lightU, lightV).setNormal(nx, ny, nz);
        }
    }
}
