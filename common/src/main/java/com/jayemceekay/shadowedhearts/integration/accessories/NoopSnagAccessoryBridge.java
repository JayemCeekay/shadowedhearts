package com.jayemceekay.shadowedhearts.integration.accessories;

import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.items.SnagMachineItem;
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
        if (player.getMainHandItem().getItem() instanceof SnagMachineItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof SnagMachineItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isAuraReaderEquipped(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.AURA_READER.get());
    }
}
