package com.jayemceekay.shadowedhearts.snag;

import com.jayemceekay.shadowedhearts.core.ModItemComponents;
import net.minecraft.world.item.ItemStack;

public final class SnagEnergy {
    private SnagEnergy() {}

    public static int get(ItemStack stack) {
        Integer v = stack.get(ModItemComponents.SNAG_ENERGY.get());
        return v == null ? 0 : Math.max(0, v);
    }

    public static void set(ItemStack stack, int value, int capacity) {
        int v = Math.max(0, Math.min(value, Math.max(0, capacity)));
        stack.set(ModItemComponents.SNAG_ENERGY.get(), v);
    }

    public static void ensureInitialized(ItemStack stack, int capacity) {
        if (!stack.has(ModItemComponents.SNAG_ENERGY.get())) {
            set(stack, capacity, capacity);
        } else {
            // clamp if capacity reduced
            set(stack, get(stack), capacity);
        }
    }

    public static boolean consume(ItemStack stack, int amount, int capacity) {
        ensureInitialized(stack, capacity);
        int cur = get(stack);
        if (cur < amount) return false;
        set(stack, cur - amount, capacity);
        return true;
    }
}
