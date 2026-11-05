package com.jayemceekay.shadowedhearts.client.gui.trainer;

import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorLayout.Bounds;

/** Physical GUI coordinates shared by drawing, widgets, scrolling and hit testing. */
public record TrainerEditorWorkspace(Bounds frame, Bounds roster, Bounds detail, Bounds form,
                                     Bounds summary, boolean showsRoster) {
    public static TrainerEditorWorkspace forScreen(int screenWidth, int screenHeight, boolean editing) {
        int width = Math.max(1, Math.min(560, screenWidth - 16));
        int height = Math.max(1, Math.min(330, screenHeight - 16));
        Bounds frame = new Bounds((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        boolean rail = !editing || width >= 400;
        int railWidth = Math.max(90, Math.min(164, (width - 24) / 3));
        int top = frame.y() + 72;
        int bottom = frame.bottom() - 44;
        Bounds roster = new Bounds(frame.x() + 6, top, railWidth, Math.max(1, bottom - top));
        int detailLeft = rail ? roster.right() + 6 : frame.x() + 6;
        Bounds detail = new Bounds(detailLeft, top, Math.max(1, frame.right() - 6 - detailLeft), Math.max(1, bottom - top));
        boolean compact = height < 290;
        Bounds summary = new Bounds(detail.x() + 6, compact ? detail.bottom() : detail.bottom() - 26,
                Math.max(1, detail.width() - 12), compact ? 0 : 22);
        int formTop = detail.y() + (compact ? 4 : 30);
        int formBottom = compact ? detail.bottom() - 22 : summary.y() - 4;
        Bounds form = new Bounds(detail.x() + 6, formTop, Math.max(1, detail.width() - 12),
                Math.max(1, formBottom - formTop));
        return new TrainerEditorWorkspace(frame, roster, detail, form, summary, rail);
    }

    public int toolbarY() { return frame.y() + 50; }
    public boolean compact() { return frame.height() < 290; }
    public int footerY() { return frame.bottom() - 30; }
    public int statusY() { return frame.bottom() - 11; }
    public int rosterRows() { return Math.max(1, (roster.height() - 24) / 20); }
}
