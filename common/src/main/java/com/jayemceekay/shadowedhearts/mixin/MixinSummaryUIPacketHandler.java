package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.net.gui.SummaryUIPacketHandler;
import com.cobblemon.mod.common.net.messages.client.ui.SummaryUIPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensure required Shadow aspects are present on Summary UI open.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = SummaryUIPacketHandler.class, remap = false)
public abstract class MixinSummaryUIPacketHandler {

    @Inject(method = "handle*", at = @At("HEAD"))
    private void shadowedhearts$ensureOnSummaryOpen(SummaryUIPacket packet, Minecraft client, CallbackInfo ci) {
        try {
            for (Pokemon p : packet.getPokemon()) {
                if (p != null) PokemonAspectUtil.ensureRequiredShadowAspects(p);
            }
        } catch (Throwable ignored) {
        }
    }
}
