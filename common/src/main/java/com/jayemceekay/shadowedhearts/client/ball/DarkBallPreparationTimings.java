package com.jayemceekay.shadowedhearts.client.ball;

/** Immutable durations for the source geometry and physical volume preparation. */
record DarkBallPreparationTimings(long queueMicros, long decodeMicros,
        long voxelMicros, long sourceMicros) {
    static DarkBallPreparationTimings completed(long submitted, long started,
            long decoded, long voxelBuilt, long completed) {
        return new DarkBallPreparationTimings(micros(submitted, started),
                micros(started, decoded), micros(decoded, voxelBuilt), micros(voxelBuilt, completed));
    }

    String summary() {
        return "cpu_us{queue=" + queueMicros + ",decode=" + decodeMicros
                + ",voxel=" + voxelMicros + ",source=" + sourceMicros + "}";
    }

    private static long micros(long start, long end) {
        return Math.max(0L, (end - start) / 1_000L);
    }
}
