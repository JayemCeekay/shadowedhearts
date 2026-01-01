package com.jayemceekay.shadowedhearts.snag;

import net.minecraft.world.entity.player.Player;

/**
 * Lightweight accessor for player Snag data backed by persistent NBT.
 * This avoids full capability boilerplate while giving a stable API surface.
 */
public final class SnagCaps {
    private SnagCaps() {}

    public static PlayerSnagData get(Player player) {
        return new SimplePlayerSnagData(player);
    }

    /** True if the player has any Snag Machine available (equipped accessory, offhand, or mainhand). */
    public static boolean hasMachineAvailable(Player player) {
        if (player == null) return false;
        return get(player).hasSnagMachine();
    }

}
