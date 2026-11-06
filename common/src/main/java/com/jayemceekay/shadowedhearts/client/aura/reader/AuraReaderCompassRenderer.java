package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.centerX;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.centerY;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.clamp01;

/**
 * Draws the compass rail, directions, contacts, and edge indicators for one HUD frame.
 * The HUD owns animation updates, the surrounding pose/blend state, and occupied areas.
 */
final class AuraReaderCompassRenderer {
    private static final int COMPASS_DIRECTION_BOX_SIZE = 32;
    private static final int COMPASS_CURVE_SEGMENTS = 64;
    private static final float COMPASS_CURVE_RISE_FACTOR = 0.70f;
    private static final float COMPASS_BOTTOM_CURVE_FACTOR = 0.45f;
    private static final float COMPASS_CENTER_THICKNESS_FACTOR = 1.40f;
    private static final float COMPASS_END_THICKNESS_FACTOR = 0.25f;
    private static final float COMPASS_EDGE_FADE_START = 0.72f;
    private static final ResourceLocation HUD_FONT = ResourceLocation.parse("uniform");
    private static final ResourceLocation COMPASS_DIRECTION_FONT =
            CobblemonResources.INSTANCE.getDEFAULT_LARGE();
    private static final ResourceLocation COMPASS_DIRECTION_BOX = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/gui/aura_reader/compass_direction_box.png"
    );

    private final float frameAlpha;

    private AuraReaderCompassRenderer(float frameAlpha) {
        this.frameAlpha = frameAlpha;
    }

    /** Direction tiles and the curved rail extend beyond the rail's nominal rectangle. */
    static AuraReaderHudRect reservedArea(AuraReaderHudRect rail) {
        int tileRadius = (compassDirectionBoxSize(rail) + 1) / 2;
        int rise = Math.max(1, Math.round(rail.height() * COMPASS_CURVE_RISE_FACTOR));
        return new AuraReaderHudRect(
                rail.left() - tileRadius,
                Math.min(centerY(rail) - rise - tileRadius, rail.top() - rise - 8),
                rail.right() + tileRadius,
                Math.max(centerY(rail) + tileRadius, rail.bottom() + 3));
    }

    static void render(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rail,
            List<AuraReaderClientState.ReadingSnapshot> readings,
            AuraReaderClientState.ReadingSnapshot focused,
            float playerYaw,
            long ticks,
            IClientConfig config,
            float frameAlpha) {
        new AuraReaderCompassRenderer(frameAlpha).drawCompass(
                graphics, minecraft, rail, readings, focused, playerYaw, ticks, config);
    }

    private void drawCompass(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rail,
            List<AuraReaderClientState.ReadingSnapshot> allReadings,
            AuraReaderClientState.ReadingSnapshot focused,
            float playerYaw,
            long ticks,
            IClientConfig config) {
        int railY = centerY(rail);
        int railCenterX = centerX(rail);
        int halfWidth = Math.max(1, rail.width() / 2);
        int curveRise = Math.max(1, Math.round(rail.height() * COMPASS_CURVE_RISE_FACTOR));
        float visibleArc = Math.max(30.0f, Math.min(180.0f, config.auraReaderCompassVisibleArc()));

        drawCurvedCompassRail(graphics, rail, railY, railCenterX, halfWidth, curveRise);

        for (int heading = -180; heading < 180; heading += 15) {
            if (Math.floorMod(heading, 45) == 0) {
                continue;
            }
            float delta = AuraReaderCompassMath.signedDelta(playerYaw, heading);
            if (!AuraReaderCompassMath.isInsideVisibleArc(delta, visibleArc)) {
                continue;
            }
            float normalizedX = AuraReaderCompassMath.normalizedXPosition(delta, visibleArc);
            int x = railCenterX + Math.round(normalizedX * halfWidth);
            int tickY = railY + compassCurveOffset(normalizedX, curveRise);
            int tickHeight = Math.floorMod(heading, 30) == 0 ? 3 : 2;
            float edgeFade = compassEdgeFade(normalizedX);
            fill(
                    graphics,
                    x,
                    tickY - tickHeight / 2,
                    x + 1,
                    tickY + (tickHeight + 1) / 2,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN_DIM, edgeFade)
            );
        }

        drawCompassDirectionIcons(
                graphics,
                minecraft,
                rail,
                railY,
                railCenterX,
                halfWidth,
                playerYaw,
                visibleArc,
                curveRise
        );

        List<AuraReaderClientState.ReadingSnapshot> readings = allReadings;
        Map<String, MarkerPresentation> insideById = new HashMap<>();
        List<AuraReaderMarkerClusterer.Marker> clusterMarkers = new ArrayList<>();
        int leftEdgeCount = 0;
        int rightEdgeCount = 0;

        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            if (reading.outOfDimension()) continue;
            float readingYaw = AuraReaderCompassMath.worldYaw(reading.directionX(), reading.directionZ());
            float delta = AuraReaderCompassMath.signedDelta(playerYaw, readingYaw);
            if (!AuraReaderCompassMath.isInsideVisibleArc(delta, visibleArc)) {
                if (shouldEdgePin(reading)) {
                    int side = AuraReaderCompassMath.edgeSide(delta);
                    int stack = side < 0 ? leftEdgeCount++ : rightEdgeCount++;
                    drawEdgeIndicator(graphics, rail, side, stack, reading);
                }
                continue;
            }

            float normalizedX = AuraReaderCompassMath.normalizedXPosition(delta, visibleArc);
            insideById.put(reading.id(), new MarkerPresentation(reading, normalizedX));
            clusterMarkers.add(new AuraReaderMarkerClusterer.Marker(
                    reading.id(),
                    reading.type(),
                    reading.source(),
                    normalizedX,
                    reading.priority(),
                    isNavigationSelected(reading) || isPinned(reading)
                            || (focused != null && focused.id().equals(reading.id())),
                    reading.locked()
            ));
        }

        List<AuraReaderMarkerClusterer.Cluster> clusters;
        if (config.auraReaderClusterMarkers()) {
            float clusterThreshold = 8.0f / halfWidth;
            clusters = AuraReaderMarkerClusterer.cluster(clusterMarkers, clusterThreshold);
        } else {
            clusters = clusterMarkers.stream()
                    .map(marker -> new AuraReaderMarkerClusterer.Cluster(
                            marker,
                            List.of(marker),
                            marker.normalizedX()
                    ))
                    .toList();
        }

        Map<String, Integer> markerOffsets = AuraReaderCompassPlacement.verticalOffsets(clusters.stream()
                .map(cluster -> {
                    var reading = insideById.get(cluster.representative().id()).reading();
                    boolean selected = isNavigationSelected(reading);
                    boolean hasFocus = focused != null && focused.id().equals(reading.id());
                    int radius = Math.max(9, Math.round(compassDirectionBoxSize(rail)
                            * (selected ? 1.0f : hasFocus ? 0.90f : 0.80f))) / 2;
                    return new AuraReaderCompassPlacement.Marker(reading.id(),
                            railCenterX + Math.round(cluster.normalizedX() * halfWidth), radius,
                            selected, isPinned(reading));
                }).toList());

        // Draw the navigation selection last so an overlapping lower-priority
        // contact cannot cover its icon or selection arcs.
        clusters = clusters.stream().sorted(Comparator
                .comparing((AuraReaderMarkerClusterer.Cluster cluster) ->
                        isNavigationSelected(insideById.get(cluster.representative().id()).reading()))
                .thenComparing(cluster -> isPinned(insideById.get(cluster.representative().id()).reading())))
                .toList();
        for (AuraReaderMarkerClusterer.Cluster cluster : clusters) {
            MarkerPresentation representative = insideById.get(cluster.representative().id());
            if (representative == null) {
                continue;
            }
            MarkerPresentation marker = new MarkerPresentation(
                    representative.reading(),
                    cluster.normalizedX()
            );
            drawCompassMarker(
                    graphics,
                    minecraft,
                    rail,
                    railY,
                    halfWidth,
                    marker,
                    cluster.count(),
                    markerOffsets.getOrDefault(marker.reading().id(), 0),
                    focused != null && focused.id().equals(marker.reading().id()),
                    visibleArc,
                    curveRise,
                    ticks,
                    config
            );
        }

        drawCenterIndex(graphics, railCenterX, rail, curveRise);
    }

    private void drawCurvedCompassRail(
            GuiGraphics graphics,
            AuraReaderHudRect rail,
            int railY,
            int railCenterX,
            int halfWidth,
            int curveRise) {
        int segments = Math.max(
                16,
                Math.min(COMPASS_CURVE_SEGMENTS, Math.max(1, rail.width()))
        );
        int centerThickness = Math.max(
                1,
                Math.round(rail.height() * COMPASS_CENTER_THICKNESS_FACTOR)
        );
        int endThickness = Math.max(
                1,
                Math.round(rail.height() * COMPASS_END_THICKNESS_FACTOR)
        );
        int bottomCurveRise = Math.max(
                1,
                Math.round(curveRise * COMPASS_BOTTOM_CURVE_FACTOR)
        );
        for (int segment = 0; segment < segments; segment++) {
            int left = rail.left() + rail.width() * segment / segments;
            int right = rail.left() + rail.width() * (segment + 1) / segments;
            if (right <= left) {
                continue;
            }

            float normalizedX = ((left + right) * 0.5f - railCenterX) / halfWidth;
            float edgeFade = compassEdgeFade(normalizedX);
            int curveOffset = compassCurveOffset(normalizedX, curveRise);
            int thickness = Math.max(
                    1,
                    Math.round(AuraReaderCompassMath.taperedRailThickness(
                            normalizedX,
                            centerThickness,
                            endThickness
                    ))
            );
            int railBottom = railY
                    + (endThickness + 1) / 2
                    + compassCurveOffset(normalizedX, bottomCurveRise);
            int railTop = railBottom - thickness;
            fill(
                    graphics,
                    left,
                    railTop,
                    right,
                    railBottom,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.PANEL_BACKGROUND_SOFT, edgeFade)
            );
            fill(
                    graphics,
                    left,
                    railY + curveOffset,
                    right,
                    railY + curveOffset + 1,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN, edgeFade)
            );
        }
    }

    private static float compassEdgeFade(float normalizedX) {
        return AuraReaderCompassMath.edgeFade(
                normalizedX,
                COMPASS_EDGE_FADE_START
        );
    }

    private static int compassCurveOffset(float normalizedX, int curveRise) {
        return Math.round(AuraReaderCompassMath.upwardCurveOffset(
                normalizedX,
                curveRise
        ));
    }

    private void drawCompassDirectionIcons(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rail,
            int railY,
            int railCenterX,
            int halfWidth,
            float playerYaw,
            float visibleArc,
            int curveRise) {
        int boxSize = compassDirectionBoxSize(rail);
        int halfBox = boxSize / 2;
        int minimumCenterX = rail.left() + halfBox;
        int maximumCenterX = rail.right() - (boxSize - halfBox);

        for (int heading = -180; heading < 180; heading += 45) {
            float delta = AuraReaderCompassMath.signedDelta(playerYaw, heading);
            if (!AuraReaderCompassMath.isInsideVisibleArc(delta, visibleArc)) {
                continue;
            }

            float normalizedX = AuraReaderCompassMath.normalizedXPosition(delta, visibleArc);
            int x = railCenterX + Math.round(normalizedX * halfWidth);
            int directionY = railY + compassCurveOffset(normalizedX, curveRise);
            float edgeFade = compassEdgeFade(normalizedX);
            if (x < minimumCenterX || x > maximumCenterX) {
                continue;
            }

            blit(
                    graphics,
                    COMPASS_DIRECTION_BOX,
                    x - halfBox,
                    directionY - halfBox,
                    boxSize,
                    boxSize,
                    0,
                    0,
                    COMPASS_DIRECTION_BOX_SIZE,
                    COMPASS_DIRECTION_BOX_SIZE,
                    COMPASS_DIRECTION_BOX_SIZE,
                    COMPASS_DIRECTION_BOX_SIZE,
                    0.92f * edgeFade
            );
            int textY = directionY - halfBox
                    + Math.max(0, (boxSize - minecraft.font.lineHeight) / 2);
            drawCenteredCompassDirection(
                    graphics,
                    cardinalLabel(heading),
                    x,
                    textY,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.TEXT_PRIMARY, edgeFade)
            );
        }
    }

    private void drawCenterIndex(
            GuiGraphics graphics,
            int x,
            AuraReaderHudRect rail,
            int curveRise) {
        int curveOffset = compassCurveOffset(0.0f, curveRise);
        int color = AuraReaderHudTheme.CYAN_BRIGHT;
        drawDownTriangle(graphics, x, rail.top() + curveOffset - 3, 5, color);
        fill(
                graphics,
                x,
                rail.top() + curveOffset,
                x + 1,
                rail.bottom() + curveOffset + 3,
                color
        );
    }

    private void drawCompassMarker(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rail,
            int railY,
            int halfWidth,
            MarkerPresentation marker,
            int clusterCount,
            int verticalOffset,
            boolean focused,
            float visibleArc,
            int curveRise,
            long ticks,
            IClientConfig config) {
        AuraReaderClientState.ReadingSnapshot reading = marker.reading();
        int x = centerX(rail) + Math.round(marker.normalizedX() * halfWidth);
        int markerY = railY + compassCurveOffset(marker.normalizedX(), curveRise) + verticalOffset;
        boolean navigationSelected = isNavigationSelected(reading);
        float edgeFade = compassEdgeFade(marker.normalizedX());
        if (navigationSelected) edgeFade = Math.max(0.65f, edgeFade);
        if (!config.auraReaderReducedMotion() && reading.interference() > 0.0f) {
            double phase = ticks * 0.78 + reading.id().hashCode() * 0.031;
            x += Math.round(
                    (float) Math.sin(phase)
                            * reading.interference()
                            * clamp01(config.auraReaderStaticIntensity())
                            * 4.0f
            );
        }

        float readingAlpha = AuraReaderClientState.getReadingAlpha(reading.id());
        float confidenceAlpha = 0.38f + reading.confidence() * 0.62f;
        float markerAlpha = readingAlpha * confidenceAlpha * edgeFade;
        int color = AuraReaderHudTheme.multiplyAlpha(
                markerColor(reading),
                markerAlpha
        );

        if (reading.bearingUncertainty() > 0.0f) {
            float fraction = Math.min(1.0f, reading.bearingUncertainty() / visibleArc);
            int uncertaintyWidth = Math.max(2, Math.round(rail.width() * fraction));
            fill(
                    graphics,
                    x - uncertaintyWidth / 2,
                    markerY - 2,
                    x + uncertaintyWidth / 2,
                    markerY + 3,
                    AuraReaderHudTheme.multiplyAlpha(color, 0.24f)
            );
        }

        int size = focused || navigationSelected ? 5 : isTrackedOrLocked(reading) ? 4 : 3;
        int visualRadius = size;
        AuraReaderCompassIcon icon = AuraReaderCompassIcon.forType(reading.type());
        if (icon != null) {
            int frameSize = Math.max(
                    9,
                    Math.round(compassDirectionBoxSize(rail) * (navigationSelected ? 1.0f : focused ? 0.90f : 0.80f))
            );
            visualRadius = frameSize / 2;
            drawTexturedCompassMarker(
                    graphics,
                    icon,
                    x,
                    markerY,
                    frameSize,
                    markerAlpha
            );
        } else {
            drawReadingGlyph(
                    graphics,
                    reading.type(),
                    x,
                    markerY,
                    size,
                    color,
                    reading.source() == AuraReadingSource.PULSE
            );
        }
        if (isPinned(reading)) {
            drawBrackets(graphics, x, markerY, visualRadius + 3, color);
        }
        if (reading.locked() || reading.source() == AuraReadingSource.LOCKED) {
            drawBrackets(
                    graphics,
                    x,
                    markerY,
                    visualRadius + 5,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.LOCKED, markerAlpha)
            );
        }
        if (focused) {
            int focusAccent = reading.type() == AuraReadingType.SHADOW_POKEMON
                    ? AuraReaderHudTheme.SHADOW_MARKER
                    : AuraReaderHudTheme.CYAN_BRIGHT;
            fill(
                    graphics,
                    x - visualRadius,
                    markerY + visualRadius + 2,
                    x + visualRadius + 1,
                    markerY + visualRadius + 3,
                    AuraReaderHudTheme.multiplyAlpha(focusAccent, edgeFade)
            );
        }
        if (clusterCount > 1) {
            drawHudString(
                    graphics,
                    minecraft,
                    Integer.toString(clusterCount),
                    x + visualRadius + 3,
                    markerY - 11,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.TEXT_SECONDARY, edgeFade)
            );
        }
        if (AuraReaderClientState.isReadingStale(reading.id())) {
            drawX(graphics, x + visualRadius, markerY + visualRadius, 2,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.TEXT_WARNING, markerAlpha));
        }
        drawVerticalHint(
                graphics,
                x,
                markerY,
                Math.max(9, visualRadius + 3),
                reading.verticalHint(),
                color
        );
    }

    private static int compassDirectionBoxSize(AuraReaderHudRect rail) {
        return Math.max(
                11,
                Math.round(rail.height() * (20.0f / 12.0f))
        );
    }

    private void drawTexturedCompassMarker(
            GuiGraphics graphics,
            AuraReaderCompassIcon icon,
            int centerX,
            int centerY,
            int frameSize,
            float alpha) {
        int halfSize = frameSize / 2;
        int left = centerX - halfSize;
        int top = centerY - halfSize;
        blit(
                graphics,
                ResourceLocation.fromNamespaceAndPath("shadowedhearts", icon.path()),
                left,
                top,
                frameSize,
                frameSize,
                0,
                0,
                icon.size(),
                icon.size(),
                icon.size(),
                icon.size(),
                alpha
        );
    }

    private void drawEdgeIndicator(
            GuiGraphics graphics,
            AuraReaderHudRect rail,
            int side,
            int stack,
            AuraReaderClientState.ReadingSnapshot reading) {
        int x = side < 0 ? rail.left() + 3 : rail.right() - 4;
        int y = centerY(rail) + Math.min(2, stack) * 4;
        if (isNavigationSelected(reading)) y -= 8;
        else if (isPinned(reading)) y += 8;
        int color = reading.locked() || reading.source() == AuraReadingSource.LOCKED
                ? AuraReaderHudTheme.LOCKED
                : AuraReaderHudTheme.TRACKED;
        float alpha = AuraReaderClientState.getReadingAlpha(reading.id());
        color = AuraReaderHudTheme.multiplyAlpha(color, alpha);
        drawHorizontalChevron(graphics, x, y, side, 5, color);
        if (AuraReaderClientState.isReadingStale(reading.id())) {
            drawX(graphics, x - side * 6, y, 2,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.TEXT_WARNING, alpha));
        }
        if (reading.locked()) {
            drawBrackets(graphics, x, y, 7, color);
        }
    }

    private void drawVerticalHint(
            GuiGraphics graphics,
            int x,
            int y,
            int offset,
            String verticalHint,
            int color) {
        if (verticalHint == null || verticalHint.isBlank() || "level".equalsIgnoreCase(verticalHint)) {
            return;
        }
        boolean above = "above".equalsIgnoreCase(verticalHint);
        int tipY = y + (above ? -offset : offset);
        if (above) {
            fill(graphics, x - 2, tipY, x + 3, tipY + 1, color);
            fill(graphics, x - 1, tipY - 1, x + 2, tipY, color);
        } else {
            fill(graphics, x - 2, tipY, x + 3, tipY + 1, color);
            fill(graphics, x - 1, tipY + 1, x + 2, tipY + 2, color);
        }
    }

    private void drawReadingGlyph(
            GuiGraphics graphics,
            AuraReadingType type,
            int x,
            int y,
            int size,
            int color,
            boolean hollow) {
        switch (type) {
            case SHADOW_POKEMON -> drawDiamond(graphics, x, y, size, color, hollow);
            case UMBRAFALL_SIGNAL -> drawHorizontalChevron(graphics, x, y, 1, size + 1, color);
            case UMBRAFALL_CORE -> drawSquare(graphics, x, y, size, color, true);
            case LEGACY_METEOROID -> drawUpTriangle(graphics, x, y, size + 1, color, hollow);
            case MOONWOUND -> {
                drawDiamond(graphics, x, y, size + 1, color, true);
                fill(graphics, x, y - size, x + 1, y + size + 1, color);
            }
            case VOID_SHADOW -> drawX(graphics, x, y, size, color);
            case PURE_RESONANCE -> drawPlus(graphics, x, y, size, color);
            case PURIFICATION_RESIDUE -> {
                drawSquare(graphics, x, y, size, color, true);
                drawPlus(graphics, x, y, Math.max(1, size - 2), color);
            }
            case UNKNOWN_ANOMALY -> {
                drawDiamond(graphics, x, y, size + 1, color, true);
                drawX(graphics, x, y, Math.max(1, size - 1), color);
            }
        }
    }

    private void drawDiamond(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color,
            boolean hollow) {
        int safeSize = Math.max(1, size);
        for (int offset = -safeSize; offset <= safeSize; offset++) {
            int halfWidth = safeSize - Math.abs(offset);
            if (hollow && halfWidth > 0) {
                fill(graphics, x - halfWidth, y + offset, x - halfWidth + 1, y + offset + 1, color);
                fill(graphics, x + halfWidth, y + offset, x + halfWidth + 1, y + offset + 1, color);
            } else {
                fill(graphics, x - halfWidth, y + offset, x + halfWidth + 1, y + offset + 1, color);
            }
        }
    }

    private void drawSquare(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color,
            boolean hollow) {
        int safeSize = Math.max(1, size);
        if (!hollow) {
            fill(graphics, x - safeSize, y - safeSize, x + safeSize + 1, y + safeSize + 1, color);
            return;
        }
        fill(graphics, x - safeSize, y - safeSize, x + safeSize + 1, y - safeSize + 1, color);
        fill(graphics, x - safeSize, y + safeSize, x + safeSize + 1, y + safeSize + 1, color);
        fill(graphics, x - safeSize, y - safeSize, x - safeSize + 1, y + safeSize + 1, color);
        fill(graphics, x + safeSize, y - safeSize, x + safeSize + 1, y + safeSize + 1, color);
    }

    private void drawUpTriangle(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color,
            boolean hollow) {
        int safeSize = Math.max(2, size);
        for (int row = 0; row < safeSize; row++) {
            int halfWidth = Math.round(row * (safeSize / (float) Math.max(1, safeSize - 1)));
            if (hollow && row > 0 && row < safeSize - 1) {
                fill(graphics, x - halfWidth, y - safeSize / 2 + row, x - halfWidth + 1, y - safeSize / 2 + row + 1, color);
                fill(graphics, x + halfWidth, y - safeSize / 2 + row, x + halfWidth + 1, y - safeSize / 2 + row + 1, color);
            } else {
                fill(graphics, x - halfWidth, y - safeSize / 2 + row, x + halfWidth + 1, y - safeSize / 2 + row + 1, color);
            }
        }
    }

    private void drawDownTriangle(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color) {
        int safeSize = Math.max(2, size);
        for (int row = 0; row < safeSize; row++) {
            int halfWidth = safeSize - row - 1;
            fill(graphics, x - halfWidth, y + row, x + halfWidth + 1, y + row + 1, color);
        }
    }

    private void drawHorizontalChevron(
            GuiGraphics graphics,
            int x,
            int y,
            int direction,
            int size,
            int color) {
        int safeDirection = direction < 0 ? -1 : 1;
        for (int offset = -size / 2; offset <= size / 2; offset++) {
            int horizontal = (size / 2 - Math.abs(offset)) * safeDirection;
            fill(graphics, x + horizontal, y + offset, x + horizontal + 1, y + offset + 1, color);
        }
    }

    private void drawBrackets(
            GuiGraphics graphics,
            int x,
            int y,
            int radius,
            int color) {
        int arm = Math.max(2, radius / 2);
        fill(graphics, x - radius, y - radius, x - radius + 1, y - radius + arm, color);
        fill(graphics, x - radius, y - radius, x - radius + arm, y - radius + 1, color);
        fill(graphics, x + radius, y - radius, x + radius + 1, y - radius + arm, color);
        fill(graphics, x + radius - arm + 1, y - radius, x + radius + 1, y - radius + 1, color);
        fill(graphics, x - radius, y + radius - arm + 1, x - radius + 1, y + radius + 1, color);
        fill(graphics, x - radius, y + radius, x - radius + arm, y + radius + 1, color);
        fill(graphics, x + radius, y + radius - arm + 1, x + radius + 1, y + radius + 1, color);
        fill(graphics, x + radius - arm + 1, y + radius, x + radius + 1, y + radius + 1, color);
    }

    private void drawX(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color) {
        for (int offset = -size; offset <= size; offset++) {
            fill(graphics, x + offset, y + offset, x + offset + 1, y + offset + 1, color);
            fill(graphics, x + offset, y - offset, x + offset + 1, y - offset + 1, color);
        }
    }

    private void drawPlus(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color) {
        fill(graphics, x - size, y, x + size + 1, y + 1, color);
        fill(graphics, x, y - size, x + 1, y + size + 1, color);
    }

    private static boolean isNavigationSelected(AuraReaderClientState.ReadingSnapshot reading) {
        return reading.id().equals(AuraReaderClientState.getSelectedSignal());
    }

    private static boolean isTrackedOrLocked(AuraReaderClientState.ReadingSnapshot reading) {
        return isNavigationSelected(reading)
                || isPinned(reading)
                || reading.locked()
                || reading.source() == AuraReadingSource.LOCKED;
    }

    private static boolean shouldEdgePin(AuraReaderClientState.ReadingSnapshot reading) {
        return isTrackedOrLocked(reading);
    }

    private static int markerColor(AuraReaderClientState.ReadingSnapshot reading) {
        if (reading.locked() || reading.source() == AuraReadingSource.LOCKED) {
            return AuraReaderHudTheme.LOCKED;
        }
        return switch (reading.source()) {
            case PULSE -> AuraReaderHudTheme.PULSE;
            case TRACKED -> AuraReaderHudTheme.TRACKED;
            case INTERFERENCE -> AuraReaderHudTheme.TEXT_DANGER;
            case PASSIVE -> AuraReaderHudTheme.PASSIVE;
            case LOCKED -> AuraReaderHudTheme.LOCKED;
        };
    }

    private static String cardinalLabel(int heading) {
        int normalized = Math.floorMod(heading, 360);
        if (normalized < 23 || normalized >= 338) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.s").getString();
        }
        if (normalized < 68) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.sw").getString();
        }
        if (normalized < 113) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.w").getString();
        }
        if (normalized < 158) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.nw").getString();
        }
        if (normalized < 203) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.n").getString();
        }
        if (normalized < 248) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.ne").getString();
        }
        if (normalized < 293) {
            return Component.translatable("hud.shadowedhearts.aura_reader.compass.e").getString();
        }
        return Component.translatable("hud.shadowedhearts.aura_reader.compass.se").getString();
    }

    private static int hudTextWidth(Minecraft minecraft, String value) {
        return minecraft.font.width(hudText(value));
    }

    private void drawCenteredHudString(
            GuiGraphics graphics,
            Minecraft minecraft,
            String value,
            int centerX,
            int y,
            int color) {
        int adjustedColor = AuraReaderHudTheme.multiplyAlpha(color, frameAlpha);
        if (FastColor.ARGB32.alpha(adjustedColor) <= 3) {
            return;
        }
        GuiUtilsKt.drawCenteredText(
                graphics,
                HUD_FONT,
                hudText(value),
                centerX,
                y,
                adjustedColor,
                false
        );
    }

    private void drawCenteredCompassDirection(
            GuiGraphics graphics,
            String value,
            int centerX,
            int y,
            int color) {
        int adjustedColor = AuraReaderHudTheme.multiplyAlpha(color, frameAlpha);
        if (FastColor.ARGB32.alpha(adjustedColor) <= 3) {
            return;
        }
        MutableComponent text = Component.literal(value == null ? "" : value)
                .withStyle(style -> style
                        .withFont(COMPASS_DIRECTION_FONT)
                        .withBold(true));
        GuiUtilsKt.drawCenteredText(
                graphics,
                COMPASS_DIRECTION_FONT,
                text,
                centerX,
                y,
                adjustedColor,
                false
        );
    }

    private void drawHudString(
            GuiGraphics graphics,
            Minecraft minecraft,
            String value,
            int x,
            int y,
            int color) {
        int adjustedColor = AuraReaderHudTheme.multiplyAlpha(color, frameAlpha);
        if (FastColor.ARGB32.alpha(adjustedColor) <= 3) {
            return;
        }
        GuiUtilsKt.drawText(
                graphics,
                HUD_FONT,
                hudText(value),
                x,
                y,
                false,
                adjustedColor,
                false,
                null,
                null
        );
    }

    private static MutableComponent hudText(String value) {
        return Component.literal(value == null ? "" : value)
                .withStyle(style -> style.withFont(HUD_FONT));
    }

    private static boolean isPinned(AuraReaderClientState.ReadingSnapshot reading) {
        return AuraReaderCompassSelection.isPinned(reading.type(), reading.source(), reading.locked());
    }

    private void fill(
            GuiGraphics graphics,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int color) {
        AuraReaderHudDrawing.fill(graphics, minX, minY, maxX, maxY, color, frameAlpha);
    }

    private void blit(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int uOffset,
            int vOffset,
            int regionWidth,
            int regionHeight,
            int textureWidth,
            int textureHeight,
            float alpha) {
        AuraReaderHudDrawing.blit(graphics, texture, x, y, width, height, uOffset, vOffset,
                regionWidth, regionHeight, textureWidth, textureHeight, alpha, frameAlpha);
    }

    private record MarkerPresentation(
            AuraReaderClientState.ReadingSnapshot reading,
            float normalizedX) {
    }
}
