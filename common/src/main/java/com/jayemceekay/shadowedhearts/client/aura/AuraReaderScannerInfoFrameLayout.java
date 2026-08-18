package com.jayemceekay.shadowedhearts.client.aura;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure geometry for Cobblemon's Pokédex scanner information frames.
 *
 * <p>Native offsets, dimensions, and text positions mirror
 * {@code PokedexScannerRenderer.renderInfoFrames}. The returned screen bounds
 * are conservative (floor at the top/left and ceil at the bottom/right), so a
 * frame accepted by this resolver will not clip or enter a reserved zone when
 * rendered at the request's uniform scanner scale.</p>
 */
public final class AuraReaderScannerInfoFrameLayout {
    public static final int AURA_TIER = 0;
    public static final int TARGET_TIER = 2;
    public static final int TIER_COUNT = 4;

    public static final int OUTER_FRAME_WIDTH = 92;
    public static final int OUTER_FRAME_HEIGHT = 55;
    public static final int INNER_FRAME_WIDTH = 120;
    public static final int INNER_FRAME_HEIGHT = 20;
    public static final int INNER_FRAME_STEM_WIDTH = 28;

    private static final int[] Y_OFFSETS = {-80, -26, 6, 25};
    private static final int[] TEXT_Y_OFFSETS = {5, 4, 8, 42};

    private AuraReaderScannerInfoFrameLayout() {
    }

    /**
     * Resolves the Aura Reader's two contextual frames using Cobblemon's
     * native tier choices: Aura is tier 0 on the right, while Target is tier 2
     * on the left. Each frame flips sides only when its preferred side is not
     * safe; a frame is omitted when neither native side is safe.
     */
    public static Layout resolve(Request request) {
        Objects.requireNonNull(request, "request");
        return new Layout(
                choose(AURA_TIER, Side.RIGHT, request),
                choose(TARGET_TIER, Side.LEFT, request));
    }

    /**
     * Chooses the preferred native side for one tier, falling back to the
     * opposite native side when required by the viewport or reserved zones.
     */
    public static Optional<FramePlacement> choose(
            int tier,
            Side preferredSide,
            Request request) {
        Objects.requireNonNull(preferredSide, "preferredSide");
        Objects.requireNonNull(request, "request");

        FramePlacement preferred = place(
                geometry(tier, preferredSide), request);
        if (isSafe(preferred, request)) {
            return Optional.of(preferred);
        }

        FramePlacement fallback = place(
                geometry(tier, preferredSide.opposite()), request);
        if (isSafe(fallback, request)) {
            return Optional.of(fallback);
        }
        return Optional.empty();
    }

    /**
     * Returns unscaled geometry relative to the scanner center.
     */
    public static NativeGeometry geometry(int tier, Side side) {
        Objects.requireNonNull(side, "side");
        checkTier(tier);

        boolean inner = tier == 1 || tier == 2;
        int width = inner ? INNER_FRAME_WIDTH : OUTER_FRAME_WIDTH;
        int height = inner ? INNER_FRAME_HEIGHT : OUTER_FRAME_HEIGHT;
        int left = (inner ? -177 : -120)
                + (side == Side.LEFT ? 0 : (inner ? 234 : 148));
        int textCenterWithinFrame = inner
                ? (INNER_FRAME_WIDTH - INNER_FRAME_STEM_WIDTH) / 2
                        + (side == Side.LEFT
                                ? 0
                                : INNER_FRAME_STEM_WIDTH)
                : OUTER_FRAME_WIDTH / 2;

        return new NativeGeometry(
                tier,
                side,
                left,
                Y_OFFSETS[tier],
                width,
                height,
                left + textCenterWithinFrame,
                Y_OFFSETS[tier] + TEXT_Y_OFFSETS[tier]);
    }

    private static FramePlacement place(
            NativeGeometry geometry,
            Request request) {
        float scale = request.scannerScale();
        float screenLeft = request.scannerCenterX()
                + geometry.xOffset() * scale;
        float screenTop = request.scannerCenterY()
                + geometry.yOffset() * scale;
        float screenWidth = geometry.width() * scale;
        float screenHeight = geometry.height() * scale;
        AuraReaderHudRect bounds = new AuraReaderHudRect(
                floorToInt(screenLeft),
                floorToInt(screenTop),
                ceilToInt(screenLeft + screenWidth),
                ceilToInt(screenTop + screenHeight));

        return new FramePlacement(
                geometry,
                scale,
                screenLeft,
                screenTop,
                screenWidth,
                screenHeight,
                request.scannerCenterX()
                        + geometry.textCenterXOffset() * scale,
                request.scannerCenterY()
                        + geometry.textYOffset() * scale,
                bounds);
    }

    private static boolean isSafe(
            FramePlacement placement,
            Request request) {
        AuraReaderHudRect bounds = placement.bounds();
        if (bounds.left() < 0
                || bounds.top() < 0
                || bounds.right() > request.viewportWidth()
                || bounds.bottom() > request.viewportHeight()) {
            return false;
        }
        return !bounds.intersects(request.partySafeZone())
                && !bounds.intersects(request.bottomSafeZone());
    }

    private static void checkTier(int tier) {
        if (tier < 0 || tier >= TIER_COUNT) {
            throw new IllegalArgumentException(
                    "tier must be between 0 and " + (TIER_COUNT - 1));
        }
    }

    private static int floorToInt(float value) {
        return (int) Math.floor(value);
    }

    private static int ceilToInt(float value) {
        return (int) Math.ceil(value);
    }

    public enum Side {
        LEFT,
        RIGHT;

        public Side opposite() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    /**
     * Native values relative to the scanner center. Text offsets already
     * account for the 28-pixel stem on inner tiers.
     */
    public record NativeGeometry(
            int tier,
            Side side,
            int xOffset,
            int yOffset,
            int width,
            int height,
            int textCenterXOffset,
            int textYOffset) {
        public NativeGeometry {
            checkTier(tier);
            Objects.requireNonNull(side, "side");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "frame dimensions must be positive");
            }
        }
    }

    public record Request(
            int viewportWidth,
            int viewportHeight,
            float scannerCenterX,
            float scannerCenterY,
            float scannerScale,
            AuraReaderHudRect partySafeZone,
            AuraReaderHudRect bottomSafeZone) {
        public Request {
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                throw new IllegalArgumentException(
                        "viewport dimensions must be positive");
            }
            if (!Float.isFinite(scannerCenterX)
                    || !Float.isFinite(scannerCenterY)) {
                throw new IllegalArgumentException(
                        "scanner center must be finite");
            }
            if (!Float.isFinite(scannerScale) || scannerScale <= 0.0f) {
                throw new IllegalArgumentException(
                        "scanner scale must be finite and positive");
            }
            Objects.requireNonNull(partySafeZone, "partySafeZone");
            Objects.requireNonNull(bottomSafeZone, "bottomSafeZone");
        }
    }

    /**
     * A native frame choice plus its uniformly scaled screen-space geometry.
     */
    public record FramePlacement(
            NativeGeometry geometry,
            float scannerScale,
            float screenLeft,
            float screenTop,
            float screenWidth,
            float screenHeight,
            float screenTextCenterX,
            float screenTextY,
            AuraReaderHudRect bounds) {
        public FramePlacement {
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(bounds, "bounds");
        }

        public int tier() {
            return geometry.tier();
        }

        public Side side() {
            return geometry.side();
        }
    }

    public record Layout(
            Optional<FramePlacement> auraFrame,
            Optional<FramePlacement> targetFrame) {
        public Layout {
            auraFrame = Objects.requireNonNull(auraFrame, "auraFrame");
            targetFrame = Objects.requireNonNull(targetFrame, "targetFrame");
        }

        public List<FramePlacement> visibleFrames() {
            return List.of(auraFrame, targetFrame).stream()
                    .flatMap(Optional::stream)
                    .toList();
        }
    }
}
