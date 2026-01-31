package com.jayemceekay.shadowedhearts.integration.accessories;

import com.jayemceekay.shadowedhearts.core.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class NoopSnagAccessoryBridge implements SnagAccessoryBridge {
    @Override
    public boolean isEquipped(Player player) {
        return !getEquippedStack(player).isEmpty();
    }

    @Override
    public ItemStack getEquippedStack(Player player) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isAuraReaderEquipped(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.AURA_READER.get());
    }

    @Override
    public ItemStack getAuraReaderStack(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(ModItems.AURA_READER.get())) {
            return head;
        }
        return ItemStack.EMPTY;
    }
}
