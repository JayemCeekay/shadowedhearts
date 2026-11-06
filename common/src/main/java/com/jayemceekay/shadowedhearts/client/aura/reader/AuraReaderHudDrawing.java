package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** Shared HUD raster operations; callers supply opacity without changing global render state. */
final class AuraReaderHudDrawing {
    static final int SCANNER_PANEL_BORDER = 0xE027ECFF;
    private static final int SCANNER_PANEL_FILL = 0x6627ECFF;
    private static final ResourceLocation SCANNER_TEXT_FONT =
            CobblemonResources.INSTANCE.getDEFAULT_LARGE();
    private static final ResourceLocation SCANNER_BRACKETS = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts", "textures/gui/aura_reader/scanner_brackets.png");

    private AuraReaderHudDrawing() {
    }

    static int centerX(AuraReaderHudRect rect) {
        return rect.left() + rect.width() / 2;
    }

    static int centerY(AuraReaderHudRect rect) {
        return rect.top() + rect.height() / 2;
    }

    static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    static void fill(
            GuiGraphics graphics,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int color,
            float frameAlpha) {
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

    static void blit(
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
            float alpha,
            float frameAlpha) {
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

    static void drawScannerPanelBrackets(
            GuiGraphics graphics, int x, int y, int width, int height, float alpha, float frameAlpha) {
        int borderColor = AuraReaderHudTheme.multiplyAlpha(SCANNER_PANEL_BORDER, alpha);
        int fillColor = AuraReaderHudTheme.multiplyAlpha(SCANNER_PANEL_FILL, alpha);
        // The left/right halves already contain translucent body pixels; only
        // fill between them so the joins do not receive a second alpha layer.
        fill(graphics, x + 5, y + 1, x + width - 5, y + height - 1, fillColor, frameAlpha);
        fill(graphics, x + 5, y, x + width - 5, y + 1, borderColor, frameAlpha);
        fill(graphics, x + 5, y + height - 1, x + width - 5, y + height, borderColor, frameAlpha);
        for (int side = 0; side < 2; side++) {
            int capX = side == 0 ? x : x + width - 5;
            int capV = side * 16;
            // Preserve the two corner rows; stretch only the straight middle.
            blit(graphics, SCANNER_BRACKETS, capX, y, 5, 2, 0, capV, 5, 2, 5, 32, alpha, frameAlpha);
            blit(graphics, SCANNER_BRACKETS, capX, y + 2, 5, height - 4,
                    0, capV + 2, 5, 1, 5, 32, alpha, frameAlpha);
            blit(graphics, SCANNER_BRACKETS, capX, y + height - 2, 5, 2,
                    0, capV + 14, 5, 2, 5, 32, alpha, frameAlpha);
        }
    }

    static int scannerTextWidth(Minecraft minecraft, String value) {
        return minecraft.font.width(scannerText(value));
    }

    private static MutableComponent scannerText(String value) {
        return Component.literal(value == null ? "" : value)
                .withStyle(style -> style.withFont(SCANNER_TEXT_FONT).withBold(true));
    }

    static void drawScannerString(
            GuiGraphics graphics,
            String value,
            int x,
            int y,
            int color,
            boolean centered,
            float frameAlpha) {
        int adjustedColor = AuraReaderHudTheme.multiplyAlpha(color, frameAlpha);
        if (value.isEmpty() || FastColor.ARGB32.alpha(adjustedColor) <= 3) {
            return;
        }
        GuiUtilsKt.drawText(graphics, SCANNER_TEXT_FONT, scannerText(value), x, y,
                centered, adjustedColor, true, null, null);
    }
}
