package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "name.modid.client.CustomBattleController$MoveTileVisualData")
public interface CobblemonBattleExtrasMoveTileVisualDataAccessor {

    @Accessor("moveName")
    String shadowedhearts$getMoveName();

    @Accessor("currentPp")
    int shadowedhearts$getCurrentPp();

    @Accessor("maxPp")
    int shadowedhearts$getMaxPp();

    @Accessor("selectable")
    boolean shadowedhearts$getSelectable();

    @Accessor("categoryKey")
    String shadowedhearts$getCategoryKey();

    @Accessor("power")
    int shadowedhearts$getPower();

    @Accessor("moveTypeName")
    String shadowedhearts$getMoveTypeName();

    @Accessor("typeColor")
    int shadowedhearts$getTypeColor();

    @Accessor("typeTextureMultiplier")
    int shadowedhearts$getTypeTextureMultiplier();
}
