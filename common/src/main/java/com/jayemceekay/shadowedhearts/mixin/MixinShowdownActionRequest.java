package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.ShadowGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(value = com.cobblemon.mod.common.battles.ShowdownActionRequest.class, remap = false)
public abstract class MixinShowdownActionRequest {

    @Inject(method = "sanitize", at = @At("TAIL"))
    private void shadowedhearts$disableNonShadowMovesAtSanitize(
            com.cobblemon.mod.common.api.battles.model.PokemonBattle battle,
            com.cobblemon.mod.common.api.battles.model.actor.BattleActor actor,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        var request = (com.cobblemon.mod.common.battles.ShowdownActionRequest) (Object) this;
        var reqMovesets = request.getActive();
        if (reqMovesets == null) return;

        var activeList = actor.getPokemonList();
        int n = Math.min(activeList.size(), reqMovesets.size());
        for (int i = 0; i < n; i++) {
            var active = activeList.get(i);
            var bp = active == null ? null : active;
            if (bp == null) {
                continue;
            }
            var effected = bp.getEffectedPokemon();
            if (!ShadowGate.isShadowLocked(effected) ) continue;

            var moveset = reqMovesets.get(i);
            if (moveset == null || moveset.getMoves() == null) continue;

            for (var m : moveset.getMoves()) {
                if (m == null) continue;
                String id = m.getId();
                boolean forced = m.getMaxpp() == 100 && m.getPp() == 100; // Thrash/forced turn placeholder
                if (forced || "struggle".equalsIgnoreCase(id)) continue;
                if (!com.jayemceekay.shadowedhearts.ShadowGate.isShadowMoveId(id)) {
                    m.setDisabled(true);
                    var gm = m.getGimmickMove();
                    if (gm != null) gm.setDisabled(true);
                }
            }
        }
    }
}