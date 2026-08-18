package com.jayemceekay.shadowedhearts.client.ball;

import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;

import java.util.Arrays;

/**
 * Small asynchronous timer-query ring for Dark Ball's direct GPU stages.
 *
 * <p>Results are consumed only after the driver reports them available. This
 * class deliberately never requests a pending result and never calls
 * {@code glFinish}, so diagnostics cannot turn into a CPU/GPU synchronization
 * point.</p>
 */
final class DarkBallGpuTimer implements AutoCloseable {

    static final String ENABLED_PROPERTY =
            "shadowedhearts.darkBallGpuTiming";
    private static final int QUERY_RING_SIZE = 4;
    private static final long NANOS_PER_MICROSECOND = 1_000L;

    enum Stage {
        SPLAT_DRAW,
        SPLAT_RESOLVE,
        SIPHON_DRAW,
        DEPTH_RESTORE
    }

    private final int[][] queryIds =
            new int[Stage.values().length][QUERY_RING_SIZE];
    private final boolean[][] pending =
            new boolean[Stage.values().length][QUERY_RING_SIZE];
    private final int[] writeSlots = new int[Stage.values().length];
    private final long[] latestMicros = new long[Stage.values().length];
    private final boolean enabled = Boolean.parseBoolean(
            System.getProperty(ENABLED_PROPERTY, "false"));

    private boolean capabilityChecked;
    private boolean supported;
    private boolean coreTimerQuery;
    private Stage activeStage;
    private int activeSlot = -1;

    DarkBallGpuTimer() {
        Arrays.fill(latestMicros, -1L);
    }

    void begin(Stage stage) {
        if (stage == null || !ensureSupported() || activeStage != null) {
            return;
        }
        pollAvailable(stage);
        int stageIndex = stage.ordinal();
        int slot = writeSlots[stageIndex];
        if (pending[stageIndex][slot]) {
            return;
        }
        int queryId = queryIds[stageIndex][slot];
        if (queryId == 0) {
            queryId = GL15.glGenQueries();
            queryIds[stageIndex][slot] = queryId;
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queryId);
        activeStage = stage;
        activeSlot = slot;
    }

    void end(Stage stage) {
        if (activeStage != stage || activeSlot < 0) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        int stageIndex = stage.ordinal();
        pending[stageIndex][activeSlot] = true;
        writeSlots[stageIndex] =
                (activeSlot + 1) % QUERY_RING_SIZE;
        activeStage = null;
        activeSlot = -1;
    }

    long latestMicros(Stage stage) {
        if (stage == null || !ensureSupported()) {
            return -1L;
        }
        pollAvailable(stage);
        return latestMicros[stage.ordinal()];
    }

    private boolean ensureSupported() {
        if (!enabled) {
            return false;
        }
        if (!capabilityChecked) {
            capabilityChecked = true;
            GLCapabilities capabilities = GL.getCapabilities();
            coreTimerQuery = capabilities.OpenGL33;
            supported = coreTimerQuery
                    || capabilities.GL_ARB_timer_query;
        }
        return supported;
    }

    private void pollAvailable(Stage stage) {
        int stageIndex = stage.ordinal();
        for (int slot = 0; slot < QUERY_RING_SIZE; slot++) {
            if (!pending[stageIndex][slot]) {
                continue;
            }
            int queryId = queryIds[stageIndex][slot];
            int available = GL15.glGetQueryObjecti(
                    queryId,
                    GL15.GL_QUERY_RESULT_AVAILABLE);
            if (available == 0) {
                continue;
            }
            long elapsedNanos = coreTimerQuery
                    ? GL33.glGetQueryObjectui64(
                    queryId, GL15.GL_QUERY_RESULT)
                    : ARBTimerQuery.glGetQueryObjectui64(
                    queryId, GL15.GL_QUERY_RESULT);
            latestMicros[stageIndex] = Math.max(
                    elapsedNanos / NANOS_PER_MICROSECOND,
                    0L);
            pending[stageIndex][slot] = false;
        }
    }

    @Override
    public void close() {
        if (!capabilityChecked || !supported) {
            return;
        }
        if (activeStage != null) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            activeStage = null;
            activeSlot = -1;
        }
        for (int[] stageQueries : queryIds) {
            for (int queryId : stageQueries) {
                if (queryId != 0) {
                    GL15.glDeleteQueries(queryId);
                }
            }
        }
        supported = false;
    }
}
