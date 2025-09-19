package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.ShadowFlag;
import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects synced data flags into PokemonEntity and exposes them via {@link ShadowFlag}.
 * Also treats shadow-aspected Pokemon as wild for capture checks, allowing Pokéballs to be thrown at
 * NPC trainer Pokémon if they are shadow (Colosseum/XD mechanic).
 */
@Mixin(PokemonEntity.class)
public abstract class MixinPokemonEntity implements ShadowFlag {

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void shadowedhearts$defineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        // Ensure our flags are defined with defaults
        ShadowPokemonData.define(builder);
    }

    @Override
    public boolean shadowedHearts$isShadow() {
        return ((PokemonEntity)(Object)this).getEntityData().get(ShadowPokemonData.SHADOW);
    }

    @Override
    public float shadowedHearts$getCorruption() {
        return ((PokemonEntity)(Object)this).getEntityData().get(ShadowPokemonData.CORRUPTION);
    }
}
