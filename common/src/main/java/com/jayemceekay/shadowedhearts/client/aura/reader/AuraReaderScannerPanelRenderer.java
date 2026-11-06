package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.jayemceekay.shadowedhearts.common.aura.AuraHeartGaugeReadout;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.SCANNER_PANEL_BORDER;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.clamp01;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.drawScannerPanelBrackets;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.drawScannerString;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.fill;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.scannerTextWidth;

/** Cached scanner context frames and connectors, with reservations returned to the owning HUD. */
final class AuraReaderScannerPanelRenderer {
    private static final float SCANNER_ARM_OPEN_FRACTION = 4.0f / 9.0f;
    private static final float MINIMUM_TEXT_SCALE = AuraReaderHudGeometry.MIN_READOUT_SCALE;

    private static String panelLayoutTargetId;
    private static AuraReaderScannerInfoFrameLayout.Request panelLayoutRequest;
    private static AuraReaderScannerInfoFrameLayout.Layout panelLayout;
    private static ScannerArmLayoutKey panelArmLayoutKey;
    private static List<AuraReaderScannerArmLayout.Arm> panelArms = List.of();
    private static List<AuraReaderHudRect> panelArmQuads = List.of();

    private AuraReaderScannerPanelRenderer() {
    }

    static Reservations render(
            GuiGraphics graphics,
            Minecraft minecraft,
            int viewportWidth,
            int viewportHeight,
            int scannerCenterX,
            int scannerCenterY,
            float scannerScale,
            AuraReaderHudGeometry.Geometry layout,
            List<AuraReaderHudRect> reservedZones,
            AuraReaderClientState.ReadingSnapshot completedReading,
            boolean reducedMotion,
            float frameAlpha) {
        if (completedReading == null || completedReading.outOfDimension() || AuraReaderClientState.getPanelProgress() <= 0.0f
                || !(scannerScale > 0.0f) || !Float.isFinite(scannerScale)) {
            panelLayoutTargetId = null;
            panelLayoutRequest = null;
            panelLayout = null;
            panelArmLayoutKey = null;
            panelArms = List.of();
            panelArmQuads = List.of();
            return Reservations.EMPTY;
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
                reservedZones,
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
        drawScannerContextArms(graphics, reducedMotion, frameAlpha);
        panelLayout.auraFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                frame,
                reducedMotion,
                frameAlpha
        ));
        panelLayout.targetFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                frame,
                reducedMotion,
                frameAlpha
        ));
        panelLayout.heartFrame().ifPresent(frame -> drawScannerContextFrame(
                graphics,
                frame,
                reducedMotion,
                frameAlpha
        ));
        graphics.pose().popPose();

        List<AuraReaderHudRect> frameBounds = panelLayout.visibleFrames().stream()
                .map(AuraReaderScannerInfoFrameLayout.FramePlacement::bounds).toList();
        List<AuraReaderHudRect> armBounds = new ArrayList<>();
        // Reserve one full envelope per connector, independently of its opening animation.
        for (var arm : panelArms) {
            if (arm.pixels().isEmpty()) continue;
            int left = arm.pixels().stream().mapToInt(AuraReaderHudRect::left).min().orElseThrow();
            int top = arm.pixels().stream().mapToInt(AuraReaderHudRect::top).min().orElseThrow();
            int right = arm.pixels().stream().mapToInt(AuraReaderHudRect::right).max().orElseThrow();
            int bottom = arm.pixels().stream().mapToInt(AuraReaderHudRect::bottom).max().orElseThrow();
            armBounds.add(new AuraReaderHudRect(left, top, right, bottom));
        }
        return new Reservations(frameBounds, List.copyOf(armBounds));
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
            boolean reducedMotion,
            float frameAlpha) {
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
                0.96f * (reducedMotion ? progress : 1.0f), frameAlpha);

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
            drawScannerString(graphics, frame.lines().get(line), 0, line * lineHeight, textColor, true, frameAlpha);
        }
        graphics.pose().popPose();
    }

    private static void drawScannerContextArms(GuiGraphics graphics, boolean reducedMotion, float frameAlpha) {
        float progress = AuraReaderClientState.getPanelProgress();
        float armProgress = reducedMotion ? 1.0f : clamp01(progress / SCANNER_ARM_OPEN_FRACTION);
        List<AuraReaderHudRect> quads = armProgress >= 1.0f
                ? panelArmQuads : AuraReaderScannerArmLayout.quads(panelArms, armProgress);
        int color = AuraReaderHudTheme.multiplyAlpha(SCANNER_PANEL_BORDER,
                0.96f * (reducedMotion ? progress : 1.0f));
        for (AuraReaderHudRect quad : quads) {
            fill(graphics, quad.left(), quad.top(), quad.right(), quad.bottom(), color, frameAlpha);
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

    private static String prettyType(AuraReadingType type) {
        return translated("hud.shadowedhearts.aura_reader.type." + type.name().toLowerCase(Locale.ROOT));
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    /** Frame bounds are screen-space; arm bounds are native scanner coordinates, before pan. */
    record Reservations(List<AuraReaderHudRect> frameBounds, List<AuraReaderHudRect> armBounds) {
        static final Reservations EMPTY = new Reservations(List.of(), List.of());
    }

    private record ScannerArmLayoutKey(
            int viewportWidth, int viewportHeight, float centerX, float centerY, float scale,
            AuraReaderHudRect partySafeZone, AuraReaderHudRect bottomSafeZone,
            List<AuraReaderHudRect> reservedZones,
            List<AuraReaderScannerInfoFrameLayout.Geometry> panels) {
    }
}
