package com.jayemceekay.shadowedhearts.common.snag;

import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig;

/** Server-side one-throw arming policy, shared by every ball type. */
public final class SnagThrowAttempt {
    private SnagThrowAttempt() {}

    /** An available armed machine authorizes exactly one throw before it must be rearmed. */
    public static boolean consumeArmedThrow(PlayerSnagData data) {
        if (!GameplayTuningConfig.INSTANCE.snaggingEnabled()) {
            if (data.isArmed()) data.setArmed(false);
            return false;
        }
        if (!data.hasSnagMachine() || !data.isArmed()) return false;
        data.setArmed(false);
        return true;
    }
}
