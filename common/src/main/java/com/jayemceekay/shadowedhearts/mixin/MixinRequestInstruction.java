package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
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

            if (!ShadowAspectUtil.hasShadowAspect(effected)) continue;

            var moveset = reqMovesets.get(i);
            if (moveset == null || moveset.getMoves() == null) continue;

            // Block gimmick flags on the moveset for shadow Pokémon based on config
            IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
            if (!cfg.shadowCanMegaEvolve()) moveset.blockGimmick(ShowdownMoveset.Gimmick.MEGA_EVOLUTION);
            if (!cfg.shadowCanUltraBurst()) moveset.blockGimmick(ShowdownMoveset.Gimmick.ULTRA_BURST);
            if (!cfg.shadowCanUseZMoves()) moveset.blockGimmick(ShowdownMoveset.Gimmick.Z_POWER);
            if (!cfg.shadowCanDynamax()) moveset.blockGimmick(ShowdownMoveset.Gimmick.DYNAMAX);
            if (!cfg.shadowCanTerastallize()) moveset.blockGimmick(ShowdownMoveset.Gimmick.TERASTALLIZATION);

            for (var m : moveset.getMoves()) {
                if (m == null) continue;
                String id = m.getId();
                boolean forced = m.getMaxpp() == 100 && m.getPp() == 100; // Thrash/forced turn placeholder
                if (forced || "struggle".equalsIgnoreCase(id)) continue;
                if (ShadowAspectUtil.shouldMaskMove(effected, id)) {
                    m.setDisabled(true);
                    var gm = m.getGimmickMove();
                    if (gm != null) gm.setDisabled(true);
                } else {
                    // Even if the base move is allowed, disable its gimmick move if config blocks it
                    var gm = m.getGimmickMove();
                    if (gm != null) {
                        boolean shouldDisableGimmick =
                                (!cfg.shadowCanUseZMoves() && moveset.getCanZMove() != null) ||
                                (!cfg.shadowCanDynamax() && moveset.getMaxMoves() != null);
                        if (shouldDisableGimmick) {
                            gm.setDisabled(true);
                        }
                    }
                }
            }
        }
    }
    
}
