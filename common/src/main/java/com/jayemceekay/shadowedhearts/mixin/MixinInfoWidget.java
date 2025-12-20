package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.client.gui.summary.widgets.screens.info.InfoWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = InfoWidget.class, remap = false)
public class MixinInfoWidget {

    @Final
    @Shadow private Pokemon pokemon;

    // Validation handled elsewhere (Summary open + PC/Chamber open). Avoid extra injections here to keep signatures stable.

    @ModifyArgs(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V", ordinal = 2))
    public void shadowedhearts$maskExperience(Args args) {
        if(PokemonAspectUtil.isLevelExpHiddenByGauge(pokemon)) {
            args.set(2, Component.literal("???").copy().withStyle(s -> s.withBold(true)));
            args.set(3,(((InfoWidget)(Object)this).getX() + 127) - (Minecraft.getInstance().font.width("???") * 0.5f));
        }
    }

    @ModifyArgs(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V", ordinal = 3))
    public void shadowedhearts$maskLevel(Args args) {
        if(PokemonAspectUtil.isLevelExpHiddenByGauge(pokemon)) {
            args.set(2, Component.literal("???").copy().withStyle(s -> s.withBold(true)));
            args.set(3,(((InfoWidget)(Object)this).getX() + 127) - (Minecraft.getInstance().font.width("???") * 0.5f));
        }
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;blitk$default(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;ZFILjava/lang/Object;)V", ordinal = 1))
    public void shadowedhearts$maskExperienceBar(PoseStack poseStack, ResourceLocation resourceLocation, Number x, Number y, Number height, Number width, Number uOffset, Number vOffset, Number textureWidth, Number textureHeight, Number blitOffset, Number red, Number green, Number blue, Number alpha, boolean b, float v, int i, Object o, Operation<Void> original) {
        if(PokemonAspectUtil.isLevelExpHiddenByGauge(pokemon)) {
            return;
        }
        original.call(poseStack, resourceLocation, x, y, height, width, uOffset, vOffset, textureWidth, textureHeight, blitOffset, red, green, blue, alpha, b, v, i, o);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/common/NatureInfoUtilsKt;reformatNatureTextIfMinted(Lcom/cobblemon/mod/common/pokemon/Pokemon;)Lnet/minecraft/network/chat/MutableComponent;"))
    public MutableComponent shadowedhearts$maskNature(Pokemon pokemon, Operation<MutableComponent> original) {
        if(PokemonAspectUtil.isNatureHiddenByGauge(pokemon)) {
            return Component.literal("????");
        }
        return original.call(pokemon);
    }
}
