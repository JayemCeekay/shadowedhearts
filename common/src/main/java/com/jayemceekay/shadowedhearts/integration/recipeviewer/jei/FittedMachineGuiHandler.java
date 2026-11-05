package com.jayemceekay.shadowedhearts.integration.recipeviewer.jei;

import com.jayemceekay.shadowedhearts.client.gui.RecipeViewerScreen;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** JEI operates in physical GUI pixels, outside the machine screens' fitted render pose. */
final class FittedMachineGuiHandler implements IGlobalGuiHandler {
    private final IIngredientManager ingredients;

    FittedMachineGuiHandler(IIngredientManager ingredients) {
        this.ingredients = ingredients;
    }

    static IGuiProperties properties(Screen screen) {
        ScreenRectangle bounds = ((RecipeViewerScreen) screen).recipeViewerBounds();
        return new Properties(screen.getClass(), bounds.left(), bounds.top(), bounds.width(), bounds.height(),
                screen.width, screen.height);
    }

    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        if (Minecraft.getInstance().screen instanceof RecipeViewerScreen screen) {
            return List.of(rectangle(screen.recipeViewerBounds()));
        }
        return List.of();
    }

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        if (!(Minecraft.getInstance().screen instanceof RecipeViewerScreen screen)) {
            return Optional.empty();
        }
        RecipeViewerScreen.HoveredStack hovered = screen.recipeViewerStackAt(mouseX, mouseY);
        if (hovered == null || hovered.stack().isEmpty()) {
            return Optional.empty();
        }
        return ingredients.createClickableIngredient(VanillaTypes.ITEM_STACK, hovered.stack().copy(),
                rectangle(hovered.bounds()), false).map(ingredient -> ingredient);
    }

    private static Rect2i rectangle(ScreenRectangle bounds) {
        return new Rect2i(bounds.left(), bounds.top(), bounds.width(), bounds.height());
    }

    private record Properties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
                              int guiXSize, int guiYSize, int screenWidth, int screenHeight)
            implements IGuiProperties { }
}
