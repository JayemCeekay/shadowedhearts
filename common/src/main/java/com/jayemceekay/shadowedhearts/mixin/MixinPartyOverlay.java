package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = PartyOverlay.class)
public class MixinPartyOverlay {

    ResourceLocation SHADOW_FRAME = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/gui/party_slot.png");

    ResourceLocation SHADOW_FRAME_ACTIVE = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/gui/party_slot_active.png");

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;blitk$default(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;ZFILjava/lang/Object;)V", ordinal = 1), index = 1)
    public ResourceLocation shadowedhearts$replacePortraitFrame(ResourceLocation resourceLocation, @Local(name = "pokemon") Pokemon pokemon, @Local(name = "index") int index) {
        if(PokemonAspectUtil.hasShadowAspect(pokemon) && !pokemon.isFainted()) {
            return CobblemonClient.INSTANCE.getStorage().getSelectedSlot() == index ? SHADOW_FRAME_ACTIVE : SHADOW_FRAME;
        }
        return resourceLocation;
    }

}
