package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundHudPresentation;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.content.umbrafall.AuraReaderSearchProgress;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.centerX;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.drawScannerPanelBrackets;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.drawScannerString;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.fill;
import static com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudDrawing.scannerTextWidth;

/** Signal, search, and Moonwound readouts; the caller owns composition and blend state. */
final class AuraReaderReadoutRenderer {
    private static final int READOUT_TEXT_HEIGHT = 8;
    private static final int SIGNAL_FRAME_VERTICAL_INSET = 2;
    private static final float MINIMUM_TEXT_SCALE = AuraReaderHudGeometry.MIN_READOUT_SCALE;

    private AuraReaderReadoutRenderer() {
    }

    static void renderSignal(
            GuiGraphics graphics,
            Minecraft minecraft,
            AuraReaderHudRect rect,
            AuraReaderClientState.ReadingSnapshot tracked,
            float frameAlpha) {
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
        drawScannerPanelBrackets(graphics, 0, frameInset, local.width(), local.height() - frameInset * 2, 0.96f, frameAlpha);
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
                    AuraReaderHudTheme.TEXT_SECONDARY, true, frameAlpha);
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
                AuraReaderHudTheme.TEXT_PRIMARY, false, frameAlpha);
        if (showStrength) {
            int strengthX = titleLeft + fittedNameWidth + 12;
            fill(graphics, strengthX - 7, textY, strengthX - 6, textY + READOUT_TEXT_HEIGHT,
                    AuraReaderHudTheme.CYAN_DIM, frameAlpha);
            drawScannerString(graphics, title.strength(), strengthX, textY,
                    AuraReaderHudTheme.TEXT_SECONDARY, false, frameAlpha);
        }
        if (!details.isEmpty()) {
            drawScannerString(graphics, details, centerX(local), textY + lineStep,
                    AuraReaderHudTheme.TEXT_SECONDARY, true, frameAlpha);
        }
        graphics.pose().popPose();
    }

    static boolean renderMoonwound(
            GuiGraphics graphics, Minecraft minecraft, AuraReaderHudRect rect,
            MoonwoundSnapshot snapshot, float frameAlpha) {
        int designHeight = AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT;
        if (!canDrawReadout(rect, designHeight)) return false;
        AuraReaderHudRect local = beginReadout(graphics, rect, designHeight);
        int usableWidth = Math.max(0, local.width() - 16);
        if (local.height() < 27 || usableWidth < scannerTextWidth(minecraft, Component.translatable("hud.shadowedhearts.aura_reader.type.moonwound").getString()) + 1) {
            graphics.pose().popPose();
            return false;
        }
        drawScannerPanelBrackets(graphics, 0, SIGNAL_FRAME_VERTICAL_INSET,
                local.width(), local.height() - SIGNAL_FRAME_VERTICAL_INSET * 2, 0.96f, frameAlpha);
        int lineStep = READOUT_TEXT_HEIGHT + 4;
        int textY = (local.height() - READOUT_TEXT_HEIGHT - lineStep) / 2;
        drawScannerString(graphics, fitScannerText(minecraft, MoonwoundHudPresentation.title(snapshot), usableWidth),
                centerX(local), textY, AuraReaderHudTheme.TEXT_PRIMARY, true, frameAlpha);
        drawScannerString(graphics, fitScannerText(minecraft, MoonwoundHudPresentation.detail(snapshot), usableWidth),
                centerX(local), textY + lineStep, AuraReaderHudTheme.TEXT_SECONDARY, true, frameAlpha);
        graphics.pose().popPose();
        return true;
    }

    static void renderSearch(
            GuiGraphics graphics, Minecraft minecraft, AuraReaderHudRect rect,
            AuraReaderSearchProgress search, boolean reducedMotion, float frameAlpha) {
        if (search == null || !canDrawReadout(rect, AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT)) return;
        AuraReaderHudRect local = beginReadout(graphics, rect, AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT);
        drawScannerPanelBrackets(graphics, 0, 0, local.width(), local.height(), 0.96f, frameAlpha);
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
                    AuraReaderHudTheme.TEXT_SECONDARY, true, frameAlpha);
        }
        drawScannerString(graphics, fitScannerText(minecraft, status, usableWidth), centerX(local),
                full ? textY + lineStep : textY, AuraReaderHudTheme.TEXT_PRIMARY, true, frameAlpha);
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
                    AuraReaderHudTheme.TEXT_SECONDARY, true, frameAlpha);
            if (!search.stage().terminal()) {
                int meterY = local.bottom() - 2;
                fill(graphics, padding, meterY, local.right() - padding, meterY + 1, AuraReaderHudTheme.METER_TRACK, frameAlpha);
                if (search.phasePercent() >= 0) {
                    fill(graphics, padding, meterY, padding + usableWidth * search.phasePercent() / 100,
                            meterY + 1, AuraReaderHudTheme.CYAN_BRIGHT, frameAlpha);
                } else if (search.stage().preflight()) {
                    long ticks = AuraReaderSearchClientState.processingElapsedTicks(minecraft.player.tickCount);
                    int sweepWidth = Math.max(2, usableWidth / 5);
                    int sweepStart = padding + (reducedMotion ? (usableWidth - sweepWidth) / 2
                            : (int) (ticks % 60L) * Math.max(0, usableWidth - sweepWidth) / 59);
                    fill(graphics, sweepStart, meterY, sweepStart + sweepWidth, meterY + 1, AuraReaderHudTheme.CYAN, frameAlpha);
                }
            }
        }
        graphics.pose().popPose();
    }

    static int moonwoundInfoBoxWidth(MoonwoundSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.max(scannerTextWidth(minecraft, MoonwoundHudPresentation.title(snapshot)),
                scannerTextWidth(minecraft, MoonwoundHudPresentation.detail(snapshot))) + 17;
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

    private static String stabilityLabel(AuraReaderClientState.ReadingSnapshot reading) {
        boolean unstable = reading.confidence() < 0.55f || reading.interference() > 0.45f;
        return translated(unstable
                ? "hud.shadowedhearts.aura_reader.status.unstable"
                : "hud.shadowedhearts.aura_reader.status.stable");
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
}
