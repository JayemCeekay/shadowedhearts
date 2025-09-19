package com.jayemceekay.shadowedhearts.runs;

import com.jayemceekay.shadowedhearts.world.RunBounds;
import com.jayemceekay.shadowedhearts.world.WorldspaceManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry for active runs. Minimal create/get/finish API. */
public final class RunRegistry {
    private RunRegistry() {}

    private static long counter = 0L;
    private static final Map<RunId, ActiveRun> RUNS = new ConcurrentHashMap<>();

    public static synchronized RunId createRun(RunParty party, RunConfig cfg) {
        RunId id = new RunId(++counter);
        RunBounds bounds = WorldspaceManager.allocateBounds(id);
        ActiveRun run = new ActiveRun(id, party, cfg, bounds, new RunState());
        RUNS.put(id, run);
        return id;
    }

    public static ActiveRun get(RunId id) {
        return RUNS.get(id);
    }

    public static void finish(RunId id) {
        ActiveRun run = RUNS.remove(id);
        if (run != null) {
            WorldspaceManager.cleanup(run.bounds());
        }
    }
}
