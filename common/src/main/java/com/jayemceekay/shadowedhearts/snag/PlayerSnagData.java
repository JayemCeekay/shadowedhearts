package com.jayemceekay.shadowedhearts.snag;

/**
 * Player-side data for Snag Machine usage.
 * Backed by persistent player NBT via {@link SimplePlayerSnagData}.
 */
public interface PlayerSnagData {
    boolean hasSnagMachine();
    boolean isArmed();
    int energy();
    int cooldown();
    void setArmed(boolean v);
    void consumeEnergy(int amt);
    /** Adds energy up to the device's capacity. Negative values are ignored. */
    void addEnergy(int amt);
    void setCooldown(int ticks);
}
