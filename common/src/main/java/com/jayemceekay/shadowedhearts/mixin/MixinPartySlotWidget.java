package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.widgets.PartySlotWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PartySlotWidget.class, remap = false)
public class MixinPartySlotWidget {

    ResourceLocation SHADOW_FRAME = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/gui/summary/summary_party_slot.png");

    @ModifyReturnValue(method = "getSlotTexture", at = @At("RETURN"))
    private ResourceLocation shadowedhearts$replaceFrame(ResourceLocation original, @Local(argsOnly = true) Pokemon pokemon) {
        if(PokemonAspectUtil.hasShadowAspect(pokemon) && !pokemon.isFainted()) {
            return SHADOW_FRAME;
        } else {
            return original;
        }
    }
}
