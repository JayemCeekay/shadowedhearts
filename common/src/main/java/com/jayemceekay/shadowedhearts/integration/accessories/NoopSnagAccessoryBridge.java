package com.jayemceekay.shadowedhearts.integration.accessories;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class NoopSnagAccessoryBridge implements SnagAccessoryBridge {
    @Override
    public boolean isEquipped(Player player) {
        return false;
    }

    @Override
    public ItemStack getEquippedStack(Player player) {
        return ItemStack.EMPTY;
    }
}
