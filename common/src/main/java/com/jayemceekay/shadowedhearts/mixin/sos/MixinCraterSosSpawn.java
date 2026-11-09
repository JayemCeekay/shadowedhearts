package com.jayemceekay.shadowedhearts.mixin.sos;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.dispatch.DispatchResultKt;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.effects.IllusionEffect;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueActors;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Keep the add-on's pre/post events, slot insertion and Showdown timing. Only the issued cast and landing are ours. */
@Pseudo
@Mixin(targets = "com.necro.sos.encounters.common.showdown.instruction.SOSInstruction", remap = false)
public abstract class MixinCraterSosSpawn {
    @Shadow @Final private BattlePokemon pokemon;
    @Unique private boolean shadowedhearts$craterCall;

    @Inject(method = "postActionEffect", at = @At("RETURN"))
    private void shadowedhearts$resumeAfterCall(PokemonBattle battle, CallbackInfo ci) {
        if (pokemon == null || !CraterRescueActors.isHowl(pokemon.getEffectedPokemon())) return;
        // Insertion resumes Showdown on success. A veto or obstructed landing leaves the turn paused.
        int turn = battle.getTurn();
        battle.dispatch(() -> {
            if (!battle.getEnded()) battle.writeShowdownAction(">shcraterhowl resume " + pokemon.getUuid() + " " + turn);
            return DispatchResultKt.getGO();
        });
    }

    @WrapOperation(method = "lambda$postActionEffect$7", at = @At(value = "INVOKE",
            target = "Lcom/necro/sos/encounters/common/api/SOSManager;rollSpawn"))
    private Pokemon shadowedhearts$chooseResponder(@Coerce Object manager, ServerPlayer player, PokemonEntity caller,
                                                    PokemonBattle battle, Operation<Pokemon> original) {
        shadowedhearts$craterCall = CraterRescueActors.site(caller.getPokemon()) != null;
        if (!shadowedhearts$craterCall) return original.call(manager, player, caller, battle);
        Pokemon responder = CraterRescueBattles.sosResponder(player, caller);
        // The native position calculation requires a Pokémon. If permission expired in the pre-event,
        // the caller is a safe position reference and the send-out below rejects its identity.
        return responder != null ? responder : caller.getPokemon();
    }

    @WrapOperation(method = "lambda$postActionEffect$7", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;sendOut"))
    private PokemonEntity shadowedhearts$landInCrater(Pokemon pokemon, ServerLevel level, Vec3 pos, IllusionEffect illusion,
                                                       Function1<PokemonEntity, Unit> callback, Operation<PokemonEntity> original,
                                                       PokemonBattle battle) {
        if (shadowedhearts$craterCall) return CraterRescueBattles.sendSosResponder(battle, pokemon);
        return original.call(pokemon, level, pos, illusion, callback);
    }

    @WrapOperation(method = "lambda$postActionEffect$7", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;setBattleId(Ljava/util/UUID;)V"))
    private void shadowedhearts$registerJoinedResponder(PokemonEntity entity, UUID id, Operation<Void> original,
                                                        PokemonBattle battle) {
        original.call(entity, id);
        if (shadowedhearts$craterCall) CraterRescueBattles.recordSosJoin(battle, entity);
    }
}
