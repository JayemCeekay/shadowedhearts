package com.jayemceekay.shadowedhearts.integration.recipeviewer.emi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/**
 * EMI requires a container as its recipe/tree return target. This carries the player's native
 * inventory menu but never opens an inventory UI; a native fallback return restores the origin.
 */
final class ChamberReturnInventoryScreen extends InventoryScreen {
    private final Screen origin;
    private final Predicate<Screen> stillOwned;

    ChamberReturnInventoryScreen(Player player, Screen origin, Predicate<Screen> stillOwned) {
        super(player);
        this.origin = origin;
        this.stillOwned = stillOwned;
    }

    Screen origin() { return origin; }

    @Override
    protected void init() { returnToOrigin(); }

    @Override
    public void onClose() { returnToOrigin(); }

    @Override
    public void removed() {
        // Native inventory widgets were never initialized and own no lifecycle resources here.
    }

    private void returnToOrigin() {
        Minecraft.getInstance().setScreen(stillOwned.test(origin) ? origin : null);
    }
}
