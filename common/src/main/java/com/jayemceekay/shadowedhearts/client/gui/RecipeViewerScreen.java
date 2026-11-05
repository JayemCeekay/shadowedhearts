package com.jayemceekay.shadowedhearts.client.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Physical GUI-pixel geometry shared by optional recipe viewers, without depending on any viewer API. */
public interface RecipeViewerScreen {
    ScreenRectangle recipeViewerBounds();

    default @Nullable HoveredStack recipeViewerStackAt(double screenX, double screenY) { return null; }

    record HoveredStack(ItemStack stack, ScreenRectangle bounds) { }

    static ScreenRectangle bounds(GuiViewportFit fit, int left, int top, int width, int height) {
        int x = (int) Math.floor(fit.toScreenX(left));
        int y = (int) Math.floor(fit.toScreenY(top));
        int right = (int) Math.ceil(fit.toScreenX(left + width));
        int bottom = (int) Math.ceil(fit.toScreenY(top + height));
        return new ScreenRectangle(x, y, right - x, bottom - y);
    }
}
