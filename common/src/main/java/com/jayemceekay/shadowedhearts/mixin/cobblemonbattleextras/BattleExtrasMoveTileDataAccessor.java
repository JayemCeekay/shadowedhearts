package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "name.modid.client.CustomBattleController$MoveTileVisualData", remap = false)
public interface BattleExtrasMoveTileDataAccessor {
    @Accessor("moveTypeName")
    String shadowedhearts$getMoveTypeName();
}
