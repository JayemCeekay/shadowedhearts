package com.jayemceekay.shadowedhearts.client.aura;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Full-content scanner panels, measured before collision-aware placement. */
public final class AuraReaderScannerInfoFrameLayout {
    public static final int AURA_TIER = 0;
    public static final int HEART_TIER = 1;
    public static final int TARGET_TIER = 2;
    public static final int TIER_COUNT = 4;
    public static final int BRACKET_WIDTH = 5;
    public static final int BRACKET_HEIGHT = 16;
    public static final int HORIZONTAL_PADDING = 6;
    public static final int LINE_HEIGHT = 10;
    public static final int TEXT_HEIGHT_WITH_SHADOW = 9;
    private static final int VERTICAL_PADDING = 3;
    private static final int SCREEN_MARGIN = 4;
    private static final int FALLBACK_GAP = 4;
    private static final int RETICLE_RADIUS = 58;
    private static final int RETICLE_GAP = 6;
    private static final int MINIMUM_WRAP_WIDTH = 48;
    private static final int MAX_CANDIDATES = 20;
    private static final int[] LABEL_TOPS = {-79, -26, 10, 63};

    private AuraReaderScannerInfoFrameLayout() { }

    /**
     * Retains a same-target result's body while its complete refreshed text still
     * fits. The caller supplies a previous layout only for that same target.
     */
    public static Layout resolve(Request request, ToIntFunction<String> measure, Layout previous) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(measure, "measure");
        if (previous != null) {
            FramePlacement aura = retain(previous.auraFrame(), request.auraContent(), request, measure);
            FramePlacement target = retain(previous.targetFrame(), request.targetContent(), request, measure);
            FramePlacement heart = retain(previous.heartFrame(), request.heartContent(), request, measure);
            if ((!hasContent(request.auraContent()) || aura != null)
                    && (!hasContent(request.targetContent()) || target != null)
                    && (!hasContent(request.heartContent()) || heart != null)
                    && !intersects(aura, target) && !intersects(aura, heart) && !intersects(target, heart)) {
                return new Layout(Optional.ofNullable(aura), Optional.ofNullable(target), Optional.ofNullable(heart));
            }
        }
        return resolve(request, measure);
    }

    private static FramePlacement retain(Optional<FramePlacement> previous, Content content, Request request,
                                         ToIntFunction<String> measure) {
        if (!hasContent(content) || previous.isEmpty()) return null;
        Geometry geometry = previous.get().geometry();
        float relativeScale = relativeTextScale(request);
        Text updated = text(content, geometry.width(), relativeScale, measure, request.scannerScale());
        if (updated == null || updated.height() > geometry.height()) return null;
        Text retainedBody = new Text(updated.lines(), updated.glyphWidth(), geometry.height());
        FramePlacement frame = frame(geometry.tier(), geometry.side(), geometry.xOffset(), geometry.yOffset(),
                geometry.width(), retainedBody, relativeScale, request);
        float radius = RETICLE_RADIUS * request.scannerScale();
        AuraReaderHudRect reticle = new AuraReaderHudRect(
                (int) Math.floor(request.scannerCenterX() - radius),
                (int) Math.floor(request.scannerCenterY() - radius),
                ceilToInt(request.scannerCenterX() + radius), ceilToInt(request.scannerCenterY() + radius));
        return isSafe(frame.bounds(), request) && !frame.bounds().intersects(reticle) ? frame : null;
    }

    private static boolean hasContent(Content content) { return content != null && !content.text().isBlank(); }
    private static boolean intersects(FramePlacement first, FramePlacement second) {
        return first != null && second != null && first.bounds().intersects(second.bounds());
    }

    public static Layout resolve(Request request, ToIntFunction<String> measure) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(measure, "measure");
        List<Candidate> aura = candidates(AURA_TIER, request.auraSide(), request.auraContent(), request, measure);
        List<Candidate> target = candidates(TARGET_TIER, request.targetSide(), request.targetContent(), request, measure);
        List<Candidate> heart = candidates(HEART_TIER, request.heartSide(), request.heartContent(), request, measure);
        aura.add(null);
        target.add(null);
        heart.add(null);
        Layout best = new Layout(Optional.empty(), Optional.empty(), Optional.empty());
        int bestPriority = -1;
        long bestScore = Long.MAX_VALUE;
        for (Candidate heartCandidate : heart) {
            for (Candidate auraCandidate : aura) {
                if (overlaps(heartCandidate, auraCandidate)) continue;
                for (Candidate targetCandidate : target) {
                    if (overlaps(heartCandidate, targetCandidate) || overlaps(auraCandidate, targetCandidate)) continue;
                    int priority = (heartCandidate == null ? 0 : 4) + (auraCandidate == null ? 0 : 2)
                            + (targetCandidate == null ? 0 : 1);
                    long score = score(heartCandidate) + score(auraCandidate) + score(targetCandidate);
                    if (priority > bestPriority || priority == bestPriority && score < bestScore) {
                        bestPriority = priority;
                        bestScore = score;
                        best = new Layout(placement(auraCandidate), placement(targetCandidate), placement(heartCandidate));
                    }
                }
            }
        }
        return best;
    }

    private static List<Candidate> candidates(int preferredTier, Side preferredSide, Content content,
                                               Request request, ToIntFunction<String> measure) {
        if (content == null || content.text().isBlank()) return new ArrayList<>();
        Side side = preferredSide == null ? Side.LEFT : preferredSide;
        Map<FramePlacement, Candidate> unique = new LinkedHashMap<>();
        for (int tier = 0; tier < TIER_COUNT; tier++) {
            for (Side candidateSide : new Side[]{side, side.opposite()}) {
                FramePlacement frame = nativeCandidate(tier, candidateSide, content, request, measure);
                if (frame != null) add(unique, frame, (tier == preferredTier ? 0 : 120 + Math.abs(tier - preferredTier) * 8)
                        + (candidateSide == side ? 0 : 10) + wrapPenalty(frame, content));
            }
        }
        int top = nativeCeil(SCREEN_MARGIN, request.scannerCenterY(), request.scannerScale());
        int bottom = nativeFloor(request.viewportHeight() - SCREEN_MARGIN, request.scannerCenterY(), request.scannerScale());
        int aboveBottom = (int) Math.floor(-RETICLE_RADIUS - RETICLE_GAP / request.scannerScale());
        int belowTop = (int) Math.ceil(RETICLE_RADIUS + RETICLE_GAP / request.scannerScale());
        addFallbacks(unique, preferredTier, side, content, request, measure, top, Math.min(bottom, aboveBottom));
        addFallbacks(unique, preferredTier, side, content, request, measure, Math.max(top, belowTop), bottom);
        addSideFallbacks(unique, preferredTier, side, content, request, measure, top, bottom);
        return unique.values().stream().sorted(Comparator.comparingInt(Candidate::score))
                .limit(MAX_CANDIDATES).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static void add(Map<FramePlacement, Candidate> candidates, FramePlacement frame, int score) {
        Candidate current = candidates.get(frame);
        if (current == null || score < current.score()) candidates.put(frame, new Candidate(frame, score));
    }

    private static int wrapPenalty(FramePlacement frame, Content content) {
        int explicitLines = content.text().split("\\R", -1).length;
        return Math.max(0, frame.lines().size() - explicitLines) * 12;
    }

    /** Native inward edges stay fixed; longer panels expand away from the ring. */
    private static FramePlacement nativeCandidate(int tier, Side side, Content content,
                                                  Request request, ToIntFunction<String> measure) {
        int inwardEdge = side == Side.LEFT ? -(tier == 1 || tier == 2 ? 85 : 28)
                : tier == 1 || tier == 2 ? 85 : 28;
        Anchor anchor = tier == 0 ? new Anchor(LABEL_TOPS[tier] + BRACKET_HEIGHT, true)
                : new Anchor(LABEL_TOPS[tier], false);
        return anchoredCandidate(tier, side, inwardEdge, content, request, measure, anchor);
    }

    private static FramePlacement anchoredCandidate(int tier, Side side, int inwardEdge, Content content,
                                                     Request request, ToIntFunction<String> measure, Anchor anchor) {
        float relativeScale = relativeTextScale(request);
        int viewportLeft = nativeCeil(SCREEN_MARGIN, request.scannerCenterX(), request.scannerScale());
        int viewportRight = nativeFloor(request.viewportWidth() - SCREEN_MARGIN, request.scannerCenterX(), request.scannerScale());
        int capacity = side == Side.LEFT ? inwardEdge - viewportLeft : viewportRight - inwardEdge;
        int width = Math.min(naturalWidth(content, measure, relativeScale), capacity);
        for (int attempt = 0; attempt < 10; attempt++) {
            Text text = text(content, width, relativeScale, measure, request.scannerScale());
            if (text == null) return null;
            int x = side == Side.LEFT ? inwardEdge - width : inwardEdge;
            int y = anchor.bottom() ? anchor.coordinate() - text.height() : anchor.coordinate();
            FramePlacement frame = frame(tier, side, x, y, width, text, relativeScale, request);
            if (isSafe(frame.bounds(), request)) return frame;
            if (!insideViewport(frame.bounds(), request)) return null;
            int newCapacity = width;
            for (AuraReaderHudRect forbidden : forbidden(request)) {
                if (!verticalOverlap(frame.bounds(), forbidden) || !frame.bounds().intersects(forbidden)) continue;
                int limit = side == Side.LEFT
                        ? inwardEdge - nativeCeil(forbidden.right(), request.scannerCenterX(), request.scannerScale())
                        : nativeFloor(forbidden.left(), request.scannerCenterX(), request.scannerScale()) - inwardEdge;
                newCapacity = Math.min(newCapacity, limit);
            }
            if (newCapacity >= width) return null;
            width = newCapacity;
        }
        return null;
    }

    /** Shift a side body around HUD cards when the native label rows are blocked. */
    private static void addSideFallbacks(Map<FramePlacement, Candidate> candidates, int tier, Side side,
                                         Content content, Request request, ToIntFunction<String> measure,
                                         int bandTop, int bandBottom) {
        List<Anchor> anchors = new ArrayList<>();
        anchors.add(new Anchor(bandTop, false));
        anchors.add(new Anchor(bandBottom, true));
        for (AuraReaderHudRect obstacle : forbidden(request)) {
            int before = nativeFloor(obstacle.top() - FALLBACK_GAP, request.scannerCenterY(), request.scannerScale());
            int after = nativeCeil(obstacle.bottom() + FALLBACK_GAP, request.scannerCenterY(), request.scannerScale());
            if (before > bandTop && before < bandBottom) anchors.add(new Anchor(before, true));
            if (after > bandTop && after < bandBottom) anchors.add(new Anchor(after, false));
        }
        // A short viewport can leave less than the normal gap above the bottom
        // HUD. The measured body may end at its boundary, with padding intact.
        AuraReaderHudRect bottomSafeZone = request.bottomSafeZone();
        if (bottomSafeZone.width() > 0 && bottomSafeZone.height() > 0) {
            int bottom = nativeFloor(bottomSafeZone.top(), request.scannerCenterY(), request.scannerScale());
            if (bottom > bandTop && bottom < bandBottom) anchors.add(new Anchor(bottom, true));
        }
        for (Anchor anchor : anchors.stream().distinct().toList()) {
            for (Side candidateSide : new Side[]{side, side.opposite()}) {
                FramePlacement frame = anchoredCandidate(tier, candidateSide, candidateSide == Side.LEFT ? -85 : 85,
                        content, request, measure, anchor);
                if (frame == null) continue;
                int distance = Math.min(100, Math.abs(frame.geometry().yOffset() - LABEL_TOPS[tier]));
                add(candidates, frame, 500 + distance + (candidateSide == side ? 0 : 10) + wrapPenalty(frame, content));
            }
        }
    }

    /** Wide shelves above/below the ring provide alternatives to dropping a readable panel. */
    private static void addFallbacks(Map<FramePlacement, Candidate> candidates, int tier, Side side, Content content,
                                     Request request, ToIntFunction<String> measure, int bandTop, int bandBottom) {
        if (bandBottom <= bandTop) return;
        List<Anchor> anchors = new ArrayList<>();
        anchors.add(new Anchor(bandTop, false));
        anchors.add(new Anchor(bandBottom, true));
        for (AuraReaderHudRect forbidden : forbidden(request)) {
            int before = nativeFloor(forbidden.top() - FALLBACK_GAP, request.scannerCenterY(), request.scannerScale());
            int after = nativeCeil(forbidden.bottom() + FALLBACK_GAP, request.scannerCenterY(), request.scannerScale());
            if (before > bandTop && before < bandBottom) anchors.add(new Anchor(before, true));
            if (after > bandTop && after < bandBottom) anchors.add(new Anchor(after, false));
        }
        for (Anchor anchor : anchors.stream().distinct().toList()) {
            FramePlacement frame = fallbackCandidate(tier, side, content, request, measure, anchor, bandTop, bandBottom);
            if (frame != null) {
                int distance = Math.min(Math.abs(frame.geometry().yOffset() - LABEL_TOPS[0]),
                        Math.abs(frame.geometry().yOffset() - LABEL_TOPS[3]));
                add(candidates, frame, 300 + Math.min(100, distance) + wrapPenalty(frame, content));
            }
        }
    }

    private static FramePlacement fallbackCandidate(int tier, Side side, Content content, Request request,
                                                     ToIntFunction<String> measure, Anchor anchor,
                                                     int bandTop, int bandBottom) {
        float relativeScale = relativeTextScale(request);
        int nativeLeft = nativeCeil(SCREEN_MARGIN, request.scannerCenterX(), request.scannerScale());
        int nativeRight = nativeFloor(request.viewportWidth() - SCREEN_MARGIN, request.scannerCenterX(), request.scannerScale());
        int width = Math.min(naturalWidth(content, measure, relativeScale), nativeRight - nativeLeft);
        for (int attempt = 0; attempt < 10; attempt++) {
            Text text = text(content, width, relativeScale, measure, request.scannerScale());
            if (text == null) return null;
            int y = anchor.bottom() ? anchor.coordinate() - text.height() : anchor.coordinate();
            if (y < bandTop || y + text.height() > bandBottom) return null;
            List<Interval> intervals = new ArrayList<>();
            intervals.add(new Interval(nativeLeft, nativeRight));
            float screenTop = request.scannerCenterY() + y * request.scannerScale();
            float screenBottom = screenTop + text.height() * request.scannerScale();
            for (AuraReaderHudRect forbidden : forbidden(request)) {
                if (screenTop >= forbidden.bottom() || screenBottom <= forbidden.top()) continue;
                int cutLeft = nativeFloor(forbidden.left(), request.scannerCenterX(), request.scannerScale());
                int cutRight = nativeCeil(forbidden.right(), request.scannerCenterX(), request.scannerScale());
                List<Interval> remaining = new ArrayList<>();
                for (Interval interval : intervals) {
                    if (cutRight <= interval.left() || cutLeft >= interval.right()) remaining.add(interval);
                    else {
                        if (cutLeft > interval.left()) remaining.add(new Interval(interval.left(), cutLeft));
                        if (cutRight < interval.right()) remaining.add(new Interval(cutRight, interval.right()));
                    }
                }
                intervals = remaining;
            }
            Interval best = intervals.stream().max(Comparator.comparingInt(Interval::width)
                    .thenComparingInt(interval -> -Math.abs(interval.left() + interval.right()))).orElse(null);
            if (best == null) return null;
            if (width > best.width()) {
                width = best.width();
                continue;
            }
            int x = Math.max(best.left(), Math.min(best.right() - width, -width / 2));
            FramePlacement frame = frame(tier, side, x, y, width, text, relativeScale, request);
            return isSafe(frame.bounds(), request) ? frame : null;
        }
        return null;
    }

    private static int naturalWidth(Content content, ToIntFunction<String> measure, float relativeScale) {
        int natural = Math.max(content.naturalWidth(), widestParagraph(content.text(), measure));
        return Math.max(BRACKET_WIDTH * 2 + 4,
                ceilToInt((natural + 1.0) * relativeScale + HORIZONTAL_PADDING * 2));
    }

    private static Text text(Content content, int width, float relativeScale,
                             ToIntFunction<String> measure, float scannerScale) {
        if (width < BRACKET_WIDTH * 2 + 4) return null;
        int glyphWidth = (int) Math.floor((width - HORIZONTAL_PADDING * 2) / relativeScale) - 1;
        int actualWidth = widestParagraph(content.text(), measure);
        if (glyphWidth < Math.min(actualWidth, MINIMUM_WRAP_WIDTH)) return null;
        List<String> lines = wrap(content.text(), glyphWidth, measure);
        if (lines == null) return null;
        int verticalPadding = Math.max(VERTICAL_PADDING, ceilToInt(1.0 / scannerScale));
        int height = Math.max(BRACKET_HEIGHT,
                ceilToInt((lines.size() * (double) LINE_HEIGHT - 1) * relativeScale + verticalPadding * 2));
        return new Text(lines, glyphWidth, height);
    }

    /** Whitespace breaks are preferred; long tokens split only at Unicode code-point boundaries. */
    private static List<String> wrap(String text, int maxWidth, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int end = start;
                int lastSpace = -1;
                while (end < paragraph.length()) {
                    int next = end + Character.charCount(paragraph.codePointAt(end));
                    if (width(measure, paragraph.substring(start, next)) > maxWidth) break;
                    if (Character.isWhitespace(paragraph.codePointAt(end))) lastSpace = end;
                    end = next;
                }
                if (end == start) return null;
                if (end == paragraph.length()) {
                    lines.add(paragraph.substring(start));
                    break;
                }
                if (lastSpace > start) {
                    lines.add(paragraph.substring(start, lastSpace));
                    start = lastSpace;
                    while (start < paragraph.length() && Character.isWhitespace(paragraph.codePointAt(start))) {
                        start += Character.charCount(paragraph.codePointAt(start));
                    }
                } else {
                    lines.add(paragraph.substring(start, end));
                    start = end;
                }
            }
        }
        return List.copyOf(lines);
    }

    private static int widestParagraph(String text, ToIntFunction<String> measure) {
        int width = 0;
        for (String paragraph : text.split("\\R", -1)) width = Math.max(width, width(measure, paragraph));
        return width;
    }
    private static int width(ToIntFunction<String> measure, String value) {
        return Math.max(0, measure.applyAsInt(value));
    }

    private static FramePlacement frame(int tier, Side side, int x, int y, int width, Text text,
                                        float relativeScale, Request request) {
        float left = request.scannerCenterX() + x * request.scannerScale();
        float top = request.scannerCenterY() + y * request.scannerScale();
        AuraReaderHudRect bounds = new AuraReaderHudRect((int) Math.floor(left), (int) Math.floor(top),
                ceilToInt(left + width * request.scannerScale()), ceilToInt(top + text.height() * request.scannerScale()));
        return new FramePlacement(new Geometry(tier, side, x, y, width, text.height()), relativeScale,
                text.lines(), text.glyphWidth(), bounds);
    }
    private static float relativeTextScale(Request request) {
        return Math.max(request.scannerScale(), request.minimumTextScale()) / request.scannerScale();
    }
    private static boolean insideViewport(AuraReaderHudRect bounds, Request request) {
        return bounds.left() >= SCREEN_MARGIN && bounds.top() >= SCREEN_MARGIN
                && bounds.right() <= request.viewportWidth() - SCREEN_MARGIN
                && bounds.bottom() <= request.viewportHeight() - SCREEN_MARGIN;
    }
    private static boolean isSafe(AuraReaderHudRect bounds, Request request) {
        return insideViewport(bounds, request) && forbidden(request).stream().noneMatch(bounds::intersects);
    }
    private static boolean verticalOverlap(AuraReaderHudRect first, AuraReaderHudRect second) {
        return first.top() < second.bottom() && first.bottom() > second.top();
    }
    private static List<AuraReaderHudRect> forbidden(Request request) {
        List<AuraReaderHudRect> forbidden = new ArrayList<>(request.reservedZones());
        forbidden.add(request.partySafeZone());
        forbidden.add(request.bottomSafeZone());
        return forbidden;
    }
    private static boolean overlaps(Candidate first, Candidate second) {
        return first != null && second != null && first.frame().bounds().intersects(second.frame().bounds());
    }
    private static long score(Candidate candidate) { return candidate == null ? 0 : candidate.score(); }
    private static Optional<FramePlacement> placement(Candidate candidate) {
        return candidate == null ? Optional.empty() : Optional.of(candidate.frame());
    }
    private static int nativeCeil(float screen, float center, float scale) {
        return ceilToInt((screen - center) / scale);
    }
    private static int nativeFloor(float screen, float center, float scale) {
        return (int) Math.floor((screen - center) / scale);
    }
    private static int ceilToInt(double value) { return (int) Math.ceil(value); }

    public enum Side {
        LEFT, RIGHT;
        public Side opposite() { return this == LEFT ? RIGHT : LEFT; }
    }
    /** naturalWidth may reserve a larger styled-font width to stabilize a live result. */
    public record Content(String text, int naturalWidth) {
        public Content {
            text = text == null ? "" : text;
            naturalWidth = Math.max(0, naturalWidth);
        }
    }
    public record Request(int viewportWidth, int viewportHeight, float scannerCenterX, float scannerCenterY,
                          float scannerScale, AuraReaderHudRect partySafeZone, AuraReaderHudRect bottomSafeZone,
                          List<AuraReaderHudRect> reservedZones, Side auraSide, Side targetSide, Side heartSide,
                          float minimumTextScale, Content auraContent, Content targetContent, Content heartContent) {
        public Request {
            if (viewportWidth <= 0 || viewportHeight <= 0) throw new IllegalArgumentException("viewport dimensions must be positive");
            if (!Float.isFinite(scannerCenterX) || !Float.isFinite(scannerCenterY)) throw new IllegalArgumentException("scanner center must be finite");
            if (!Float.isFinite(scannerScale) || scannerScale <= 0) throw new IllegalArgumentException("scanner scale must be finite and positive");
            if (!Float.isFinite(minimumTextScale) || minimumTextScale <= 0) throw new IllegalArgumentException("minimum text scale must be finite and positive");
            Objects.requireNonNull(partySafeZone, "partySafeZone");
            Objects.requireNonNull(bottomSafeZone, "bottomSafeZone");
            reservedZones = List.copyOf(Objects.requireNonNull(reservedZones, "reservedZones"));
            Objects.requireNonNull(auraSide, "auraSide");
            Objects.requireNonNull(targetSide, "targetSide");
        }
    }
    public record Geometry(int tier, Side side, int xOffset, int yOffset, int width, int height) {
        public Geometry {
            if (tier < 0 || tier >= TIER_COUNT) throw new IllegalArgumentException("invalid panel tier");
            Objects.requireNonNull(side, "side");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("panel dimensions must be positive");
        }
    }
    /** Bounds use screen coordinates; geometry and relativeTextScale use the scanner pose. */
    public record FramePlacement(Geometry geometry, float relativeTextScale, List<String> lines,
                                 int textWidth, AuraReaderHudRect bounds) {
        public FramePlacement {
            Objects.requireNonNull(geometry, "geometry");
            lines = List.copyOf(lines);
            Objects.requireNonNull(bounds, "bounds");
        }
        public int tier() { return geometry.tier(); }
        public Side side() { return geometry.side(); }
    }
    public record Layout(Optional<FramePlacement> auraFrame, Optional<FramePlacement> targetFrame,
                         Optional<FramePlacement> heartFrame) {
        public Layout {
            Objects.requireNonNull(auraFrame, "auraFrame");
            Objects.requireNonNull(targetFrame, "targetFrame");
            Objects.requireNonNull(heartFrame, "heartFrame");
        }
        public List<FramePlacement> visibleFrames() {
            return List.of(auraFrame, targetFrame, heartFrame).stream().flatMap(Optional::stream).toList();
        }
    }
    private record Candidate(FramePlacement frame, int score) { }
    private record Text(List<String> lines, int glyphWidth, int height) { }
    private record Anchor(int coordinate, boolean bottom) { }
    private record Interval(int left, int right) { private int width() { return right - left; } }
}
