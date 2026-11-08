package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.MoonwoundRaidSession;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.raids.RaidSession;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RaidInstance.class, remap = false)
public abstract class MixinMoonwoundRaidInstance {
    @WrapWithCondition(method = "syncHealth", at = @At(value = "INVOKE",
            target = "Lcom/necro/raid/dens/common/raids/RaidSession$DamageRecord;addDamage(F)V"), require = 1, allow = 1)
    private boolean shadowedhearts$creditPlayerDamage(RaidSession.DamageRecord record, float damage) {
        return !(((RaidInstance) (Object) this).session() instanceof MoonwoundRaidSession session && session.isUncreditedHealthChange());
    }

    /** Native detachment and party restoration finish immediately before the faint/lives branch. */
    @Inject(method = "removePlayer", at = @At(value = "FIELD",
            target = "Lcom/necro/raid/dens/common/raids/RaidInstance;raidState:Lcom/necro/raid/dens/common/raids/RaidState;"),
            cancellable = true, require = 1, allow = 1)
    private void shadowedhearts$pause(ServerPlayer player, PokemonBattle battle, boolean ignoreLives, CallbackInfo ci) {
        if (((RaidInstance) (Object) this).session() instanceof MoonwoundRaidSession session && session.isWithdrawing(player)) {
            ci.cancel();
        }
    }
}
