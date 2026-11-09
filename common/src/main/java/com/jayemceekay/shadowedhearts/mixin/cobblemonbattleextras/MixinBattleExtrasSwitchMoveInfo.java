package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasSwitchMove;
import com.jayemceekay.shadowedhearts.client.battle.ShadowBattleMoveDetails;
import com.jayemceekay.shadowedhearts.client.gui.ShadowMaskingUtil;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "name.modid.client.CustomBattleController$MoveDisplayInfo", remap = false)
public abstract class MixinBattleExtrasSwitchMoveInfo implements BattleExtrasSwitchMove {
    @Shadow String rawName;
    @Shadow String displayName;
    @Shadow MoveTemplate moveTemplate;
    @Shadow int currentPP;
    @Shadow int maxPP;
    @Shadow Object moveType;
    @Shadow int typeTextureX;
    @Shadow String ppText;
    @Shadow int displayNameWidth;
    @Shadow int ppColor;

    @Override
    public void shadowedhearts$prepareSwitchMove(Pokemon pokemon) {
        if (ShadowUnlockPolicy.shouldMaskMove(pokemon, moveTemplate == null ? rawName : moveTemplate.getName())) {
            displayName = ShadowMaskingUtil.MASKED_NAME.getString();
            displayNameWidth = Minecraft.getInstance().font.width(displayName);
            moveTemplate = null; // Also suppresses the damage/effectiveness preview for this row.
            moveType = ShadowMaskingUtil.getLockedType();
            currentPP = maxPP = 0;
            ppText = ShadowMaskingUtil.MASKED_PP.getString();
            ppColor = 0xFFFFFF;
        } else if (ShadowBattleMoveDetails.unlimited(moveTemplate, pokemon)) {
            ppText = Component.translatable("ui.shadowedhearts.battle_rules.unlimited_pp").getString();
            ppColor = 0xFFFFFF;
        }
        if (shadowedhearts$switchIcon() != null) typeTextureX = 0;
    }

    @Override
    public ResourceLocation shadowedhearts$switchIcon() {
        return moveType instanceof ElementalType type ? BattleExtrasMovePresentation.icon(type.getName(), false) : null;
    }
}
