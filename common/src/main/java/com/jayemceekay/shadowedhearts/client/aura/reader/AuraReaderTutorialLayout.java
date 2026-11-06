package com.jayemceekay.shadowedhearts.client.aura.reader;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Places measured tutorial text in GUI pixels, independently of the window's pixel scale. */
public final class AuraReaderTutorialLayout {
    private static final int MARGIN = 8;
    private static final int GAP = 8;
    private static final int PREFERRED_WIDTH = 300;
    private static final int MIN_SIDE_WIDTH = 120;

    private AuraReaderTutorialLayout() {}

    public static Placement resolve(int width, int height, int footerHeight,
                                    AuraReaderHudRect reticle, List<AuraReaderHudRect> hud,
                                    IntUnaryOperator heightForWidth) {
        int inset = Math.min(MARGIN, Math.max(0, (width - 1) / 2));
        var safe = new AuraReaderHudRect(inset, Math.min(MARGIN, height - 1),
                Math.max(inset + 1, width - inset), Math.max(Math.min(MARGIN, height - 1) + 1,
                height - footerHeight - MARGIN));
        int preferredWidth = Math.min(PREFERRED_WIDTH, safe.width());
        int leftWidth = Math.min(preferredWidth, reticle.left() - GAP - safe.left());
        int rightWidth = Math.min(preferredWidth, safe.right() - reticle.right() - GAP);
        Placement best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int textWidth : new int[]{preferredWidth, leftWidth, rightWidth}) {
            if (textWidth <= 0 || (textWidth < MIN_SIDE_WIDTH && textWidth != preferredWidth)) continue;
            int textHeight = Math.max(1, heightForWidth.applyAsInt(textWidth));
            double scale = Math.min(1.0, safe.height() / (double) textHeight);
            int panelWidth = (int) Math.ceil(textWidth * scale);
            int panelHeight = (int) Math.ceil(textHeight * scale);
            int centerX = (width - panelWidth) / 2;
            int centerY = (height - panelHeight) / 2;
            // Prefer below the scanner, then either side, then above it. Clamp only
            // after measuring all wrapped text and the step counter.
            int[][] positions = {
                    {centerX, reticle.bottom() + GAP},
                    {reticle.right() + GAP, centerY},
                    {reticle.left() - GAP - panelWidth, centerY},
                    {centerX, reticle.top() - GAP - panelHeight},
                    {safe.right() - panelWidth, safe.bottom() - panelHeight},
                    {safe.left(), safe.bottom() - panelHeight},
                    {centerX, safe.top()}
            };
            for (int index = 0; index < positions.length; index++) {
                int[] position = positions[index];
                var bounds = new AuraReaderHudRect(position[0], position[1],
                        position[0] + panelWidth, position[1] + panelHeight).clamp(safe);
                long overlap = overlap(bounds, reticle);
                for (var area : hud) overlap += overlap(bounds, area);
                // Any clear native-size position beats overlap. On cramped screens,
                // retain readable text and minimize the covered HUD area instead.
                double score = overlap * 1000.0 + (1.0 - scale) * 100000.0
                        + (preferredWidth - textWidth) + index;
                if (score < bestScore) {
                    bestScore = score;
                    best = new Placement(bounds, textWidth, textHeight, scale);
                }
            }
        }
        return best;
    }

    private static long overlap(AuraReaderHudRect a, AuraReaderHudRect b) {
        return (long) Math.max(0, Math.min(a.right(), b.right()) - Math.max(a.left(), b.left()))
                * Math.max(0, Math.min(a.bottom(), b.bottom()) - Math.max(a.top(), b.top()));
    }

    /** contentHeight includes the step counter; bounds include its scaled footprint. */
    public record Placement(AuraReaderHudRect bounds, int contentWidth, int contentHeight, double scale) {}
}
