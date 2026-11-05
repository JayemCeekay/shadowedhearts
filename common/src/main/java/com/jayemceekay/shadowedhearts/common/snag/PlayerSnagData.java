package com.jayemceekay.shadowedhearts.common.snag;

/**
 * Player-side data for Snag Machine usage.
 * Backed by item data components via {@link SimplePlayerSnagData}.
 */
public interface PlayerSnagData {
    boolean hasSnagMachine();
    boolean isArmed();
    int cooldown();
    void setArmed(boolean v);
    void setCooldown(int ticks);
    boolean lastSyncedEligibility();
    void setLastSyncedEligibility(boolean v);

    int failedSnagAttempts();
    void incrementFailedSnagAttempts();
    void resetFailedSnagAttempts();
}
