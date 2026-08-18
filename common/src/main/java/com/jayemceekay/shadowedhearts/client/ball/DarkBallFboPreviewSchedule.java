package com.jayemceekay.shadowedhearts.client.ball;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure timing/scheduling contract for the temporary Dark Ball framebuffer
 * deep-trace diagnostics. It deliberately contains no Minecraft, OpenGL, or
 * file-system calls.
 */
final class DarkBallFboPreviewSchedule {

    static final int MIN_SAMPLES_PER_PHASE = 1;
    static final int MAX_SAMPLES_PER_PHASE = 5;
    static final int DEFAULT_DEEP_TRACE_SAMPLES_PER_PHASE = 5;
    static final float DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS =
            DarkBallCaptureVfx
                    .SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS;
    static final float MAX_CAPTURE_LATENESS_SECONDS =
            DarkBallCaptureVfx.MAX_TICK_DELTA_SECONDS + 0.05f;

    private static final List<Phase> PHASES = List.of(
            phase(0, "formation", "formation",
                    DarkBallCaptureVfx.CONVERSION_START,
                    DarkBallCaptureVfx.CONVERSION_SOLID),
            phase(1, "turbulence-ramp", "turbulence ramp",
                    DarkBallCaptureVfx.TURBULENCE_START,
                    DarkBallCaptureVfx.TURBULENCE_FULL),
            phase(2, "turbulence-hold", "maximum turbulence hold",
                    DarkBallCaptureVfx.TURBULENCE_FULL,
                    DarkBallCaptureVfx.SIPHON_START),
            phase(3, "collapse", "body collapse / siphon extension",
                    DarkBallCaptureVfx.SIPHON_START,
                    DarkBallCaptureVfx.SIPHON_COLLAPSE_START),
            phase(4, "drain", "terminal handoff / siphon drain",
                    DarkBallCaptureVfx.SIPHON_COLLAPSE_START,
                    DarkBallCaptureVfx.BALL_ABSORB_END),
            phase(5, "fade", "final absorption fade",
                    DarkBallCaptureVfx.BALL_ABSORB_END,
                    DarkBallCaptureVfx.VFX_END)
    );

    private DarkBallFboPreviewSchedule() {
    }

    static List<Phase> phases() {
        return PHASES;
    }

    /**
     * Builds globally age-sorted, phase-relative interior samples. Five
     * requested samples land at 10, 30, 50, 70, and 90 percent of a phase.
     * The final absorption fade is deliberately reduced to one sample. Every
     * substantial phase retains the requested interior sample count even in a
     * faster duration profile so cross-profile comparisons preserve the same
     * 10/30/50/70/90 percent landmarks.
     */
    static List<CapturePoint> capturePoints(int requestedSamplesPerPhase) {
        int requested = clampSamplesPerPhase(requestedSamplesPerPhase);
        List<CapturePoint> unsorted = new ArrayList<>();
        for (Phase phase : PHASES) {
            float duration = phase.endAge() - phase.startAge();
            int sampleCount = phase.index() == 5 ? 1 : requested;
            for (int sampleIndex = 0;
                 sampleIndex < sampleCount;
                 sampleIndex++) {
                float fraction =
                        (sampleIndex + 0.5f) / sampleCount;
                float targetAge =
                        phase.startAge() + duration * fraction;
                unsorted.add(new CapturePoint(
                        -1,
                        phase,
                        sampleIndex,
                        sampleCount,
                        targetAge));
            }
        }
        unsorted.sort(
                Comparator.comparingDouble(CapturePoint::targetAge)
                        .thenComparingInt(
                                point -> point.phase().index())
                        .thenComparingInt(CapturePoint::sampleIndex));

        List<CapturePoint> ordered =
                new ArrayList<>(unsorted.size());
        for (int index = 0; index < unsorted.size(); index++) {
            CapturePoint point = unsorted.get(index);
            ordered.add(new CapturePoint(
                    index,
                    point.phase(),
                    point.sampleIndex(),
                    point.sampleCount(),
                    point.targetAge()));
        }
        return List.copyOf(ordered);
    }

    static int clampSamplesPerPhase(int requestedSamplesPerPhase) {
        return Math.max(
                MIN_SAMPLES_PER_PHASE,
                Math.min(
                        MAX_SAMPLES_PER_PHASE,
                        requestedSamplesPerPhase));
    }

    static String phaseAt(float age) {
        if (age < DarkBallCaptureVfx.TURBULENCE_START) {
            return "formation";
        }
        if (age < DarkBallCaptureVfx.TURBULENCE_FULL) {
            return "turbulence ramp";
        }
        if (age < DarkBallCaptureVfx.SIPHON_START) {
            return "maximum turbulence hold";
        }
        if (age < DarkBallCaptureVfx.SIPHON_COLLAPSE_START) {
            return "body collapse / siphon extension";
        }
        if (age < DarkBallCaptureVfx.BALL_ABSORB_END) {
            return "terminal handoff / siphon drain";
        }
        return "final absorption fade";
    }

    static String captureFilename(
            String sessionToken,
            int pokemonId,
            int ballId,
            int runOrdinal,
            String renderPath,
            String presentationAuthority,
            int shotNumber,
            String captureSlug,
            CapturePoint point,
            float actualAge) {
        String filename = String.format(
                Locale.ROOT,
                "shot-%03d-path-%s-authority-%s-capture-%s-"
                        + "phase-%02d-%s-"
                        + "sample-%02d-of-%02d-target-%05dms-"
                        + "actual-%05dms.png",
                shotNumber,
                safeToken(renderPath),
                safeToken(presentationAuthority),
                safeToken(captureSlug),
                point.phase().index() + 1,
                safeToken(point.phase().slug()),
                point.sampleNumber(),
                point.sampleCount(),
                Math.max(0, Math.round(point.targetAge() * 1000.0f)),
                Math.max(0, Math.round(actualAge * 1000.0f)));
        return captureDirectoryName(
                sessionToken,
                pokemonId,
                ballId,
                runOrdinal)
                + "/" + filename;
    }

    static String transitionCaptureFilename(
            String sessionToken,
            int pokemonId,
            int ballId,
            int runOrdinal,
            String renderPath,
            String presentationAuthority,
            int shotNumber,
            DirectRendererActivationCapture transition,
            float actualAge) {
        String filename = String.format(
                Locale.ROOT,
                "shot-%03d-path-%s-authority-%s-capture-%s-"
                        + "target-%05dms-actual-%05dms.png",
                shotNumber,
                safeToken(renderPath),
                safeToken(presentationAuthority),
                safeToken(transition.kind().slug()),
                Math.max(0, Math.round(
                        transition.targetAge() * 1000.0f)),
                Math.max(0, Math.round(actualAge * 1000.0f)));
        return captureDirectoryName(
                sessionToken,
                pokemonId,
                ballId,
                runOrdinal)
                + "/" + filename;
    }

    static String manifestFilename(
            String sessionToken,
            int pokemonId,
            int ballId,
            int runOrdinal) {
        return captureDirectoryName(
                sessionToken,
                pokemonId,
                ballId,
                runOrdinal)
                + "/manifest.jsonl";
    }

    static String captureDirectoryName(
            String sessionToken,
            int pokemonId,
            int ballId,
            int runOrdinal) {
        return String.format(
                Locale.ROOT,
                "dark-ball-fbo-session-%s-pokemon-%d-ball-%d-run-%03d",
                safeToken(sessionToken),
                pokemonId,
                ballId,
                runOrdinal);
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String safe = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "unknown" : safe;
    }

    private static Phase phase(int index, String slug, String label,
                               float startAge, float endAge) {
        return new Phase(index, slug, label, startAge, endAge);
    }

    record Phase(
            int index,
            String slug,
            String label,
            float startAge,
            float endAge) {
    }

    record CapturePoint(
            int globalIndex,
            Phase phase,
            int sampleIndex,
            int sampleCount,
            float targetAge) {

        int shotNumber() {
            return globalIndex + 1;
        }

        int sampleNumber() {
            return sampleIndex + 1;
        }
    }

    enum DirectRendererActivationCaptureKind {
        BEFORE("direct-renderer-activation-before",
                "latest retained frame before direct renderer activation",
                true),
        AFTER("direct-renderer-activation-after",
                "first direct renderer activation frame", false),
        SETTLED("direct-renderer-activation-settled",
                "direct renderer activation plus 450 milliseconds", false);

        private final String slug;
        private final String label;
        private final boolean previousFrame;

        DirectRendererActivationCaptureKind(
                String slug,
                String label,
                boolean previousFrame) {
            this.slug = slug;
            this.label = label;
            this.previousFrame = previousFrame;
        }

        String slug() {
            return slug;
        }

        String label() {
            return label;
        }

        boolean previousFrame() {
            return previousFrame;
        }
    }

    record DirectRendererActivationCapture(
            DirectRendererActivationCaptureKind kind,
            float targetAge,
            float activationAge,
            float observedPreviousAge) {

        DirectRendererActivationCapture withTargetAge(float capturedAge) {
            return new DirectRendererActivationCapture(
                    kind,
                    capturedAge,
                    activationAge,
                    observedPreviousAge);
        }
    }

    /**
     * Tracks the presentation handoff independently from the renderer
     * selection path. The first non-exact extracted-surface frame yields a pair
     * of requests:
     * one for the retained previous atlas and one for the current atlas. A
     * third request is produced 450 ms after that first active direct-renderer
     * frame. Mesh and topology-independent splat handoffs share this contract.
     */
    static final class DirectRendererActivationTracker {
        private long captureKey = Long.MIN_VALUE;
        private String previousAuthority;
        private float previousAge;
        private float activationAge = Float.NaN;
        private float activationObservedPreviousAge = Float.NaN;
        private boolean settledEmitted;

        List<DirectRendererActivationCapture> observe(
                long observedCaptureKey,
                float age,
                String presentationAuthority) {
            if (!Float.isFinite(age) || age < 0.0f) {
                return List.of();
            }
            String authority = normalizedAuthority(
                    presentationAuthority);
            if (captureKey != observedCaptureKey
                    || age + 0.0001f < previousAge) {
                reset(observedCaptureKey);
            }

            List<DirectRendererActivationCapture> captures =
                    new ArrayList<>(2);
            if (!Float.isFinite(activationAge)
                    && "exact-mask".equals(previousAuthority)
                    && isDirectRendererAuthority(authority)) {
                activationAge = age;
                activationObservedPreviousAge = previousAge;
                captures.add(new DirectRendererActivationCapture(
                        DirectRendererActivationCaptureKind.BEFORE,
                        previousAge,
                        activationAge,
                        activationObservedPreviousAge));
                captures.add(new DirectRendererActivationCapture(
                        DirectRendererActivationCaptureKind.AFTER,
                        age,
                        activationAge,
                        activationObservedPreviousAge));
            }
            if (Float.isFinite(activationAge)
                    && !settledEmitted
                    && age + 0.0001f >= activationAge
                    + DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS) {
                settledEmitted = true;
                captures.add(new DirectRendererActivationCapture(
                        DirectRendererActivationCaptureKind.SETTLED,
                        activationAge
                                + DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS,
                        activationAge,
                        activationObservedPreviousAge));
            }

            previousAuthority = authority;
            previousAge = Math.max(previousAge, age);
            return List.copyOf(captures);
        }

        private void reset(long observedCaptureKey) {
            captureKey = observedCaptureKey;
            previousAuthority = null;
            previousAge = 0.0f;
            activationAge = Float.NaN;
            activationObservedPreviousAge = Float.NaN;
            settledEmitted = false;
        }

        private static boolean isDirectRendererAuthority(String authority) {
            return "splat-activation".equals(authority)
                    || "splat".equals(authority);
        }

        private static String normalizedAuthority(String authority) {
            if (authority == null || authority.isBlank()) {
                return "unknown";
            }
            return authority.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Computes an aspect-preserving UV crop around the projected effect. The
     * crop keeps a small amount of scene context and a minimum pixel footprint
     * so tiny subjects become legible without stretching.
     */
    static UvCrop magnifiedCrop(
            float minimumU,
            float minimumV,
            float maximumU,
            float maximumV,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight,
            int paddingPixels) {
        if (!Float.isFinite(minimumU)
                || !Float.isFinite(minimumV)
                || !Float.isFinite(maximumU)
                || !Float.isFinite(maximumV)
                || maximumU <= minimumU
                || maximumV <= minimumV
                || sourceWidth <= 0
                || sourceHeight <= 0
                || targetWidth <= 0
                || targetHeight <= 0) {
            return UvCrop.fullScreen();
        }

        float minX = clamp01(minimumU) * sourceWidth;
        float maxX = clamp01(maximumU) * sourceWidth;
        float minY = clamp01(minimumV) * sourceHeight;
        float maxY = clamp01(maximumV) * sourceHeight;
        if (maxX <= minX || maxY <= minY) {
            return UvCrop.fullScreen();
        }

        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float width = Math.max(
                maxX - minX + Math.max(paddingPixels, 0) * 2.0f,
                Math.min(sourceWidth, 96.0f));
        float height = Math.max(
                maxY - minY + Math.max(paddingPixels, 0) * 2.0f,
                Math.min(sourceHeight, 72.0f));
        float targetAspect = targetWidth / (float) targetHeight;
        if (width / height < targetAspect) {
            width = height * targetAspect;
        } else {
            height = width / targetAspect;
        }
        if (width > sourceWidth) {
            width = sourceWidth;
            height = Math.min(sourceHeight, width / targetAspect);
        }
        if (height > sourceHeight) {
            height = sourceHeight;
            width = Math.min(sourceWidth, height * targetAspect);
        }

        float left = clamp(
                centerX - width * 0.5f,
                0.0f,
                sourceWidth - width);
        float bottom = clamp(
                centerY - height * 0.5f,
                0.0f,
                sourceHeight - height);
        return new UvCrop(
                left / sourceWidth,
                bottom / sourceHeight,
                (left + width) / sourceWidth,
                (bottom + height) / sourceHeight);
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record UvCrop(
            float minU,
            float minV,
            float maxU,
            float maxV) {

        private static UvCrop fullScreen() {
            return new UvCrop(0.0f, 0.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Per-capture threshold tracker. Multiple thresholds crossed by one large
     * tick are queued in order, while callers drain no more than one per
     * successfully rendered frame.
     */
    static final class Tracker {
        private final ArrayDeque<CapturePoint> pending =
                new ArrayDeque<>();
        private final List<CapturePoint> capturePoints;
        private long captureKey = Long.MIN_VALUE;
        private float previousAge;
        private int nextPointIndex;
        private int submittedCount;
        private int skippedCount;
        private int runOrdinal;

        Tracker() {
            this(DEFAULT_DEEP_TRACE_SAMPLES_PER_PHASE);
        }

        Tracker(int requestedSamplesPerPhase) {
            capturePoints = DarkBallFboPreviewSchedule.capturePoints(
                    requestedSamplesPerPhase);
        }

        List<CapturePoint> observe(
                long observedCaptureKey,
                float age) {
            if (!Float.isFinite(age) || age < 0.0f) {
                return List.of();
            }
            if (captureKey != observedCaptureKey
                    || age + 0.0001f < previousAge) {
                reset(observedCaptureKey);
            }
            List<CapturePoint> newlySkipped = new ArrayList<>();
            boolean evictedStalePending = false;

            while (!pending.isEmpty()
                    && age - pending.peekFirst().targetAge()
                    > MAX_CAPTURE_LATENESS_SECONDS) {
                newlySkipped.add(pending.removeFirst());
                skippedCount++;
                evictedStalePending = true;
            }
            if (evictedStalePending) {
                // Do not relabel the same rendered frame as the next scheduled
                // shot after its predecessor has just expired. Leave threshold
                // crossing and previousAge untouched so the next fresh frame
                // can either capture that target or skip it by its own age.
                return List.copyOf(newlySkipped);
            }

            while (nextPointIndex < capturePoints.size()) {
                CapturePoint point = capturePoints.get(nextPointIndex);
                if (age < point.targetAge()) {
                    break;
                }
                nextPointIndex++;
                if (previousAge < point.targetAge()
                        && age - point.targetAge()
                        <= MAX_CAPTURE_LATENESS_SECONDS) {
                    pending.addLast(point);
                } else {
                    newlySkipped.add(point);
                    skippedCount++;
                }
            }
            previousAge = Math.max(previousAge, age);
            return List.copyOf(newlySkipped);
        }

        CapturePoint pendingCapture() {
            return pending.peekFirst();
        }

        void markSubmitted(CapturePoint point) {
            if (point == null || pending.peekFirst() != point) {
                return;
            }
            pending.removeFirst();
            submittedCount++;
        }

        int runOrdinal() {
            return runOrdinal;
        }

        int submittedCount() {
            return submittedCount;
        }

        int pendingCount() {
            return pending.size();
        }

        int skippedCount() {
            return skippedCount;
        }

        int capturePointCount() {
            return capturePoints.size();
        }

        private void reset(long observedCaptureKey) {
            captureKey = observedCaptureKey;
            previousAge = 0.0f;
            nextPointIndex = 0;
            submittedCount = 0;
            skippedCount = 0;
            pending.clear();
            runOrdinal++;
        }
    }
}
