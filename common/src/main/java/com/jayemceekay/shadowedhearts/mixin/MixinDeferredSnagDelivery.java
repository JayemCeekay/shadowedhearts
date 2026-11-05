package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.reactive.EventObservable;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.snag.DeferredSnagDelivery;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Capture succeeds normally, but XD's NPC reward is committed after the battle outcome. */
@Mixin(EmptyPokeBallEntity.class)
public abstract class MixinDeferredSnagDelivery {
    @WrapOperation(method = "shakeBall$lambda$0", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/storage/party/PlayerPartyStore;add(Lcom/cobblemon/mod/common/pokemon/Pokemon;)Z"))
    private static boolean shadowedhearts$deferStorage(PlayerPartyStore party, Pokemon pokemon,
            Operation<Boolean> original, @Local(argsOnly = true) EmptyPokeBallEntity ball) {
        return !DeferredSnagDelivery.deferInsertion(party, pokemon, ball) && original.call(party, pokemon);
    }

    @WrapOperation(method = "shakeBall$lambda$0", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/reactive/EventObservable;emit([Ljava/lang/Object;)V"))
    private static void shadowedhearts$deferCapturedEvent(EventObservable<?> observable, Object[] events,
            Operation<Void> original) {
        if (events.length == 1 && events[0] instanceof PokemonCapturedEvent captured
                && DeferredSnagDelivery.deferCaptureEvent(captured)) return;
        original.call(observable, events);
    }
}
