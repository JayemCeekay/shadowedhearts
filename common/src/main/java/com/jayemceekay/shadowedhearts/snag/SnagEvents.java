// common/src/main/java/com/jayemceekay/shadowedhearts/snag/SnagEvents.java
package com.jayemceekay.shadowedhearts.snag;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.world.entity.player.Player;

public final class SnagEvents {
    private SnagEvents() {}

    /** Call once during common init on both loaders. */
    public static void init() {
        // Runs after each player's tick on both client and server.
        TickEvent.PLAYER_POST.register(SnagEvents::onPlayerPostTick);
    }

    private static void onPlayerPostTick(Player player) {
        if (player == null || player.level().isClientSide) return;
        var cap = SnagCaps.get(player);
        int cd = cap.cooldown();
        if (cd > 0) cap.setCooldown(cd - 1);
    }
}
