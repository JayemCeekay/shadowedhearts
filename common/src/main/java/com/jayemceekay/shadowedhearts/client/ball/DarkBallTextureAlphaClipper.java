package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;

import java.util.function.Consumer;

/**
 * Converts a render triangle into texture-alpha-clipped microtriangles.
 *
 * <p>The output vertices remain barycentric with respect to the captured
 * triangle. This lets both capture bounds and voxel rasterization consume the
 * exact same clipped surface without converting world positions to floats.
 */
final class DarkBallTextureAlphaClipper {
    static final float ALPHA_CUTOFF = 0.08f;

    private DarkBallTextureAlphaClipper() {
    }

    static void forEachVisibleTriangle(CapturedTriangle source,
                                       AlphaField texture,
                                       Consumer<ParamTriangle> consumer) {
        ParamVertex a = vertex(1.0f, 0.0f, 0.0f,
                source.au(), source.av(), source.alphaA() / 255.0f, texture);
        ParamVertex b = vertex(0.0f, 1.0f, 0.0f,
                source.bu(), source.bv(), source.alphaB() / 255.0f, texture);
        ParamVertex c = vertex(0.0f, 0.0f, 1.0f,
                source.cu(), source.cv(), source.alphaC() / 255.0f, texture);

        if (texture == null) {
            clipLinear(a, b, c, consumer);
            return;
        }
        AlphaRange range = combinedAlphaRange(a, b, c, texture);
        if (range.maximum() < ALPHA_CUTOFF) {
            return;
        }
        if (range.minimum() >= ALPHA_CUTOFF) {
            consumer.accept(new ParamTriangle(a, b, c));
            return;
        }
        clipTexelCells(a, b, c, texture, consumer);
    }

    /**
     * Exact nearest-filtered texture clipping. Each texel is a constant-alpha
     * UV cell, so clipping the source triangle against the cell rectangle and
     * then against interpolated vertex alpha reproduces the render cutoff.
     */
    private static void clipTexelCells(ParamVertex a, ParamVertex b,
                                       ParamVertex c, AlphaField texture,
                                       Consumer<ParamTriangle> consumer) {
        float minimumU = Math.min(a.u(), Math.min(b.u(), c.u()));
        float maximumU = Math.max(a.u(), Math.max(b.u(), c.u()));
        float minimumV = Math.min(a.v(), Math.min(b.v(), c.v()));
        float maximumV = Math.max(a.v(), Math.max(b.v(), c.v()));
        int minimumX = texture.texelX(minimumU);
        int maximumX = texture.texelX(maximumU);
        int minimumY = texture.texelY(minimumV);
        int maximumY = texture.texelY(maximumV);
        ParamVertex[] polygon = new ParamVertex[16];
        ParamVertex[] scratch = new ParamVertex[16];
        float maximumVertexAlpha = Math.max(a.vertexAlpha(),
                Math.max(b.vertexAlpha(), c.vertexAlpha()));
        for (int y = minimumY; y <= maximumY; y++) {
            float lowerV = y == 0 && minimumV < 0.0f
                    ? minimumV : y / (float) texture.height();
            float upperV = y == texture.height() - 1 && maximumV > 1.0f
                    ? maximumV : (y + 1.0f) / texture.height();
            for (int x = minimumX; x <= maximumX; x++) {
                float texelAlpha = texture.texel(x, y);
                if (texelAlpha * maximumVertexAlpha < ALPHA_CUTOFF) {
                    continue;
                }
                float lowerU = x == 0 && minimumU < 0.0f
                        ? minimumU : x / (float) texture.width();
                float upperU = x == texture.width() - 1 && maximumU > 1.0f
                        ? maximumU : (x + 1.0f) / texture.width();

                polygon[0] = a;
                polygon[1] = b;
                polygon[2] = c;
                int count = 3;
                count = clipCoordinate(polygon, scratch, count,
                        lowerU, true, true);
                ParamVertex[] swap = polygon;
                polygon = scratch;
                scratch = swap;
                count = clipCoordinate(polygon, scratch, count,
                        upperU, true, false);
                swap = polygon;
                polygon = scratch;
                scratch = swap;
                count = clipCoordinate(polygon, scratch, count,
                        lowerV, false, true);
                swap = polygon;
                polygon = scratch;
                scratch = swap;
                count = clipCoordinate(polygon, scratch, count,
                        upperV, false, false);
                swap = polygon;
                polygon = scratch;
                scratch = swap;
                if (count < 3) {
                    continue;
                }

                for (int i = 0; i < count; i++) {
                    ParamVertex vertex = polygon[i];
                    polygon[i] = new ParamVertex(
                            vertex.weightA(), vertex.weightB(), vertex.weightC(),
                            vertex.u(), vertex.v(), vertex.vertexAlpha(),
                            texelAlpha * vertex.vertexAlpha()
                    );
                }
                count = clipCombinedAlpha(polygon, scratch, count);
                if (count < 3) {
                    continue;
                }
                ParamVertex origin = scratch[0];
                for (int i = 1; i + 1 < count; i++) {
                    consumer.accept(new ParamTriangle(
                            origin, scratch[i], scratch[i + 1]));
                }
            }
        }
    }

    private static int clipCoordinate(ParamVertex[] input,
                                      ParamVertex[] output,
                                      int inputCount,
                                      float boundary,
                                      boolean useU,
                                      boolean keepGreater) {
        if (inputCount == 0) {
            return 0;
        }
        int outputCount = 0;
        ParamVertex previous = input[inputCount - 1];
        float previousCoordinate = useU ? previous.u() : previous.v();
        boolean previousInside = keepGreater
                ? previousCoordinate >= boundary
                : previousCoordinate <= boundary;
        for (int i = 0; i < inputCount; i++) {
            ParamVertex current = input[i];
            float currentCoordinate = useU ? current.u() : current.v();
            boolean currentInside = keepGreater
                    ? currentCoordinate >= boundary
                    : currentCoordinate <= boundary;
            if (currentInside != previousInside) {
                float denominator = currentCoordinate - previousCoordinate;
                float amount = Math.abs(denominator) <= 1e-8f
                        ? 0.5f
                        : (boundary - previousCoordinate) / denominator;
                output[outputCount++] = interpolate(previous, current,
                        clamp(amount, 0.0f, 1.0f));
            }
            if (currentInside) {
                output[outputCount++] = current;
            }
            previous = current;
            previousCoordinate = currentCoordinate;
            previousInside = currentInside;
        }
        return outputCount;
    }

    private static int clipCombinedAlpha(ParamVertex[] input,
                                         ParamVertex[] output,
                                         int inputCount) {
        if (inputCount == 0) {
            return 0;
        }
        int outputCount = 0;
        ParamVertex previous = input[inputCount - 1];
        boolean previousInside = previous.combinedAlpha() >= ALPHA_CUTOFF;
        for (int i = 0; i < inputCount; i++) {
            ParamVertex current = input[i];
            boolean currentInside = current.combinedAlpha() >= ALPHA_CUTOFF;
            if (currentInside != previousInside) {
                output[outputCount++] = alphaIntersection(previous, current);
            }
            if (currentInside) {
                output[outputCount++] = current;
            }
            previous = current;
            previousInside = currentInside;
        }
        return outputCount;
    }

    private static ParamVertex interpolate(ParamVertex from, ParamVertex to,
                                           float amount) {
        return new ParamVertex(
                lerp(from.weightA(), to.weightA(), amount),
                lerp(from.weightB(), to.weightB(), amount),
                lerp(from.weightC(), to.weightC(), amount),
                lerp(from.u(), to.u(), amount),
                lerp(from.v(), to.v(), amount),
                lerp(from.vertexAlpha(), to.vertexAlpha(), amount),
                lerp(from.combinedAlpha(), to.combinedAlpha(), amount)
        );
    }

    private static void clipLinear(ParamVertex a, ParamVertex b, ParamVertex c,
                                   Consumer<ParamTriangle> consumer) {
        ParamVertex[] input = {a, b, c};
        ParamVertex[] output = new ParamVertex[6];
        int outputCount = 0;
        ParamVertex previous = input[input.length - 1];
        boolean previousInside = previous.combinedAlpha() >= ALPHA_CUTOFF;

        for (ParamVertex current : input) {
            boolean currentInside = current.combinedAlpha() >= ALPHA_CUTOFF;
            if (currentInside != previousInside) {
                output[outputCount++] = alphaIntersection(previous, current);
            }
            if (currentInside) {
                output[outputCount++] = current;
            }
            previous = current;
            previousInside = currentInside;
        }

        if (outputCount < 3) {
            return;
        }
        ParamVertex origin = output[0];
        for (int i = 1; i + 1 < outputCount; i++) {
            consumer.accept(new ParamTriangle(origin, output[i], output[i + 1]));
        }
    }

    private static ParamVertex alphaIntersection(ParamVertex from, ParamVertex to) {
        float denominator = to.combinedAlpha() - from.combinedAlpha();
        float amount = Math.abs(denominator) <= 1e-8f
                ? 0.5f
                : (ALPHA_CUTOFF - from.combinedAlpha()) / denominator;
        amount = clamp(amount, 0.0f, 1.0f);
        return new ParamVertex(
                lerp(from.weightA(), to.weightA(), amount),
                lerp(from.weightB(), to.weightB(), amount),
                lerp(from.weightC(), to.weightC(), amount),
                lerp(from.u(), to.u(), amount),
                lerp(from.v(), to.v(), amount),
                lerp(from.vertexAlpha(), to.vertexAlpha(), amount),
                ALPHA_CUTOFF
        );
    }

    private static ParamVertex vertex(float weightA, float weightB, float weightC,
                                      float u, float v, float vertexAlpha,
                                      AlphaField texture) {
        float boundedVertexAlpha = clamp(vertexAlpha, 0.0f, 1.0f);
        float combinedAlpha = texture == null
                ? boundedVertexAlpha
                : texture.sample(u, v) * boundedVertexAlpha;
        return new ParamVertex(weightA, weightB, weightC, u, v,
                boundedVertexAlpha, combinedAlpha);
    }

    private static AlphaRange combinedAlphaRange(ParamVertex a, ParamVertex b,
                                                 ParamVertex c,
                                                 AlphaField texture) {
        float minimumU = Math.min(a.u(), Math.min(b.u(), c.u()));
        float maximumU = Math.max(a.u(), Math.max(b.u(), c.u()));
        float minimumV = Math.min(a.v(), Math.min(b.v(), c.v()));
        float maximumV = Math.max(a.v(), Math.max(b.v(), c.v()));
        AlphaRange textureRange = texture.range(minimumU, minimumV,
                maximumU, maximumV);
        float minimumVertexAlpha = Math.min(a.vertexAlpha(),
                Math.min(b.vertexAlpha(), c.vertexAlpha()));
        float maximumVertexAlpha = Math.max(a.vertexAlpha(),
                Math.max(b.vertexAlpha(), c.vertexAlpha()));
        return new AlphaRange(
                textureRange.minimum() * minimumVertexAlpha,
                textureRange.maximum() * maximumVertexAlpha
        );
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class AlphaField {
        private final int width;
        private final int height;
        private final float[] alpha;

        AlphaField(int width, int height, float[] alpha) {
            if (width <= 0 || height <= 0
                    || alpha == null || alpha.length != width * height) {
                throw new IllegalArgumentException(
                        "alpha field dimensions must match its samples");
            }
            this.width = width;
            this.height = height;
            this.alpha = alpha.clone();
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        float sample(float u, float v) {
            if (!Float.isFinite(u) || !Float.isFinite(v)) {
                return 0.0f;
            }
            int x = Math.max(0, Math.min(width - 1,
                    (int) Math.floor(clamp(u, 0.0f, 1.0f) * width)));
            int y = Math.max(0, Math.min(height - 1,
                    (int) Math.floor(clamp(v, 0.0f, 1.0f) * height)));
            return texel(x, y);
        }

        AlphaRange range(float minimumU, float minimumV,
                         float maximumU, float maximumV) {
            float clampedMinimumU = clamp(Math.min(minimumU, maximumU),
                    0.0f, 1.0f);
            float clampedMaximumU = clamp(Math.max(minimumU, maximumU),
                    0.0f, 1.0f);
            float clampedMinimumV = clamp(Math.min(minimumV, maximumV),
                    0.0f, 1.0f);
            float clampedMaximumV = clamp(Math.max(minimumV, maximumV),
                    0.0f, 1.0f);

            float minimumX = clampedMinimumU * width;
            float maximumX = clampedMaximumU * width;
            float minimumY = clampedMinimumV * height;
            float maximumY = clampedMaximumV * height;
            int x0 = Math.max(0, Math.min(width - 1,
                    (int) Math.floor(minimumX)));
            int x1 = Math.max(0, Math.min(width - 1,
                    (int) Math.floor(maximumX)));
            int y0 = Math.max(0, Math.min(height - 1,
                    (int) Math.floor(minimumY)));
            int y1 = Math.max(0, Math.min(height - 1,
                    (int) Math.floor(maximumY)));

            float minimum = Float.POSITIVE_INFINITY;
            float maximum = Float.NEGATIVE_INFINITY;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float value = texel(x, y);
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
            }
            return new AlphaRange(minimum, maximum);
        }

        private float texel(int x, int y) {
            return alpha[y * width + x];
        }

        private int texelX(float u) {
            if (!Float.isFinite(u)) {
                return 0;
            }
            return Math.max(0, Math.min(width - 1,
                    (int) Math.floor(clamp(u, 0.0f, 1.0f) * width)));
        }

        private int texelY(float v) {
            if (!Float.isFinite(v)) {
                return 0;
            }
            return Math.max(0, Math.min(height - 1,
                    (int) Math.floor(clamp(v, 0.0f, 1.0f) * height)));
        }
    }

    record ParamVertex(float weightA, float weightB, float weightC,
                       float u, float v, float vertexAlpha,
                       float combinedAlpha) {
    }

    record ParamTriangle(ParamVertex a, ParamVertex b, ParamVertex c) {
    }

    private record AlphaRange(float minimum, float maximum) {
    }
}
