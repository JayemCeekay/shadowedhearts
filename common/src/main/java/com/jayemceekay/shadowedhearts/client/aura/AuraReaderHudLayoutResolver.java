package com.jayemceekay.shadowedhearts.client.aura;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure screen-space geometry resolver for the Aura Reader HUD.
 *
 * <p>The resolver has no rendering, Minecraft, configuration, or client-state
 * dependencies. Callers provide already-scaled screen measurements and may
 * cache the returned immutable layout for as long as those inputs remain
 * unchanged.</p>
 */
public final class AuraReaderHudLayoutResolver {
    private static final float DEFAULT_COMPASS_WIDTH_FRACTION = 0.42f;
    private static final float MIN_INPUT_SCALE = 0.25f;
    private static final float MAX_INPUT_SCALE = 3.0f;
    private static final int REFERENCE_WIDTH = 854;
    private static final int REFERENCE_HEIGHT = 480;
    private static final float MIN_ADAPTIVE_SCALE = 0.55f;
    private static final int MAX_RETICLE_DIAMETER = 460;

    private AuraReaderHudLayoutResolver() {
    }

    public static Layout resolve(Request request) {
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

        PresetMetrics preset = PresetMetrics.forPreset(request.preset());
        float adaptiveScale = adaptiveScale(screenWidth, screenHeight);
        float componentScale = request.hudScale()
                * adaptiveScale
                * preset.componentScale();
        float panelScale = componentScale * preset.panelScale();
        int effectiveRightInset = Math.min(
                request.rightOverlayMargin(),
                Math.max(0, (screenWidth - 1) / 2));
        int contentRight = screenWidth - effectiveRightInset;
        AuraReaderHudRect panelBounds = new AuraReaderHudRect(
                0, 0, contentRight, bottomSafeTop);

        int maxCenteredWidth = Math.max(
                0, screenWidth - effectiveRightInset * 2);
        int requestedRailWidth = Math.round(
                screenWidth
                        * request.compassWidthFraction()
                        * preset.compassWidthScale());
        int compassRailWidth = matchScreenParity(
                Math.min(requestedRailWidth, maxCenteredWidth),
                screenWidth);
        int compassRailHeight = scaledDimension(12, componentScale);
        int compassCapHeight = scaledDimension(34, componentScale);
        int horizontalCapGap = scaledDimension(8, componentScale);

        int compassTop = Math.max(
                0,
                request.bossBarMargin()
                        + request.compassVerticalOffset());
        int availableCapHeight = Math.max(
                0, bottomSafeTop - Math.min(compassTop, bottomSafeTop));
        compassCapHeight = Math.min(compassCapHeight, availableCapHeight);
        compassTop = clampValue(
                compassTop,
                0,
                Math.max(0, bottomSafeTop - compassCapHeight));
        compassRailHeight = Math.min(
                compassRailHeight, compassCapHeight);
        int compassRailTop = compassTop
                + Math.max(0, (compassCapHeight - compassRailHeight) / 2);
        AuraReaderHudRect compassRail = centeredRect(
                screenWidth,
                compassRailTop,
                compassRailWidth,
                compassRailHeight).clamp(upperViewport);

        int modeCapRight = Math.max(
                0, compassRail.left() - horizontalCapGap);
        int modeCapWidth = Math.min(
                scaledDimension(88, componentScale), modeCapRight);
        AuraReaderHudRect modeCap = rectFromSize(
                modeCapRight - modeCapWidth,
                compassTop,
                modeCapWidth,
                compassCapHeight).clamp(upperViewport);

        int chargeCapLeft = Math.min(
                contentRight,
                compassRail.right() + horizontalCapGap);
        int chargeCapWidth = Math.min(
                scaledDimension(148, componentScale),
                Math.max(0, contentRight - chargeCapLeft));
        AuraReaderHudRect chargeCap = rectFromSize(
                chargeCapLeft,
                compassTop,
                chargeCapWidth,
                compassCapHeight).clamp(upperViewport);

        int verticalGap = scaledDimension(8, componentScale);
        int desiredPopoverWidth = Math.max(
                scaledDimension(132, componentScale),
                Math.round(compassRail.width() * 0.54f));
        int popoverWidth = matchScreenParity(
                Math.min(
                        Math.min(
                                desiredPopoverWidth,
                                scaledDimension(380, componentScale)),
                        maxCenteredWidth),
                screenWidth);
        int popoverTop = Math.max(
                Math.max(modeCap.bottom(), chargeCap.bottom()),
                compassRail.bottom()) + verticalGap;
        int popoverHeight = Math.min(
                scaledDimension(46, componentScale),
                Math.max(0, bottomSafeTop - popoverTop));
        AuraReaderHudRect signalPopover = centeredRect(
                screenWidth,
                popoverTop,
                popoverWidth,
                popoverHeight).clamp(upperViewport);

        int reticleVerticalGap = scaledDimension(12, componentScale);
        int reticleCenterY = screenHeight / 2;
        int reticleTopLimit = Math.min(
                signalPopover.bottom() + reticleVerticalGap,
                Math.max(0, reticleCenterY - scaledDimension(24, componentScale)));
        int requestedReticleDiameter = Math.round(
                screenHeight
                        * 0.48f
                        * request.hudScale()
                        * request.reticleScale()
                        * preset.reticleScale());
        int maximumReticleDiameter = Math.round(
                MAX_RETICLE_DIAMETER
                        * request.hudScale()
                        * preset.reticleScale());
        int centeredVerticalDiameter = Math.max(
                0,
                2 * Math.min(
                        Math.max(0, reticleCenterY - reticleTopLimit),
                        Math.max(0, screenHeight - reticleCenterY)));
        int reticleDiameter = Math.min(
                Math.min(
                        requestedReticleDiameter,
                        maximumReticleDiameter),
                Math.min(maxCenteredWidth, centeredVerticalDiameter));
        reticleDiameter = matchScreenParity(
                Math.max(0, reticleDiameter), screenWidth);
        int reticleTop = reticleCenterY - reticleDiameter / 2;
        AuraReaderHudRect reticle = centeredRect(
                screenWidth,
                reticleTop,
                reticleDiameter,
                reticleDiameter).clamp(viewport);

        int panelGap = scaledDimension(14, componentScale * preset.gapScale());
        int targetWidth = scaledDimension(154, panelScale);
        int targetHeight = scaledDimension(58, panelScale);
        List<AuraReaderHudRect> targetForbidden = nonEmptyRectangles(
                partySafeZone,
                bottomSafeZone,
                compassRail,
                modeCap,
                chargeCap,
                signalPopover,
                reticle);
        AuraReaderHudRect targetPanel = placePanel(
                targetWidth,
                targetHeight,
                reticle,
                panelGap,
                panelBounds,
                targetForbidden,
                List.of(PanelSlot.LEFT_CENTER),
                List.of(
                        PanelSlot.RIGHT_CENTER,
                        PanelSlot.RIGHT_UPPER,
                        PanelSlot.RIGHT_LOWER,
                        PanelSlot.LEFT_UPPER,
                        PanelSlot.LEFT_LOWER,
                        PanelSlot.ABOVE_CENTER),
                request.presentationSeed() ^ 0x54A3C9D27E18B6F1L);

        int auraWidth = scaledDimension(160, panelScale);
        int auraHeight = scaledDimension(68, panelScale);
        List<AuraReaderHudRect> auraForbidden = new ArrayList<>(
                targetForbidden);
        if (targetPanel.width() > 0 && targetPanel.height() > 0) {
            auraForbidden.add(targetPanel);
        }
        AuraReaderHudRect auraPanel = placePanel(
                auraWidth,
                auraHeight,
                reticle,
                panelGap,
                panelBounds,
                auraForbidden,
                List.of(
                        PanelSlot.RIGHT_UPPER,
                        PanelSlot.RIGHT_LOWER),
                List.of(
                        PanelSlot.RIGHT_CENTER,
                        PanelSlot.LEFT_UPPER,
                        PanelSlot.LEFT_LOWER,
                        PanelSlot.LEFT_CENTER,
                        PanelSlot.ABOVE_CENTER),
                request.presentationSeed() ^ 0x37D8E4B90AC6251FL);

        return new Layout(
                compassRail.clamp(viewport),
                modeCap.clamp(viewport),
                chargeCap.clamp(viewport),
                signalPopover.clamp(viewport),
                reticle.clamp(viewport),
                targetPanel.clamp(viewport),
                auraPanel.clamp(viewport),
                bottomSafeZone.clamp(viewport),
                partySafeZone.clamp(viewport));
    }

    private static AuraReaderHudRect placePanel(
            int requestedWidth,
            int requestedHeight,
            AuraReaderHudRect reticle,
            int gap,
            AuraReaderHudRect bounds,
            List<AuraReaderHudRect> forbidden,
            List<PanelSlot> preferredSlots,
            List<PanelSlot> fallbackSlots,
            long seed) {
        if (requestedWidth <= 0
                || requestedHeight <= 0
                || bounds.width() <= 0
                || bounds.height() <= 0) {
            return emptyAtSafeEdge(bounds, forbidden);
        }

        List<PanelSlot> seededFallbacks = seededOrder(fallbackSlots, seed);
        float[] sizeSteps = {1.0f, 0.88f, 0.74f, 0.60f};
        for (float sizeStep : sizeSteps) {
            int width = Math.min(
                    bounds.width(),
                    Math.max(1, Math.round(requestedWidth * sizeStep)));
            int height = Math.min(
                    bounds.height(),
                    Math.max(1, Math.round(requestedHeight * sizeStep)));

            AuraReaderHudRect preferred = firstClearSlot(
                    preferredSlots,
                    width,
                    height,
                    reticle,
                    gap,
                    bounds,
                    forbidden);
            if (preferred != null) {
                return preferred;
            }

            AuraReaderHudRect fallback = firstClearSlot(
                    seededFallbacks,
                    width,
                    height,
                    reticle,
                    gap,
                    bounds,
                    forbidden);
            if (fallback != null) {
                return fallback;
            }
        }

        return emptyAtSafeEdge(bounds, forbidden);
    }

    private static AuraReaderHudRect firstClearSlot(
            List<PanelSlot> slots,
            int width,
            int height,
            AuraReaderHudRect reticle,
            int gap,
            AuraReaderHudRect bounds,
            List<AuraReaderHudRect> forbidden) {
        for (PanelSlot slot : slots) {
            AuraReaderHudRect candidate = panelRect(
                    slot, width, height, reticle, gap).clamp(bounds);
            if (isClear(candidate, forbidden)) {
                return candidate;
            }
        }
        return null;
    }

    private static AuraReaderHudRect panelRect(
            PanelSlot slot,
            int width,
            int height,
            AuraReaderHudRect reticle,
            int gap) {
        int reticleCenterX = (reticle.left() + reticle.right()) / 2;
        int reticleCenterY = (reticle.top() + reticle.bottom()) / 2;
        int leftX = reticle.left() - gap - width;
        int rightX = reticle.right() + gap;
        return switch (slot) {
            case LEFT_CENTER -> rectFromSize(
                    leftX,
                    reticleCenterY - height / 2,
                    width,
                    height);
            case LEFT_UPPER -> rectFromSize(
                    leftX,
                    reticle.top() - height / 3,
                    width,
                    height);
            case LEFT_LOWER -> rectFromSize(
                    leftX,
                    reticle.bottom() - height * 2 / 3,
                    width,
                    height);
            case RIGHT_CENTER -> rectFromSize(
                    rightX,
                    reticleCenterY - height / 2,
                    width,
                    height);
            case RIGHT_UPPER -> rectFromSize(
                    rightX,
                    reticle.top() - height / 3,
                    width,
                    height);
            case RIGHT_LOWER -> rectFromSize(
                    rightX,
                    reticle.bottom() - height * 2 / 3,
                    width,
                    height);
            case ABOVE_CENTER -> rectFromSize(
                    reticleCenterX - width / 2,
                    reticle.top() - gap - height,
                    width,
                    height);
        };
    }

    private static boolean isClear(
            AuraReaderHudRect candidate,
            List<AuraReaderHudRect> forbidden) {
        if (candidate.width() <= 0 || candidate.height() <= 0) {
            return false;
        }
        for (AuraReaderHudRect occupied : forbidden) {
            if (candidate.intersects(occupied)) {
                return false;
            }
        }
        return true;
    }

    private static List<PanelSlot> seededOrder(
            List<PanelSlot> slots,
            long seed) {
        if (slots.size() < 2) {
            return slots;
        }

        ArrayList<PanelSlot> ordered = new ArrayList<>(slots.size());
        long mixedSeed = mixSeed(seed);
        int rotation = (int) Math.floorMod(mixedSeed, slots.size());
        boolean reverse = (mixedSeed & 1L) != 0L;
        for (int index = 0; index < slots.size(); index++) {
            int offset = reverse ? -index : index;
            int sourceIndex = Math.floorMod(rotation + offset, slots.size());
            ordered.add(slots.get(sourceIndex));
        }
        return List.copyOf(ordered);
    }

    private static long mixSeed(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    private static AuraReaderHudRect emptyAtSafeEdge(
            AuraReaderHudRect bounds,
            List<AuraReaderHudRect> forbidden) {
        int left = bounds.left();
        int top = bounds.top();
        for (AuraReaderHudRect occupied : forbidden) {
            if (occupied.left() <= left && occupied.right() > left) {
                left = Math.min(bounds.right(), occupied.right());
            }
            if (occupied.top() <= top && occupied.bottom() > top) {
                top = Math.min(bounds.bottom(), occupied.bottom());
            }
        }
        return new AuraReaderHudRect(left, top, left, top);
    }

    private static List<AuraReaderHudRect> nonEmptyRectangles(
            AuraReaderHudRect... rectangles) {
        ArrayList<AuraReaderHudRect> result = new ArrayList<>(
                rectangles.length);
        for (AuraReaderHudRect rectangle : rectangles) {
            if (rectangle.width() > 0 && rectangle.height() > 0) {
                result.add(rectangle);
            }
        }
        return result;
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
            int compassVerticalOffset,
            float reticleScale,
            int bottomSafeMargin,
            int partyOverlayWidth,
            int partyOverlayMargin,
            int bossBarMargin,
            int rightOverlayMargin,
            AuraReaderHudLayoutPreset preset,
            long presentationSeed) {
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
            bossBarMargin = Math.max(0, bossBarMargin);
            rightOverlayMargin = Math.max(0, rightOverlayMargin);
            preset = preset == null
                    ? AuraReaderHudLayoutPreset.DEFAULT
                    : preset;
        }
    }

    public record Layout(
            AuraReaderHudRect compassRail,
            AuraReaderHudRect modeCap,
            AuraReaderHudRect chargeCap,
            AuraReaderHudRect signalPopover,
            AuraReaderHudRect reticle,
            AuraReaderHudRect targetPanel,
            AuraReaderHudRect auraPanel,
            AuraReaderHudRect bottomSafeZone,
            AuraReaderHudRect partySafeZone) {
        public Layout {
            Objects.requireNonNull(compassRail, "compassRail");
            Objects.requireNonNull(modeCap, "modeCap");
            Objects.requireNonNull(chargeCap, "chargeCap");
            Objects.requireNonNull(signalPopover, "signalPopover");
            Objects.requireNonNull(reticle, "reticle");
            Objects.requireNonNull(targetPanel, "targetPanel");
            Objects.requireNonNull(auraPanel, "auraPanel");
            Objects.requireNonNull(bottomSafeZone, "bottomSafeZone");
            Objects.requireNonNull(partySafeZone, "partySafeZone");
        }
    }

    private record PresetMetrics(
            float componentScale,
            float compassWidthScale,
            float reticleScale,
            float panelScale,
            float gapScale) {
        private static PresetMetrics forPreset(
                AuraReaderHudLayoutPreset preset) {
            return switch (preset) {
                case DEFAULT -> new PresetMetrics(
                        1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
                case COMPACT -> new PresetMetrics(
                        0.86f, 0.92f, 0.88f, 0.90f, 0.88f);
                case MINIMAL -> new PresetMetrics(
                        0.72f, 0.82f, 0.76f, 0.76f, 0.75f);
                case ULTRAWIDE -> new PresetMetrics(
                        1.0f, 0.92f, 1.0f, 1.0f, 0.70f);
                case STREAMER -> new PresetMetrics(
                        1.16f, 1.0f, 1.08f, 1.12f, 1.08f);
            };
        }
    }

    private enum PanelSlot {
        LEFT_CENTER,
        LEFT_UPPER,
        LEFT_LOWER,
        RIGHT_CENTER,
        RIGHT_UPPER,
        RIGHT_LOWER,
        ABOVE_CENTER
    }
}
