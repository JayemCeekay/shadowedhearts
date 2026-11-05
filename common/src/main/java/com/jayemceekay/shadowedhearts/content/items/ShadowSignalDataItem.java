package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSites;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ShadowSignalDataItem extends Item {
    private final UmbrafallTier tier;
    public ShadowSignalDataItem(Properties properties, UmbrafallTier tier) { super(properties); this.tier = tier; }
    public UmbrafallTier tier() { return tier; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) UmbrafallSites.request(serverPlayer, hand, tier);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
