package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData;
import com.cobblemon.mod.common.api.storage.player.adapter.NbtBackedPlayerData;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NbtBackedPlayerData.class, remap = false)
public abstract class MixinNbtBackedPlayerData<T extends InstancedPlayerData> {

    @Inject(
            method = "save",
            at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;result()Ljava/util/Optional;")
    )
    private void shadowedhearts$debugPokedexSave(T playerData, CallbackInfo ci, @Local(name = "encodeResult") DataResult<Tag> encodeResult) {
        if (!encodeResult.result().isPresent()) {
            String errorMessage = encodeResult.error().map(DataResult.Error::message)
                    .orElse("Unknown Serialization Error");

            Cobblemon.LOGGER.error("CRITICAL: Failed to encode player data for UUID: " + playerData.getUuid());
            Cobblemon.LOGGER.error("Error details: " + errorMessage);

            // If it's the Pokedex, we can try to log more info
            if (playerData instanceof com.cobblemon.mod.common.api.pokedex.PokedexManager dex) {
                Cobblemon.LOGGER.error("Problematic Pokedex contains " + dex.getSpeciesRecords().size() + " species records.");
            }
        }
    }
}