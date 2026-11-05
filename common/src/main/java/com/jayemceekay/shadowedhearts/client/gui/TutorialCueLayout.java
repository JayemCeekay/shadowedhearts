package com.jayemceekay.shadowedhearts.client.gui;

import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHudRect;

/** Reattaches tutorial cues after the popup has been wrapped, scaled and clamped. */
public final class TutorialCueLayout {
    public enum Side { TOP, BOTTOM, LEFT, RIGHT }
    public record Cue(Side side, int targetX, int targetY, AuraReaderHudRect highlight) {}

    private TutorialCueLayout() {}

    public static Cue resolve(int width, int height, AuraReaderHudRect popup, AuraReaderHudRect highlight,
                              int targetX, int targetY) {
        var safe = new AuraReaderHudRect(1, 1, Math.max(1, width - 1), Math.max(1, height - 1));
        var target = highlight == null ? null : highlight.clamp(safe);
        int x = Math.max(1, Math.min(targetX, width - 2));
        int y = Math.max(1, Math.min(targetY, height - 2));
        if (target != null && (target.width() == 0 || target.height() == 0 || target.intersects(popup))) {
            return new Cue(null, x, y, null);
        }
        Side side = y < popup.top() ? Side.TOP : y >= popup.bottom() ? Side.BOTTOM
                : x < popup.left() ? Side.LEFT : x >= popup.right() ? Side.RIGHT : null;
        return new Cue(side, x, y, target);
    }
}
