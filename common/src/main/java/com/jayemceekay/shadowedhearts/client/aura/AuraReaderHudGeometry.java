package com.jayemceekay.shadowedhearts.client.aura;

import java.util.Objects;

/**
 * Screen measurements for the single Aura Reader HUD presentation.
 *
 * <p>The resolver has no rendering, Minecraft, configuration, or client-state
 * dependencies. Callers provide already-scaled screen measurements and may
 * cache the returned immutable geometry for as long as those inputs remain
 * unchanged.</p>
 */
public final class AuraReaderHudGeometry {
    public static final int MODE_CAP_WIDTH = 76;
    public static final int MODE_CAP_HEIGHT = 22;
    public static final int SIGNAL_POPOVER_HEIGHT = 34;
    public static final int SIGNAL_STATUS_HEIGHT = 20;
    public static final float MIN_READOUT_SCALE = 0.75f;
    public static final int SCANNER_RETICLE_DIAMETER = 116;

    private static final float DEFAULT_COMPASS_WIDTH_FRACTION = 0.42f;
    private static final float MIN_INPUT_SCALE = 0.25f;
    private static final float MAX_INPUT_SCALE = 3.0f;
    private static final int REFERENCE_WIDTH = 854;
    private static final int REFERENCE_HEIGHT = 480;
    private static final float MIN_ADAPTIVE_SCALE = 0.55f;

    private AuraReaderHudGeometry() {
    }

    public static Geometry resolve(Request request) {
        Objects.requireNonNull(request, "request");

        int screenWidth = request.screenWidth();
        int screenHeight = request.screenHeight();
        int bottomSafeTop = Math.max(
                0, screenHeight - Math.min(
                        request.bottomSafeMargin(), screenHeight));
        int partySafeRight = request.partyOverlayWidth() > 0
                ? Math.min(
                screenWidth,
                request.partyOverlayWidth()
                        + request.partyOverlayMargin())
                : 0;

        AuraReaderHudRect viewport = new AuraReaderHudRect(
                0, 0, screenWidth, screenHeight);
        AuraReaderHudRect bottomSafeZone = new AuraReaderHudRect(
                0, bottomSafeTop, screenWidth, screenHeight);
        AuraReaderHudRect partySafeZone = new AuraReaderHudRect(
                0, 0, partySafeRight, screenHeight);
        AuraReaderHudRect upperViewport = new AuraReaderHudRect(
                0, 0, screenWidth, bottomSafeTop);

        float componentScale = request.hudScale() * adaptiveScale(screenWidth, screenHeight);
        int effectiveRightInset = Math.min(
                request.rightOverlayMargin(),
                Math.max(0, (screenWidth - 1) / 2));
        int maxCenteredWidth = Math.max(
                0, screenWidth - effectiveRightInset * 2);
        int requestedRailWidth = Math.round(
                screenWidth * request.compassWidthFraction());
        int compassRailWidth = matchScreenParity(
                Math.min(requestedRailWidth, maxCenteredWidth),
                screenWidth);
        int compassRailHeight = scaledDimension(12, componentScale);
        // Two marker lanes keep a selection distinct from a pin at the same
        // bearing, including their arcs and the rail's upward curve.
        int compassReservedHeight = Math.max(36, scaledDimension(56, componentScale));
        // Edge-pin lock brackets extend four screen pixels beyond the rail
        // even when the HUD is small, so retain that clearance at every scale.
        int horizontalCapGap = Math.max(4, scaledDimension(4, componentScale));

        int compassTop = Math.max(0, request.topInset());
        int availableCapHeight = Math.max(
                0, bottomSafeTop - Math.min(compassTop, bottomSafeTop));
        compassReservedHeight = Math.min(compassReservedHeight, availableCapHeight);
        compassTop = clampValue(
                compassTop,
                0,
                Math.max(0, bottomSafeTop - compassReservedHeight));
        compassRailHeight = Math.min(
                compassRailHeight, compassReservedHeight);
        int compassRailTop = compassTop
                + Math.max(0, (compassReservedHeight - compassRailHeight) / 2);
        AuraReaderHudRect compassRail = centeredRect(
                screenWidth,
                compassRailTop,
                compassRailWidth,
                compassRailHeight).clamp(upperViewport);

        int modeCapHeight = Math.min(
                readableDimension(MODE_CAP_HEIGHT, componentScale), compassReservedHeight);
        int modeCapTop = compassTop
                + Math.max(0, (compassReservedHeight - modeCapHeight) / 2);
        int modeCapRight = Math.max(
                0, compassRail.left() - horizontalCapGap);
        int modeCapWidth = Math.min(
                scaledDimension(MODE_CAP_WIDTH, componentScale),
                Math.max(0, modeCapRight - partySafeRight));
        AuraReaderHudRect modeCap = rectFromSize(
                modeCapRight - modeCapWidth,
                modeCapTop,
                modeCapWidth,
                modeCapHeight).clamp(upperViewport);

        int verticalGap = scaledDimension(6, componentScale);
        int popoverDesignHeight = request.compactSignal() ? SIGNAL_STATUS_HEIGHT : SIGNAL_POPOVER_HEIGHT;
        float popoverWidthScale = Math.max(MIN_READOUT_SCALE, componentScale);
        int unreservedCompassWidth = matchScreenParity(requestedRailWidth, screenWidth);
        int desiredPopoverWidth = Math.max(
                scaledDimension(176, popoverWidthScale),
                Math.round(unreservedCompassWidth * 0.50f));
        int popoverWidth = matchScreenParity(
                Math.min(
                        Math.min(
                                desiredPopoverWidth,
                                scaledDimension(320, popoverWidthScale)),
                        screenWidth),
                screenWidth);
        int popoverHeight = readableDimension(popoverDesignHeight, componentScale);

        // Pokédex rings use native GUI pixels at the integer GUI midpoint.
        // HUD readouts and safe areas must reflow around this scanner instead
        // of changing its scale; only the explicit reticle setting changes it.
        int reticleCenterX = screenWidth / 2;
        int reticleCenterY = screenHeight / 2;
        int reticleDiameter = Math.round(
                SCANNER_RETICLE_DIAMETER * request.reticleScale());
        int reticleLeft = reticleCenterX - reticleDiameter / 2;
        int reticleTop = reticleCenterY - reticleDiameter / 2;
        AuraReaderHudRect reticle = rectFromSize(
                reticleLeft,
                reticleTop,
                reticleDiameter,
                reticleDiameter);

        // Anchor the card to screen geometry, never to moving compass icons.
        // A fixed nominal icon row keeps the gap useful without making the
        // card follow yaw, marker stacking, focus, or lock decorations.
        int horizontalInset = Math.min(4, (screenWidth - 1) / 2);
        int verticalInset = Math.min(4, (screenHeight - 1) / 2);
        int visiblePopoverWidth = Math.min(popoverWidth, screenWidth - horizontalInset * 2);
        int visiblePopoverHeight = Math.min(popoverHeight, screenHeight - verticalInset * 2);
        int popoverTop;
        if (compassRail.height() > 0) {
            int compassIconSize = Math.max(11, Math.round(compassRail.height() * (20.0f / 12.0f)));
            int compassBottom = compassRail.top() + compassRail.height() / 2 + (compassIconSize + 1) / 2 + 1;
            int firstTop = Math.max(verticalInset, compassBottom);
            int lastTop = reticle.top() - visiblePopoverHeight;
            int fallbackGap = Math.max(2, Math.round(compassRail.height() / 3.0f));
            popoverTop = firstTop <= lastTop
                    ? firstTop + (lastTop - firstTop) / 2
                    : Math.min(firstTop + fallbackGap, reticleCenterY - visiblePopoverHeight);
        } else {
            popoverTop = reticle.top() - Math.max(4, verticalGap) - 1 - visiblePopoverHeight;
        }
        popoverTop = clampValue(popoverTop, verticalInset,
                screenHeight - verticalInset - visiblePopoverHeight);
        AuraReaderHudRect signalPopover = centeredRect(
                screenWidth, popoverTop, visiblePopoverWidth, visiblePopoverHeight);

        return new Geometry(
                compassRail.clamp(viewport),
                modeCap.clamp(viewport),
                signalPopover.clamp(viewport),
                reticle,
                bottomSafeZone.clamp(viewport),
                partySafeZone.clamp(viewport));
    }

    private static AuraReaderHudRect centeredRect(
            int screenWidth,
            int top,
            int width,
            int height) {
        int left = (screenWidth - width) / 2;
        return rectFromSize(left, top, width, height);
    }

    private static AuraReaderHudRect rectFromSize(
            int left,
            int top,
            int width,
            int height) {
        return new AuraReaderHudRect(
                left,
                top,
                left + Math.max(0, width),
                top + Math.max(0, height));
    }

    private static int matchScreenParity(int width, int screenWidth) {
        int clampedWidth = Math.max(0, Math.min(width, screenWidth));
        if (((clampedWidth ^ screenWidth) & 1) != 0) {
            if (clampedWidth > 0) {
                clampedWidth--;
            } else if (screenWidth > 0) {
                clampedWidth = 1;
            }
        }
        return clampedWidth;
    }

    private static int scaledDimension(int base, float scale) {
        if (base <= 0 || scale <= 0.0f) {
            return 0;
        }
        return Math.max(1, Math.round(base * scale));
    }

    private static int readableDimension(int base, float scale) {
        return Math.max((int) Math.ceil(base * MIN_READOUT_SCALE), scaledDimension(base, scale));
    }

    private static float adaptiveScale(int screenWidth, int screenHeight) {
        float widthScale = screenWidth / (float) REFERENCE_WIDTH;
        float heightScale = screenHeight / (float) REFERENCE_HEIGHT;
        return Math.min(
                1.0f,
                Math.max(
                        MIN_ADAPTIVE_SCALE,
                        Math.min(widthScale, heightScale)));
    }

    private static int clampValue(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static float sanitizedScale(float value, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(MIN_INPUT_SCALE, Math.min(value, MAX_INPUT_SCALE));
    }

    private static float sanitizedFraction(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT_COMPASS_WIDTH_FRACTION;
        }
        return Math.max(0.10f, Math.min(value, 1.0f));
    }

    public record Request(
            int screenWidth,
            int screenHeight,
            float hudScale,
            float compassWidthFraction,
            int topInset,
            float reticleScale,
            int bottomSafeMargin,
            int partyOverlayWidth,
            int partyOverlayMargin,
            int rightOverlayMargin,
            boolean compactSignal) {
        public Request(int screenWidth, int screenHeight, float hudScale,
                       float compassWidthFraction, int topInset, float reticleScale,
                       int bottomSafeMargin, int partyOverlayWidth, int partyOverlayMargin,
                       int rightOverlayMargin) {
            this(screenWidth, screenHeight, hudScale, compassWidthFraction, topInset,
                    reticleScale, bottomSafeMargin, partyOverlayWidth, partyOverlayMargin,
                    rightOverlayMargin, false);
        }

        public Request {
            if (screenWidth <= 0 || screenHeight <= 0) {
                throw new IllegalArgumentException(
                        "screen dimensions must be positive");
            }
            hudScale = sanitizedScale(hudScale, 1.0f);
            compassWidthFraction = sanitizedFraction(compassWidthFraction);
            reticleScale = sanitizedScale(reticleScale, 1.0f);
            bottomSafeMargin = Math.max(0, bottomSafeMargin);
            partyOverlayWidth = Math.max(0, partyOverlayWidth);
            partyOverlayMargin = Math.max(0, partyOverlayMargin);
            rightOverlayMargin = Math.max(0, rightOverlayMargin);
        }
    }

    public record Geometry(
            AuraReaderHudRect compassRail,
            AuraReaderHudRect modeCap,
            AuraReaderHudRect signalPopover,
            AuraReaderHudRect reticle,
            AuraReaderHudRect bottomSafeZone,
            AuraReaderHudRect partySafeZone) {
        public Geometry {
            Objects.requireNonNull(compassRail, "compassRail");
            Objects.requireNonNull(modeCap, "modeCap");
            Objects.requireNonNull(signalPopover, "signalPopover");
            Objects.requireNonNull(reticle, "reticle");
            Objects.requireNonNull(bottomSafeZone, "bottomSafeZone");
            Objects.requireNonNull(partySafeZone, "partySafeZone");
        }
    }

}
