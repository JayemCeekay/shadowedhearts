package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "name.modid.client.CustomBattleController$MoveDisplayInfo")
public interface CobblemonBattleExtrasMoveDisplayInfoAccessor {

    @Accessor("rawName")
    String shadowedhearts$getRawName();
    @Accessor("rawName")
    void shadowedhearts$setRawName(String rawName);

    @Accessor("displayName")
    String shadowedhearts$getDisplayName();
    @Accessor("displayName")
    void shadowedhearts$setDisplayName(String displayName);

    @Accessor("ppText")
    String shadowedhearts$getPpText();
    @Accessor("ppText")
    void shadowedhearts$setPpText(String ppText);

    @Accessor("ppColor")
    int shadowedhearts$getPpColor();
    @Accessor("ppColor")
    void shadowedhearts$setPpColor(int ppColor);

    @Accessor("typeTextureX")
    int shadowedhearts$getTypeTextureX();
    @Accessor("typeTextureX")
    void shadowedhearts$setTypeTextureX(int typeTextureX);

    @Accessor("moveType")
    Object shadowedhearts$getMoveType();
    @Accessor("moveType")
    void shadowedhearts$setMoveType(Object moveType);
}
