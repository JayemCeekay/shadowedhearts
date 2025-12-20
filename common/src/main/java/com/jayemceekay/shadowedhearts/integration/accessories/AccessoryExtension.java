package com.jayemceekay.shadowedhearts.integration.accessories;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.world.item.ItemStack;

public interface AccessoryExtension extends Accessory {
    String getAttributePath();
    String getDefaultSlot();

    @Override
    default void onEquip(ItemStack stack, SlotReference reference) {
        // We can implement default behavior here if needed, 
        // but accessorify's version depended on its own config.
        // For now, we'll leave it simple or implement what's necessary for ShadowedHearts.
    }

    @Override
    default void onUnequip(ItemStack stack, SlotReference reference) {
    }
}
