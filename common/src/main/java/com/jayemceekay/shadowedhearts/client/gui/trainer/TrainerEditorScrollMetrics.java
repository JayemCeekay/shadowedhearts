package com.jayemceekay.shadowedhearts.client.gui.trainer;

/** Item-based scroll range and pixel geometry, independent of Minecraft widgets. */
record TrainerEditorScrollMetrics(int trackSize, int thumbSize, int thumbOffset,
                                  int maximum, int position, int visibleSize) {
    private static final int MIN_THUMB_SIZE = 16;

    static TrainerEditorScrollMetrics forRange(int trackSize, int contentSize, int visibleSize, int position) {
        int track = Math.max(0, trackSize);
        int content = Math.max(0, contentSize);
        int visible = Math.max(1, visibleSize);
        int maximum = Math.max(0, content - visible);
        int clamped = Math.clamp(position, 0, maximum);
        int thumb = track;
        if (maximum > 0 && track > 1) {
            int minimum = Math.min(MIN_THUMB_SIZE, track - 1);
            thumb = Math.clamp((int) Math.round((double) track * visible / content), minimum, track - 1);
        }
        int offset = maximum == 0 ? 0 : (int) Math.round((double) clamped * (track - thumb) / maximum);
        return new TrainerEditorScrollMetrics(track, thumb, offset, maximum, clamped, visible);
    }

    int travel() { return trackSize - thumbSize; }

    int positionAt(double thumbOffset) {
        if (maximum == 0 || travel() == 0 || Double.isNaN(thumbOffset)) return position;
        double offset = Math.clamp(thumbOffset, 0, travel());
        return (int) Math.round(offset * maximum / travel());
    }
}
