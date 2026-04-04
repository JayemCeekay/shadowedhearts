package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import name.modid.client.TypeChart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Only for >=1.7.41
// TypeChart is used to calculate effectiveness to display texts in some custom tooltips
// The Mixin only modifies the methods so Shadow moves always display a x2 (if enabled in config)
// TODO: This should check if the opposing Pokémon is a Shadow Pokémon too, but I don't think there is a way to do it here
@Mixin(TypeChart.class)
public abstract class CobblemonBattleExtrasTypeChartMixin {

    @Unique
    private static float shadowedhearts$getTypeMultiplier() {
        IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        if (cfg.superEffectiveShadowMovesEnabled()) {
            return 2.0F;
        }
        return 1.0F;
    }

    @WrapMethod(method = "getEffectiveness", remap = false)
    private static float shadowedhearts$getEffectiveness(MoveTemplate move, ElementalType defenderType1, ElementalType defenderType2, Operation<Float> original) {
        if (move == null) {
            return 1.0F;
        } else {
            String moveType = move.getElementalType().getName().toLowerCase();
            if (moveType.equals("shadow"))
                return shadowedhearts$getTypeMultiplier();
            return original.call(move, defenderType1, defenderType2);
        }
    }

    @WrapMethod(method = "getEffectivenessAgainstTypes", remap = false)
    private static float shadowedhearts$getEffectivenessAgainstTypes(String moveType, String defenderType1, String defenderType2, Operation<Float> original) {
        if (moveType.equals("shadow"))
            return shadowedhearts$getTypeMultiplier();
        return original.call(moveType, defenderType1, defenderType2);
    }
}
