package com.jayemceekay.shadowedhearts.blocks.menu;

import com.jayemceekay.shadowedhearts.blocks.entity.SignalLocatorBlockEntity;
import com.jayemceekay.shadowedhearts.core.ModMenuTypes;
import com.jayemceekay.shadowedhearts.signals.SignalFragmentItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the Signal Locator.
 */
public class SignalLocatorMenu extends AbstractContainerMenu {
    private final Level level;
    private final @Nullable SignalLocatorBlockEntity be;
    private final Container grid;
    private final Container out;

    // Client-side constructor (no BE available)
    public SignalLocatorMenu(int id, Inventory playerInv) {
        super(ModMenuTypes.SIGNAL_LOCATOR_MENU.get(), id);
        this.level = playerInv.player.level();
        this.be = null;
        this.grid = new SimpleContainer(9);
        this.out = new SimpleContainer(1);
        initSlots(playerInv);
    }

    // Server-side constructor (BE available)
    public SignalLocatorMenu(int id, Inventory playerInv, SignalLocatorBlockEntity be) {
        super(ModMenuTypes.SIGNAL_LOCATOR_MENU.get(), id);
        this.level = be.getLevel();
        this.be = be;
        this.grid = be.grid();
        this.out = be.output();
        initSlots(playerInv);
    }

    private void initSlots(Inventory playerInv) {

        // Grid 3x3 starting at (44,17)
        int startX = 44, startY = 17;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int idx = r * 3 + c;
                addSlot(new Slot(grid, idx, startX + c * 18, startY + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof SignalFragmentItem;
                    }
                });
            }
        }
        // Output at (124,35)
        addSlot(new Slot(out, 0, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
            @Override
            public void onTake(Player player, ItemStack taken) {
                if (be != null) be.onTakeOutput(player);
                super.onTake(player, taken);
            }
        });

        // Player inventory
        layoutPlayerInventorySlots(playerInv, 8, 84);
    }

    private void layoutPlayerInventorySlots(Inventory inv, int leftCol, int topRow) {
        // main inv
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, leftCol + col * 18, topRow + row * 18));
            }
        }
        // hotbar
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, leftCol + col * 18, topRow + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (be != null && be.getBlockPos() != null) {
            BlockPos p = be.getBlockPos();
            return ContainerLevelAccess.create(level, p).evaluate((lvl, ignored) -> player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            int containerSlots = 9 + 1; // grid + out
            if (index < containerSlots) {
                // move to player inventory
                if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                // from player inv to grid only if valid
                if (stack.getItem() instanceof SignalFragmentItem) {
                    if (!this.moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            if (stack.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return itemstack;
    }
}
