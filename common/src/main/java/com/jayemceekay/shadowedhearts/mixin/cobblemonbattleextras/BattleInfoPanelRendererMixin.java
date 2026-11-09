package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import com.jayemceekay.shadowedhearts.client.gui.ShadowMaskingUtil;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "name.modid.client.BattleInfoPanelRenderer", remap = false)
public abstract class BattleInfoPanelRendererMixin {
    // Build the local player's display row with the masked name and type before measuring or coloring it.
    @ModifyArgs(method = "resolvePlayerMoves(Lcom/cobblemon/mod/common/pokemon/Pokemon;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lname/modid/client/RevealedBattleInfo$PokemonRevealedInfo$MoveInfo;<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"))
    private static void shadowedhearts$maskPlayerMove(Args args, @Local(argsOnly = true) Pokemon pokemon) {
        if (ShadowUnlockPolicy.shouldMaskMove(pokemon, (String) args.get(0))) {
            args.set(1, ShadowMaskingUtil.MASKED_NAME.getString());
            args.set(2, "shadow-locked");
        }
    }

    @Inject(method = "renderTypeIconScaled", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$drawShadowIcon(GuiGraphics graphics, int x, int y, String type,
                                                     float alpha, float brightness, int size, CallbackInfo ci) {
        ResourceLocation texture = BattleExtrasMovePresentation.icon(type, false);
        if (texture == null) return;

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float light = Math.max(0, brightness);
        RenderSystem.setShaderColor(light, light, light, alpha);
        graphics.blit(texture, x, y, size, size, 0, 0, 36, 36, 36, 36);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.pose().popPose();
        graphics.flush();
        ci.cancel();
    }
}
