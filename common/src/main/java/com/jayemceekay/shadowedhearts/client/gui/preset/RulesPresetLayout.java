package com.jayemceekay.shadowedhearts.client.gui.preset;

/** NPC-style workspace geometry; review text never displaces the fixed action and status rows. */
record RulesPresetLayout(int x, int y, int width, int height) {
    static RulesPresetLayout forScreen(int width, int height) {
        int frameWidth = Math.max(1, Math.min(560, width - 16));
        int frameHeight = Math.max(1, Math.min(330, height - 16));
        return new RulesPresetLayout((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
    }

    int contentX() { return x + 6; }
    int contentWidth() { return Math.max(1, width - 12); }
    int headerY() { return y + 7; }
    int doneWidth() { return 42; }
    int doneX() { return contentX() + contentWidth() - doneWidth(); }
    int presetsTabWidth() { return Math.min(78, (contentWidth() - doneWidth() - 10) / 2); }
    int snapshotsTabX() { return contentX() + presetsTabWidth() + 4; }
    int snapshotsTabWidth() { return Math.min(88, doneX() - 6 - snapshotsTabX()); }
    int selectorY() { return y + 28; }
    int filterY() { return y + 50; }
    boolean showsPresetList() { return width >= 400 && height >= 216; }
    int railWidth() { return Math.min(164, (width - 18) / 3); }
    int railY() { return filterY(); }
    int railHeight() { return controlsY() - 8 - railY(); }
    int presetY(int index) { return railY() + 16 + index * 22; }
    boolean showsOverview() { return showsPresetList() && railHeight() >= 174; }
    int overviewY() { return railY() + 108; }
    int overviewHeight() { return Math.max(1, railHeight() - 110); }
    int reviewX() { return contentX() + (showsPresetList() ? railWidth() + 6 : 0); }
    int reviewWidth() { return contentX() + contentWidth() - reviewX(); }
    int reviewY() { return filterY() + 22; }
    int reviewHeight() { return Math.max(1, controlsY() - reviewY() - 8); }
    int statusY() { return y + height - 11; }
    int controlsY() { return y + height - 30; }
    int buttonWidth() { return Math.max(1, Math.min(100, (contentWidth() - 6) / 2)); }
    int previewX() { return contentX() + contentWidth() - buttonWidth() * 2 - 6; }
    int applyX() { return contentX() + contentWidth() - buttonWidth(); }
}
