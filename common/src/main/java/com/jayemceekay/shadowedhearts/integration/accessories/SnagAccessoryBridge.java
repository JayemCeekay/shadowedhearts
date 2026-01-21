package com.jayemceekay.shadowedhearts.integration.accessories;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface SnagAccessoryBridge {
    boolean isEquipped(Player player);

    ItemStack getEquippedStack(Player player);

    boolean isAuraReaderEquipped(Player player);

    default ItemStack getAuraReaderStack(Player player) {
        if (isAuraReaderEquipped(player)) {
            ItemStack accessory = getEquippedStack(player);
            if (!accessory.isEmpty() && accessory.getItem() instanceof com.jayemceekay.shadowedhearts.items.AuraReaderItem) {
                return accessory;
            }
            return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        }
        return ItemStack.EMPTY;
    }
}
