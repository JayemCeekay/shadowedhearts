package com.jayemceekay.shadowedhearts.events;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;

import java.util.List;
import java.util.UUID;

/**
 * Central domain events exposed via Architectury's cross-platform event system.
 *
 * Each V1 event is a simple data holder so external code can subscribe by
 * registering a listener to the corresponding Architectury Event field.
 * Static post(...) helpers are provided for convenience. Versioning is kept via VersionedPayload.version().
 */
public final class ShadowsEvents {
    private ShadowsEvents() {}

    // Version marker
    public interface VersionedPayload {
        int version();
    }

    // ===== V1 Event payloads =====
    public static class SignalSynthesizedV1 implements VersionedPayload {
        public final String theme;
        public final int tier;
        public final List<String> affixes;
        public final long seed;
        public final List<UUID> party;
        public SignalSynthesizedV1(String theme, int tier, List<String> affixes, long seed, List<UUID> party) {
            this.theme = theme; this.tier = tier; this.affixes = affixes; this.seed = seed; this.party = party;
        }
        public int version() { return 1; }
    }

    public static class RunCreatedV1 implements VersionedPayload {
        public final String runId;
        public final List<UUID> party;
        public final String configSummary;
        public RunCreatedV1(String runId, List<UUID> party, String configSummary) {
            this.runId = runId; this.party = party; this.configSummary = configSummary;
        }
        public int version() { return 1; }
    }

    public static class RoomClearedV1 implements VersionedPayload {
        public final String runId;
        public final String roomId;
        public final int roomsCleared;
        public RoomClearedV1(String runId, String roomId, int roomsCleared) {
            this.runId = runId; this.roomId = roomId; this.roomsCleared = roomsCleared;
        }
        public int version() { return 1; }
    }

    public static class PuzzleSolvedV1 implements VersionedPayload {
        public final String runId;
        public final String roomId;
        public final String puzzleType;
        public final long timeMs;
        public PuzzleSolvedV1(String runId, String roomId, String puzzleType, long timeMs) {
            this.runId = runId; this.roomId = roomId; this.puzzleType = puzzleType; this.timeMs = timeMs;
        }
        public int version() { return 1; }
    }

    public static class TrainerDefeatedV1 implements VersionedPayload {
        public final String runId;
        public final String roomId;
        public final String rosterId;
        public TrainerDefeatedV1(String runId, String roomId, String rosterId) {
            this.runId = runId; this.roomId = roomId; this.rosterId = rosterId;
        }
        public int version() { return 1; }
    }

    public static class BossPhaseChangedV1 implements VersionedPayload {
        public final String runId;
        public final String bossId;
        public final int phase;
        public final float sharedHpRatio;
        public BossPhaseChangedV1(String runId, String bossId, int phase, float sharedHpRatio) {
            this.runId = runId; this.bossId = bossId; this.phase = phase; this.sharedHpRatio = sharedHpRatio;
        }
        public int version() { return 1; }
    }

    public static class BossDefeatedV1 implements VersionedPayload {
        public final String runId;
        public final String bossId;
        public final boolean purified;
        public BossDefeatedV1(String runId, String bossId, boolean purified) {
            this.runId = runId; this.bossId = bossId; this.purified = purified;
        }
        public int version() { return 1; }
    }

    public static class CaptureWindowOpenedV1 implements VersionedPayload {
        public final String runId;
        public final String bossId;
        public final int windowSeconds;
        public CaptureWindowOpenedV1(String runId, String bossId, int windowSeconds) {
            this.runId = runId; this.bossId = bossId; this.windowSeconds = windowSeconds;
        }
        public int version() { return 1; }
    }

    public static class RunFinishedV1 implements VersionedPayload {
        public final String runId;
        public final String outcome;
        public RunFinishedV1(String runId, String outcome) {
            this.runId = runId; this.outcome = outcome;
        }
        public int version() { return 1; }
    }

    public static class OverworldTrainerDefeatedV1 implements VersionedPayload {
        public final UUID player;
        public final String trainerType;
        public final String locationKey;
        public OverworldTrainerDefeatedV1(UUID player, String trainerType, String locationKey) {
            this.player = player; this.trainerType = trainerType; this.locationKey = locationKey;
        }
        public int version() { return 1; }
    }

    // ===== Architectury Event listener interfaces =====
    public interface SignalSynthesizedListener { void onSignalSynthesized(SignalSynthesizedV1 e); }
    public interface RunCreatedListener { void onRunCreated(RunCreatedV1 e); }
    public interface RoomClearedListener { void onRoomCleared(RoomClearedV1 e); }
    public interface PuzzleSolvedListener { void onPuzzleSolved(PuzzleSolvedV1 e); }
    public interface TrainerDefeatedListener { void onTrainerDefeated(TrainerDefeatedV1 e); }
    public interface BossPhaseChangedListener { void onBossPhaseChanged(BossPhaseChangedV1 e); }
    public interface BossDefeatedListener { void onBossDefeated(BossDefeatedV1 e); }
    public interface CaptureWindowOpenedListener { void onCaptureWindowOpened(CaptureWindowOpenedV1 e); }
    public interface RunFinishedListener { void onRunFinished(RunFinishedV1 e); }
    public interface OverworldTrainerDefeatedListener { void onOverworldTrainerDefeated(OverworldTrainerDefeatedV1 e); }

    // ===== Architectury Event fields =====
    public static final Event<SignalSynthesizedListener> SIGNAL_SYNTHESIZED = EventFactory.createLoop();
    public static final Event<RunCreatedListener> RUN_CREATED = EventFactory.createLoop();
    public static final Event<RoomClearedListener> ROOM_CLEARED = EventFactory.createLoop();
    public static final Event<PuzzleSolvedListener> PUZZLE_SOLVED = EventFactory.createLoop();
    public static final Event<TrainerDefeatedListener> TRAINER_DEFEATED = EventFactory.createLoop();
    public static final Event<BossPhaseChangedListener> BOSS_PHASE_CHANGED = EventFactory.createLoop();
    public static final Event<BossDefeatedListener> BOSS_DEFEATED = EventFactory.createLoop();
    public static final Event<CaptureWindowOpenedListener> CAPTURE_WINDOW_OPENED = EventFactory.createLoop();
    public static final Event<RunFinishedListener> RUN_FINISHED = EventFactory.createLoop();
    public static final Event<OverworldTrainerDefeatedListener> OVERWORLD_TRAINER_DEFEATED = EventFactory.createLoop();

    // ===== Post helpers (invoke Architectury events) =====
    public static void post(SignalSynthesizedV1 e) { SIGNAL_SYNTHESIZED.invoker().onSignalSynthesized(e); }
    public static void post(RunCreatedV1 e) { RUN_CREATED.invoker().onRunCreated(e); }
    public static void post(RoomClearedV1 e) { ROOM_CLEARED.invoker().onRoomCleared(e); }
    public static void post(PuzzleSolvedV1 e) { PUZZLE_SOLVED.invoker().onPuzzleSolved(e); }
    public static void post(TrainerDefeatedV1 e) { TRAINER_DEFEATED.invoker().onTrainerDefeated(e); }
    public static void post(BossPhaseChangedV1 e) { BOSS_PHASE_CHANGED.invoker().onBossPhaseChanged(e); }
    public static void post(BossDefeatedV1 e) { BOSS_DEFEATED.invoker().onBossDefeated(e); }
    public static void post(CaptureWindowOpenedV1 e) { CAPTURE_WINDOW_OPENED.invoker().onCaptureWindowOpened(e); }
    public static void post(RunFinishedV1 e) { RUN_FINISHED.invoker().onRunFinished(e); }
    public static void post(OverworldTrainerDefeatedV1 e) { OVERWORLD_TRAINER_DEFEATED.invoker().onOverworldTrainerDefeated(e); }
}
