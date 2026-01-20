package com.jayemceekay.shadowedhearts.aura;

import com.jayemceekay.shadowedhearts.core.ModItemComponents;
import net.minecraft.world.item.ItemStack;

public final class AuraReaderCharge {
    private AuraReaderCharge() {}

    public static int get(ItemStack stack) {
        Integer v = stack.get(ModItemComponents.AURA_READER_CHARGE.get());
        return v == null ? 0 : Math.max(0, v);
    }

    public static void set(ItemStack stack, int value, int capacity) {
        int v = Math.max(0, Math.min(value, Math.max(0, capacity)));
        stack.set(ModItemComponents.AURA_READER_CHARGE.get(), v);
    }

    public static void add(ItemStack stack, int amount, int capacity) {
        ensureInitialized(stack, capacity);
        int cur = get(stack);
        set(stack, cur + amount, capacity);
    }

    public static void ensureInitialized(ItemStack stack, int capacity) {
        if (!stack.has(ModItemComponents.AURA_READER_CHARGE.get())) {
            set(stack, capacity, capacity);
        } else {
            set(stack, get(stack), capacity);
        }
    }

    public static boolean consume(ItemStack stack, int amount, int capacity) {
        ensureInitialized(stack, capacity);
        int cur = get(stack);
        if (cur < amount) {
            set(stack, 0, capacity);
            return false;
        }
        set(stack, cur - amount, capacity);
        return true;
    }
}
