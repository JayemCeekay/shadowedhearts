package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Pseudo
@Mixin(targets = "name.modid.client.CustomBattleController", remap = false)
public abstract class MixinBattleExtrasMoveEffectiveness {
    @WrapOperation(method = {"resolveMoveEffectivenessTint", "buildBestSwitchMovePreviewLine", "calculateSwitchDamagePreview"},
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lname/modid/client/CustomBattleController$OpponentMatchupEntry;types:Ljava/util/List;"))
    private static List<ElementalType> shadowedhearts$rememberOpponent(@Coerce Object entry, Operation<List<ElementalType>> original,
            @Share("opponent") LocalRef<BattleExtrasOpponentAccessor> opponent) {
        opponent.set((BattleExtrasOpponentAccessor) entry);
        return original.call(entry);
    }

    @WrapOperation(method = {"resolveMoveEffectivenessTint", "buildBestSwitchMovePreviewLine", "calculateSwitchDamagePreview"},
            at = @At(value = "INVOKE", target = "Lname/modid/client/TypeChart;getEffectivenessAgainstTypes(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)F"),
            require = 3)
    private static float shadowedhearts$shadowMatchup(String attack, String first, String second, Operation<Float> original,
            @Share("opponent") LocalRef<BattleExtrasOpponentAccessor> opponent, @Share("bestShadow") LocalFloatRef bestShadow) {
        if (!"shadow".equalsIgnoreCase(attack)) return original.call(attack, first, second);
        var target = opponent.get();
        // Switch previews multiply one factor per elemental type. Shadow has one factor per Pokémon.
        if (target == null) return 1.0F;
        opponent.set(null);
        float multiplier = BattleExtrasMovePresentation.effectiveness(target.shadowedhearts$getBattlePokemon());
        bestShadow.set(Math.max(bestShadow.get(), multiplier));
        return multiplier;
    }

    @ModifyArg(method = "buildBestSwitchMovePreviewLine", index = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lname/modid/client/CustomBattleController;buildEffectivenessComponent(F)Lnet/minecraft/network/chat/MutableComponent;"))
    private static float shadowedhearts$bestShadowMatchup(float original, @Share("bestShadow") LocalFloatRef bestShadow) {
        // Battle Extras starts its maximum at 1x, even when every target resists.
        return bestShadow.get() > 0.0F ? bestShadow.get() : original;
    }
}
