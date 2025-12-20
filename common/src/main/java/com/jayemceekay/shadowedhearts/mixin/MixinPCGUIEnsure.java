package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensure required Shadow aspects are present when the PC GUI opens.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = PCGUI.class, remap = false)
public abstract class MixinPCGUIEnsure {

    @Inject(method = "init", at = @At("HEAD"))
    private void shadowedhearts$ensureOnPCOpen(CallbackInfo ci) {
        try {
            PCGUI self = (PCGUI) (Object) this;
            ClientParty party = self.getParty();
            ClientPC pc = self.getPc();
            // Validate party slots
            for (Pokemon p : party.getSlots()) {
                if (p != null) PokemonAspectUtil.ensureRequiredShadowAspects(p);
            }
            // Validate all PC boxes (safe, idempotent; ensures visible ones are covered)
            for (ClientBox box : pc.getBoxes()) {
                for (Pokemon p : box.getSlots()) {
                    if (p != null) PokemonAspectUtil.ensureRequiredShadowAspects(p);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
