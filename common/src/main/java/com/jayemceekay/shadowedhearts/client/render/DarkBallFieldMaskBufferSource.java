package com.jayemceekay.shadowedhearts.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture-only buffer wrapper for the Dark Ball impact snapshot.
 *
 * <p>Eligible Pokemon model render types are recorded as world-space textured
 * quads for later FBO replay, but no submitted geometry reaches the live render
 * buffers. This lets the Dark Ball capture the exact model silhouette on the
 * hit frame without drawing the original Pokemon's color or depth.
 */
public final class DarkBallFieldMaskBufferSource implements MultiBufferSource {

    private static final VertexConsumer DISCARD = new DiscardingVertexConsumer();

    private final List<CapturedQuad> capturedQuads;
    private final List<Vec3> capturedWorldPositions;
    private final List<CapturedTriangle> capturedTriangles;
    private final Vec3 cameraPosition;
    private final List<CapturingVertexConsumer> consumers = new ArrayList<>();

    public DarkBallFieldMaskBufferSource(List<CapturedQuad> capturedQuads,
                                         List<Vec3> capturedWorldPositions,
                                         List<CapturedTriangle> capturedTriangles,
                                         Vec3 cameraPosition) {
        this.capturedQuads = capturedQuads;
        this.capturedWorldPositions = capturedWorldPositions;
        this.capturedTriangles = capturedTriangles;
        this.cameraPosition = cameraPosition;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
        if (!DarkBallEntityRenderTypeFilter.accepts(renderType)) {
            return DISCARD;
        }

        CapturingVertexConsumer capturing = new CapturingVertexConsumer(
                capturedQuads,
                capturedWorldPositions,
                capturedTriangles,
                cameraPosition
        );
        consumers.add(capturing);
        return capturing;
    }

    /** Returns a buffer source that emits neither color nor depth geometry. */
    public static MultiBufferSource discarding() {
        return renderType -> DISCARD;
    }

    public void finishCapture() {
        for (CapturingVertexConsumer consumer : consumers) {
            consumer.finishCapture();
        }
        consumers.clear();
    }

    public record CapturedQuad(CapturedVertex p1,
                               CapturedVertex p2,
                               CapturedVertex p3,
                               CapturedVertex p4,
                               Vec3 center) {
    }

    public record CapturedVertex(Vec3 worldPos,
                                  float u,
                                  float v,
                                  int red,
                                  int green,
                                  int blue,
                                  int alpha,
                                  int overlayU,
                                  int overlayV,
                                  int lightU,
                                  int lightV,
                                  float normalX,
                                  float normalY,
                                  float normalZ) {
    }

    public record CapturedTriangle(Vec3 a, Vec3 b, Vec3 c,
                                   float au, float av,
                                   float bu, float bv,
                                   float cu, float cv,
                                   int alphaA, int alphaB, int alphaC) {
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private static final int MAX_CAPTURED_QUADS = 16000;
        private static final int MAX_CAPTURED_VERTICES = 24000;
        private static final int MAX_CAPTURED_TRIANGLES = 32000;

        private final List<CapturedQuad> capturedQuads;
        private final List<Vec3> capturedWorldPositions;
        private final List<CapturedTriangle> capturedTriangles;
        private final Vec3 cameraPosition;
        private final CapturedVertex[] pendingFace = new CapturedVertex[4];
        private int pendingFaceCount = 0;
        private MutableVertex current;

        private CapturingVertexConsumer(List<CapturedQuad> capturedQuads,
                                        List<Vec3> capturedWorldPositions,
                                        List<CapturedTriangle> capturedTriangles,
                                        Vec3 cameraPosition) {
            this.capturedQuads = capturedQuads;
            this.capturedWorldPositions = capturedWorldPositions;
            this.capturedTriangles = capturedTriangles;
            this.cameraPosition = cameraPosition;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishCurrentVertex();
            current = new MutableVertex(new Vec3(
                    cameraPosition.x + x,
                    cameraPosition.y + y,
                    cameraPosition.z + z
            ));
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (current != null) {
                current.red = red;
                current.green = green;
                current.blue = blue;
                current.alpha = alpha;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (current != null) {
                current.u = u;
                current.v = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (current != null) {
                current.overlayU = u;
                current.overlayV = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (current != null) {
                current.lightU = u;
                current.lightV = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (current != null) {
                current.normalX = x;
                current.normalY = y;
                current.normalZ = z;
            }
            return this;
        }

        private void finishCapture() {
            finishCurrentVertex();
            pendingFaceCount = 0;
        }

        private void finishCurrentVertex() {
            if (current == null) {
                return;
            }

            CapturedVertex vertex = current.freeze();
            current = null;
            if (capturedWorldPositions != null && capturedWorldPositions.size() < MAX_CAPTURED_VERTICES) {
                capturedWorldPositions.add(vertex.worldPos());
            }
            pendingFace[pendingFaceCount++] = vertex;
            if (pendingFaceCount == 4) {
                if (capturedQuads.size() < MAX_CAPTURED_QUADS) {
                    Vec3 center = pendingFace[0].worldPos()
                            .add(pendingFace[1].worldPos())
                            .add(pendingFace[2].worldPos())
                            .add(pendingFace[3].worldPos())
                            .scale(0.25);
                    capturedQuads.add(new CapturedQuad(
                            pendingFace[0],
                            pendingFace[1],
                            pendingFace[2],
                            pendingFace[3],
                            center
                    ));
                    if (capturedWorldPositions != null && capturedWorldPositions.size() < MAX_CAPTURED_VERTICES) {
                        capturedWorldPositions.add(center);
                    }
                }
                if (capturedTriangles != null && capturedTriangles.size() + 1 < MAX_CAPTURED_TRIANGLES) {
                    capturedTriangles.add(new CapturedTriangle(
                            pendingFace[0].worldPos(),
                            pendingFace[1].worldPos(),
                            pendingFace[2].worldPos(),
                            pendingFace[0].u(), pendingFace[0].v(),
                            pendingFace[1].u(), pendingFace[1].v(),
                            pendingFace[2].u(), pendingFace[2].v(),
                            pendingFace[0].alpha(), pendingFace[1].alpha(), pendingFace[2].alpha()
                    ));
                    capturedTriangles.add(new CapturedTriangle(
                            pendingFace[0].worldPos(),
                            pendingFace[2].worldPos(),
                            pendingFace[3].worldPos(),
                            pendingFace[0].u(), pendingFace[0].v(),
                            pendingFace[2].u(), pendingFace[2].v(),
                            pendingFace[3].u(), pendingFace[3].v(),
                            pendingFace[0].alpha(), pendingFace[2].alpha(), pendingFace[3].alpha()
                    ));
                }
                pendingFaceCount = 0;
            }
        }
    }

    private static final class DiscardingVertexConsumer implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static final class MutableVertex {
        private final Vec3 worldPos;
        private float u;
        private float v;
        private int red = 255;
        private int green = 255;
        private int blue = 255;
        private int alpha = 255;
        private int overlayU = OverlayTexture.NO_OVERLAY & 0xFFFF;
        private int overlayV = (OverlayTexture.NO_OVERLAY >>> 16) & 0xFFFF;
        private int lightU = 240;
        private int lightV = 240;
        private float normalX = 0f;
        private float normalY = 1f;
        private float normalZ = 0f;

        private MutableVertex(Vec3 worldPos) {
            this.worldPos = worldPos;
        }

        private CapturedVertex freeze() {
            return new CapturedVertex(
                    worldPos,
                    u,
                    v,
                    red,
                    green,
                    blue,
                    alpha,
                    overlayU,
                    overlayV,
                    lightU,
                    lightV,
                    normalX,
                    normalY,
                    normalZ
            );
        }
    }
}
