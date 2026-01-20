package com.jayemceekay.shadowedhearts.integration.accessories;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface SnagAccessoryBridge {
    boolean isEquipped(Player player);
    ItemStack getEquippedStack(Player player);
    boolean isAuraReaderEquipped(Player player);
}
