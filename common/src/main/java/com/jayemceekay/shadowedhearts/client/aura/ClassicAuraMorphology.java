package com.jayemceekay.shadowedhearts.client.aura;

/** Geometry adaptation plus deliberately sparse art overrides, shared by world and previews. */
public final class ClassicAuraMorphology {
    private ClassicAuraMorphology() {}

    /** Region dimensions are posed cube axes in blocks, not total Pokemon height. */
    public static float localScale(float x, float y, float z, boolean surface) {
        float low = Math.min(x, Math.min(y, z));
        float high = Math.max(x, Math.max(y, z));
        float middle = x + y + z - low - high;
        float size = surface ? middle * 0.32f : low * 0.58f + middle * 0.32f;
        return Float.isFinite(size) ? Math.max(0.10f, Math.min(1.35f, size)) : 0.10f;
    }

    /** Broad support belongs to an anatomical region, independently of its tessellation. */
    public static float envelopeScale(float x, float y, float z, boolean surface) {
        float low = Math.max(0.025f, Math.min(x, Math.min(y, z)));
        float high = Math.max(x, Math.max(y, z));
        float middle = Math.max(low, x + y + z - low - high);
        float scale = surface ? middle * 0.46f : (float) Math.sqrt(low * middle) * 1.08f;
        return Float.isFinite(scale) ? Math.max(0.14f, Math.min(2.6f, scale)) : 0.14f;
    }

    public static float curlScale(float cubeScale, float envelopeScale) {
        return Math.max(0.14f, Math.min(1.35f, Math.max(cubeScale, envelopeScale * 0.60f)));
    }

    /** Longer regions require more overlapping supports, rather than longer/wider curls. */
    public static float coverageDemand(float x, float y, float z, float supportScale, float emphasis) {
        float area = 2.0f * (x * y + y * z + x * z);
        float demand = area / Math.max(0.025f, supportScale * supportScale) * emphasis;
        return Float.isFinite(demand) ? Math.max(0.02f, Math.min(72.0f, demand)) : 0.02f;
    }

    public static int nearCoverageTarget(float demand, boolean xd) {
        return Math.max(16, Math.min(xd ? 160 : 120, (int) Math.ceil(12 + demand * (xd ? 1.05 : 0.75))));
    }

    public static int plumeCoverageTarget(float demand) {
        return Math.max(24, Math.min(240, (int) Math.ceil(18 + demand * 1.60)));
    }

    public static int curveTarget(float demand, float quality) {
        // More overlapping folds at the same individual size and opacity.
        int target = Math.max(50, Math.min(250, (int) Math.ceil(50 + demand * 1.3125)));
        return Math.max(8, Math.round(target * (0.65f + Math.max(0, Math.min(1, quality)) * 0.35f)));
    }

    /** Small anatomical details share a useful part of the body's surrounding XD field. */
    public static float xdFieldScale(float localEnvelope, float bodyEnvelope) {
        float local = Float.isFinite(localEnvelope) ? Math.max(0, localEnvelope) : .14f;
        float body = Float.isFinite(bodyEnvelope) ? Math.max(0, bodyEnvelope) : local;
        return Math.max(.14f, Math.min(2.6f, Math.max(local, body * .70f)));
    }

    /** Hovering Ledyba keeps folds around its lower body and a quieter upper shell. */
    public static float xdHeightWeight(String species, float relativeHeight, boolean envelope) {
        if (species == null || !species.endsWith(":ledyba")) return 1;
        float h = Float.isFinite(relativeHeight) ? Math.max(0, Math.min(1, relativeHeight)) : .5f;
        return envelope ? 1.15f - .45f * h : 1.40f - 1.10f * h;
    }

    /** Height is relative to the actual posed bounds; a floating body remains elevated. */
    public static float regionWeight(String species, float relativeHeight, boolean surface,
                                     boolean appendage, boolean envelope) {
        float h = Float.isFinite(relativeHeight) ? Math.max(0.0f, Math.min(1.0f, relativeHeight)) : 0.5f;
        String name = species == null ? "" : species;
        float weight = surface ? (envelope ? 0.42f : 0.30f) : appendage ? 0.90f : 1.0f;
        if (name.endsWith(":chansey") || name.endsWith(":blissey")) {
            weight *= envelope ? 1.08f - 0.34f * h : 1.32f - 1.08f * h;
        } else if (name.endsWith(":scyther") || name.endsWith(":scizor")) {
            weight *= surface && h > 0.53f ? 0.14f : 1.18f - 0.72f * h;
        } else if (name.endsWith(":spheal") || name.endsWith(":carvanha")) {
            // Compact round and suspended fish keep surrounding coverage; no floor emitter.
            weight *= surface ? 1.30f : 1.0f;
        } else if (!envelope) {
            weight *= 1.08f - 0.32f * h;
        }
        return Math.max(0.02f, Math.min(1.4f, weight));
    }
}
