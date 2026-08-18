package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Field presentation for the Aura Reader.
 *
 * <p>World data stays server-authoritative. This class is deliberately limited
 * to screen-space selection, collision-aware layout, and presentation.</p>
 */
public final class AuraReaderHud {
    private static final int SCAN_OVERLAY_NOTCH_WIDTH = 200;
    private static final int SCAN_RING_MIDDLE_WIDTH = 100;
    private static final int SCAN_RING_MIDDLE_HEIGHT = 1;
    private static final int SCAN_RING_OUTER_DIAMETER = 116;
    private static final int SCAN_RING_INNER_DIAMETER = 84;
    private static final int SCAN_UNKNOWN_WIDTH = 34;
    private static final int SCAN_UNKNOWN_HEIGHT = 46;
    private static final int COMPASS_DIRECTION_BOX_SIZE = 32;
    private static final int COMPASS_CURVE_SEGMENTS = 64;
    private static final float COMPASS_CURVE_RISE_FACTOR = 0.70f;
    private static final float COMPASS_BOTTOM_CURVE_FACTOR = 0.45f;
    private static final float COMPASS_CENTER_THICKNESS_FACTOR = 1.40f;
    private static final float COMPASS_END_THICKNESS_FACTOR = 0.25f;
    private static final float COMPASS_EDGE_FADE_START = 0.72f;
    private static final int SCANNER_INFO_FRAME_STEPS = 10;
    private static final int PARTY_OVERLAY_SAFE_WIDTH = 72;
    private static final float SOFT_FOCUS_DEGREES = 15.0f;
    private static final ResourceLocation HUD_FONT = ResourceLocation.parse("uniform");
    private static final ResourceLocation COMPASS_DIRECTION_FONT =
            CobblemonResources.INSTANCE.getDEFAULT_LARGE();

    private static final ResourceLocation SCAN_OVERLAY_CORNERS = cobblemon("textures/gui/pokedex/scan/overlay_corners.png");
    private static final ResourceLocation SCAN_OVERLAY_TOP = cobblemon("textures/gui/pokedex/scan/overlay_border_top.png");
    private static final ResourceLocation SCAN_OVERLAY_BOTTOM = cobblemon("textures/gui/pokedex/scan/overlay_border_bottom.png");
    private static final ResourceLocation SCAN_OVERLAY_LEFT = cobblemon("textures/gui/pokedex/scan/overlay_border_left.png");
    private static final ResourceLocation SCAN_OVERLAY_RIGHT = cobblemon("textures/gui/pokedex/scan/overlay_border_right.png");
    private static final ResourceLocation SCAN_OVERLAY_LINES = cobblemon("textures/gui/pokedex/scan/overlay_scanlines.png");
    private static final ResourceLocation SCAN_OVERLAY_NOTCH = cobblemon("textures/gui/pokedex/scan/overlay_notch.png");
    private static final ResourceLocation SCAN_RING_OUTER = cobblemon("textures/gui/pokedex/scan/scan_ring_outer.png");
    private static final ResourceLocation SCAN_RING_MIDDLE = cobblemon("textures/gui/pokedex/scan/scan_ring_middle.png");
    private static final ResourceLocation SCAN_RING_INNER = cobblemon("textures/gui/pokedex/scan/scan_ring_inner.png");
    private static final ResourceLocation SCAN_UNKNOWN = cobblemon("textures/gui/pokedex/scan/scan_unknown.png");
    private static final ResourceLocation COMPASS_DIRECTION_BOX = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/gui/aura_reader/compass_direction_box.png"
    );

    private static float frameAlpha = 1.0f;

    private AuraReaderHud() {
    }

    private static ResourceLocation cobblemon(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        IClientConfig config = ShadowedHeartsConfigs.getInstance().getClientConfig();
        if (!config.auraScannerEnabled()) {
            return;
        }

        AuraReaderClientState.updateHudAnimations();
        float activationProgress = AuraReaderClientState.getActivationProgress();
        if (activationProgress <= 0.0f && !AuraReaderClientState.isActive()) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        long ticks = System.currentTimeMillis() / 50L;
        float playerYaw = minecraft.player.getViewYRot(1.0f);
        List<AuraReaderClientState.ReadingSnapshot> readings = AuraReaderClientState.getReadings();
        AuraReaderClientState.ReadingSnapshot preliminaryFocus = getFocusedReading(readings, playerYaw);
        AuraReaderClientState.ReadingSnapshot tracked = getTrackedReading(readings);
        AuraReaderHudLayoutPreset preset = AuraReaderHudLayoutPreset.fromName(config.auraReaderLayoutPreset());
        List<AuraReaderClientState.ReadingSnapshot> displayedReadings = selectReadings(
                readings,
                config.auraReaderMaxMarkers(),
                preliminaryFocus,
                preset
        );
        AuraReaderClientState.ReadingSnapshot focused = getFocusedReading(displayedReadings, playerYaw);
        long presentationSeed = focused != null
                ? focused.id().hashCode()
                : AuraReaderClientState.getSelectedSignal().hashCode();

        AuraReaderHudLayoutResolver.Layout layout = AuraReaderHudLayoutResolver.resolve(
                new AuraReaderHudLayoutResolver.Request(
                        width,
                        height,
                        config.auraReaderHudScale(),
                        config.auraReaderCompassWidth(),
                        config.auraReaderCompassVerticalOffset(),
                        config.auraReaderReticleScale(),
                        config.auraReaderBottomSafeMargin(),
                        visiblePartyOverlayWidth(),
                        config.auraReaderPartyOverlayMargin(),
                        config.auraReaderBossBarMargin(),
                        config.auraReaderRightOverlayMargin(),
                        preset,
                        presentationSeed
                )
        );

        frameAlpha = clamp01(activationProgress * config.auraReaderHudOpacity());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        float panOffset = config.auraReaderReducedMotion()
                ? 0.0f
                : (float) (-Math.pow(1.0f - activationProgress, 2.0f) * height);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, panOffset, 0.0f);

        drawScreenWash(graphics, width, height, ticks, config);
        drawScannerBorder(graphics, width, height);
        drawCompass(graphics, minecraft, layout, displayedReadings, focused, playerYaw, ticks, config);
        float scannerScale = Math.min(layout.reticle().width(), layout.reticle().height())
                / (float) SCAN_RING_OUTER_DIAMETER;
        int scannerCenterX = width / 2;
        int scannerCenterY = height / 2;
        if (preset != AuraReaderHudLayoutPreset.MINIMAL) {
            drawSignalPopover(graphics, minecraft, layout.signalPopover(), tracked);
            drawContextPanels(
                    graphics,
                    minecraft,
                    width,
                    height,
                    scannerCenterX,
                    scannerCenterY,
                    scannerScale,
                    layout,
                    focused
            );
        }

        drawReticle(
                graphics,
                scannerCenterX,
                scannerCenterY,
                scannerScale,
                focused,
                config
        );

        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        frameAlpha = 1.0f;
    }

    private static void drawScreenWash(
            GuiGraphics graphics,
            int width,
            int height,
            long ticks,
            IClientConfig config) {
        float staticIntensity = clamp01(config.auraReaderStaticIntensity());
        if (staticIntensity <= 0.0f) {
            return;
        }

        float interlace = config.auraReaderReducedMotion()
                ? 0.0f
                : (float) (((ticks % 14) * 0.5f) * 0.5f);
        for (int y = 0; y < height; y += 4) {
            blit(
                    graphics,
                    SCAN_OVERLAY_LINES,
                    0,
                    Math.round(y - interlace),
                    width,
                    4,
                    0,
                    0,
                    1,
                    4,
                    1,
                    4,
                    0.35f * staticIntensity
            );
        }
    }

    private static void drawScannerBorder(GuiGraphics graphics, int width, int height) {
        blit(graphics, SCAN_OVERLAY_CORNERS, 0, 0, 4, 4, 0, 0, 4, 4, 8, 8, 0.9f);
        blit(graphics, SCAN_OVERLAY_CORNERS, width - 4, 0, 4, 4, 4, 0, 4, 4, 8, 8, 0.9f);
        blit(graphics, SCAN_OVERLAY_CORNERS, 0, height - 4, 4, 4, 0, 4, 4, 4, 8, 8, 0.9f);
        blit(graphics, SCAN_OVERLAY_CORNERS, width - 4, height - 4, 4, 4, 4, 4, 4, 4, 8, 8, 0.9f);

        int notchStartX = (width - SCAN_OVERLAY_NOTCH_WIDTH) / 2;
        blit(graphics, SCAN_OVERLAY_TOP, 4, 0, notchStartX - 4, 3, 0, 0, 1, 3, 1, 3, 0.9f);
        blit(
                graphics,
                SCAN_OVERLAY_TOP,
                notchStartX + SCAN_OVERLAY_NOTCH_WIDTH,
                0,
                width - notchStartX - SCAN_OVERLAY_NOTCH_WIDTH - 4,
                3,
                0,
                0,
                1,
                3,
                1,
                3,
                0.9f
        );
        blit(graphics, SCAN_OVERLAY_BOTTOM, 4, height - 3, width - 8, 3, 0, 0, 1, 3, 1, 3, 0.9f);
        blit(graphics, SCAN_OVERLAY_LEFT, 0, 4, 3, height - 8, 0, 0, 3, 1, 3, 1, 0.9f);
        blit(graphics, SCAN_OVERLAY_RIGHT, width - 3, 4, 3, height - 8, 0, 0, 3, 1, 3, 1, 0.9f);
        blit(
                graphics,
                SCAN_OVERLAY_NOTCH,
                notchStartX,
                0,
                SCAN_OVERLAY_NOTCH_WIDTH,
                12,
                0,
                0,
                200,
                12,
                200,
                12,
                0.9f
        );
    }

    private static void drawCompass(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudLayoutResolver.Layout layout,
            List<AuraReaderClientState.ReadingSnapshot> allReadings,
            AuraReaderClientState.ReadingSnapshot focused,
            float playerYaw,
            long ticks,
            IClientConfig config) {
        AuraReaderHudRect rail = layout.compassRail();
        int railY = centerY(rail);
        int railCenterX = centerX(rail);
        int halfWidth = Math.max(1, rail.width() / 2);
        int curveRise = Math.max(1, Math.round(rail.height() * COMPASS_CURVE_RISE_FACTOR));
        float visibleArc = Math.max(30.0f, Math.min(180.0f, config.auraReaderCompassVisibleArc()));

        drawCurvedCompassRail(graphics, rail, railY, railCenterX, halfWidth, curveRise);
        drawModeCap(graphics, minecraft, layout.modeCap());
        drawChargeCap(graphics, minecraft, layout.chargeCap(), config);

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
                    reading.selected() || (focused != null && focused.id().equals(reading.id())),
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
                    focused != null && focused.id().equals(marker.reading().id()),
                    visibleArc,
                    curveRise,
                    ticks,
                    config
            );
        }

        drawCenterIndex(graphics, railCenterX, rail, curveRise);
    }

    private static void drawCurvedCompassRail(
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

    private static void drawModeCap(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        drawBeveledPanel(graphics, rect, AuraReaderHudTheme.CYAN, AuraReaderHudTheme.PANEL_BACKGROUND);
        drawCenteredHudString(
                graphics,
                minecraft,
                modeLabel(AuraReaderClientState.getMode()),
                centerX(rect),
                centerY(rect) - 4,
                AuraReaderHudTheme.TEXT_PRIMARY
        );
    }

    private static void drawChargeCap(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect,
            IClientConfig config) {
        String visibility = config.auraReaderChargeDisplay();
        if ("hidden".equalsIgnoreCase(visibility)
                || rect.width() <= 0
                || rect.height() <= 0) {
            return;
        }

        boolean conditional = "conditional".equalsIgnoreCase(visibility);
        float chargeFraction = AuraReaderClientState.getChargeFraction();
        if (conditional
                && chargeFraction >= 0.999f
                && !AuraReaderClientState.isPulseOnCooldown()) {
            return;
        }

        drawBeveledPanel(graphics, rect, AuraReaderHudTheme.CYAN, AuraReaderHudTheme.PANEL_BACKGROUND);
        int midY = centerY(rect);
        int labelX = rect.left() + 10;
        String chargeLabel = translated("hud.shadowedhearts.aura_reader.label.charge");
        drawHudString(
                graphics,
                minecraft,
                chargeLabel,
                labelX,
                midY - 4,
                chargeTextColor(chargeFraction)
        );

        int percent = Math.round(chargeFraction * 100.0f);
        String percentLabel = percent + "%";
        int percentWidth = hudTextWidth(minecraft, percentLabel);
        int barLeft = labelX + hudTextWidth(minecraft, chargeLabel) + 7;
        int barRight = Math.max(barLeft, rect.right() - percentWidth - 9);
        int barTop = midY - 3;
        fill(graphics, barLeft, barTop, barRight, barTop + 6, 0xCC061116);
        int filledRight = barLeft + Math.round((barRight - barLeft) * chargeFraction);
        fill(graphics, barLeft + 1, barTop + 1, Math.max(barLeft + 1, filledRight), barTop + 5, chargeColor(chargeFraction));
        drawHudString(
                graphics,
                minecraft,
                percentLabel,
                rect.right() - percentWidth - 5,
                midY - 4,
                chargeTextColor(chargeFraction)
        );
    }

    private static void drawCompassDirectionIcons(
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

    private static void drawCenterIndex(
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

    private static void drawCompassMarker(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rail,
            int railY,
            int halfWidth,
            MarkerPresentation marker,
            int clusterCount,
            boolean focused,
            float visibleArc,
            int curveRise,
            long ticks,
            IClientConfig config) {
        AuraReaderClientState.ReadingSnapshot reading = marker.reading();
        int x = centerX(rail) + Math.round(marker.normalizedX() * halfWidth);
        int markerY = railY + compassCurveOffset(marker.normalizedX(), curveRise);
        float edgeFade = compassEdgeFade(marker.normalizedX());
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

        int size = focused ? 5 : isTrackedOrLocked(reading) ? 4 : 3;
        int visualRadius = size;
        if (reading.type() == AuraReadingType.SHADOW_POKEMON) {
            int frameSize = Math.max(
                    9,
                    Math.round(compassDirectionBoxSize(rail) * (focused ? 0.90f : 0.80f))
            );
            visualRadius = frameSize / 2;
            drawShadowPokemonCompassMarker(
                    graphics,
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
        if (reading.source() == AuraReadingSource.TRACKED) {
            drawBrackets(graphics, x, markerY, visualRadius + 3, color);
        }
        if (reading.locked() || reading.source() == AuraReadingSource.LOCKED) {
            drawBrackets(
                    graphics,
                    x,
                    markerY,
                    visualRadius + 5,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.LOCKED, edgeFade)
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

    private static void drawShadowPokemonCompassMarker(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int frameSize,
            float alpha) {
        int halfSize = frameSize / 2;
        int left = centerX - halfSize;
        int top = centerY - halfSize;
        int right = left + frameSize;
        int bottom = top + frameSize;
        int bevel = Math.max(1, Math.round(frameSize * (2.0f / COMPASS_DIRECTION_BOX_SIZE)));
        int borderColor = AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.SHADOW_MARKER, alpha);
        int backgroundColor = AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.SHADOW_MARKER_BACKGROUND, alpha);

        fill(graphics, left + bevel, top, right - bevel, bottom, backgroundColor);
        fill(graphics, left, top + bevel, right, bottom - bevel, backgroundColor);
        fill(graphics, left + bevel, top, right - bevel, top + 1, borderColor);
        fill(graphics, left + bevel, bottom - 1, right - bevel, bottom, borderColor);
        fill(graphics, left, top + bevel, left + 1, bottom - bevel, borderColor);
        fill(graphics, right - 1, top + bevel, right, bottom - bevel, borderColor);

        int questionHeight = Math.max(7, frameSize - 6);
        int questionWidth = Math.max(
                5,
                Math.round(questionHeight * (SCAN_UNKNOWN_WIDTH / (float) SCAN_UNKNOWN_HEIGHT))
        );
        blit(
                graphics,
                SCAN_UNKNOWN,
                centerX - questionWidth / 2,
                centerY - questionHeight / 2,
                questionWidth,
                questionHeight,
                0,
                0,
                SCAN_UNKNOWN_WIDTH,
                SCAN_UNKNOWN_HEIGHT,
                SCAN_UNKNOWN_WIDTH,
                SCAN_UNKNOWN_HEIGHT,
                alpha
        );
    }

    private static void drawEdgeIndicator(
            GuiGraphics graphics,
            AuraReaderHudRect rail,
            int side,
            int stack,
            AuraReaderClientState.ReadingSnapshot reading) {
        int x = side < 0 ? rail.left() + 3 : rail.right() - 4;
        int y = centerY(rail) + Math.min(2, stack) * 4;
        int color = reading.locked() || reading.source() == AuraReadingSource.LOCKED
                ? AuraReaderHudTheme.LOCKED
                : AuraReaderHudTheme.TRACKED;
        drawHorizontalChevron(graphics, x, y, side, 5, color);
        if (reading.locked()) {
            drawBrackets(graphics, x, y, 7, color);
        }
    }

    private static void drawVerticalHint(
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

    private static void drawReticle(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            float scale,
            AuraReaderClientState.ReadingSnapshot focused,
            IClientConfig config) {
        if (!(scale > 0.0f) || !Float.isFinite(scale)) {
            return;
        }

        boolean locked = focused != null
                && (focused.locked() || focused.source() == AuraReadingSource.LOCKED);
        float pulseProgress = AuraReaderClientState.getPulseAnimationProgress();
        boolean pulseActive = pulseProgress > 0.0f;
        float ringAlpha = locked ? 1.0f : focused != null ? 0.96f : pulseActive ? 0.94f : 0.82f;
        float rotation = config.auraReaderReducedMotion()
                ? 0.0f
                : AuraReaderClientState.getUsageIntervals() % 360.0f;
        float innerRotation = config.auraReaderReducedMotion()
                ? 0.0f
                : AuraReaderClientState.getInnerRingRotation();

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);

        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(rotation * -0.5f)));
        blitNative(
                graphics,
                SCAN_RING_OUTER,
                -(SCAN_RING_OUTER_DIAMETER / 2.0f),
                -(SCAN_RING_OUTER_DIAMETER / 2.0f),
                SCAN_RING_OUTER_DIAMETER,
                SCAN_RING_OUTER_DIAMETER,
                0,
                0,
                SCAN_RING_OUTER_DIAMETER,
                SCAN_RING_OUTER_DIAMETER,
                ringAlpha
        );
        graphics.pose().popPose();

        float progressOpacity = ringAlpha;
        int middleSegments = 40;
        float scanProgress = AuraReaderClientState.getReticleScanProgress();
        if (scanProgress > 0.0f) {
            if (scanProgress < 20.0f) {
                progressOpacity = Math.max(0.0f, ringAlpha - scanProgress * 0.05f);
            } else {
                middleSegments = Math.max(0, Math.min(40, (int) Math.floor((scanProgress - 20.0f) / 2.0f)));
            }
        }
        for (int index = 0; index < middleSegments; index++) {
            graphics.pose().pushPose();
            float segmentRotation = index * 4.5f + rotation * 0.5f;
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(segmentRotation)));
            blitNative(
                    graphics,
                    SCAN_RING_MIDDLE,
                    -(SCAN_RING_MIDDLE_WIDTH / 2.0f),
                    -(SCAN_RING_MIDDLE_HEIGHT / 2.0f),
                    SCAN_RING_MIDDLE_WIDTH,
                    SCAN_RING_MIDDLE_HEIGHT,
                    0,
                    0,
                    SCAN_RING_MIDDLE_WIDTH,
                    SCAN_RING_MIDDLE_HEIGHT,
                    progressOpacity
            );
            graphics.pose().popPose();
        }

        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-innerRotation)));
        blitNative(
                graphics,
                SCAN_RING_INNER,
                -(SCAN_RING_INNER_DIAMETER / 2.0f),
                -(SCAN_RING_INNER_DIAMETER / 2.0f),
                SCAN_RING_INNER_DIAMETER,
                SCAN_RING_INNER_DIAMETER,
                0,
                0,
                SCAN_RING_INNER_DIAMETER,
                SCAN_RING_INNER_DIAMETER,
                ringAlpha
        );
        graphics.pose().popPose();

        drawCooldownRing(graphics);
        if (pulseActive) {
            float sweepRotation = config.auraReaderReducedMotion()
                    ? -90.0f
                    : pulseProgress * 360.0f - 90.0f;
            graphics.pose().pushPose();
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(sweepRotation)));
            fill(
                    graphics,
                    -1,
                    -SCAN_RING_INNER_DIAMETER / 2,
                    1,
                    2,
                    AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.PULSE, 0.58f)
            );
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }

    private static void drawCooldownRing(GuiGraphics graphics) {
        float readyFraction = 1.0f - AuraReaderClientState.getPulseCooldownFraction();
        float chargeFraction = AuraReaderClientState.getChargeFraction();
        int litSegments = Math.round(24 * readyFraction);
        for (int index = 0; index < 24; index++) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(index * 15.0f)));
            int color;
            if (chargeFraction < 0.2f) {
                color = index < litSegments
                        ? AuraReaderHudTheme.TEXT_WARNING
                        : AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.TEXT_WARNING, 0.20f);
            } else {
                color = index < litSegments
                        ? AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN, 0.72f)
                        : AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN_DIM, 0.24f);
            }
            fill(graphics, -1, -57, 1, -53, color);
            graphics.pose().popPose();
        }
    }

    private static void drawSignalPopover(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect,
            AuraReaderClientState.ReadingSnapshot tracked) {
        if (tracked == null || rect.width() < 48 || rect.height() < 20) {
            return;
        }

        drawBeveledPanel(graphics, rect, AuraReaderHudTheme.TRACKED, AuraReaderHudTheme.PANEL_BACKGROUND);
        drawPanelHeader(
                graphics,
                minecraft,
                rect,
                translated("hud.shadowedhearts.aura_reader.panel.signal"),
                AuraReaderHudTheme.TRACKED
        );

        String firstLine = tracked.label().isBlank()
                ? prettyType(tracked.type())
                : tracked.label();
        String secondLine = joinSignalDetails(tracked);
        drawCenteredHudString(
                graphics,
                minecraft,
                trimToWidth(minecraft, firstLine, rect.width() - 16),
                centerX(rect),
                rect.top() + Math.max(13, rect.height() / 2 - 4),
                AuraReaderHudTheme.TEXT_PRIMARY
        );
        if (rect.height() >= 34) {
            drawCenteredHudString(
                    graphics,
                    minecraft,
                    trimToWidth(minecraft, secondLine, rect.width() - 16),
                    centerX(rect),
                    rect.bottom() - 11,
                    AuraReaderHudTheme.TEXT_SECONDARY
            );
        }
    }

    private static void drawContextPanels(
            GuiGraphics graphics,
            Minecraft minecraft,
            int viewportWidth,
            int viewportHeight,
            int scannerCenterX,
            int scannerCenterY,
            float scannerScale,
            AuraReaderHudLayoutResolver.Layout layout,
            AuraReaderClientState.ReadingSnapshot focused) {
        if (focused == null || !(scannerScale > 0.0f) || !Float.isFinite(scannerScale)) {
            return;
        }

        AuraReaderScannerInfoFrameLayout.Layout frameLayout =
                AuraReaderScannerInfoFrameLayout.resolve(
                        new AuraReaderScannerInfoFrameLayout.Request(
                                viewportWidth,
                                viewportHeight,
                                scannerCenterX,
                                scannerCenterY,
                                scannerScale,
                                layout.partySafeZone(),
                                layout.bottomSafeZone()
                        )
                );

        graphics.pose().pushPose();
        graphics.pose().translate(scannerCenterX, scannerCenterY, 0.0f);
        graphics.pose().scale(scannerScale, scannerScale, 1.0f);
        frameLayout.auraFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                minecraft,
                frame,
                translated("hud.shadowedhearts.aura_reader.panel.aura"),
                auraStatus(focused)
        ));
        frameLayout.targetFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                minecraft,
                frame,
                translated("hud.shadowedhearts.aura_reader.panel.target"),
                focused.label().isBlank() ? prettyType(focused.type()) : focused.label()
        ));
        graphics.pose().popPose();
    }

    private static void drawScannerContextFrame(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderScannerInfoFrameLayout.FramePlacement frame,
            String title,
            String value) {
        AuraReaderScannerInfoFrameLayout.NativeGeometry geometry = frame.geometry();
        int step = Math.max(
                0,
                Math.min(
                        SCANNER_INFO_FRAME_STEPS - 1,
                        (int) Math.ceil(
                                clamp01(AuraReaderClientState.getReticleScanProgress() / 100.0f)
                                        * (SCANNER_INFO_FRAME_STEPS - 1)
                        )
                )
        );
        int frameHeight = geometry.height();
        blitNative(
                graphics,
                infoFrameResource(
                        frame.side() == AuraReaderScannerInfoFrameLayout.Side.LEFT,
                        frame.tier()
                ),
                geometry.xOffset(),
                geometry.yOffset(),
                geometry.width(),
                frameHeight,
                0,
                frameHeight * step,
                geometry.width(),
                frameHeight * SCANNER_INFO_FRAME_STEPS,
                0.96f
        );

        if (!AuraReaderClientState.shouldShowPanelText()) {
            return;
        }

        int textWidth = (frame.tier() == 1 || frame.tier() == 2)
                ? AuraReaderScannerInfoFrameLayout.INNER_FRAME_WIDTH
                        - AuraReaderScannerInfoFrameLayout.INNER_FRAME_STEM_WIDTH
                        - 12
                : geometry.width() - 12;
        int textCenterX = geometry.textCenterXOffset();
        int titleY = scannerFrameTitleY(geometry);
        int valueY = scannerFrameValueY(geometry);
        drawCenteredHudString(
                graphics,
                minecraft,
                trimToWidth(minecraft, title, textWidth),
                textCenterX,
                titleY,
                AuraReaderHudTheme.TEXT_SECONDARY
        );
        drawCenteredHudString(
                graphics,
                minecraft,
                trimToWidth(minecraft, value, textWidth),
                textCenterX,
                valueY,
                AuraReaderHudTheme.TEXT_PRIMARY
        );
    }

    private static int scannerFrameTitleY(
            AuraReaderScannerInfoFrameLayout.NativeGeometry geometry) {
        if (geometry.tier() == 1 || geometry.tier() == 2) {
            return geometry.yOffset() + 3;
        }
        return geometry.yOffset() + (geometry.tier() == 3 ? 31 : 6);
    }

    private static int scannerFrameValueY(
            AuraReaderScannerInfoFrameLayout.NativeGeometry geometry) {
        if (geometry.tier() == 1 || geometry.tier() == 2) {
            return geometry.yOffset() + 11;
        }
        return geometry.yOffset() + (geometry.tier() == 3 ? 42 : 18);
    }

    private static void drawPanelHeader(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect,
            String title,
            int color) {
        int titleWidth = hudTextWidth(minecraft, title);
        int center = centerX(rect);
        int y = rect.top() + 6;
        fill(graphics, rect.left() + 9, y, center - titleWidth / 2 - 5, y + 1, AuraReaderHudTheme.multiplyAlpha(color, 0.65f));
        fill(graphics, center + titleWidth / 2 + 5, y, rect.right() - 9, y + 1, AuraReaderHudTheme.multiplyAlpha(color, 0.65f));
        drawCenteredHudString(graphics, minecraft, title, center, rect.top() + 2, AuraReaderHudTheme.TEXT_SECONDARY);
    }

    private static ResourceLocation infoFrameResource(boolean leftSide, int tier) {
        return cobblemon(
                "textures/gui/pokedex/scan/scan_info_frame_"
                        + (leftSide ? "left" : "right")
                        + "_"
                        + tier
                        + ".png"
        );
    }

    private static void drawBeveledPanel(
            GuiGraphics graphics,
            AuraReaderHudRect rect,
            int borderColor,
            int backgroundColor) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        int bevel = Math.min(5, Math.min(rect.width(), rect.height()) / 4);
        fill(graphics, rect.left() + bevel, rect.top(), rect.right() - bevel, rect.bottom(), backgroundColor);
        fill(graphics, rect.left(), rect.top() + bevel, rect.right(), rect.bottom() - bevel, backgroundColor);

        fill(graphics, rect.left() + bevel, rect.top(), rect.right() - bevel, rect.top() + 1, borderColor);
        fill(graphics, rect.left() + bevel, rect.bottom() - 1, rect.right() - bevel, rect.bottom(), borderColor);
        fill(graphics, rect.left(), rect.top() + bevel, rect.left() + 1, rect.bottom() - bevel, borderColor);
        fill(graphics, rect.right() - 1, rect.top() + bevel, rect.right(), rect.bottom() - bevel, borderColor);
        for (int offset = 0; offset < bevel; offset++) {
            fill(graphics, rect.left() + offset, rect.top() + bevel - offset - 1, rect.left() + offset + 1, rect.top() + bevel - offset, borderColor);
            fill(graphics, rect.right() - offset - 1, rect.top() + bevel - offset - 1, rect.right() - offset, rect.top() + bevel - offset, borderColor);
            fill(graphics, rect.left() + offset, rect.bottom() - bevel + offset, rect.left() + offset + 1, rect.bottom() - bevel + offset + 1, borderColor);
            fill(graphics, rect.right() - offset - 1, rect.bottom() - bevel + offset, rect.right() - offset, rect.bottom() - bevel + offset + 1, borderColor);
        }
    }

    private static void drawReadingGlyph(
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

    private static void drawDiamond(
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

    private static void drawSquare(
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

    private static void drawUpTriangle(
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

    private static void drawDownTriangle(
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

    private static void drawHorizontalChevron(
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

    private static void drawBrackets(
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

    private static void drawX(
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

    private static void drawPlus(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            int color) {
        fill(graphics, x - size, y, x + size + 1, y + 1, color);
        fill(graphics, x, y - size, x + 1, y + size + 1, color);
    }

    private static void drawAxisLine(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color) {
        if (x1 == x2) {
            fill(graphics, x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
        } else if (y1 == y2) {
            fill(graphics, Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
        }
    }

    private static List<AuraReaderClientState.ReadingSnapshot> selectReadings(
            List<AuraReaderClientState.ReadingSnapshot> readings,
            int maximum,
            AuraReaderClientState.ReadingSnapshot focused,
            AuraReaderHudLayoutPreset preset) {
        if (readings == null || readings.isEmpty()) {
            return List.of();
        }

        ArrayList<AuraReaderClientState.ReadingSnapshot> sorted = new ArrayList<>();
        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            if (preset != AuraReaderHudLayoutPreset.MINIMAL || isTrackedOrLocked(reading)) {
                sorted.add(reading);
            }
        }
        sorted.sort(
                Comparator
                        .comparing((AuraReaderClientState.ReadingSnapshot reading) -> !isTrackedOrLocked(reading))
                        .thenComparing(reading -> focused == null || !focused.id().equals(reading.id()))
                        .thenComparing(Comparator.comparingInt(AuraReaderClientState.ReadingSnapshot::priority).reversed())
                        .thenComparing(Comparator.comparingDouble(AuraReaderClientState.ReadingSnapshot::strength).reversed())
                        .thenComparing(AuraReaderClientState.ReadingSnapshot::id)
        );
        return List.copyOf(sorted.subList(0, Math.min(Math.max(1, maximum), sorted.size())));
    }

    private static int visiblePartyOverlayWidth() {
        if (!PartyOverlay.Companion.canRender()) {
            return 0;
        }
        return CobblemonClient.INSTANCE.getStorage().getParty().getSlots().stream()
                .anyMatch(java.util.Objects::nonNull)
                ? PARTY_OVERLAY_SAFE_WIDTH
                : 0;
    }

    private static AuraReaderClientState.ReadingSnapshot getFocusedReading(
            List<AuraReaderClientState.ReadingSnapshot> readings,
            float playerYaw) {
        AuraReaderClientState.ReadingSnapshot closest = null;
        float closestDelta = SOFT_FOCUS_DEGREES;
        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            float readingYaw = AuraReaderCompassMath.worldYaw(reading.directionX(), reading.directionZ());
            float delta = Math.abs(AuraReaderCompassMath.signedDelta(playerYaw, readingYaw));
            if (delta <= closestDelta) {
                closestDelta = delta;
                closest = reading;
            }
        }
        return closest;
    }

    private static AuraReaderClientState.ReadingSnapshot getTrackedReading(
            List<AuraReaderClientState.ReadingSnapshot> readings) {
        String selectedSignal = AuraReaderClientState.getSelectedSignal();
        if (!AuraReaderClientState.isTracking()
                && (selectedSignal == null || selectedSignal.isBlank())) {
            return null;
        }
        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            if (selectedSignal != null
                    && !selectedSignal.isBlank()
                    && selectedSignal.equals(reading.id())) {
                return reading;
            }
        }
        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            if (reading.selected()
                    && (reading.source() == AuraReadingSource.TRACKED
                    || reading.source() == AuraReadingSource.LOCKED)) {
                return reading;
            }
        }
        return null;
    }

    private static boolean isTrackedOrLocked(AuraReaderClientState.ReadingSnapshot reading) {
        return reading.selected()
                || reading.locked()
                || reading.source() == AuraReadingSource.TRACKED
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

    private static String modeLabel(AuraReaderMode mode) {
        String key = switch (mode) {
            case PASSIVE_AURA -> "hud.shadowedhearts.aura_reader.mode.aura";
            case SIGNAL_TRACKING -> "hud.shadowedhearts.aura_reader.mode.track";
            case PULSE_SCAN, ENTITY_ANALYSIS -> "hud.shadowedhearts.aura_reader.mode.analyze";
            case ANOMALY_SCAN -> "hud.shadowedhearts.aura_reader.mode.anomaly";
        };
        return translated(key);
    }

    private static String cardinalLabel(int heading) {
        int normalized = Math.floorMod(heading, 360);
        if (normalized < 23 || normalized >= 338) {
            return "S";
        }
        if (normalized < 68) {
            return "SW";
        }
        if (normalized < 113) {
            return "W";
        }
        if (normalized < 158) {
            return "NW";
        }
        if (normalized < 203) {
            return "N";
        }
        if (normalized < 248) {
            return "NE";
        }
        if (normalized < 293) {
            return "E";
        }
        return "SE";
    }

    private static String joinSignalDetails(AuraReaderClientState.ReadingSnapshot reading) {
        List<String> parts = new ArrayList<>(3);
        if (!reading.distanceBand().isBlank()) {
            parts.add(reading.distanceBand().toUpperCase(Locale.ROOT));
        }
        if (!reading.verticalHint().isBlank() && !"level".equalsIgnoreCase(reading.verticalHint())) {
            parts.add(verticalLabel(reading.verticalHint()));
        }
        parts.add(stabilityLabel(reading));
        return String.join("  \u2022  ", parts);
    }

    private static String verticalLabel(String verticalHint) {
        if ("above".equalsIgnoreCase(verticalHint)) {
            return translated("hud.shadowedhearts.aura_reader.status.above");
        }
        if ("below".equalsIgnoreCase(verticalHint)) {
            return translated("hud.shadowedhearts.aura_reader.status.below");
        }
        return translated("hud.shadowedhearts.aura_reader.status.level");
    }

    private static String auraStatus(AuraReaderClientState.ReadingSnapshot reading) {
        if (reading.outOfDimension()) {
            return translated("hud.shadowedhearts.aura_reader.status.out_of_phase");
        }
        if (!reading.status().isBlank()) {
            return reading.status();
        }
        if (reading.locked() || reading.source() == AuraReadingSource.LOCKED) {
            return translated("hud.shadowedhearts.aura_reader.status.locked");
        }
        return prettyType(reading.type());
    }

    private static String stabilityLabel(AuraReaderClientState.ReadingSnapshot reading) {
        boolean unstable = reading.confidence() < 0.55f || reading.interference() > 0.45f;
        return translated(unstable
                ? "hud.shadowedhearts.aura_reader.status.unstable"
                : "hud.shadowedhearts.aura_reader.status.stable");
    }

    private static int stabilityColor(AuraReaderClientState.ReadingSnapshot reading) {
        return reading.confidence() < 0.55f || reading.interference() > 0.45f
                ? AuraReaderHudTheme.TEXT_WARNING
                : AuraReaderHudTheme.CYAN_BRIGHT;
    }

    private static String prettyType(AuraReadingType type) {
        String normalized = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (capitalize && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
            if (character == ' ') {
                capitalize = true;
            }
        }
        return result.toString();
    }

    private static int chargeColor(float fraction) {
        if (fraction < 0.2f) {
            return AuraReaderHudTheme.TEXT_DANGER;
        }
        if (fraction < 0.5f) {
            return AuraReaderHudTheme.TEXT_WARNING;
        }
        return AuraReaderHudTheme.CYAN;
    }

    private static int chargeTextColor(float fraction) {
        return chargeColor(fraction);
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    private static String trimToWidth(Minecraft minecraft, String value, int maxWidth) {
        String safeValue = value == null ? "" : value;
        if (maxWidth <= 0 || hudTextWidth(minecraft, safeValue) <= maxWidth) {
            return maxWidth <= 0 ? "" : safeValue;
        }

        String ellipsis = "...";
        int end = safeValue.length();
        while (end > 0
                && hudTextWidth(minecraft, safeValue.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : safeValue.substring(0, end) + ellipsis;
    }

    private static int hudTextWidth(Minecraft minecraft, String value) {
        return minecraft.font.width(hudText(value));
    }

    private static void drawCenteredHudString(
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

    private static void drawCenteredCompassDirection(
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

    private static void drawHudString(
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

    private static int centerX(AuraReaderHudRect rect) {
        return rect.left() + rect.width() / 2;
    }

    private static int centerY(AuraReaderHudRect rect) {
        return rect.top() + rect.height() / 2;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void fill(
            GuiGraphics graphics,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int color) {
        if (maxX <= minX || maxY <= minY) {
            return;
        }

        int adjustedColor = AuraReaderHudTheme.multiplyAlpha(color, frameAlpha);
        float alpha = FastColor.ARGB32.alpha(adjustedColor) / 255.0f;
        float red = FastColor.ARGB32.red(adjustedColor) / 255.0f;
        float green = FastColor.ARGB32.green(adjustedColor) / 255.0f;
        float blue = FastColor.ARGB32.blue(adjustedColor) / 255.0f;
        GuiUtilsKt.blitk(
                graphics.pose(),
                CobblemonResources.INSTANCE.getWHITE(),
                minX,
                minY,
                maxY - minY,
                maxX - minX,
                0,
                0,
                1,
                1,
                0,
                red,
                green,
                blue,
                alpha,
                true,
                1.0f
        );
    }

    private static void blit(
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
        if (width <= 0 || height <= 0) {
            return;
        }
        if (regionWidth <= 0 || regionHeight <= 0) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(
                width / (float) regionWidth,
                height / (float) regionHeight,
                1.0f
        );
        GuiUtilsKt.blitk(
                graphics.pose(),
                texture,
                0,
                0,
                regionHeight,
                regionWidth,
                uOffset,
                vOffset,
                textureWidth,
                textureHeight,
                0,
                1.0f,
                1.0f,
                1.0f,
                clamp01(alpha * frameAlpha),
                true,
                1.0f
        );
        graphics.pose().popPose();
    }

    /**
     * Draws a native-sized texture region while preserving fractional scanner
     * coordinates. Any visual resizing belongs on the surrounding pose so the
     * texture's UV region and its geometry always stay in lockstep, matching
     * Cobblemon's scanner renderer contract.
     */
    private static void blitNative(
            GuiGraphics graphics,
            ResourceLocation texture,
            float x,
            float y,
            float width,
            float height,
            int uOffset,
            int vOffset,
            int textureWidth,
            int textureHeight,
            float alpha) {
        if (!(width > 0.0f) || !(height > 0.0f)) {
            return;
        }
        GuiUtilsKt.blitk(
                graphics.pose(),
                texture,
                x,
                y,
                height,
                width,
                uOffset,
                vOffset,
                textureWidth,
                textureHeight,
                0,
                1.0f,
                1.0f,
                1.0f,
                clamp01(alpha * frameAlpha),
                true,
                1.0f
        );
    }

    private record MarkerPresentation(
            AuraReaderClientState.ReadingSnapshot reading,
            float normalizedX) {
    }
}
