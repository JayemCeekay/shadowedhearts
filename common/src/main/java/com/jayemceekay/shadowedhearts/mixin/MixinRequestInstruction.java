package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RequestInstruction.class, remap = false)
public abstract class MixinRequestInstruction {

    @Shadow
    public abstract BattleActor getBattleActor();
    
    @Inject(method = "invoke$lambda$0", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/battles/model/actor/BattleActor;sendUpdate(Lcom/cobblemon/mod/common/api/net/NetworkPacket;)V", shift = At.Shift.BEFORE))
    private static void shadowedhearts$disableNonShadowMovesAtInvoke(RequestInstruction this$0, ShowdownActionRequest $request, PokemonBattle $battle, CallbackInfoReturnable<Unit> cir) {
        var reqMovesets = $request.getActive();
        if (reqMovesets == null) return;

        var actor = this$0.getBattleActor();
        if (actor.getType() != ActorType.PLAYER)
            return;

        var requestSide = $request.getSide();
        if (requestSide == null || requestSide.getPokemon() == null) return;

        var activeInRequest = requestSide.getPokemon().stream()
                .filter(com.cobblemon.mod.common.battles.ShowdownPokemon::getActive)
                .toList();

        for (int i = 0; i < Math.min(activeInRequest.size(), reqMovesets.size()); i++) {
            var showdownPokemon = activeInRequest.get(i);
            java.util.UUID pokemonUuid = showdownPokemon.getUuid();

            var battlePokemon = actor.getPokemonList().stream()
                    .filter(p -> p.getUuid().equals(pokemonUuid))
                    .findFirst()
                    .orElse(null);

            if (battlePokemon == null) continue;
            var effected = battlePokemon.getEffectedPokemon();

            if (!com.jayemceekay.shadowedhearts.ShadowGate.isShadowLocked(effected)) continue;

            var moveset = reqMovesets.get(i);
            if (moveset == null || moveset.getMoves() == null) continue;

            int allowed = com.jayemceekay.shadowedhearts.PokemonAspectUtil.getAllowedVisibleNonShadowMoves(effected);
            int nonShadowIndex = 0;

            for (var m : moveset.getMoves()) {
                if (m == null) continue;
                String id = m.getId();
                boolean forced = m.getMaxpp() == 100 && m.getPp() == 100; // Thrash/forced turn placeholder
                if (forced || "struggle".equalsIgnoreCase(id)) continue;
                if (!com.jayemceekay.shadowedhearts.ShadowGate.isShadowMoveId(id)) {
                    if (nonShadowIndex >= allowed) {
                        m.setDisabled(true);
                        var gm = m.getGimmickMove();
                        if (gm != null) gm.setDisabled(true);
                    }
                    nonShadowIndex++;
                }
            }
        }
    }
    
}
