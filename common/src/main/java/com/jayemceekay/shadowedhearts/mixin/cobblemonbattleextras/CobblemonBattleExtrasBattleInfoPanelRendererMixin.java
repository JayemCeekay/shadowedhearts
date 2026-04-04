package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import name.modid.client.BattleInfoPanelRenderer;
import name.modid.client.LocalBattlePartyTracker;
import name.modid.client.RevealedBattleInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Only for >=1.7.41
// BattleInfoPanel is the panel that appears when hovering over the top right/left Pokémon portraits/life bars
@Mixin(BattleInfoPanelRenderer.class)
public class CobblemonBattleExtrasBattleInfoPanelRendererMixin {

    // Mask Attack name and set white color
    @ModifyArgs(method = "renderPanel",
            at = @At(value = "INVOKE", target = "Lname/modid/client/BattleInfoPanelRenderer;drawScaledText(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/Minecraft;Ljava/lang/String;IIIFF)V",
                    ordinal = 6)
    )
    private static void shadowedhearts$maskAttackNameAndColor(Args args, @Local(name = "move") RevealedBattleInfo.PokemonRevealedInfo.MoveInfo move, @Local(ordinal = 1, argsOnly = true) String side, @Local(argsOnly = true) Object battlePokemon) {
        ClientBattlePokemon bp = (ClientBattlePokemon) battlePokemon;
        if (side.equals("player")) {
            Pokemon currentPoke = null;
            for (Pokemon partyPokemon : LocalBattlePartyTracker.getLocalPartySnapshot()) {
                if (bp.getUuid().equals(partyPokemon.getUuid())) {
                    currentPoke = partyPokemon;
                }
            }
            if (ShadowAspectUtil.shouldMaskMove(currentPoke, move.moveId)) {
                args.set(2, "????");
                args.set(5, 16777215); // white color
            }
        }
    }

    // Replace type with "shadow-locked" if the move was locked
    @ModifyArgs(method = "renderPanel",
            at = @At(value = "INVOKE", target = "Lname/modid/client/BattleInfoPanelRenderer;renderTypeIconScaled(Lnet/minecraft/client/gui/GuiGraphics;IILjava/lang/String;FFI)V")
    )
    private static void shadowedhearts$changeTypeToLocked(Args args, @Local(name = "move") RevealedBattleInfo.PokemonRevealedInfo.MoveInfo move, @Local(ordinal = 1, argsOnly = true) String side, @Local(argsOnly = true) Object battlePokemon) {
        ClientBattlePokemon bp = (ClientBattlePokemon) battlePokemon;
        if (side.equals("player")) {
            Pokemon currentPoke = null;
            for (Pokemon partyPokemon : LocalBattlePartyTracker.getLocalPartySnapshot()) {
                if (bp.getUuid().equals(partyPokemon.getUuid())) {
                    currentPoke = partyPokemon;
                }
            }
            if (ShadowAspectUtil.shouldMaskMove(currentPoke, move.moveId)) {
                args.set(3, "shadow-locked");
            }
        }
    }

    // Render the shadow/locked type icon
    @Inject(method = "renderTypeIconScaled", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$renderShadowIcon(GuiGraphics gui, int x, int y, String type, float alpha, float brightness, int displaySize, CallbackInfo ci) {
        ResourceLocation texture = null;
        boolean customIcon = false;
        if (type.equals("shadow")) {
            texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/shadow_type.png");
            customIcon = true;
        } else if (type.equals("shadow-locked")) {
            texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/disabled_move.png");
            customIcon = true;
        }

        if (customIcon) {
            gui.flush();
            gui.pose().pushPose();
            gui.pose().translate(0.0F, 0.0F, 100.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float clampedBrightness = Math.max(0.0F, brightness);
            RenderSystem.setShaderColor(clampedBrightness, clampedBrightness, clampedBrightness, alpha);
            gui.blit(texture, x, y, displaySize, displaySize, 0.0F, 0.0F, 36, 36, 36, 36);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            gui.pose().popPose();
            gui.flush();

            ci.cancel();
        }
    }
}
