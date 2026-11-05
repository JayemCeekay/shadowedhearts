package com.jayemceekay.shadowedhearts.integration.recipeviewer.emi;

import com.jayemceekay.shadowedhearts.client.purification.PurificationChamberClientSession;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiSidebars;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Scoped compatibility with EMI 1.1.23–1.1.24's generic-screen fallback and native return history.
 * The pinned seam is EmiApi.setPages/viewRecipeTree plus RecipeScreen/BoMScreen.old.
 */
public final class EmiChamberReturn {
    private EmiChamberReturn() {}

    public static boolean tryOpenRecipes(Map<EmiRecipeCategory, List<EmiRecipe>> recipes, EmiIngredient stack) {
        return tryOpenRecipes(Minecraft.getInstance(), recipes, stack,
                PurificationChamberClientSession.INSTANCE::ownsScreen);
    }

    static boolean tryOpenRecipes(Minecraft client, Map<EmiRecipeCategory, List<EmiRecipe>> recipes,
                                  EmiIngredient stack, Predicate<Screen> owned) {
        Screen origin = client.screen;
        if (!canBridge(client, origin, owned)) return false;
        // Matches native EmiApi.setPages: empty categories do not open a screen or change history.
        var pages = recipes.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!pages.isEmpty()) {
            EmiSidebars.lookup(stack);
            EmiHistory.clear();
            EmiHistory.push(origin);
            client.setScreen(new RecipeScreen(new ChamberReturnInventoryScreen(client.player, origin, owned), pages));
        }
        return true;
    }

    public static boolean tryOpenTree() {
        return tryOpenTree(Minecraft.getInstance(), PurificationChamberClientSession.INSTANCE::ownsScreen);
    }

    static boolean tryOpenTree(Minecraft client, Predicate<Screen> owned) {
        Screen origin = client.screen;
        if (!canBridge(client, origin, owned)) return false;
        EmiHistory.clear();
        EmiHistory.push(origin);
        client.setScreen(new BoMScreen(new ChamberReturnInventoryScreen(client.player, origin, owned)));
        return true;
    }

    private static boolean canBridge(Minecraft client, Screen origin, Predicate<Screen> owned) {
        return client.player != null && origin != null && !(origin instanceof AbstractContainerScreen<?>)
                && owned.test(origin);
    }

    public static boolean returnsTo(Screen screen, Screen origin) {
        if (origin == null) return false;
        Screen old = screen instanceof RecipeScreen recipes ? recipes.old
                : screen instanceof BoMScreen tree ? tree.old : screen;
        return old instanceof ChamberReturnInventoryScreen bridge && bridge.origin() == origin;
    }
}
