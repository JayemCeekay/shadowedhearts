package com.jayemceekay.shadowedhearts.snag;

import com.jayemceekay.shadowedhearts.core.ModItems;
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

    /** True if the player is currently holding the Snag Machine in their offhand. */
    public static boolean hasMachineInOffhand(Player player) {
        return player != null && !player.getOffhandItem().isEmpty() && player.getOffhandItem().is(ModItems.SNAG_MACHINE.get());
    }

    /** Placeholder rule: treat throws as Snag if offhand has the machine and the device is armed. */
    public static boolean shouldTreatThrowsAsSnag(Player player) {
        if (player == null) return false;
        if (!hasMachineInOffhand(player)) return false;
        return get(player).isArmed();
    }
}
