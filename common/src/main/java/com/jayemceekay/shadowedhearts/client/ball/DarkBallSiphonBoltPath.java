package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;

/**
 * Builds a camera-independent, continuously evolving plasma-arc polyline for
 * the Dark Ball siphon. Spatially coherent bands deform the complete arc while
 * progressively finer bands add electrical irregularity. Fixed tangent
 * collars pin both endpoints without introducing a visible animated kink.
 */
final class DarkBallSiphonBoltPath {
    static final int ANIMATED_BEND_COUNT = 10;
    // Root + root collar + animated bends + terminal collar + endpoint.
    static final int NODE_COUNT = ANIMATED_BEND_COUNT + 4;
    static final float BEND_RADIUS_SCALE = 0.40f;
    static final float BEND_CHORD_CAP = 0.095f;
    static final float COLLAR_CHORD_FRACTION = 0.035f;

    private static final float[] BROAD_PATH_SCALES = {
            0.70f, 1.30f, 2.05f
    };
    private static final float[] BROAD_TIME_SCALES = {
            0.60f, 0.99f, 1.40f
    };
    private static final float[] BROAD_WEIGHTS = {
            0.56f, 0.29f, 0.15f
    };
    private static final float[] FINE_PATH_SCALES = {
            2.40f, 3.25f, 4.20f
    };
    private static final float[] FINE_TIME_SCALES = {
            2.53f, 3.15f, 3.82f
    };
    private static final float[] FINE_WEIGHTS = {
            0.28f, 0.44f, 0.28f
    };
    private static final float[] GRADIENT_X = {
            1.0f, 0.9238795f, 0.7071068f, 0.3826834f,
            0.0f, -0.3826834f, -0.7071068f, -0.9238795f,
            -1.0f, -0.9238795f, -0.7071068f, -0.3826834f,
            0.0f, 0.3826834f, 0.7071068f, 0.9238795f
    };
    private static final float[] GRADIENT_Y = {
            0.0f, 0.3826834f, 0.7071068f, 0.9238795f,
            1.0f, 0.9238795f, 0.7071068f, 0.3826834f,
            0.0f, -0.3826834f, -0.7071068f, -0.9238795f,
            -1.0f, -0.9238795f, -0.7071068f, -0.3826834f
    };

    private static final float MAX_CAPTURE_TIME = 64.0f;
    private static final float BROAD_DISPLACEMENT_WEIGHT = 0.55f;
    private static final float FINE_DISPLACEMENT_WEIGHT = 3.55f;
    private static final float FINE_CONTRAST_GAIN = 2.10f;
    private static final float GRADIENT_NOISE_GAIN = 1.55f;
    private static final float PATH_DOMAIN_WARP_STRENGTH = 0.040f;
    private static final float TIME_DOMAIN_WARP_STRENGTH = 0.070f;
    private static final float PATH_DOMAIN_WARP_TIME_SCALE = 0.47f;
    private static final float TIME_DOMAIN_WARP_TIME_SCALE = 0.36f;
    private static final float LONGITUDINAL_JITTER_FRACTION = 0.010f;
    private static final float ENDPOINT_FADE_FRACTION = 0.12f;
    private static final float EPSILON_SQUARED = 0.0000001f;

    private DarkBallSiphonBoltPath() {
    }

    static void populate(Vector3f root,
                         Vector3f controlA,
                         Vector3f controlB,
                         Vector3f end,
                         float bodyRadius,
                         float captureTime,
                         Vector3f[] destination) {
        requireFinite(root, "root");
        requireFinite(controlA, "controlA");
        requireFinite(controlB, "controlB");
        requireFinite(end, "end");
        if (destination == null || destination.length < NODE_COUNT) {
            throw new IllegalArgumentException(
                    "destination must contain at least " + NODE_COUNT + " entries");
        }

        float safeRadius = Float.isFinite(bodyRadius)
                ? Math.max(bodyRadius, 0.0f)
                : 0.0f;
        float safeTime = safeCaptureTime(captureTime);

        Vector3f chord = new Vector3f(end).sub(root);
        float chordLength = chord.length();
        Vector3f forward = stableForward(chord, controlA, controlB);
        Vector3f primary = stablePrimary(
                root, controlA, controlB, end, forward);
        Vector3f secondary = new Vector3f(forward).cross(primary);
        if (secondary.lengthSquared() < EPSILON_SQUARED) {
            secondary.set(0.0f, 0.0f, 1.0f);
        } else {
            secondary.normalize();
        }

        float collarLength = chordLength * COLLAR_CHORD_FRACTION;
        Vector3f rootTangent = normalizedOr(
                new Vector3f(controlA).sub(root), forward);
        Vector3f terminalTangent = normalizedOr(
                new Vector3f(end).sub(controlB), forward);

        setNode(destination, 0, root);
        setNode(destination, 1, new Vector3f(root)
                .fma(collarLength, rootTangent));

        float bendAmplitude = bendAmplitude(safeRadius, chordLength);
        // Only capture-fixed inputs may seed motion. The endpoint and Bezier
        // controls move with the ball every frame and cannot safely seed it.
        int captureSeed = captureSeed(root, safeRadius);

        for (int nodeIndex = 2; nodeIndex <= NODE_COUNT - 3; nodeIndex++) {
            float baseT = nodeIndex / (NODE_COUNT - 1.0f);
            float endpointEnvelope = endpointEnvelope(baseT);
            DomainWarp domainWarp = domainWarp(
                    baseT, safeTime, captureSeed);

            // Every node samples the same continuous spatial fields. This is
            // deliberately not per-node random motion: low bands move the
            // complete arc coherently and higher bands add smaller wrinkles.
            float broadPrimary = gradientNoiseBand(
                    baseT, safeTime, domainWarp, captureSeed, 0,
                    BROAD_PATH_SCALES,
                    BROAD_TIME_SCALES,
                    BROAD_WEIGHTS);
            float broadSecondary = gradientNoiseBand(
                    baseT, safeTime, domainWarp, captureSeed, 1,
                    BROAD_PATH_SCALES,
                    BROAD_TIME_SCALES,
                    BROAD_WEIGHTS);
            float finePrimary = gradientNoiseBand(
                    baseT, safeTime, domainWarp, captureSeed, 2,
                    FINE_PATH_SCALES,
                    FINE_TIME_SCALES,
                    FINE_WEIGHTS);
            float fineSecondary = gradientNoiseBand(
                    baseT, safeTime, domainWarp, captureSeed, 3,
                    FINE_PATH_SCALES,
                    FINE_TIME_SCALES,
                    FINE_WEIGHTS);
            finePrimary = boundedContrast(
                    finePrimary, FINE_CONTRAST_GAIN);
            fineSecondary = boundedContrast(
                    fineSecondary, FINE_CONTRAST_GAIN);

            float primarySignal = BROAD_DISPLACEMENT_WEIGHT * broadPrimary
                    + FINE_DISPLACEMENT_WEIGHT * finePrimary;
            float secondarySignal =
                    BROAD_DISPLACEMENT_WEIGHT * broadSecondary
                            + FINE_DISPLACEMENT_WEIGHT * fineSecondary;
            float signalLengthSquared =
                    primarySignal * primarySignal
                            + secondarySignal * secondarySignal;
            // A fourth-power soft cap is nearly identity through the useful
            // middle range, then asymptotically bounds only the strongest
            // elbows. This preserves decisive zigzags without a hard clamp.
            float inverseRadialScale = (float) (1.0
                    / Math.sqrt(Math.sqrt(1.0
                    + signalLengthSquared * signalLengthSquared)));
            primarySignal *= inverseRadialScale;
            secondarySignal *= inverseRadialScale;

            float longitudinalSignal =
                    0.72f * gradientNoiseBand(
                            baseT, safeTime, domainWarp, captureSeed, 4,
                            BROAD_PATH_SCALES,
                            BROAD_TIME_SCALES,
                            BROAD_WEIGHTS)
                            + 0.28f * gradientNoiseBand(
                            baseT, safeTime, domainWarp, captureSeed, 5,
                            FINE_PATH_SCALES,
                            FINE_TIME_SCALES,
                            FINE_WEIGHTS);
            longitudinalSignal = (float) Math.tanh(longitudinalSignal);
            float longitudinalJitter =
                    LONGITUDINAL_JITTER_FRACTION
                            * endpointEnvelope * longitudinalSignal;
            float t = clamp01(baseT + longitudinalJitter);
            Vector3f node = cubicPoint(
                    root, controlA, controlB, end, t);

            float displacement = bendAmplitude * endpointEnvelope;
            node.fma(displacement * primarySignal, primary)
                    .fma(displacement * secondarySignal, secondary);
            setNode(destination, nodeIndex, node);
        }

        setNode(destination, NODE_COUNT - 2, new Vector3f(end)
                .fma(-collarLength, terminalTangent));
        setNode(destination, NODE_COUNT - 1, end);
    }

    static float bendAmplitude(float bodyRadius, float chordLength) {
        float safeRadius = Float.isFinite(bodyRadius)
                ? Math.max(bodyRadius, 0.0f)
                : 0.0f;
        float safeChord = Float.isFinite(chordLength)
                ? Math.max(chordLength, 0.0f)
                : 0.0f;
        return Math.min(safeRadius * BEND_RADIUS_SCALE,
                safeChord * BEND_CHORD_CAP);
    }

    static Vector3f cubicPoint(Vector3f root,
                               Vector3f controlA,
                               Vector3f controlB,
                               Vector3f end,
                               float t) {
        float u = clamp01(t);
        float inverse = 1.0f - u;
        return new Vector3f(root).mul(inverse * inverse * inverse)
                .add(new Vector3f(controlA).mul(
                        3.0f * inverse * inverse * u))
                .add(new Vector3f(controlB).mul(
                        3.0f * inverse * u * u))
                .add(new Vector3f(end).mul(u * u * u));
    }

    private static float gradientNoiseBand(float pathCoordinate,
                                           float captureTime,
                                           DomainWarp domainWarp,
                                           int captureSeed,
                                           int channel,
                                           float[] pathScales,
                                           float[] timeScales,
                                           float[] weights) {
        float signal = 0.0f;
        for (int octave = 0; octave < weights.length; octave++) {
            int octaveSeed = captureSeed
                    ^ channel * 0x632be5ab
                    ^ octave * 0x9e3779b9
                    ^ 0x4f1bbcdc;
            float pathOffset = 11.0f * unitHash(
                    channel, octave, octaveSeed ^ 0x2d3a91e7);
            float timeOffset = 11.0f * unitHash(
                    octave, channel, octaveSeed ^ 0x79c6a15b);
            float samplePath =
                    (pathCoordinate + domainWarp.path())
                            * pathScales[octave] + pathOffset;
            float sampleTime =
                    (captureTime + domainWarp.time())
                            * timeScales[octave] + timeOffset;
            signal += weights[octave] * gradientNoise2d(
                    samplePath, sampleTime, octaveSeed);
        }
        return signal * GRADIENT_NOISE_GAIN;
    }

    private static DomainWarp domainWarp(float pathCoordinate,
                                         float captureTime,
                                         int captureSeed) {
        float pathWarp = gradientNoise2d(
                pathCoordinate * 0.72f + 4.37f,
                captureTime * PATH_DOMAIN_WARP_TIME_SCALE - 2.91f,
                captureSeed ^ 0x19f4c2a7);
        float timeWarp = gradientNoise2d(
                pathCoordinate * 0.53f - 3.14f,
                captureTime * TIME_DOMAIN_WARP_TIME_SCALE + 5.83f,
                captureSeed ^ 0x6d83a951);
        return new DomainWarp(
                pathWarp * PATH_DOMAIN_WARP_STRENGTH,
                timeWarp * TIME_DOMAIN_WARP_STRENGTH);
    }

    private static float gradientNoise2d(float x, float y, int seed) {
        int latticeX = (int) Math.floor(x);
        int latticeY = (int) Math.floor(y);
        float fractionX = x - latticeX;
        float fractionY = y - latticeY;
        float fadeX = smootherStep(fractionX);
        float fadeY = smootherStep(fractionY);

        float lowerLeft = gradientDot(
                latticeX, latticeY, fractionX, fractionY, seed);
        float lowerRight = gradientDot(
                latticeX + 1, latticeY,
                fractionX - 1.0f, fractionY, seed);
        float upperLeft = gradientDot(
                latticeX, latticeY + 1,
                fractionX, fractionY - 1.0f, seed);
        float upperRight = gradientDot(
                latticeX + 1, latticeY + 1,
                fractionX - 1.0f, fractionY - 1.0f, seed);
        return lerp(
                lerp(lowerLeft, lowerRight, fadeX),
                lerp(upperLeft, upperRight, fadeX),
                fadeY);
    }

    private static float gradientDot(int latticeX,
                                     int latticeY,
                                     float offsetX,
                                     float offsetY,
                                     int seed) {
        int gradientIndex = latticeHash(latticeX, latticeY, seed)
                & (GRADIENT_X.length - 1);
        return GRADIENT_X[gradientIndex] * offsetX
                + GRADIENT_Y[gradientIndex] * offsetY;
    }

    private static int latticeHash(int latticeX, int latticeY, int seed) {
        int hash = seed;
        hash ^= latticeX * 0x9e3779b9;
        hash ^= latticeY * 0x632be5ab;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        return hash ^ hash >>> 16;
    }

    private static float endpointEnvelope(float pathCoordinate) {
        float clamped = clamp01(pathCoordinate);
        float rootFade = smootherStep(
                (clamped - COLLAR_CHORD_FRACTION)
                        / ENDPOINT_FADE_FRACTION);
        float terminalFade = smootherStep(
                ((1.0f - COLLAR_CHORD_FRACTION) - clamped)
                        / ENDPOINT_FADE_FRACTION);
        return rootFade * terminalFade;
    }

    private static float boundedContrast(float value, float gain) {
        return (float) Math.tanh(value * gain);
    }

    private static int captureSeed(Vector3f root, float bodyRadius) {
        int hash = 0x51ed270b;
        hash = mixGeometryBits(hash, Float.floatToIntBits(root.x));
        hash = mixGeometryBits(hash, Float.floatToIntBits(root.y));
        hash = mixGeometryBits(hash, Float.floatToIntBits(root.z));
        return mixGeometryBits(hash, Float.floatToIntBits(bodyRadius));
    }

    private static int mixGeometryBits(int hash, int value) {
        int mixed = hash ^ value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }

    private static float unitHash(int coordinateA,
                                  int coordinateB,
                                  int salt) {
        int hash = coordinateA * 0x9e3779b9;
        hash ^= coordinateB * 0x632be5ab;
        hash ^= salt;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return (hash >>> 8) * (1.0f / 16777215.0f);
    }

    private static float safeCaptureTime(float captureTime) {
        return Float.isFinite(captureTime)
                ? Math.max(0.0f, Math.min(captureTime, MAX_CAPTURE_TIME))
                : 0.0f;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(value, 1.0f));
    }

    private static float lerp(float lower, float upper, float amount) {
        return lower + (upper - lower) * amount;
    }

    private static float smootherStep(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0f - 15.0f) + 10.0f);
    }

    private static Vector3f stableForward(Vector3f chord,
                                           Vector3f controlA,
                                           Vector3f controlB) {
        Vector3f forward = new Vector3f(chord);
        if (forward.lengthSquared() < EPSILON_SQUARED) {
            forward.set(controlB).sub(controlA);
        }
        if (forward.lengthSquared() < EPSILON_SQUARED) {
            return new Vector3f(1.0f, 0.0f, 0.0f);
        }
        return forward.normalize();
    }

    private static Vector3f stablePrimary(Vector3f root,
                                           Vector3f controlA,
                                           Vector3f controlB,
                                           Vector3f end,
                                           Vector3f forward) {
        Vector3f primary = new Vector3f(controlA)
                .add(controlB)
                .sub(root)
                .sub(end)
                .mul(0.5f);
        primary.fma(-primary.dot(forward), forward);
        if (primary.lengthSquared() >= EPSILON_SQUARED) {
            return primary.normalize();
        }

        Vector3f reference = Math.abs(forward.y) < 0.82f
                ? new Vector3f(0.0f, 1.0f, 0.0f)
                : new Vector3f(1.0f, 0.0f, 0.0f);
        reference.fma(-reference.dot(forward), forward);
        if (reference.lengthSquared() < EPSILON_SQUARED) {
            reference.set(0.0f, 0.0f, 1.0f);
        }
        return reference.normalize();
    }

    private static Vector3f normalizedOr(Vector3f candidate,
                                          Vector3f fallback) {
        if (candidate.lengthSquared() < EPSILON_SQUARED) {
            return new Vector3f(fallback);
        }
        return candidate.normalize();
    }

    private static void setNode(Vector3f[] destination,
                                int index,
                                Vector3f value) {
        if (destination[index] == null) {
            destination[index] = new Vector3f(value);
        } else {
            destination[index].set(value);
        }
    }

    private static void requireFinite(Vector3f value, String name) {
        if (value == null
                || !Float.isFinite(value.x)
                || !Float.isFinite(value.y)
                || !Float.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private record DomainWarp(float path, float time) {
    }
}
