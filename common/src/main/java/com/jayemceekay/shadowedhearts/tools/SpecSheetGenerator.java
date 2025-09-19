package com.jayemceekay.shadowedhearts.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility to export a flat spec sheet PNG for the Trainers' Whistle order wheel (tween menu).
 *
 * It reproduces the layout math used by WhistleSelectionClient for a 1920x1080 reference canvas
 * (baseDim = 1080) and draws labeled boxes at their 100% sizes (no tween scaling).
 *
 * Output: Project Documents/whistle_order_wheel_spec_1920x1080.png
 */
public final class SpecSheetGenerator {
    private SpecSheetGenerator() {}

    public static void main(String[] args) throws IOException {
        final int W = 1920;
        final int H = 1080;
        final int baseDim = Math.min(W, H); // 1080 for 1920x1080
        final int cx = W / 2;
        final int cy = H / 2;

        // Sizes per spec (100% state)
        final int centerSize = Math.max(48, (int)Math.round(baseDim * 0.0833)); // 90
        final int gap = Math.max(8, (int)Math.round(baseDim * 0.0203)); // 22
        final int catH = Math.max(24, (int)Math.round(baseDim * 0.0390)); // 42
        final int catW = Math.max(140, (int)Math.round(baseDim * 0.2040)); // 220

        // Submenus at 100%
        final int combatCount = 3;
        final int spacing = Math.max(6, (int)Math.round(baseDim * 0.0093)); // 10
        final int subW = Math.max(50, (int)Math.round(baseDim * 0.0650)); // 70
        final int subH = Math.max(22, (int)Math.round(baseDim * 0.0296)); // 32

        final int posW = Math.max(70, Math.max((int)Math.round(baseDim * 0.0740), catW / 2)); // ≈ 110
        final int posH = Math.max(22, (int)Math.round(baseDim * 0.0259)); // 28

        final int utilW = Math.max(90, Math.max((int)Math.round(baseDim * 0.1020), catW / 2)); // ≈ 110
        final int utilH = Math.max(22, (int)Math.round(baseDim * 0.0259)); // 28

        final int ctxW = Math.min(Math.max(90, Math.max((int)Math.round(baseDim * 0.1020), catW / 2)), catW - 8); // ≤ catW-8
        final int ctxH = Math.max(22, (int)Math.round(baseDim * 0.0259)); // 28

        // Rects
        final int cX0 = cx - centerSize / 2, cY0 = cy - centerSize / 2, cX1 = cX0 + centerSize, cY1 = cY0 + centerSize;
        final int topX0 = cx - catW / 2, topY0 = cy - (centerSize / 2 + gap + catH), topX1 = topX0 + catW, topY1 = topY0 + catH;
        final int leftX0 = cx - (centerSize / 2 + gap + catW), leftY0 = cy - catH / 2, leftX1 = leftX0 + catW, leftY1 = leftY0 + catH;
        final int rightX0 = cx + (centerSize / 2 + gap), rightY0 = cy - catH / 2, rightX1 = rightX0 + catW, rightY1 = rightY0 + catH;
        final int bottomX0 = cx - catW / 2, bottomY0 = cy + (centerSize / 2 + gap), bottomX1 = bottomX0 + catW, bottomY1 = bottomY0 + catH;

        // Submenu positions (100%, no tween clamps)
        final int combatY = topY0 - gap - subH;
        final int combatFirstX = cx - (subW * combatCount + spacing * (combatCount - 1)) / 2;

        final int posX0 = leftX0 - gap - posW;
        final int posY0a = leftY0 - posH - 6; // upper
        final int posY0b = leftY0 + 6;        // lower

        final int utilX0 = rightX1 + gap;
        final int utilY0 = rightY0 + (catH - utilH) / 2;

        final int ctxY0 = bottomY1 + gap;
        final int ctxX0 = bottomX0 + (catW - ctxW) / 2;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Background
            g.setColor(new Color(0xFF, 0xFF, 0xFF));
            g.fillRect(0, 0, W, H);

            // Title
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("Segoe UI", Font.BOLD, 28));
            drawStringCentered(g, "Trainers' Whistle Order Wheel — Flat Spec (1920×1080)", W / 2, 44);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            drawStringCentered(g, "Base reference: baseDim = min(1920,1080) = 1080 — all sizes at 100% (no tweening)", W / 2, 68);

            // Helpers
            Color catColor = new Color(0x1E, 0x1E, 0x1E, 170);
            Color centerColor = new Color(0xAA, 0x44, 0x44);
            Color subColor = new Color(0x2A, 0x2A, 0x2A);
            Color outline = new Color(0x22, 0x22, 0x22);

            // Center (Cancel)
            fillRounded(g, cX0, cY0, centerSize, centerSize, 8, centerColor, outline);
            annotate(g, "Center — Cancel\n90 × 90", cx, cY0 - 18);

            // Categories
            fillRounded(g, topX0, topY0, catW, catH, 8, catColor, outline);
            fillRounded(g, leftX0, leftY0, catW, catH, 8, catColor, outline);
            fillRounded(g, rightX0, rightY0, catW, catH, 8, catColor, outline);
            fillRounded(g, bottomX0, bottomY0, catW, catH, 8, catColor, outline);
            labelCentered(g, "Combat", cx, topY0 + catH / 2 + 6);
            labelCentered(g, "Position", leftX0 + catW / 2, leftY0 + catH / 2 + 6);
            labelCentered(g, "Utility", rightX0 + catW / 2, rightY0 + catH / 2 + 6);
            labelCentered(g, "Context", bottomX0 + catW / 2, bottomY0 + catH / 2 + 6);
            annotate(g, "Category bars\n220 × 42", rightX1 + 200, topY0 + 4);

            // Gap indicator
            g.setColor(new Color(0x55, 0x99, 0xDD));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(cx, cY0, cx, topY1);
            g.drawLine(cX1, cy, leftX0, cy);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            drawStringCentered(g, "gap = 22 px", cx + 40, (cY0 + topY1) / 2);
            drawStringCentered(g, "gap = 22 px", (cX1 + leftX0) / 2, cy - 10);

            // Combat submenu (3 buttons)
            int x = combatFirstX;
            for (int i = 0; i < combatCount; i++) {
                fillRounded(g, x, combatY, subW, subH, 6, subColor, outline);
                String label = switch (i) {
                    case 0 -> "Attack";
                    case 1 -> "Guard";
                    default -> "Disengage";
                };
                labelCentered(g, label, x + subW / 2, combatY + subH / 2 + 4);
                x += subW + spacing;
            }
            annotate(g, "Combat submenu\n3 × (70 × 32) with 10 px spacing", cx, combatY - 18);

            // Position submenu (left)
            fillRounded(g, posX0, posY0a, posW, posH, 6, subColor, outline);
            labelCentered(g, "Move To", posX0 + posW / 2, posY0a + posH / 2 + 4);
            fillRounded(g, posX0, posY0b, posW, posH, 6, subColor, outline);
            labelCentered(g, "Hold Position", posX0 + posW / 2, posY0b + posH / 2 + 4);
            annotate(g, "Position submenu\n2 × (110 × 28)", posX0 - 10, leftY0);

            // Utility submenu (right)
            fillRounded(g, utilX0, utilY0, utilW, utilH, 6, subColor, outline);
            labelCentered(g, "Regroup to Me", utilX0 + utilW / 2, utilY0 + utilH / 2 + 4);
            annotate(g, "Utility submenu\n110 × 28", utilX0 + utilW + 10, utilY0 + utilH / 2);

            // Context submenu (bottom)
            fillRounded(g, ctxX0, ctxY0, ctxW, ctxH, 6, subColor, outline);
            labelCentered(g, "Hold At Me", ctxX0 + ctxW / 2, ctxY0 + ctxH / 2 + 4);
            annotate(g, "Context submenu\n110 × 28 (≤ catW − 8)", ctxX0 + ctxW / 2, ctxY0 + ctxH + 24);

            // Quick reference block
            int refX = 60, refY = 120, refW = 430, refH = 280;
            g.setColor(new Color(245, 248, 252));
            g.fillRoundRect(refX, refY, refW, refH, 12, 12);
            g.setColor(outline);
            g.drawRoundRect(refX, refY, refW, refH, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            int ty = refY + 28;
            g.drawString("Quick reference (percent of baseDim = 1080):", refX + 16, ty); ty += 26;
            g.drawString("Center: 8.33% (min 48px) → 90 × 90", refX + 16, ty); ty += 22;
            g.drawString("Category bars: 20.40% × 3.90% (mins 140 × 24) → 220 × 42", refX + 16, ty); ty += 22;
            g.drawString("Gap: 2.03% (min 8px) → 22", refX + 16, ty); ty += 22;
            g.drawString("Combat buttons: 6.50% × 2.96% (min 50 × 22) → 70 × 32", refX + 16, ty); ty += 22;
            g.drawString("Spacing between combat: 0.93% (min 6px) → 10", refX + 16, ty); ty += 22;
            g.drawString("Position/Utility/Context: 10.20% × 2.59% → 110 × 28", refX + 16, ty);

            // Footer
            g.setColor(new Color(0x66, 0x66, 0x66));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            drawStringCentered(g, "Designed at 100% size. The in-game UI tweens down to 50% during animation.", W / 2, H - 28);
        } finally {
            g.dispose();
        }

        File out = new File("Project Documents/whistle_order_wheel_spec_1920x1080.png");
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote spec sheet to: " + out.getAbsolutePath());
    }

    private static void fillRounded(Graphics2D g, int x, int y, int w, int h, int r, Color fill, Color outline) {
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape rr = new RoundRectangle2D.Float(x, y, w, h, r, r);
        g.setColor(fill);
        g.fill(rr);
        g.setColor(outline);
        g.draw(rr);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    private static void labelCentered(Graphics2D g, String text, int cx, int cy) {
        Font old = g.getFont();
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getAscent();
        g.setColor(Color.WHITE);
        g.drawString(text, cx - tw / 2, cy + th / 2 - 4);
        g.setFont(old);
    }

    private static void annotate(Graphics2D g, String text, int cx, int cy) {
        // Multi-line small gray text, centered
        g.setColor(new Color(60, 60, 60));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        FontMetrics fm = g.getFontMetrics();
        String[] lines = text.split("\\n");
        int totalH = lines.length * fm.getHeight();
        int y = cy - totalH / 2 + fm.getAscent();
        for (String line : lines) {
            int tw = fm.stringWidth(line);
            g.drawString(line, cx - tw / 2, y);
            y += fm.getHeight();
        }
    }

    private static void drawStringCentered(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getAscent();
        g.drawString(text, cx - tw / 2, cy + th / 2 - 4);
    }
}
