package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.dispatch.CauserInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.MoonwoundRaidSession;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.necro.raid.dens.common.data.raid.RaidBattleFormat;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.showdown.instructions.RaidDamageInstruction;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RaidDamageInstruction.class, remap = false)
public abstract class MixinMoonwoundRaidDamage {
    @Shadow @Final private BattleMessage privateMessage;

    @WrapOperation(method = "lambda$postActionEffect$5", at = @At(value = "INVOKE",
            target = "Lcom/necro/raid/dens/common/raids/RaidInstance;syncHealth(Lnet/minecraft/server/level/ServerPlayer;Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;F)V",
            remap = true), require = 1, allow = 1)
    private void shadowedhearts$creditAttacker(RaidInstance encounter, ServerPlayer originalPlayer, PokemonBattle battle,
                                               float damage, Operation<Void> original,
                                               @Local(argsOnly = true) CauserInstruction causer) {
        if (!(encounter.session() instanceof MoonwoundRaidSession session) || encounter.boss().getBattleFormat() != RaidBattleFormat.RAID) {
            original.call(encounter, originalPlayer, battle, damage);
            return;
        }
        // Direct hits omit [of]. Their preceding move instruction identifies the attacking Pokémon.
        BattlePokemon source = privateMessage.battlePokemonFromOptional(battle, "of");
        if (source == null && !privateMessage.hasOptionalArgument("from") && !privateMessage.hasOptionalArgument("silent")
                && causer instanceof MoveInstruction move) source = move.getUserPokemon();
        session.syncSharedDamage(battle, source, damage);
    }
}
