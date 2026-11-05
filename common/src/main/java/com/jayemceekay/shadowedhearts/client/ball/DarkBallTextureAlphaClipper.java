package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
        // Guidance coverage takes closest distances per intersecting triangle;
        // retain its original decomposition even when the visible union agrees.
        forEachVisibleTriangle(source, texture, consumer, false);
    }

    /** Direct-surface input: preserve coverage while removing internal texel edges. */
    static void forEachVisibleTriangleCoalesced(CapturedTriangle source,
                                                AlphaField texture,
                                                Consumer<ParamTriangle> consumer) {
        forEachVisibleTriangle(source, texture, consumer, true);
    }

    /** Reference decomposition retained for exact coverage comparisons. */
    static void forEachVisibleTrianglePerTexel(CapturedTriangle source,
                                               AlphaField texture,
                                               Consumer<ParamTriangle> consumer) {
        forEachVisibleTriangle(source, texture, consumer, false);
    }

    private static void forEachVisibleTriangle(CapturedTriangle source,
                                               AlphaField texture,
                                               Consumer<ParamTriangle> consumer,
                                               boolean coalesceRegions) {
        DarkBallPreparationCancellation.check();
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
        clipTexelCells(a, b, c, texture, consumer, coalesceRegions);
    }

    /**
     * Exact nearest-filtered texture clipping. Each texel is a constant-alpha
     * UV cell. Equal-alpha cells may be joined into non-overlapping rectangles
     * before clipping; the texture multiplier and interpolated vertex alpha
     * stay identical throughout each rectangle. Narrow islands and gradients
     * therefore retain the same coverage as the per-texel decomposition.
     */
    private static void clipTexelCells(ParamVertex a, ParamVertex b,
                                       ParamVertex c, AlphaField texture,
                                       Consumer<ParamTriangle> consumer,
                                       boolean coalesceRegions) {
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
        Map<TexelRun, TexelRegion> previous = new LinkedHashMap<>();
        for (int y = minimumY; y <= maximumY; y++) {
            DarkBallPreparationCancellation.check();
            Map<TexelRun, TexelRegion> current = new LinkedHashMap<>();
            for (int x = minimumX; x <= maximumX; x++) {
                DarkBallPreparationCancellation.checkEvery(x);
                float texelAlpha = texture.texel(x, y);
                if (texelAlpha * maximumVertexAlpha < ALPHA_CUTOFF) {
                    continue;
                }
                int firstX = x;
                int alphaBits = Float.floatToIntBits(texelAlpha);
                if (coalesceRegions) {
                    while (x < maximumX
                            && Float.floatToIntBits(texture.texel(x + 1, y)) == alphaBits) {
                        DarkBallPreparationCancellation.checkEvery(x);
                        x++;
                    }
                }
                TexelRun run = new TexelRun(firstX, x, alphaBits);
                TexelRegion region = coalesceRegions ? previous.remove(run) : null;
                if (region == null) {
                    region = new TexelRegion(run, y, y, texelAlpha);
                } else {
                    region.lastY = y;
                }
                if (coalesceRegions) {
                    current.put(run, region);
                } else {
                    clipRegion(a, b, c, texture, region, minimumU, maximumU,
                            minimumV, maximumV, polygon, scratch, consumer);
                }
            }
            for (TexelRegion region : previous.values()) {
                clipRegion(a, b, c, texture, region, minimumU, maximumU,
                        minimumV, maximumV, polygon, scratch, consumer);
            }
            previous = current;
        }
        for (TexelRegion region : previous.values()) {
            clipRegion(a, b, c, texture, region, minimumU, maximumU,
                    minimumV, maximumV, polygon, scratch, consumer);
        }
    }

    private static void clipRegion(ParamVertex a, ParamVertex b, ParamVertex c,
                                   AlphaField texture, TexelRegion region,
                                   float minimumU, float maximumU,
                                   float minimumV, float maximumV,
                                   ParamVertex[] polygon, ParamVertex[] scratch,
                                   Consumer<ParamTriangle> consumer) {
        DarkBallPreparationCancellation.check();
        float lowerU = region.run.firstX() == 0 && minimumU < 0.0f
                ? minimumU : region.run.firstX() / (float) texture.width();
        float upperU = region.run.lastX() == texture.width() - 1 && maximumU > 1.0f
                ? maximumU : (region.run.lastX() + 1.0f) / texture.width();
        float lowerV = region.firstY == 0 && minimumV < 0.0f
                ? minimumV : region.firstY / (float) texture.height();
        float upperV = region.lastY == texture.height() - 1 && maximumV > 1.0f
                ? maximumV : (region.lastY + 1.0f) / texture.height();
        polygon[0] = a;
        polygon[1] = b;
        polygon[2] = c;
        int count = clipCoordinate(polygon, scratch, 3, lowerU, true, true);
        count = clipCoordinate(scratch, polygon, count, upperU, true, false);
        count = clipCoordinate(polygon, scratch, count, lowerV, false, true);
        count = clipCoordinate(scratch, polygon, count, upperV, false, false);
        if (count < 3) {
            return;
        }
        for (int i = 0; i < count; i++) {
            ParamVertex vertex = polygon[i];
            polygon[i] = new ParamVertex(
                    vertex.weightA(), vertex.weightB(), vertex.weightC(),
                    vertex.u(), vertex.v(), vertex.vertexAlpha(),
                    region.alpha * vertex.vertexAlpha());
        }
        count = clipCombinedAlpha(polygon, scratch, count);
        if (count < 3) {
            return;
        }
        if (region.run.firstX() != region.run.lastX() || region.firstY != region.lastY) {
            triangulateRegionBoundary(scratch, count, texture, consumer);
            return;
        }
        ParamVertex origin = scratch[0];
        for (int i = 1; i + 1 < count; i++) {
            consumer.accept(new ParamTriangle(origin, scratch[i], scratch[i + 1]));
        }
    }

    /**
     * Keep texel breakpoints on the perimeter so touching regions retain shared
     * vertices even at T-junctions. Only interior texel edges are discarded;
     * this also preserves positional component grouping in the surfel planner.
     */
    private static void triangulateRegionBoundary(ParamVertex[] polygon, int count,
                                                   AlphaField texture,
                                                   Consumer<ParamTriangle> consumer) {
        float a = 0, b = 0, c = 0, u = 0, v = 0, vertexAlpha = 0, combinedAlpha = 0;
        for (int i = 0; i < count; i++) {
            ParamVertex point = polygon[i];
            a += point.weightA(); b += point.weightB(); c += point.weightC();
            u += point.u(); v += point.v();
            vertexAlpha += point.vertexAlpha(); combinedAlpha += point.combinedAlpha();
        }
        ParamVertex center = new ParamVertex(a / count, b / count, c / count,
                u / count, v / count, vertexAlpha / count, combinedAlpha / count);
        ArrayList<Float> cuts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DarkBallPreparationCancellation.check();
            ParamVertex from = polygon[i];
            ParamVertex to = polygon[(i + 1) % count];
            cuts.clear();
            addGridCuts(cuts, from.u(), to.u(), texture.width());
            addGridCuts(cuts, from.v(), to.v(), texture.height());
            cuts.sort(Float::compare);
            ParamVertex previous = from;
            float previousAmount = 0;
            for (float amount : cuts) {
                if (amount <= previousAmount || amount >= 1.0f) continue;
                ParamVertex next = interpolate(from, to, amount);
                consumer.accept(new ParamTriangle(center, previous, next));
                previous = next;
                previousAmount = amount;
            }
            consumer.accept(new ParamTriangle(center, previous, to));
        }
    }

    private static void addGridCuts(ArrayList<Float> cuts, float from, float to, int cells) {
        float span = to - from;
        if (Math.abs(span) <= 1e-8f) return;
        int first = Math.max(0, (int) Math.floor(Math.min(from, to) * cells) + 1);
        int last = Math.min(cells, (int) Math.ceil(Math.max(from, to) * cells) - 1);
        for (int boundary = first; boundary <= last; boundary++) {
            DarkBallPreparationCancellation.checkEvery(boundary);
            cuts.add((boundary / (float) cells - from) / span);
        }
    }

    private record TexelRun(int firstX, int lastX, int alphaBits) {
    }

    private static final class TexelRegion {
        final TexelRun run;
        final int firstY;
        int lastY;
        final float alpha;

        TexelRegion(TexelRun run, int firstY, int lastY, float alpha) {
            this.run = run;
            this.firstY = firstY;
            this.lastY = lastY;
            this.alpha = alpha;
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
                DarkBallPreparationCancellation.checkEvery(y);
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

    record AlphaRange(float minimum, float maximum) {
    }
}
