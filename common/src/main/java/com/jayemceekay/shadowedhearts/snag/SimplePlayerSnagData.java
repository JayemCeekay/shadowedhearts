package com.jayemceekay.shadowedhearts.snag;
import com.jayemceekay.shadowedhearts.core.ModItemComponents;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.items.SnagMachineItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
/**
 * Cross-platform implementation that stores Snag Machine state on the item's Data Components.
 * This implementation is server-authoritative; all modifications should occur on the server.
 */
public class SimplePlayerSnagData implements PlayerSnagData {
    private final Player player;
    public SimplePlayerSnagData(Player player) {
        this.player = player;
    }
    /** Returns the player's Snag Machine stack, preferring accessory slot, then offhand, else any inventory slot. */
    public ItemStack findMachine() {
        if (player == null) return ItemStack.EMPTY;
        
        ItemStack accessory = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(player);
        if (!accessory.isEmpty()) return accessory;
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && (off.is(ModItems.SNAG_MACHINE_PROTOTYPE.get()) || off.is(ModItems.SNAG_MACHINE_ADVANCED.get()))) return off;
        try {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && (st.is(ModItems.SNAG_MACHINE_PROTOTYPE.get()) || st.is(ModItems.SNAG_MACHINE_ADVANCED.get()))) return st;
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }
    private int machineCapacity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var item = stack.getItem();
        if (item instanceof SnagMachineItem sm) {
            return Math.max(0, sm.capacity());
        }
        return 1000;
    }
    @Override
    public boolean hasSnagMachine() {
        return !findMachine().isEmpty();
    }
    public ItemStack getMachineStack() {
        return findMachine();
    }
    @Override
    public boolean isArmed() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return false;
        Boolean armed = machine.get(ModItemComponents.SNAG_ARMED.get());
        return armed != null && armed;
    }
    @Override
    public int energy() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return 0;
        return SnagEnergy.get(machine);
    }
    @Override
    public int cooldown() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return 0;
        Integer cd = machine.get(ModItemComponents.SNAG_COOLDOWN.get());
        return cd != null ? cd : 0;
    }
    @Override
    public void setArmed(boolean v) {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        machine.set(ModItemComponents.SNAG_ARMED.get(), v);
    }
    @Override
    public void consumeEnergy(int amt) {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        SnagEnergy.consume(machine, amt, machineCapacity(machine));
    }
    @Override
    public void addEnergy(int amt) {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        int capacity = machineCapacity(machine);
        int cur = SnagEnergy.get(machine);
        SnagEnergy.set(machine, cur + amt, capacity);
    }
    @Override
    public void setCooldown(int ticks) {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        machine.set(ModItemComponents.SNAG_COOLDOWN.get(), Math.max(0, ticks));
    }
    @Override
    public boolean lastSyncedEligibility() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return false;
        Boolean eligible = machine.get(ModItemComponents.SNAG_ELIGIBLE.get());
        return eligible != null && eligible;
    }
    @Override
    public void setLastSyncedEligibility(boolean v) {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        machine.set(ModItemComponents.SNAG_ELIGIBLE.get(), v);
    }

    @Override
    public int failedSnagAttempts() {
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return 0;
        Integer attempts = machine.get(ModItemComponents.SNAG_FAIL_ATTEMPTS.get());
        return attempts != null ? attempts : 0;
    }

    @Override
    public void incrementFailedSnagAttempts() {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        int current = failedSnagAttempts();
        machine.set(ModItemComponents.SNAG_FAIL_ATTEMPTS.get(), current + 1);
    }

    @Override
    public void resetFailedSnagAttempts() {
        if (player != null && player.level().isClientSide) return;
        ItemStack machine = findMachine();
        if (machine.isEmpty()) return;
        machine.set(ModItemComponents.SNAG_FAIL_ATTEMPTS.get(), 0);
    }
}
