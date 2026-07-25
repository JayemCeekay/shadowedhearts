package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public final class AuraReaderHud {
    private static final int CYAN = 0xDD42E8F3;
    private static final int CYAN_DIM = 0x7A34D4DE;
    private static final int TEXT_PRIMARY = 0xEAFBFF;
    private static final int TEXT_SECONDARY = 0xBFEAF1;
    private static final int TEXT_WARNING = 0xFFE7C15B;
    private static final int TEXT_DANGER = 0xFFFF6D82;
    private static final int SCAN_OVERLAY_NOTCH_WIDTH = 200;
    private static final int CENTER_INFO_FRAME_WIDTH = 128;
    private static final int CENTER_INFO_FRAME_HEIGHT = 16;
    private static final int INFO_FRAME_FINAL_STEP = 9;
    private static final int OUTER_INFO_FRAME_WIDTH = 92;
    private static final int OUTER_INFO_FRAME_HEIGHT = 55;
    private static final int INNER_INFO_FRAME_WIDTH = 120;
    private static final int INNER_INFO_FRAME_HEIGHT = 20;
    private static final int INNER_INFO_FRAME_STEM_WIDTH = 28;
    private static final int POINTER_WIDTH = 6;
    private static final int POINTER_HEIGHT = 10;
    private static final int POINTER_OFFSET = 30;
    private static final int SCAN_RING_MIDDLE_WIDTH = 100;
    private static final int SCAN_RING_MIDDLE_HEIGHT = 1;
    private static final int SCAN_RING_OUTER_DIAMETER = 116;
    private static final int SCAN_RING_INNER_DIAMETER = 84;
    private static final int BAR_WIDTH = 38;
    private static final int BAR_HEIGHT = 3;

    private static final ResourceLocation HUD_FONT = ResourceLocation.parse("uniform");
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
    private static final ResourceLocation CENTER_INFO_FRAME = cobblemon("textures/gui/pokedex/scan/scan_info_frame.png");
    private static final ResourceLocation POINTER = cobblemon("textures/gui/pokedex/scan/pointer.png");

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
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().auraScannerEnabled()) {
            return;
        }
        if (!AuraReaderClientState.isActive()) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        long ticks = System.currentTimeMillis() / 50L;
        AuraReaderClientState.updateHudAnimations();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        drawScreenWash(graphics, width, height, ticks);
        drawScannerBorder(graphics, width, height);
        drawReticle(graphics, centerX, centerY, ticks);
        drawStatusStrip(graphics, minecraft, centerX, centerY);
        drawInfoPanels(graphics, minecraft, centerX, centerY, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static void drawScreenWash(GuiGraphics graphics, int width, int height, long ticks) {
        float interlace = (float) (((ticks % 14) * 0.5f) * 0.5f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.35f);
        for (int y = 0; y < height; y += 4) {
            blit(graphics, SCAN_OVERLAY_LINES, 0, Math.round(y - interlace), width, 4, 0, 0, 1, 4, 1, 4);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawScannerBorder(GuiGraphics graphics, int width, int height) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.9f);
        blit(graphics, SCAN_OVERLAY_CORNERS, 0, 0, 4, 4, 0, 0, 4, 4, 8, 8);
        blit(graphics, SCAN_OVERLAY_CORNERS, width - 4, 0, 4, 4, 4, 0, 4, 4, 8, 8);
        blit(graphics, SCAN_OVERLAY_CORNERS, 0, height - 4, 4, 4, 0, 4, 4, 4, 8, 8);
        blit(graphics, SCAN_OVERLAY_CORNERS, width - 4, height - 4, 4, 4, 4, 4, 4, 4, 8, 8);

        int notchStartX = (width - SCAN_OVERLAY_NOTCH_WIDTH) / 2;
        blit(graphics, SCAN_OVERLAY_TOP, 4, 0, notchStartX - 4, 3, 0, 0, 1, 3, 1, 3);
        blit(graphics, SCAN_OVERLAY_TOP, notchStartX + SCAN_OVERLAY_NOTCH_WIDTH, 0, width - notchStartX - SCAN_OVERLAY_NOTCH_WIDTH - 4, 3, 0, 0, 1, 3, 1, 3);
        blit(graphics, SCAN_OVERLAY_BOTTOM, 4, height - 3, width - 8, 3, 0, 0, 1, 3, 1, 3);
        blit(graphics, SCAN_OVERLAY_LEFT, 0, 4, 3, height - 8, 0, 0, 3, 1, 3, 1);
        blit(graphics, SCAN_OVERLAY_RIGHT, width - 3, 4, 3, height - 8, 0, 0, 3, 1, 3, 1);
        blit(graphics, SCAN_OVERLAY_NOTCH, notchStartX, 0, SCAN_OVERLAY_NOTCH_WIDTH, 12, 0, 0, 200, 12, 200, 12);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawReticle(GuiGraphics graphics, int centerX, int centerY, long ticks) {
        float rotation = AuraReaderClientState.getUsageIntervals() % 360.0f;
        float innerRotation = AuraReaderClientState.getInnerRingRotation();
        float scanProgress = AuraReaderClientState.getReticleScanProgress();
        boolean scanOpening = AuraReaderClientState.isFocusedScanOpening();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.78f);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0f);

        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-rotation * 0.5f)));
        blit(graphics, SCAN_RING_OUTER, -SCAN_RING_OUTER_DIAMETER / 2, -SCAN_RING_OUTER_DIAMETER / 2, SCAN_RING_OUTER_DIAMETER, SCAN_RING_OUTER_DIAMETER, 0, 0, 116, 116, 116, 116);
        graphics.pose().popPose();

        int segments = middleRingSegments(scanProgress);
        float middleOpacity = middleRingOpacity(scanProgress);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, middleOpacity);
        for (int i = 0; i < segments; i++) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians((i * 4.5f) + (rotation * 0.5f))));
            blit(graphics, SCAN_RING_MIDDLE, -SCAN_RING_MIDDLE_WIDTH / 2, -SCAN_RING_MIDDLE_HEIGHT / 2, SCAN_RING_MIDDLE_WIDTH, SCAN_RING_MIDDLE_HEIGHT, 0, 0, 100, 1, 100, 1);
            graphics.pose().popPose();
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.78f);
        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-innerRotation)));
        blit(graphics, SCAN_RING_INNER, -SCAN_RING_INNER_DIAMETER / 2, -SCAN_RING_INNER_DIAMETER / 2, SCAN_RING_INNER_DIAMETER, SCAN_RING_INNER_DIAMETER, 0, 0, 84, 84, 84, 84);
        graphics.pose().popPose();

        if (scanOpening) {
            drawScanPointers(graphics, innerRotation, scanProgress);
        }

        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static int middleRingSegments(float scanProgress) {
        if (scanProgress <= 0.0f || scanProgress < 20.0f) {
            return 40;
        }
        return Math.max(1, Math.min(40, (int) Math.floor((scanProgress - 20.0f) / 2.0f)));
    }

    private static float middleRingOpacity(float scanProgress) {
        if (scanProgress <= 0.0f) {
            return 0.78f;
        }
        if (scanProgress < 20.0f) {
            return Math.max(0.08f, 0.78f - (scanProgress * 0.035f));
        }
        return 0.78f;
    }

    private static void drawScanPointers(GuiGraphics graphics, float innerRotation, float scanProgress) {
        float opacity = Math.max(0.0f, Math.min(0.86f, scanProgress * 0.035f));
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, opacity);
        graphics.pose().pushPose();
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(innerRotation * 0.5f)));
        blit(graphics, POINTER, -POINTER_WIDTH - POINTER_OFFSET, -POINTER_HEIGHT / 2, POINTER_WIDTH, POINTER_HEIGHT, 0, 0, POINTER_WIDTH, POINTER_HEIGHT, POINTER_WIDTH * 2, POINTER_HEIGHT);
        blit(graphics, POINTER, POINTER_OFFSET, -POINTER_HEIGHT / 2, POINTER_WIDTH, POINTER_HEIGHT, POINTER_WIDTH, 0, POINTER_WIDTH, POINTER_HEIGHT, POINTER_WIDTH * 2, POINTER_HEIGHT);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.78f);
    }

    private static void drawStatusStrip(GuiGraphics graphics, Minecraft minecraft, int centerX, int centerY) {
        int charge = AuraReaderClientState.getCharge();
        int maxCharge = AuraReaderClientState.getMaxCharge();
        int filled = Math.round(BAR_WIDTH * AuraReaderClientState.getChargeFraction());
        int percent = Math.round(AuraReaderClientState.getChargeFraction() * 100.0f);

        int x = centerX - CENTER_INFO_FRAME_WIDTH / 2;
        int y = centerY - (SCAN_RING_OUTER_DIAMETER / 2) - CENTER_INFO_FRAME_HEIGHT - 28;

        blit(graphics, CENTER_INFO_FRAME, x, y, CENTER_INFO_FRAME_WIDTH, CENTER_INFO_FRAME_HEIGHT, 0, CENTER_INFO_FRAME_HEIGHT * 5, 128, 16, 128, 96);
        drawHudString(graphics, minecraft, "CHG", x + 12, y + 5, TEXT_PRIMARY);

        int barX = x + 36;
        int barY = y + 7;
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF1D2430);
        graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, chargeColor(charge, maxCharge));
        drawHudString(graphics, minecraft, percent + "%", barX + BAR_WIDTH + 6, y + 5, chargeTextColor(charge, maxCharge));

        drawPulseCooldownStrip(graphics, minecraft, centerX);
    }

    private static void drawPulseCooldownStrip(GuiGraphics graphics, Minecraft minecraft, int centerX) {
        float cooldownFraction = AuraReaderClientState.getPulseCooldownFraction();
        float readyFraction = 1.0f - cooldownFraction;
        int filled = Math.round(BAR_WIDTH * readyFraction);
        int x = centerX - CENTER_INFO_FRAME_WIDTH / 2;
        int y = 16;

        blit(graphics, CENTER_INFO_FRAME, x, y, CENTER_INFO_FRAME_WIDTH, CENTER_INFO_FRAME_HEIGHT, 0, CENTER_INFO_FRAME_HEIGHT * 5, 128, 16, 128, 96);
        drawHudString(graphics, minecraft, "PLS", x + 12, y + 5, TEXT_PRIMARY);

        int barX = x + 36;
        int barY = y + 7;
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF1D2430);
        graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, pulseCooldownColor(cooldownFraction));

        String label = AuraReaderClientState.getPulseCooldownLabel();
        drawHudString(graphics, minecraft, label, barX + BAR_WIDTH + 6, y + 5, pulseCooldownTextColor(cooldownFraction));
    }

    private static void drawInfoPanels(GuiGraphics graphics, Minecraft minecraft, int centerX, int centerY, int width, int height) {
        drawInfoPanel(graphics, minecraft, false, 1, centerX + 57, centerY - 26, "MODE", displayName(AuraReaderClientState.getMode()), false);

        AuraReaderClientState.ReadingSnapshot reading = AuraReaderClientState.getPrimaryReading();
        if (reading == null) {
            return;
        }

        drawInfoPanel(graphics, minecraft, true, 0, centerX - 120, centerY - 80, "AURA", diagnosticLine(), true);
        drawInfoPanel(graphics, minecraft, true, 2, centerX - 177, centerY + 6, "TARGET", targetLine(), true);
        drawInfoPanel(graphics, minecraft, false, 3, centerX + 28, centerY + 25, "SIGNAL", signalLine(), true);
    }

    private static void drawInfoPanel(GuiGraphics graphics, Minecraft minecraft, boolean leftSide, int tier, int x, int y, String title, String value, boolean animate) {
        int frameWidth = infoFrameWidth(tier);
        int frameHeight = infoFrameHeight(tier);
        int animationStep = animate ? AuraReaderClientState.getPanelAnimationStep() : INFO_FRAME_FINAL_STEP;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.95f);
        blit(
                graphics,
                infoFrameResource(leftSide, tier),
                x,
                y,
                frameWidth,
                frameHeight,
                0,
                frameHeight * animationStep,
                frameWidth,
                frameHeight,
                frameWidth,
                frameHeight * 10
        );
        //RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (animate && !AuraReaderClientState.shouldShowPanelText()) {
            return;
        }

        int textMaxWidth = isInnerInfoFrame(tier) ? INNER_INFO_FRAME_WIDTH - INNER_INFO_FRAME_STEM_WIDTH - 12 : frameWidth - 12;
        String cleanValue = trimToWidth(minecraft, value, textMaxWidth);
        int titleWidth = hudTextWidth(minecraft, title);
        int valueWidth = hudTextWidth(minecraft, cleanValue);
        int textCenterX = x + textCenterOffset(leftSide, tier);
        int titleY = titleY(y, tier);
        int valueY = valueY(y, tier);
        drawHudString(graphics, minecraft, title, textCenterX - titleWidth / 2, titleY, TEXT_SECONDARY);
        drawHudString(graphics, minecraft, cleanValue, textCenterX - valueWidth / 2, valueY, TEXT_PRIMARY);
    }

    private static int infoFrameWidth(int tier) {
        return isInnerInfoFrame(tier) ? INNER_INFO_FRAME_WIDTH : OUTER_INFO_FRAME_WIDTH;
    }

    private static int infoFrameHeight(int tier) {
        return isInnerInfoFrame(tier) ? INNER_INFO_FRAME_HEIGHT : OUTER_INFO_FRAME_HEIGHT;
    }

    private static boolean isInnerInfoFrame(int tier) {
        return tier == 1 || tier == 2;
    }

    private static int textCenterOffset(boolean leftSide, int tier) {
        if (!isInnerInfoFrame(tier)) {
            return OUTER_INFO_FRAME_WIDTH / 2;
        }
        int stemAdjustedCenter = (INNER_INFO_FRAME_WIDTH - INNER_INFO_FRAME_STEM_WIDTH) / 2;
        return leftSide ? stemAdjustedCenter : stemAdjustedCenter + INNER_INFO_FRAME_STEM_WIDTH;
    }

    private static int titleY(int y, int tier) {
        if (isInnerInfoFrame(tier)) {
            return y + 3;
        }
        return tier == 3 ? y + 31 : y + 6;
    }

    private static int valueY(int y, int tier) {
        if (isInnerInfoFrame(tier)) {
            return y + 11;
        }
        return tier == 3 ? y + 42 : y + 18;
    }

    private static String displayName(AuraReaderMode mode) {
        return switch (mode) {
            case PASSIVE_AURA -> "Passive Aura";
            case PULSE_SCAN -> "Pulse Scan";
            case SIGNAL_TRACKING -> "Signal Tracking";
            case ENTITY_ANALYSIS -> "Entity Analysis";
            case ANOMALY_SCAN -> "Anomaly Scan";
        };
    }

    private static String targetLine() {
        AuraReaderClientState.ReadingSnapshot reading = AuraReaderClientState.getPrimaryReading();
        if (reading != null && !reading.label().isBlank()) {
            return reading.label();
        }

        AuraReaderLockType lockType = AuraReaderClientState.getLockType();
        return switch (lockType) {
            case ENTITY -> "Entity Lock";
            case UMBRAFALL_SITE -> "Umbrafall Lock";
            case BLOCK -> "Block Lock";
            case NONE -> "No Target";
        };
    }

    private static String signalLine() {
        AuraReaderClientState.ReadingSnapshot reading = AuraReaderClientState.getPrimaryReading();
        if (reading != null && !reading.distanceBand().isBlank()) {
            String lockPrefix = AuraReaderClientState.getLockType() == AuraReaderLockType.NONE ? "" : "LOCK ";
            if (reading.verticalHint().isBlank() || "Level".equals(reading.verticalHint())) {
                return lockPrefix + reading.distanceBand();
            }
            return lockPrefix + reading.distanceBand() + " " + reading.verticalHint();
        }

        String selectedSignal = AuraReaderClientState.getSelectedSignal();
        if (selectedSignal == null || selectedSignal.isBlank()) {
            return AuraReaderClientState.isTracking() ? "Tracking" : "No Signal";
        }
        int separator = Math.max(selectedSignal.lastIndexOf(':'), selectedSignal.lastIndexOf('/'));
        return separator >= 0 && separator + 1 < selectedSignal.length()
                ? selectedSignal.substring(separator + 1)
                : selectedSignal;
    }

    private static String diagnosticLine() {
        AuraReaderClientState.ReadingSnapshot reading = AuraReaderClientState.getPrimaryReading();
        if (reading != null && !reading.status().isBlank()) {
            return reading.status();
        }

        return switch (AuraReaderClientState.getMode()) {
            case PASSIVE_AURA -> "Ambient Scan";
            case PULSE_SCAN -> "Pulse Ready";
            case SIGNAL_TRACKING -> AuraReaderClientState.isTracking() ? "Signal Lock" : "Standby";
            case ENTITY_ANALYSIS -> "Analysis";
            case ANOMALY_SCAN -> "Anomaly";
        };
    }

    private static String trimToWidth(Minecraft minecraft, String value, int maxWidth) {
        if (hudTextWidth(minecraft, value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && hudTextWidth(minecraft, value.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : value.substring(0, end) + ellipsis;
    }

    private static int hudTextWidth(Minecraft minecraft, String value) {
        return minecraft.font.width(hudText(value));
    }

    private static void drawHudString(GuiGraphics graphics, Minecraft minecraft, String value, int x, int y, int color) {
        graphics.drawString(minecraft.font, hudText(value), x, y, color, true);
    }

    private static Component hudText(String value) {
        return Component.literal(value == null ? "" : value).withStyle(style -> style.withFont(HUD_FONT));
    }

    private static int chargeColor(int charge, int maxCharge) {
        float fraction = maxCharge <= 0 ? 0.0f : (float) charge / (float) maxCharge;
        if (fraction < 0.2f) {
            return 0xFFE05A6F;
        }
        if (fraction < 0.5f) {
            return 0xFFE4C15D;
        }
        return 0xFF4FE3E8;
    }

    private static int chargeTextColor(int charge, int maxCharge) {
        float fraction = maxCharge <= 0 ? 0.0f : (float) charge / (float) maxCharge;
        if (fraction < 0.2f) {
            return TEXT_DANGER;
        }
        if (fraction < 0.5f) {
            return TEXT_WARNING;
        }
        return TEXT_PRIMARY;
    }

    private static int pulseCooldownColor(float cooldownFraction) {
        return cooldownFraction > 0.0f ? 0xFFE4C15D : 0xFF4FE3E8;
    }

    private static int pulseCooldownTextColor(float cooldownFraction) {
        return cooldownFraction > 0.0f ? TEXT_WARNING : TEXT_PRIMARY;
    }

    private static ResourceLocation infoFrameResource(boolean leftSide, int tier) {
        return cobblemon("textures/gui/pokedex/scan/scan_info_frame_" + (leftSide ? "left" : "right") + "_" + tier + ".png");
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int uOffset, int vOffset, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(texture, x, y, width, height, (float) uOffset, (float) vOffset, regionWidth, regionHeight, textureWidth, textureHeight);
    }
}
