package com.jayemceekay.shadowedhearts.client.battle;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.resources.ResourceLocation;

public final class BattleExtrasMovePresentation {
    private static final ResourceLocation SHADOW = texture("shadow_type");
    private static final ResourceLocation SMALL_SHADOW = texture("shadow_type_small");
    private static final ResourceLocation LOCKED = texture("disabled_move");
    private static final ResourceLocation SMALL_LOCKED = texture("disabled_move_small");

    private BattleExtrasMovePresentation() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/gui/" + name + ".png");
    }

    public static ResourceLocation icon(String type, boolean small) {
        if ("shadow".equalsIgnoreCase(type)) return small ? SMALL_SHADOW : SHADOW;
        if ("shadow-locked".equalsIgnoreCase(type)) return small ? SMALL_LOCKED : LOCKED;
        return null;
    }

    public static float effectiveness(ClientBattlePokemon target) {
        if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().superEffectiveShadowMovesEnabled()) return 1.0F;
        return target != null && target.getState().getCurrentAspects().contains(SHAspects.SHADOW) ? 0.5F : 2.0F;
    }
}
