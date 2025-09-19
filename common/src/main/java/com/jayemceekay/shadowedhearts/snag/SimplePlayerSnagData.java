package com.jayemceekay.shadowedhearts.snag;

import com.jayemceekay.shadowedhearts.core.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Cross-platform implementation that stores Snag Machine state on the item's CustomData (Data Components).
 * This avoids platform-specific player persistent data and works on both Fabric and NeoForge.
 */
public class SimplePlayerSnagData implements PlayerSnagData {
    private static final String KEY = "shadowedhearts.snag";
    private static final String K_ARMED = "armed";
    private static final String K_ENERGY = "energy";
    private static final String K_COOLDOWN = "cooldown";

    private final Player player;

    public SimplePlayerSnagData(Player player) {
        this.player = player;
    }

    /** Returns the player's Snag Machine stack, preferring offhand, else any inventory slot. */
    private ItemStack findMachine() {
        if (player == null) return ItemStack.EMPTY;
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.SNAG_MACHINE.get())) return off;
        try {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && st.is(ModItems.SNAG_MACHINE.get())) return st;
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    private CompoundTag readRoot(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private void writeRoot(ItemStack stack, CompoundTag root) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    @Override
    public boolean hasSnagMachine() {
        return !findMachine().isEmpty();
    }

    @Override
    public boolean isArmed() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return false;
        CompoundTag root = readRoot(machine);
        if (root == null) return false;
        CompoundTag tag = root.getCompound(KEY);
        return tag.getBoolean(K_ARMED);
    }

    @Override
    public int energy() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return 0;
        CompoundTag root = readRoot(machine);
        if (root == null) return 1000;
        CompoundTag tag = root.getCompound(KEY);
        return tag.contains(K_ENERGY) ? tag.getInt(K_ENERGY) : 1000;
    }

    @Override
    public int cooldown() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return 0;
        CompoundTag root = readRoot(machine);
        if (root == null) return 0;
        CompoundTag tag = root.getCompound(KEY);
        return tag.contains(K_COOLDOWN) ? tag.getInt(K_COOLDOWN) : 0;
    }

    @Override
    public void setArmed(boolean v) {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        CompoundTag root = readRoot(machine);
        if (root == null) root = new CompoundTag();
        CompoundTag tag = root.getCompound(KEY);
        tag.putBoolean(K_ARMED, v);
        root.put(KEY, tag);
        writeRoot(machine, root);
    }

    @Override
    public void consumeEnergy(int amt) {
        if (amt <= 0) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        CompoundTag root = readRoot(machine);
        if (root == null) root = new CompoundTag();
        CompoundTag tag = root.getCompound(KEY);
        int cur = Math.max(0, (tag.contains(K_ENERGY) ? tag.getInt(K_ENERGY) : 1000) - amt);
        tag.putInt(K_ENERGY, cur);
        root.put(KEY, tag);
        writeRoot(machine, root);
    }

    @Override
    public void setCooldown(int ticks) {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        CompoundTag root = readRoot(machine);
        if (root == null) root = new CompoundTag();
        CompoundTag tag = root.getCompound(KEY);
        tag.putInt(K_COOLDOWN, Math.max(0, ticks));
        root.put(KEY, tag);
        writeRoot(machine, root);
    }
}
