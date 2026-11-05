package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;

import java.util.Locale;
import java.util.Objects;

/** Player-facing job state; work counters describe one fixed preparation phase, never a search ETA. */
public record AuraReaderSearchProgress(UmbrafallTier tier, Stage stage, int candidate,
                                       int completedWork, int totalWork, WaitReason waitReason) {
    public static final int MAX_CANDIDATES = 32;

    public enum Stage {
        SEARCHING, SURVEYING, CHECKING, ESTABLISHING, WAITING,
        READY, READY_ELSEWHERE, NO_LOCATION, CANCELLED, FAILED, PAUSED, RELEASED;

        public boolean preflight() {
            return this == SEARCHING || this == SURVEYING || this == CHECKING;
        }

        public boolean terminal() {
            return this == READY || this == READY_ELSEWHERE || this == NO_LOCATION || this == CANCELLED
                    || this == FAILED || this == RELEASED;
        }

        public static Stage fromName(String name) {
            try { return valueOf(name); }
            catch (IllegalArgumentException ignored) { return PAUSED; }
        }
    }

    public enum WaitReason {
        NONE, TERRAIN, AREA, SAFETY;

        public static WaitReason fromName(String name) {
            try { return valueOf(name); }
            catch (IllegalArgumentException ignored) { return NONE; }
        }
    }

    public AuraReaderSearchProgress {
        Objects.requireNonNull(tier);
        Objects.requireNonNull(stage);
        waitReason = waitReason == null ? WaitReason.NONE : waitReason;
        candidate = Math.clamp(candidate, 0, MAX_CANDIDATES);
        totalWork = Math.max(0, totalWork);
        completedWork = Math.clamp(completedWork, 0, totalWork);
        if (stage != Stage.ESTABLISHING && stage != Stage.WAITING && stage != Stage.PAUSED) {
            completedWork = 0;
            totalWork = 0;
        }
        if (stage != Stage.WAITING) waitReason = WaitReason.NONE;
    }

    public static AuraReaderSearchProgress of(UmbrafallTier tier, Stage stage, int candidate) {
        return new AuraReaderSearchProgress(tier, stage, candidate, 0, 0, WaitReason.NONE);
    }

    public int maxCandidates() { return MAX_CANDIDATES; }

    public boolean dataRetained() {
        return stage == Stage.NO_LOCATION || stage == Stage.CANCELLED || stage == Stage.FAILED;
    }

    /** Only the committed site's fixed cursor is suitable for a preparation percentage. */
    public int phasePercent() {
        return totalWork == 0 ? -1 : (int) (completedWork * 100L / totalWork);
    }

    public String translationKey() {
        return "hud.shadowedhearts.aura_reader.search.stage." + stage.name().toLowerCase(Locale.ROOT);
    }

    public String waitTranslationKey() {
        return "hud.shadowedhearts.aura_reader.search.wait." + waitReason.name().toLowerCase(Locale.ROOT);
    }
}
