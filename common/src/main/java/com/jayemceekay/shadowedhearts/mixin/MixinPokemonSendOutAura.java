package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.network.AuraServerSync;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Reserves animated send-outs for their delayed POST event before the entity enters the level. */
@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinPokemonSendOutAura {
    @ModifyVariable(method = {"sendOutWithAnimation", "sendOutFromShoulder"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Function1<? super PokemonEntity, Unit> shadowedhearts$deferAnimatedSendOutAura(
            Function1<? super PokemonEntity, Unit> mutation) {
        return entity -> {
            mutation.invoke(entity);
            AuraServerSync.deferToSendOutEvent(entity);
            return Unit.INSTANCE;
        };
    }
}
