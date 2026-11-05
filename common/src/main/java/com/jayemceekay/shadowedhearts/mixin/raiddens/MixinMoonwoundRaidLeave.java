package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.jayemceekay.shadowedhearts.common.moonwound.raid.RaidDensMoonwoundAdapter;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Upstream's non-den fallback only releases its queue; also detach our actual battle and boss bars. */
@Pseudo
@Mixin(targets = "com.necro.raid.dens.common.util.RaidUtils", remap = false)
public abstract class MixinMoonwoundRaidLeave {
    @Inject(method = "leaveRaid", at = @At("HEAD"))
    private static void shadowedhearts$leave(Player player, CallbackInfo ci) {
        RaidDensMoonwoundAdapter.leaveNativeRaid(player);
    }
}
