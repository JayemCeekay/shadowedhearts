package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Adds Shadow aura rendering to PC storage slot preview model.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = StorageSlot.class)
public abstract class MixinStorageSlotAura {
/*
    @Shadow
    public abstract com.cobblemon.mod.common.pokemon.Pokemon getPokemon();

    @Unique
    private static boolean shadowedhearts$isShadow(RenderablePokemon rp) {
        return rp != null && rp.getAspects() != null && rp.getAspects().contains(SHAspects.SHADOW);
    }

    @Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/PokemonGuiUtilsKt;drawProfilePokemon$default(Lcom/cobblemon/mod/common/pokemon/RenderablePokemon;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Quaternionf;Lcom/cobblemon/mod/common/entity/PoseType;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFZZFFFFFFILjava/lang/Object;)V", ordinal = 0, shift = At.Shift.AFTER, remap = false))
    private void shadowedhearts$renderAura(GuiGraphics context, int posX, int posY, float partialTicks, CallbackInfo ci) {
        var p = getPokemon();
        if (p == null) return;
        RenderablePokemon rp = p.asRenderablePokemon();
        if (!shadowedhearts$isShadow(rp)) return;

        // Storage slot uses a small fixed scale (2.5 then 4.5 in draw call); choose conservative aura size
        float uiScale = 0.9F; // tuned constant for 25px slot
        float radius = 0.22F * uiScale;
        float halfHeight = 0.50F * uiScale;

        PoseStack matrices = context.pose();
        //AuraEmitters.renderInGui(matrices, context.bufferSource(), radius, halfHeight, 1.0F, partialTicks, QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13F, 35F, 0F)));
    }*/
}
