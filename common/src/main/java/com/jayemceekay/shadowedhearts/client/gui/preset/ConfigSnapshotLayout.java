package com.jayemceekay.shadowedhearts.client.gui.preset;

/** Shared row edges and a compact selector keep the review usable at high GUI scales. */
record ConfigSnapshotLayout(RulesPresetLayout frame) {
    static final int CONTROL_HEIGHT = 16;
    static final int ENTRY_HEIGHT = 20;
    int gap() { return frame.height() < 216 ? 4 : 6; }
    int scopeY() { return frame.headerY() + CONTROL_HEIGHT + gap(); }
    int nameY() { return scopeY() + CONTROL_HEIGHT + gap(); }
    int bodyY() { return nameY() + CONTROL_HEIGHT + gap(); }
    int sideActionWidth() { return Math.min(86, frame.contentWidth() / 3); }
    int sideActionX() { return frame.contentX() + frame.contentWidth() - sideActionWidth(); }
    int inputWidth() { return sideActionX() - gap() - frame.contentX(); }
    boolean showsList() { return frame.width() >= 400 && frame.height() >= 216; }
    int listWidth() { return Math.min(200, frame.contentWidth() / 2 - gap()); }
    int bodyBottom() { return frame.controlsY() - gap(); }
    int navigationY() { return bodyBottom() - CONTROL_HEIGHT; }
    int rows() { return Math.max(1, (navigationY() - gap() - bodyY() + 4) / (ENTRY_HEIGHT + 4)); }
    int entryY(int index) { return bodyY() + index * (ENTRY_HEIGHT + 4); }
    int reviewX() { return frame.contentX() + (showsList() ? listWidth() + gap() : 0); }
    int reviewY() { return showsList() ? bodyY() : bodyY() + CONTROL_HEIGHT + gap(); }
    int reviewWidth() { return frame.contentX() + frame.contentWidth() - reviewX(); }
    int reviewHeight() { return bodyBottom() - reviewY(); }
    int footerX(int index) { return frame.contentX() + index * ((frame.contentWidth() - 2 * gap()) / 3 + gap()); }
    int footerWidth(int index) {
        return index == 2 ? frame.contentX() + frame.contentWidth() - footerX(index) : (frame.contentWidth() - 2 * gap()) / 3;
    }
}
