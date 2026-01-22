package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * PC GUI masking for Shadow heart gauge rules.
 * - Nature text is masked as "????" when nature hidden by gauge.
 * - EV values are masked as "??" on the EV page.
 */
@Mixin(value = PCGUI.class)
public abstract class MixinPCGUIMasking {

    // Shadow fields to detect current page and pokemon in the PC GUI
    @org.spongepowered.asm.mixin.Shadow private com.cobblemon.mod.common.pokemon.Pokemon previewPokemon;
    @org.spongepowered.asm.mixin.Shadow private int currentStatIndex;

    @Shadow
    @Final
    public static int STAT_EV;

    @Shadow
    @Final
    public static int STAT_IV;

    // Mask nature text in Info page
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/common/NatureInfoUtilsKt;reformatNatureTextIfMinted(Lcom/cobblemon/mod/common/pokemon/Pokemon;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent shadowedhearts$maskNatureInPC(Pokemon pokemon, Operation<MutableComponent> original) {
        if (PokemonAspectUtil.isNatureHiddenByGauge(pokemon)) {
            return Component.literal("????");
        }
        return original.call(pokemon);
    }

    // Mask EV numeric values in EV page drawScaledText calls (the EV value is rendered after label in the loop)
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V", ordinal = 17
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPCEvValue(MutableComponent original) {
        Pokemon pokemon = this.previewPokemon;
        if (pokemon == null) return original;
        if (this.currentStatIndex != STAT_EV) return original;
        if (!PokemonAspectUtil.isEVHiddenByGauge(pokemon)) return original;

        String s = original.getString();
        // Numeric-only components in EV page represent the EV value display (including italic variant)
        if (s != null && s.matches("\\d{1,2}")) {
            return Component.literal("??").copy().withStyle(st -> st.withBold(true));
        }
        return original;
    }

    // Broad safeguard: when on the IV page, replace numeric IV values with "??"
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V", ordinal = 15
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPCIvValues(MutableComponent original) {
        Pokemon pokemon = this.previewPokemon;
        if (pokemon == null) return original;
        if (this.currentStatIndex != STAT_IV) return original;
        if (!PokemonAspectUtil.isIVHiddenByGauge(pokemon)) return original;

        String s = original.getString();
        // Numeric-only components in IV page represent the IV value display (including italic variant)
        if (s != null && s.matches("\\d{1,2}")) {
            return Component.literal("??").copy().withStyle(st -> st.withBold(true));
        }
        return original;
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 13
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPCMoveName(
            MutableComponent original,
            @Local(name = "pokemon") Pokemon pokemon,
            @Local(name = "i") int i
    ) {
        try {
            if (pokemon == null) return original;

            // Build list of up to 4 actual moves (no placeholders)
            var moves = pokemon.getMoveSet().getMoves();
            int realCount = Math.min(moves.size(), 4);

            // If current index points to placeholder beyond real moves, keep original (it's the "—" text)
            if (i >= realCount) return original;

            // Determine if this move should be masked based on allowed visible non-Shadow moves
            int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);

            // Count non-shadow moves before the current index
            int nonShadowIndex = 0;
            for (int idx = 0; idx < i; idx++) {
                var mv = moves.get(idx);
                if (!ShadowGate.isShadowMoveId(mv.getName())) nonShadowIndex++;
            }

            var current = moves.get(i);
            // Shadow moves are never masked
            if (ShadowGate.isShadowMoveId(current.getName())) return original;

            boolean mask = nonShadowIndex >= allowed;
            if (mask) {
                return Component.literal("???").copy().withStyle(s -> s.withBold(true));
            }
        } catch (Throwable ignored) { }
        return original;
    }
}
