package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.showdown.MicroBattleAI;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts melee hits to run a Cobblemon micro-battle between entities in the overworld.
 * - Pokemon vs Pokemon: start a one-turn battle between them.
 * - Pokemon vs Player: start a one-turn battle against a player proxy and apply damage back to the player on end.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(Mob.class)
public abstract class MixinMob_DoHurtTargetMicro {

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$microBattleOnMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof PokemonEntity attacker) {
            if (!attacker.level().isClientSide()) {
                if (target instanceof PokemonEntity defender) {
                    MicroBattleAI.fire(attacker, defender);
                    // Pretend the vanilla hit succeeded to advance AI cooldowns, but block default damage/knockback.
                    cir.setReturnValue(true);
                } else if (target instanceof ServerPlayer player) {
                    MicroBattleAI.fireAgainstPlayer(attacker, player);
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
