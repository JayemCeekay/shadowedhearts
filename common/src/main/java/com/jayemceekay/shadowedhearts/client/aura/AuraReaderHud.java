package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundHudPresentation;
import com.jayemceekay.shadowedhearts.common.aura.AuraHeartGaugeReadout;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderTargeting;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.content.umbrafall.AuraReaderSearchProgress;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
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
import java.util.Objects;

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
    private static final int SCAN_RING_OUTER_DIAMETER = AuraReaderHudGeometry.SCANNER_RETICLE_DIAMETER;
    private static final int SCAN_RING_INNER_DIAMETER = 84;
    private static final int COMPASS_DIRECTION_BOX_SIZE = 32;
    private static final int COMPASS_CURVE_SEGMENTS = 64;
    private static final float COMPASS_CURVE_RISE_FACTOR = 0.70f;
    private static final float COMPASS_BOTTOM_CURVE_FACTOR = 0.45f;
    private static final float COMPASS_CENTER_THICKNESS_FACTOR = 1.40f;
    private static final float COMPASS_END_THICKNESS_FACTOR = 0.25f;
    private static final float COMPASS_EDGE_FADE_START = 0.72f;
    private static final int SCANNER_PANEL_BORDER = 0xE027ECFF;
    private static final int SCANNER_PANEL_FILL = 0x6627ECFF;
    private static final float SCANNER_ARM_OPEN_FRACTION = 4.0f / 9.0f;
    private static final int READOUT_TEXT_HEIGHT = 8;
    private static final int SIGNAL_FRAME_VERTICAL_INSET = 2;
    private static final int PARTY_OVERLAY_SAFE_WIDTH = 72;
    private static final float MINIMUM_TEXT_SCALE = AuraReaderHudGeometry.MIN_READOUT_SCALE;
    private static final ResourceLocation HUD_FONT = ResourceLocation.parse("uniform");
    private static final ResourceLocation COMPASS_DIRECTION_FONT =
            CobblemonResources.INSTANCE.getDEFAULT_LARGE();
    private static final ResourceLocation SCANNER_TEXT_FONT =
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
    private static final ResourceLocation COMPASS_DIRECTION_BOX = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/gui/aura_reader/compass_direction_box.png"
    );
    private static final ResourceLocation SCANNER_BRACKETS = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts", "textures/gui/aura_reader/scanner_brackets.png");

    private static float frameAlpha = 1.0f;
    private static final List<AuraReaderHudRect> occupiedAreas = new ArrayList<>();
    private static String panelLayoutTargetId;
    private static AuraReaderScannerInfoFrameLayout.Request panelLayoutRequest;
    private static AuraReaderScannerInfoFrameLayout.Layout panelLayout;
    private static ScannerArmLayoutKey panelArmLayoutKey;
    private static List<AuraReaderScannerArmLayout.Arm> panelArms = List.of();
    private static List<AuraReaderHudRect> panelArmQuads = List.of();

    private AuraReaderHud() {
    }

    private static ResourceLocation cobblemon(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
    }

    public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        occupiedAreas.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        IClientConfig config = ShadowedHeartsConfigs.getInstance().getClientConfig();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        long ticks = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        float playerYaw = minecraft.player.getViewYRot(1.0f);
        List<AuraReaderClientState.ReadingSnapshot> readings = AuraReaderClientState.getReadings();
        AuraReaderSearchProgress search = AuraReaderSearchClientState.getProgress(minecraft.player.tickCount);
        var reticleTarget = AuraReaderClientState.isActive() && !readings.isEmpty()
                ? AuraReaderTargeting.pickEntity(
                        minecraft.level, minecraft.getCameraEntity(), tickCounter.getGameTimeDeltaPartialTick(false),
                        AuraReaderClientState.getScanRange())
                : null;
        AuraReaderClientState.ReadingSnapshot observedFocus = getFocusedReading(
                readings, reticleTarget == null ? null : reticleTarget.getUUID().toString());
        AuraReaderClientState.ReadingSnapshot tracked = getTrackedReading(readings);

        List<AuraReaderClientState.ReadingSnapshot> displayedReadings = selectReadings(
                readings,
                observedFocus
        );
        float elapsedSeconds = minecraft.isPaused() ? 0.0f : tickCounter.getRealtimeDeltaTicks() / 20.0f;
        AuraReaderClientState.updateHudAnimations(observedFocus, tracked, elapsedSeconds);
        AuraReaderClientState.ReadingSnapshot focused = AuraReaderClientState.getFocusedReading();
        float activationProgress = AuraReaderClientState.getActivationProgress();
        boolean visorVisible = activationProgress > 0.0f || AuraReaderClientState.isActive();
        if (!visorVisible && search == null) {
            return;
        }
        boolean compactSignal = tracked == null
                && (search == null || AuraReaderSearchClientState.isCollapsed());
        AuraReaderHudGeometry.Geometry layout = resolveGeometry(width, height, config, compactSignal);

        frameAlpha = clamp01((visorVisible ? activationProgress : 1.0f) * config.auraReaderHudOpacity());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (!visorVisible) {
            //drawReaderCap(graphics, minecraft, layout.modeCap(), search);
            if (!AuraReaderSearchClientState.isCollapsed()) {
                drawSearchPopover(graphics, minecraft, layout.signalPopover(), search, config.auraReaderReducedMotion());
                occupiedAreas.add(layout.signalPopover());
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            frameAlpha = 1.0f;
            return;
        }

        float panOffset = config.auraReaderReducedMotion()
                ? 0.0f
                : (float) (-Math.pow(1.0f - activationProgress, 2.0f) * height);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, panOffset, 0.0f);

        drawScreenWash(graphics, width, height, ticks, config);
        drawScannerBorder(graphics, width, height);
        drawCompass(graphics, minecraft, layout, displayedReadings, focused, playerYaw, ticks, search, config);
        float scannerScale = Math.min(layout.reticle().width(), layout.reticle().height())
                / (float) SCAN_RING_OUTER_DIAMETER;
        int scannerCenterX = width / 2;
        int scannerCenterY = height / 2;
        drawContextPanels(
                    graphics,
                    minecraft,
                    width,
                    height,
                    scannerCenterX,
                    scannerCenterY,
                    scannerScale,
                    layout,
                    AuraReaderClientState.getPanelReading(),
                    config.auraReaderReducedMotion()
            );

        drawReticle(
                graphics,
                scannerCenterX,
                scannerCenterY,
                scannerScale,
                focused,
                config
        );

        // Keep text in front of the ring when a short viewport needs overlap.
        if (search != null && !AuraReaderSearchClientState.isCollapsed()) {
            drawSearchPopover(graphics, minecraft, layout.signalPopover(), search, config.auraReaderReducedMotion());
        } else {
            drawSignalPopover(graphics, minecraft, layout.signalPopover(), tracked);
        }

        graphics.pose().popPose();
        // Other field readouts share the scanner's actual visible reservations.
        int panY = Math.round(panOffset);
        var areas = new ArrayList<>(scannerReservedZones(layout));
        areas.add(layout.reticle());
        if (panelLayout != null) panelLayout.visibleFrames().forEach(frame -> areas.add(frame.bounds()));
        for (var area : areas) {
            occupiedAreas.add(new AuraReaderHudRect(area.left(), area.top() + panY,
                    area.right(), area.bottom() + panY));
        }
        // Reserve one envelope per connector, not hundreds of its raster pixels.
        for (var arm : panelArms) {
            if (arm.pixels().isEmpty()) continue;
            int left = arm.pixels().stream().mapToInt(AuraReaderHudRect::left).min().orElseThrow();
            int top = arm.pixels().stream().mapToInt(AuraReaderHudRect::top).min().orElseThrow();
            int right = arm.pixels().stream().mapToInt(AuraReaderHudRect::right).max().orElseThrow();
            int bottom = arm.pixels().stream().mapToInt(AuraReaderHudRect::bottom).max().orElseThrow();
            occupiedAreas.add(new AuraReaderHudRect(
                    (int) Math.floor(scannerCenterX + left * scannerScale),
                    (int) Math.floor(scannerCenterY + top * scannerScale + panOffset),
                    (int) Math.ceil(scannerCenterX + right * scannerScale),
                    (int) Math.ceil(scannerCenterY + bottom * scannerScale + panOffset)));
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        frameAlpha = 1.0f;
    }

    /** Both loaders render other field readouts after this visor, in the same frame. */
    public static List<AuraReaderHudRect> occupiedAreas(int width, int height, IClientConfig config) {
        var layout = resolveGeometry(width, height, config);
        var areas = new ArrayList<>(occupiedAreas);
        areas.add(layout.partySafeZone());
        areas.add(layout.bottomSafeZone());
        return List.copyOf(areas);
    }

    /** The same bracket texture, font and two-line readout used by the Reader, without requiring its visor. */
    public static void renderMoonwoundInfoBox(GuiGraphics graphics, AuraReaderHudRect rect,
                                             MoonwoundSnapshot snapshot, float opacity) {
        float previousAlpha = frameAlpha;
        frameAlpha = clamp01(opacity);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        try {
            drawMoonwoundPopover(graphics, Minecraft.getInstance(), rect, snapshot);
        } finally {
            frameAlpha = previousAlpha;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }

    public static int moonwoundInfoBoxWidth(MoonwoundSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.max(scannerTextWidth(minecraft, MoonwoundHudPresentation.title(snapshot)),
                scannerTextWidth(minecraft, MoonwoundHudPresentation.detail(snapshot))) + 17;
    }

    /** Tutorial arrows use the same scale, offsets, and overlay clearances as the live visor. */
    public static AuraReaderHudGeometry.Geometry getTutorialGeometry(int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        IClientConfig config = ShadowedHeartsConfigs.getInstance().getClientConfig();
        var search = minecraft.player == null ? null
                : AuraReaderSearchClientState.getProgress(minecraft.player.tickCount);
        boolean compactSignal = getTrackedReading(AuraReaderClientState.getReadings()) == null
                && (search == null || AuraReaderSearchClientState.isCollapsed());
        return resolveGeometry(width, height, config, compactSignal);
    }

    private static AuraReaderHudGeometry.Geometry resolveGeometry(
            int width, int height, IClientConfig config) {
        return resolveGeometry(width, height, config, false);
    }

    private static AuraReaderHudGeometry.Geometry resolveGeometry(
            int width, int height, IClientConfig config, boolean compactSignal) {
        return AuraReaderHudGeometry.resolve(new AuraReaderHudGeometry.Request(
                width, height, config.auraReaderHudScale(), config.auraReaderCompassWidth(),
                config.auraReaderTopInset(), config.auraReaderReticleScale(),
                config.auraReaderBottomSafeMargin(), visiblePartyOverlayWidth(),
                config.auraReaderPartyOverlayMargin(),
                config.auraReaderRightOverlayMargin(), compactSignal));
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
            AuraReaderHudGeometry.Geometry layout,
            List<AuraReaderClientState.ReadingSnapshot> allReadings,
            AuraReaderClientState.ReadingSnapshot focused,
            float playerYaw,
            long ticks,
            AuraReaderSearchProgress search,
            IClientConfig config) {
        AuraReaderHudRect rail = layout.compassRail();
        int railY = centerY(rail);
        int railCenterX = centerX(rail);
        int halfWidth = Math.max(1, rail.width() / 2);
        int curveRise = Math.max(1, Math.round(rail.height() * COMPASS_CURVE_RISE_FACTOR));
        float visibleArc = Math.max(30.0f, Math.min(180.0f, config.auraReaderCompassVisibleArc()));

        drawCurvedCompassRail(graphics, rail, railY, railCenterX, halfWidth, curveRise);
        //drawReaderCap(graphics, minecraft, layout.modeCap(), search);

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

    private static void drawReaderCap(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect,
            AuraReaderSearchProgress search) {
        if (!canDrawReadout(rect, AuraReaderHudGeometry.MODE_CAP_HEIGHT)) {
            return;
        }
        AuraReaderHudRect local = beginReadout(graphics, rect, AuraReaderHudGeometry.MODE_CAP_HEIGHT);
        drawReadoutBrackets(graphics, local);
        if (search == null) {
            String title = fitScannerText(minecraft, translated("hud.shadowedhearts.aura_reader.title"), local.width() - 16);
            drawScannerString(graphics, title, centerX(local),
                    (local.height() - READOUT_TEXT_HEIGHT) / 2, AuraReaderHudTheme.TEXT_PRIMARY, true);
        } else {
            String phase = fitScannerText(minecraft, translated(search.translationKey()), local.width() - 12);
            drawScannerString(graphics, phase, centerX(local),
                    (local.height() - READOUT_TEXT_HEIGHT) / 2, AuraReaderHudTheme.TEXT_PRIMARY, true);
        }
        graphics.pose().popPose();
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

    private static void drawTexturedCompassMarker(
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

    private static void drawEdgeIndicator(
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

        graphics.pose().popPose();
    }

    private static void drawCooldownRing(GuiGraphics graphics) {
        float readyFraction = 1.0f - AuraReaderClientState.getPulseCooldownFraction();
        int litSegments = Math.round(24 * readyFraction);
        for (int index = 0; index < 24; index++) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(index * 15.0f)));
            int color = index < litSegments
                    ? AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN, 0.72f)
                    : AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.CYAN_DIM, 0.24f);
            fill(graphics, -1, -57, 1, -53, color);
            graphics.pose().popPose();
        }
    }

    private static void drawSignalPopover(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect,
            AuraReaderClientState.ReadingSnapshot tracked) {
        int designHeight = tracked == null ? AuraReaderHudGeometry.SIGNAL_STATUS_HEIGHT
                : AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT;
        if (!canDrawReadout(rect, designHeight)) {
            return;
        }

        AuraReaderHudRect local = beginReadout(graphics, rect, designHeight);
        boolean twoRows = local.height() >= 27;
        int textY = (local.height() - READOUT_TEXT_HEIGHT) / 2;
        int padding = 8;
        int valueWidth = Math.max(0, local.width() - padding * 2);
        if (valueWidth < scannerTextWidth(minecraft, "...") + 1) {
            graphics.pose().popPose();
            return;
        }
        // Trim frame padding without changing the readable text scale or layout clearance.
        int frameInset = twoRows ? SIGNAL_FRAME_VERTICAL_INSET : 0;
        drawScannerPanelBrackets(graphics, 0, frameInset, local.width(), local.height() - frameInset * 2, 0.96f);
        if (tracked == null) {
            int contactCount = AuraReaderClientState.getLastPulseContactCount();
            String resultKey = contactCount == 0 ? "hud.shadowedhearts.aura_reader.pulse.empty"
                    : contactCount == 1 ? "hud.shadowedhearts.aura_reader.pulse.one_contact"
                    : "hud.shadowedhearts.aura_reader.pulse.contacts";
            String status = AuraReaderClientState.hasRecentPulseResult()
                    ? Component.translatable(resultKey, contactCount).getString()
                    : translated(AuraReaderClientState.isSignalRecentlyLost()
                    ? "hud.shadowedhearts.aura_reader.status.signal_lost"
                    : AuraReaderClientState.getReadings().stream().anyMatch(reading ->
                            !reading.outOfDimension()
                                    && !AuraReaderClientState.isReadingStale(reading.id()))
                            ? "hud.shadowedhearts.aura_reader.pulse.select_hint"
                            : "hud.shadowedhearts.aura_reader.pulse.search_hint");
            drawScannerString(graphics, fitScannerText(minecraft, status, valueWidth), centerX(local), textY,
                    AuraReaderHudTheme.TEXT_SECONDARY, true);
            graphics.pose().popPose();
            return;
        }

        AuraReaderReadoutPresentation.SignalTitle title = AuraReaderReadoutPresentation.signalTitle(
                tracked.type(), tracked.labelText(), prettyType(tracked.type()));
        int strengthWidth = scannerTextWidth(minecraft, title.strength()) + 1;
        boolean showStrength = !title.strength().isEmpty()
                && valueWidth >= strengthWidth + 12 + Math.min(80, scannerTextWidth(minecraft, title.name()) + 1);
        int nameWidth = valueWidth - (showStrength ? strengthWidth + 12 : 0);
        String fittedName = fitScannerText(minecraft, title.name(), nameWidth);
        int fittedNameWidth = scannerTextWidth(minecraft, fittedName) + 1;
        int titleWidth = fittedNameWidth + (showStrength ? strengthWidth + 12 : 0);
        int titleLeft = (local.width() - titleWidth) / 2;
        String details = AuraReaderReadoutPresentation.fitDetails(
                "above".equalsIgnoreCase(tracked.verticalHint()) || "below".equalsIgnoreCase(tracked.verticalHint())
                        ? verticalLabel(tracked.verticalHint()) : "",
                distanceLabel(tracked.distanceBand()).toUpperCase(Locale.ROOT),
                stabilityLabel(tracked),
                twoRows ? valueWidth : 0,
                value -> scannerTextWidth(minecraft, value) + 1
        );
        int lineStep = READOUT_TEXT_HEIGHT + 4;
        int textBlockHeight = READOUT_TEXT_HEIGHT + (details.isEmpty() ? 0 : lineStep);
        textY = (local.height() - textBlockHeight) / 2;
        drawScannerString(graphics, fittedName, titleLeft, textY,
                AuraReaderHudTheme.TEXT_PRIMARY, false);
        if (showStrength) {
            int strengthX = titleLeft + fittedNameWidth + 12;
            fill(graphics, strengthX - 7, textY, strengthX - 6, textY + READOUT_TEXT_HEIGHT,
                    AuraReaderHudTheme.CYAN_DIM);
            drawScannerString(graphics, title.strength(), strengthX, textY,
                    AuraReaderHudTheme.TEXT_SECONDARY, false);
        }
        if (!details.isEmpty()) {
            drawScannerString(graphics, details, centerX(local), textY + lineStep,
                    AuraReaderHudTheme.TEXT_SECONDARY, true);
        }
        graphics.pose().popPose();
    }

    private static boolean drawMoonwoundPopover(
            GuiGraphics graphics, Minecraft minecraft, AuraReaderHudRect rect, MoonwoundSnapshot snapshot) {
        int designHeight = AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT;
        if (!canDrawReadout(rect, designHeight)) return false;
        AuraReaderHudRect local = beginReadout(graphics, rect, designHeight);
        int usableWidth = Math.max(0, local.width() - 16);
        if (local.height() < 27 || usableWidth < scannerTextWidth(minecraft, Component.translatable("hud.shadowedhearts.aura_reader.type.moonwound").getString()) + 1) {
            graphics.pose().popPose();
            return false;
        }
        drawScannerPanelBrackets(graphics, 0, SIGNAL_FRAME_VERTICAL_INSET,
                local.width(), local.height() - SIGNAL_FRAME_VERTICAL_INSET * 2, 0.96f);
        int lineStep = READOUT_TEXT_HEIGHT + 4;
        int textY = (local.height() - READOUT_TEXT_HEIGHT - lineStep) / 2;
        drawScannerString(graphics, fitScannerText(minecraft, MoonwoundHudPresentation.title(snapshot), usableWidth),
                centerX(local), textY, AuraReaderHudTheme.TEXT_PRIMARY, true);
        drawScannerString(graphics, fitScannerText(minecraft, MoonwoundHudPresentation.detail(snapshot), usableWidth),
                centerX(local), textY + lineStep, AuraReaderHudTheme.TEXT_SECONDARY, true);
        graphics.pose().popPose();
        return true;
    }

    private static void drawSearchPopover(
            GuiGraphics graphics, Minecraft minecraft, AuraReaderHudRect rect,
            AuraReaderSearchProgress search, boolean reducedMotion) {
        if (search == null || !canDrawReadout(rect, AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT)) return;
        AuraReaderHudRect local = beginReadout(graphics, rect, AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT);
        drawScannerPanelBrackets(graphics, 0, 0, local.width(), local.height(), 0.96f);
        int padding = 8;
        int usableWidth = Math.max(0, local.width() - padding * 2);
        boolean full = local.height() >= AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT;
        int lineStep = READOUT_TEXT_HEIGHT + 2;
        int textBlockHeight = READOUT_TEXT_HEIGHT + (full ? lineStep * 2 : 0);
        int textY = (local.height() - textBlockHeight) / 2;
        String status = translated(search.translationKey());
        if (full) {
            String title = translated("item.shadowedhearts.shadow_signal_data_"
                    + search.tier().name().toLowerCase(Locale.ROOT));
            drawScannerString(graphics, fitScannerText(minecraft, title, usableWidth), centerX(local), textY,
                    AuraReaderHudTheme.TEXT_SECONDARY, true);
        }
        drawScannerString(graphics, fitScannerText(minecraft, status, usableWidth), centerX(local),
                full ? textY + lineStep : textY, AuraReaderHudTheme.TEXT_PRIMARY, true);
        if (full) {
            String detail = search.dataRetained() ? translated("hud.shadowedhearts.aura_reader.search.retained")
                    : switch (search.stage()) {
                        case SEARCHING, SURVEYING, CHECKING -> translated("hud.shadowedhearts.aura_reader.search.keep_in_hand");
                        case READY -> translated("hud.shadowedhearts.aura_reader.search.follow");
                        case READY_ELSEWHERE -> translated("message.shadowedhearts.aura_reader.signal.ready_elsewhere");
                        case WAITING -> translated(search.waitTranslationKey());
                        case PAUSED -> translated("hud.shadowedhearts.aura_reader.search.operator");
                        case RELEASED -> translated("hud.shadowedhearts.aura_reader.search.released");
                        default -> search.phasePercent() >= 0
                                ? Component.translatable("hud.shadowedhearts.aura_reader.search.phase_progress", search.phasePercent()).getString() : "";
                    };
            drawScannerString(graphics, fitScannerText(minecraft, detail, usableWidth), centerX(local), textY + lineStep * 2,
                    AuraReaderHudTheme.TEXT_SECONDARY, true);
            if (!search.stage().terminal()) {
                int meterY = local.bottom() - 2;
                fill(graphics, padding, meterY, local.right() - padding, meterY + 1, AuraReaderHudTheme.METER_TRACK);
                if (search.phasePercent() >= 0) {
                    fill(graphics, padding, meterY, padding + usableWidth * search.phasePercent() / 100,
                            meterY + 1, AuraReaderHudTheme.CYAN_BRIGHT);
                } else if (search.stage().preflight()) {
                    long ticks = AuraReaderSearchClientState.processingElapsedTicks(minecraft.player.tickCount);
                    int sweepWidth = Math.max(2, usableWidth / 5);
                    int sweepStart = padding + (reducedMotion ? (usableWidth - sweepWidth) / 2
                            : (int) (ticks % 60L) * Math.max(0, usableWidth - sweepWidth) / 59);
                    fill(graphics, sweepStart, meterY, sweepStart + sweepWidth, meterY + 1, AuraReaderHudTheme.CYAN);
                }
            }
        }
        graphics.pose().popPose();
    }

    private static void drawContextPanels(
            GuiGraphics graphics,
            Minecraft minecraft,
            int viewportWidth,
            int viewportHeight,
            int scannerCenterX,
            int scannerCenterY,
            float scannerScale,
            AuraReaderHudGeometry.Geometry layout,
            AuraReaderClientState.ReadingSnapshot completedReading,
            boolean reducedMotion) {
        if (completedReading == null || completedReading.outOfDimension() || AuraReaderClientState.getPanelProgress() <= 0.0f
                || !(scannerScale > 0.0f) || !Float.isFinite(scannerScale)) {
            panelLayoutTargetId = null;
            panelLayoutRequest = null;
            panelLayout = null;
            panelArmLayoutKey = null;
            panelArms = List.of();
            panelArmQuads = List.of();
            return;
        }

        boolean sameTarget = Objects.equals(panelLayoutTargetId, completedReading.id());
        String auraText = Component.translatable("hud.shadowedhearts.aura_reader.panel.aura_value", auraStatus(completedReading)).getString();
        String targetText = completedReading.label().isBlank()
                ? prettyType(completedReading.type()) : completedReading.label();
        boolean hasHeart = completedReading.type() == AuraReadingType.SHADOW_POKEMON
                && completedReading.heartGauge() != null;
        AuraReaderScannerInfoFrameLayout.Request previous = sameTarget ? panelLayoutRequest : null;
        AuraReaderScannerInfoFrameLayout.Request request = new AuraReaderScannerInfoFrameLayout.Request(
                viewportWidth,
                viewportHeight,
                scannerCenterX,
                scannerCenterY,
                scannerScale,
                layout.partySafeZone(),
                layout.bottomSafeZone(),
                scannerReservedZones(layout),
                scannerPanelSide(AuraReaderScannerInfoFrameLayout.AURA_TIER),
                scannerPanelSide(AuraReaderScannerInfoFrameLayout.TARGET_TIER),
                hasHeart ? scannerPanelSide(AuraReaderScannerInfoFrameLayout.HEART_TIER) : null,
                MINIMUM_TEXT_SCALE,
                scannerPanelContent(minecraft, auraText, previous == null ? null : previous.auraContent()),
                scannerPanelContent(minecraft, targetText, previous == null ? null : previous.targetContent()),
                hasHeart ? scannerPanelContent(minecraft, heartGaugeText(completedReading.heartGauge()),
                        previous == null ? null : previous.heartContent()) : null
        );
        // Retain peak measured widths for this target so changing live values
        // do not make the frame contract. Reflow when content or safe areas change.
        if (!sameTarget || !request.equals(panelLayoutRequest)) {
            panelLayout = AuraReaderScannerInfoFrameLayout.resolve(request,
                    value -> scannerTextWidth(minecraft, value), sameTarget ? panelLayout : null);
            panelLayoutRequest = request;
            panelLayoutTargetId = completedReading.id();
        }
        ScannerArmLayoutKey armLayoutKey = new ScannerArmLayoutKey(
                viewportWidth, viewportHeight, scannerCenterX, scannerCenterY, scannerScale,
                request.partySafeZone(), request.bottomSafeZone(), request.reservedZones(),
                panelLayout.visibleFrames().stream().map(AuraReaderScannerInfoFrameLayout.FramePlacement::geometry).toList());
        if (!armLayoutKey.equals(panelArmLayoutKey)) {
            panelArms = AuraReaderScannerArmLayout.resolve(panelLayout, request);
            panelArmQuads = AuraReaderScannerArmLayout.quads(panelArms, 1.0f);
            panelArmLayoutKey = armLayoutKey;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(scannerCenterX, scannerCenterY, 0.0f);
        graphics.pose().scale(scannerScale, scannerScale, 1.0f);
        drawScannerContextArms(graphics, reducedMotion);
        panelLayout.auraFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                frame,
                reducedMotion
        ));
        panelLayout.targetFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                frame,
                reducedMotion
        ));
        panelLayout.heartFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                frame,
                reducedMotion
        ));
        graphics.pose().popPose();
    }

    private static AuraReaderScannerInfoFrameLayout.Content scannerPanelContent(
            Minecraft minecraft, String value, AuraReaderScannerInfoFrameLayout.Content previous) {
        int measuredWidth = value.lines().mapToInt(line -> scannerTextWidth(minecraft, line)).max().orElse(0);
        return new AuraReaderScannerInfoFrameLayout.Content(value,
                Math.max(measuredWidth, previous == null ? 0 : previous.naturalWidth()));
    }

    private static void drawScannerContextFrame(
            GuiGraphics graphics,
            AuraReaderScannerInfoFrameLayout.FramePlacement frame,
            boolean reducedMotion) {
        var geometry = frame.geometry();
        float progress = AuraReaderClientState.getPanelProgress();
        float bodyProgress = reducedMotion ? 1.0f
                : clamp01((progress - SCANNER_ARM_OPEN_FRACTION) / (1.0f - SCANNER_ARM_OPEN_FRACTION));
        if (bodyProgress <= 0.0f) {
            return;
        }
        var attachment = panelArms.stream()
                .filter(arm -> arm.panel().equals(geometry))
                .map(AuraReaderScannerArmLayout.Arm::attachment)
                .findFirst()
                .orElse(AuraReaderScannerArmLayout.attachment(geometry));
        var visible = AuraReaderScannerArmLayout.reveal(geometry, attachment, bodyProgress);
        drawScannerPanelBrackets(graphics, visible.xOffset(), visible.yOffset(), visible.width(), visible.height(),
                0.96f * (reducedMotion ? progress : 1.0f));

        if (!AuraReaderClientState.shouldShowPanelText()) {
            return;
        }

        graphics.pose().pushPose();
        int lineHeight = AuraReaderScannerInfoFrameLayout.LINE_HEIGHT;
        float textHeight = ((frame.lines().size() - 1) * lineHeight
                + AuraReaderScannerInfoFrameLayout.TEXT_HEIGHT_WITH_SHADOW) * frame.relativeTextScale();
        graphics.pose().translate(geometry.xOffset() + geometry.width() / 2.0f,
                geometry.yOffset() + (geometry.height() - textHeight) / 2.0f, 0.0f);
        graphics.pose().scale(frame.relativeTextScale(), frame.relativeTextScale(), 1.0f);
        int textColor = AuraReaderHudTheme.multiplyAlpha(AuraReaderHudTheme.TEXT_PRIMARY,
                AuraReaderClientState.getPanelTextAlpha());
        for (int line = 0; line < frame.lines().size(); line++) {
            drawScannerString(graphics, frame.lines().get(line), 0, line * lineHeight, textColor, true);
        }
        graphics.pose().popPose();
    }

    private static void drawScannerContextArms(GuiGraphics graphics, boolean reducedMotion) {
        float progress = AuraReaderClientState.getPanelProgress();
        float armProgress = reducedMotion ? 1.0f : clamp01(progress / SCANNER_ARM_OPEN_FRACTION);
        List<AuraReaderHudRect> quads = armProgress >= 1.0f
                ? panelArmQuads : AuraReaderScannerArmLayout.quads(panelArms, armProgress);
        int color = AuraReaderHudTheme.multiplyAlpha(SCANNER_PANEL_BORDER,
                0.96f * (reducedMotion ? progress : 1.0f));
        for (AuraReaderHudRect quad : quads) {
            fill(graphics, quad.left(), quad.top(), quad.right(), quad.bottom(), color);
        }
    }

    private static void drawScannerPanelBrackets(
            GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int borderColor = AuraReaderHudTheme.multiplyAlpha(SCANNER_PANEL_BORDER, alpha);
        int fillColor = AuraReaderHudTheme.multiplyAlpha(SCANNER_PANEL_FILL, alpha);
        // The left/right halves already contain translucent body pixels; only
        // fill between them so the joins do not receive a second alpha layer.
        fill(graphics, x + 5, y + 1, x + width - 5, y + height - 1, fillColor);
        fill(graphics, x + 5, y, x + width - 5, y + 1, borderColor);
        fill(graphics, x + 5, y + height - 1, x + width - 5, y + height, borderColor);
        for (int side = 0; side < 2; side++) {
            int capX = side == 0 ? x : x + width - 5;
            int capV = side * 16;
            // Preserve the two corner rows; stretch only the straight middle.
            blit(graphics, SCANNER_BRACKETS, capX, y, 5, 2, 0, capV, 5, 2, 5, 32, alpha);
            blit(graphics, SCANNER_BRACKETS, capX, y + 2, 5, height - 4,
                    0, capV + 2, 5, 1, 5, 32, alpha);
            blit(graphics, SCANNER_BRACKETS, capX, y + height - 2, 5, 2,
                    0, capV + 14, 5, 2, 5, 32, alpha);
        }
    }

    private static String heartGaugeText(AuraHeartGaugeReadout readout) {
        String bandKey = readout.band() == null ? ""
                : readout.band().name().toLowerCase(Locale.ROOT);
        return AuraReaderHeartGaugePresentation.format(
                readout,
                translated("hud.shadowedhearts.aura_reader.panel.heart_gauge"),
                bandKey.isEmpty() ? "" : translated("hud.shadowedhearts.aura_reader.heart_gauge." + bandKey),
                translated("hud.shadowedhearts.aura_reader.heart_gauge.hidden")
        );
    }

    private static AuraReaderScannerInfoFrameLayout.Side scannerPanelSide(int tier) {
        return AuraReaderClientState.getPanelSide(tier)
                ? AuraReaderScannerInfoFrameLayout.Side.LEFT
                : AuraReaderScannerInfoFrameLayout.Side.RIGHT;
    }

    private static List<AuraReaderHudRect> scannerReservedZones(AuraReaderHudGeometry.Geometry layout) {
        // Direction tiles and the curved rail extend beyond the rail's nominal rectangle.
        AuraReaderHudRect rail = layout.compassRail();
        int tileRadius = (compassDirectionBoxSize(rail) + 1) / 2;
        int rise = Math.max(1, Math.round(rail.height() * COMPASS_CURVE_RISE_FACTOR));
        AuraReaderHudRect compassBounds = new AuraReaderHudRect(
                rail.left() - tileRadius,
                Math.min(centerY(rail) - rise - tileRadius, rail.top() - rise - 8),
                rail.right() + tileRadius,
                Math.max(centerY(rail) + tileRadius, rail.bottom() + 3));
        return List.of(compassBounds, layout.modeCap(), layout.signalPopover());
    }

    private static AuraReaderHudRect beginReadout(GuiGraphics graphics, AuraReaderHudRect rect, int designHeight) {
        float scale = Math.max(MINIMUM_TEXT_SCALE, rect.height() / (float) designHeight);
        graphics.pose().pushPose();
        graphics.pose().translate(rect.left(), rect.top(), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        return new AuraReaderHudRect(0, 0, (int) Math.floor(rect.width() / scale),
                (int) Math.floor(rect.height() / scale));
    }

    private static boolean canDrawReadout(AuraReaderHudRect rect, int designHeight) {
        float scale = Math.max(MINIMUM_TEXT_SCALE, rect.height() / (float) designHeight);
        return Math.floor(rect.width() / scale) >= 24 && Math.floor(rect.height() / scale) >= 13;
    }

    private static void drawReadoutBrackets(GuiGraphics graphics, AuraReaderHudRect rect) {
        fill(graphics, rect.left(), rect.top(), rect.right(), rect.bottom(), AuraReaderHudTheme.READOUT_BACKGROUND);
        int corner = Math.min(7, Math.min(rect.width(), rect.height()) / 2);
        fill(graphics, rect.left(), rect.top(), rect.left() + corner, rect.top() + 1, AuraReaderHudTheme.CYAN);
        fill(graphics, rect.left(), rect.top() + 1, rect.left() + 1, rect.top() + corner, AuraReaderHudTheme.CYAN);
        fill(graphics, rect.right() - corner, rect.bottom() - 1, rect.right(), rect.bottom(), AuraReaderHudTheme.CYAN);
        fill(graphics, rect.right() - 1, rect.bottom() - corner, rect.right(), rect.bottom() - 1, AuraReaderHudTheme.CYAN);
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
        fill(graphics, rect.left(), rect.top() + bevel, rect.left() + bevel, rect.bottom() - bevel, backgroundColor);
        fill(graphics, rect.right() - bevel, rect.top() + bevel, rect.right(), rect.bottom() - bevel, backgroundColor);

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
            AuraReaderClientState.ReadingSnapshot focused) {
        if (readings == null || readings.isEmpty()) {
            return List.of();
        }
        Map<String, AuraReaderClientState.ReadingSnapshot> byId = new HashMap<>();
        List<AuraReaderCompassSelection.Candidate> candidates = new ArrayList<>(readings.size());
        for (var reading : readings) {
            byId.put(reading.id(), reading);
            candidates.add(compassCandidate(reading, focused != null && focused.id().equals(reading.id())));
        }
        return AuraReaderCompassSelection.order(candidates, AuraReaderClientState.getSelectedSignal())
                .stream().map(candidate -> byId.get(candidate.id())).toList();
    }

    private static AuraReaderCompassSelection.Candidate compassCandidate(
            AuraReaderClientState.ReadingSnapshot reading, boolean focused) {
        return new AuraReaderCompassSelection.Candidate(reading.id(), reading.outOfDimension(),
                isPinned(reading), reading.locked() || reading.source() == AuraReadingSource.LOCKED,
                focused, reading.priority(), reading.strength());
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
            String reticleTargetId) {
        List<AuraReaderFocusSelector.Candidate> candidates = new ArrayList<>(readings.size());
        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            if (AuraReaderClientState.isReadingStale(reading.id())) continue;
            candidates.add(new AuraReaderFocusSelector.Candidate(
                    reading.id(), reading.type(), reading.outOfDimension()));
        }
        String selectedId = AuraReaderFocusSelector.select(candidates, reticleTargetId);
        for (AuraReaderClientState.ReadingSnapshot reading : readings) {
            if (reading.id().equals(selectedId)) {
                return reading;
            }
        }
        return null;
    }

    private static AuraReaderClientState.ReadingSnapshot getTrackedReading(
            List<AuraReaderClientState.ReadingSnapshot> readings) {
        List<AuraReaderCompassSelection.Candidate> freshCandidates = readings.stream()
                .filter(reading -> !AuraReaderClientState.isReadingStale(reading.id()))
                .map(reading -> compassCandidate(reading, false)).toList();
        String primaryId = AuraReaderCompassSelection.primaryId(
                freshCandidates, AuraReaderClientState.getSelectedSignal());
        return readings.stream().filter(reading -> reading.id().equals(primaryId)).findFirst().orElse(null);
    }

    private static boolean isNavigationSelected(AuraReaderClientState.ReadingSnapshot reading) {
        return reading.id().equals(AuraReaderClientState.getSelectedSignal());
    }

    private static boolean isPinned(AuraReaderClientState.ReadingSnapshot reading) {
        return reading.type() == AuraReadingType.SHADOW_POKEMON
                && (reading.source() == AuraReadingSource.TRACKED
                || reading.source() == AuraReadingSource.LOCKED || reading.locked());
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

    private static String distanceLabel(String band) {
        String key = "hud.shadowedhearts.aura_reader.distance." + band.toLowerCase(Locale.ROOT);
        return net.minecraft.locale.Language.getInstance().has(key) ? translated(key) : band;
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
        return translated("hud.shadowedhearts.aura_reader.type." + type.name().toLowerCase(Locale.ROOT));
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    private static String fitScannerText(Minecraft minecraft, String value, int maxWidth) {
        String safeValue = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        // Reserve the final pixel for the native font's shadow as well as its bold glyph width.
        int glyphWidth = maxWidth - 1;
        if (glyphWidth <= 0) {
            return "";
        }
        if (scannerTextWidth(minecraft, safeValue) <= glyphWidth) {
            return safeValue;
        }

        String ellipsis = "...";
        if (scannerTextWidth(minecraft, ellipsis) > glyphWidth) {
            return "";
        }
        int end = safeValue.length();
        while (end > 0
                && scannerTextWidth(minecraft, safeValue.substring(0, end) + ellipsis) > glyphWidth) {
            end = safeValue.offsetByCodePoints(end, -1);
        }
        return safeValue.substring(0, end) + ellipsis;
    }

    private static int scannerTextWidth(Minecraft minecraft, String value) {
        return minecraft.font.width(scannerText(value));
    }

    private static MutableComponent scannerText(String value) {
        return Component.literal(value == null ? "" : value)
                .withStyle(style -> style.withFont(SCANNER_TEXT_FONT).withBold(true));
    }

    private static void drawScannerString(
            GuiGraphics graphics,
            String value,
            int x,
            int y,
            int color,
            boolean centered) {
        int adjustedColor = AuraReaderHudTheme.multiplyAlpha(color, frameAlpha);
        if (value.isEmpty() || FastColor.ARGB32.alpha(adjustedColor) <= 3) {
            return;
        }
        GuiUtilsKt.drawText(graphics, SCANNER_TEXT_FONT, scannerText(value), x, y,
                centered, adjustedColor, true, null, null);
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

    private record ScannerArmLayoutKey(
            int viewportWidth, int viewportHeight, float centerX, float centerY, float scale,
            AuraReaderHudRect partySafeZone, AuraReaderHudRect bottomSafeZone,
            List<AuraReaderHudRect> reservedZones,
            List<AuraReaderScannerInfoFrameLayout.Geometry> panels) {
    }

    private record MarkerPresentation(
            AuraReaderClientState.ReadingSnapshot reading,
            float normalizedX) {
    }
}
