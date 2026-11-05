package com.jayemceekay.shadowedhearts.common.moonwound.raid;

/** Retains the event-specific definition in Raid Dens' queued capture reward across restarts. */
public interface MoonwoundRaidRewardDefinitionAccess {
    void shadowedhearts$restoreMoonwoundDefinition(byte[] definition);
}
