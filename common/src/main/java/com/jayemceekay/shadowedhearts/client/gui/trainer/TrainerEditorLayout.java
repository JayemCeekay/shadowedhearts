package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

/** Logical-pixel form geometry, independent of Minecraft widgets and GUI scale. */
public record TrainerEditorLayout(int contentLeft, int formWidth, int columns, int rowsPerColumn,
                                  int summaryX, int summaryWidth, int editBottom,
                                  int fieldTop, int rowStep, int fieldHeight) {
    public static final int FIELD_TOP = 68;
    public static final int FIELD_HEIGHT = 27;
    public static final int ROW_STEP = 30;
    public static final int COLUMN_GAP = 10;
    private static final int SUMMARY_GAP = 12;

    public record Bounds(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    public static TrainerEditorLayout forScreen(int width, int height) {
        int left = 12;
        int availableWidth = Math.max(1, width - left * 2);
        int summaryWidth = width >= 720 ? Math.min(260, availableWidth * 35 / 100) : 0;
        int formWidth = availableWidth - (summaryWidth > 0 ? summaryWidth + SUMMARY_GAP : 0);
        int columns = formWidth >= 360 ? 2 : 1;
        int editBottom = height - 46;
        // The final field needs its 27 pixels, but no trailing 3-pixel row gap.
        int rows = Math.max(1, (editBottom - FIELD_TOP + ROW_STEP - FIELD_HEIGHT) / ROW_STEP);
        int summaryX = summaryWidth > 0 ? left + formWidth + SUMMARY_GAP : width - left;
        return new TrainerEditorLayout(left, formWidth, columns, rows, summaryX, summaryWidth, editBottom,
                FIELD_TOP, ROW_STEP, FIELD_HEIGHT);
    }

    /** The roster inspector uses compact label/control rows where both remain readable. */
    public static TrainerEditorLayout forArea(Bounds area) {
        boolean inline = area.width() >= 220;
        int step = inline ? 20 : ROW_STEP;
        int height = inline ? 16 : FIELD_HEIGHT;
        int rows = Math.max(1, (area.height() + step - height) / step);
        return new TrainerEditorLayout(area.x(), area.width(), 1, rows, area.right(), 0, area.bottom(),
                area.y(), step, height);
    }

    public boolean inlineLabels() { return fieldHeight == 16; }

    public boolean hasSummary() { return summaryWidth > 0; }
    public int visibleFieldsCount() { return columns * rowsPerColumn; }
    public int fieldWidth() { return fieldWidth(0); }
    private int fieldWidth(int rightInset) {
        return Math.max(1, (formWidth - rightInset - (columns - 1) * COLUMN_GAP) / columns);
    }

    /** Bounds include the label at y and the control at y + 11. Slots fill across each row. */
    public Bounds field(int localIndex) {
        return field(localIndex, 0);
    }

    /** Reserve a gutter inside the form without moving its summary or changing column order. */
    public Bounds field(int localIndex, int rightInset) {
        if (localIndex < 0 || localIndex >= visibleFieldsCount()) {
            throw new IndexOutOfBoundsException(tr("screen.shadowedhearts.trainer_editor.field_outside_form", localIndex));
        }
        int column = localIndex % columns;
        int row = localIndex / columns;
        return new Bounds(contentLeft + column * (fieldWidth(rightInset) + COLUMN_GAP),
                fieldTop + row * rowStep, fieldWidth(rightInset), fieldHeight);
    }
}
