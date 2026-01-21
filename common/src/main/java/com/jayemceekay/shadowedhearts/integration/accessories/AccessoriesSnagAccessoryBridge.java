package com.jayemceekay.shadowedhearts.integration.accessories;

import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.snag.SnagMachineItem;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class AccessoriesSnagAccessoryBridge implements SnagAccessoryBridge {

    public AccessoriesSnagAccessoryBridge() {
        register(ModItems.SNAG_MACHINE_PROTOTYPE.get());
        register(ModItems.SNAG_MACHINE_ADVANCED.get());
        register(ModItems.AURA_READER.get());
    }

    private void register(net.minecraft.world.item.Item item) {
        if (item instanceof SnagMachineItem) {
            try {
                AccessoriesAPI.registerAccessory(item, new io.wispforest.accessories.api.Accessory() {
                    @Override
                    public void onEquip(ItemStack stack, SlotReference reference) {

                    }

                    @Override
                    public boolean canEquip(ItemStack stack, SlotReference reference) {
                        if (!(reference.entity() instanceof Player player)) return true;
                        ItemStack equipped = getEquippedStack(player);
                        return equipped.isEmpty() || equipped == stack;
                    }
                });
            } catch (Throwable ignored) {}
        } else if (item == ModItems.AURA_READER.get()) {
            try {
                AccessoriesAPI.registerAccessory(item, new io.wispforest.accessories.api.Accessory() {});
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean isEquipped(Player player) {
        return !getEquippedStack(player).isEmpty();
    }

    @Override
    public boolean isAuraReaderEquipped(Player player) {
        try {
            AccessoriesCapability capability = AccessoriesCapability.get(player);
            if (capability != null) {
                for (var container : capability.getContainers().values()) {
                    for (var accessory : container.getAccessories()) {
                        if (accessory != null && accessory.getSecond().is(ModItems.AURA_READER.get())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return player.getInventory().armor.get(3).is(ModItems.AURA_READER.get());
    }


    @Override
    public ItemStack getEquippedStack(Player player) {
        try {
            AccessoriesCapability capability = AccessoriesCapability.get(player);
            if (capability != null) {
                for (var container : capability.getContainers().values()) {
                    for (var accessory : container.getAccessories()) {
                        if (accessory != null && (accessory.getSecond().getItem() instanceof SnagMachineItem || accessory.getSecond().is(ModItems.AURA_READER.get()))) {
                            return accessory.getSecond();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (player.getMainHandItem().getItem() instanceof SnagMachineItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof SnagMachineItem) return player.getOffhandItem();

        return ItemStack.EMPTY;
    }
}
