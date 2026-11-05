package com.jayemceekay.shadowedhearts.common.shadow.battle;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.config.ShadowBattleRulesConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.content.items.ScentItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Direct item application only: healing machines and centers do not pass through this policy. */
public final class ShadowModeItemPolicy {
    private ShadowModeItemPolicy() {}

    public static boolean refuses(Pokemon pokemon, ItemStack stack) {
        if (pokemon == null || stack.getItem() instanceof ScentItem || !pokemon.getAspects().contains(SHAspects.SHADOW)) return false;
        var rules = ShadowBattleRulesConfig.INSTANCE;
        var config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        return config.hyperModeEnabled() && rules.hyperItemRefusal() && pokemon.getAspects().contains(SHAspects.HYPER_MODE)
                || config.reverseModeEnabled() && rules.reverseItemRefusal() && pokemon.getAspects().contains(SHAspects.REVERSE_MODE);
    }

    public static boolean reject(ServerPlayer player, Pokemon pokemon, ItemStack stack) {
        if (!refuses(pokemon, stack)) return false;
        player.sendSystemMessage(Component.translatable("message.shadowedhearts.mode_item_refusal", pokemon.getDisplayName(false)).withStyle(ChatFormatting.RED));
        return true;
    }
}
