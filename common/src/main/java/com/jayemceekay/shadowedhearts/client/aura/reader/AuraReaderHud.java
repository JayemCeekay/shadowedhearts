package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderTargeting;
import com.jayemceekay.shadowedhearts.content.umbrafall.AuraReaderSearchProgress;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.clamp01;

/**
 * Field presentation for the Aura Reader.
 *
 * <p>World data stays server-authoritative. This class is deliberately limited
 * to screen-space selection, collision-aware layout, and presentation.</p>
 */
public final class AuraReaderHud {
    private static final int SCAN_OVERLAY_NOTCH_WIDTH = 200;
    private static final int PARTY_OVERLAY_SAFE_WIDTH = 72;

    private static final ResourceLocation SCAN_OVERLAY_CORNERS = cobblemon("textures/gui/pokedex/scan/overlay_corners.png");
    private static final ResourceLocation SCAN_OVERLAY_TOP = cobblemon("textures/gui/pokedex/scan/overlay_border_top.png");
    private static final ResourceLocation SCAN_OVERLAY_BOTTOM = cobblemon("textures/gui/pokedex/scan/overlay_border_bottom.png");
    private static final ResourceLocation SCAN_OVERLAY_LEFT = cobblemon("textures/gui/pokedex/scan/overlay_border_left.png");
    private static final ResourceLocation SCAN_OVERLAY_RIGHT = cobblemon("textures/gui/pokedex/scan/overlay_border_right.png");
    private static final ResourceLocation SCAN_OVERLAY_LINES = cobblemon("textures/gui/pokedex/scan/overlay_scanlines.png");
    private static final ResourceLocation SCAN_OVERLAY_NOTCH = cobblemon("textures/gui/pokedex/scan/overlay_notch.png");

    private static float frameAlpha = 1.0f;
    private static final List<AuraReaderHudRect> occupiedAreas = new ArrayList<>();

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
            if (!AuraReaderSearchClientState.isCollapsed()) {
                AuraReaderReadoutRenderer.renderSearch(graphics, minecraft, layout.signalPopover(), search,
                        config.auraReaderReducedMotion(), frameAlpha);
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
        AuraReaderCompassRenderer.render(graphics, minecraft, layout.compassRail(),
                displayedReadings, focused, playerYaw, ticks, config, frameAlpha);
        float scannerScale = Math.min(layout.reticle().width(), layout.reticle().height())
                / (float) AuraReaderHudGeometry.SCANNER_RETICLE_DIAMETER;
        int scannerCenterX = width / 2;
        int scannerCenterY = height / 2;
        var panelReservations = AuraReaderScannerPanelRenderer.render(
                    graphics,
                    minecraft,
                    width,
                    height,
                    scannerCenterX,
                    scannerCenterY,
                    scannerScale,
                    layout,
                    scannerReservedZones(layout),
                    AuraReaderClientState.getPanelReading(),
                    config.auraReaderReducedMotion(),
                    frameAlpha
            );

        AuraReaderReticleRenderer.render(
                graphics,
                scannerCenterX,
                scannerCenterY,
                scannerScale,
                focused,
                config,
                frameAlpha
        );

        // Keep text in front of the ring when a short viewport needs overlap.
        if (search != null && !AuraReaderSearchClientState.isCollapsed()) {
            AuraReaderReadoutRenderer.renderSearch(graphics, minecraft, layout.signalPopover(), search,
                    config.auraReaderReducedMotion(), frameAlpha);
        } else {
            AuraReaderReadoutRenderer.renderSignal(graphics, minecraft, layout.signalPopover(), tracked, frameAlpha);
        }

        graphics.pose().popPose();
        // Other field readouts share the scanner's actual visible reservations.
        int panY = Math.round(panOffset);
        var areas = new ArrayList<>(scannerReservedZones(layout));
        areas.add(layout.reticle());
        areas.addAll(panelReservations.frameBounds());
        for (var area : areas) {
            occupiedAreas.add(new AuraReaderHudRect(area.left(), area.top() + panY,
                    area.right(), area.bottom() + panY));
        }
        // Reserve one envelope per connector, not hundreds of its raster pixels.
        for (var arm : panelReservations.armBounds()) {
            int left = arm.left();
            int top = arm.top();
            int right = arm.right();
            int bottom = arm.bottom();
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
        float readoutAlpha = clamp01(opacity);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        try {
            AuraReaderReadoutRenderer.renderMoonwound(graphics, Minecraft.getInstance(), rect, snapshot, readoutAlpha);
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
    }

    public static int moonwoundInfoBoxWidth(MoonwoundSnapshot snapshot) {
        return AuraReaderReadoutRenderer.moonwoundInfoBoxWidth(snapshot);
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

    private static List<AuraReaderHudRect> scannerReservedZones(AuraReaderHudGeometry.Geometry layout) {
        AuraReaderHudRect compassBounds = AuraReaderCompassRenderer.reservedArea(layout.compassRail());
        return List.of(compassBounds, layout.modeCap(), layout.signalPopover());
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

    private static boolean isPinned(AuraReaderClientState.ReadingSnapshot reading) {
        return AuraReaderCompassSelection.isPinned(reading.type(), reading.source(), reading.locked());
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
        AuraReaderHudDrawing.blit(graphics, texture, x, y, width, height, uOffset, vOffset,
                regionWidth, regionHeight, textureWidth, textureHeight, alpha, frameAlpha);
    }
}
