package com.jayemceekay.shadowedhearts.integration.recipeviewer.emi;

import com.jayemceekay.shadowedhearts.client.purification.ChamberRecipeViewerSupport;

import com.jayemceekay.shadowedhearts.client.gui.PurificationChamberGUI;
import com.jayemceekay.shadowedhearts.client.gui.RecipeViewerScreen;
import com.jayemceekay.shadowedhearts.client.gui.UmbraInfuserScreen;
import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Loaded by EMI's optional client entrypoint; no machine transfer handler is registered. */
@EmiEntrypoint
public final class ShadowedHeartsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        ChamberRecipeViewerSupport.register("emi", EmiChamberReturn::returnsTo);
        EmiStack infuser = EmiStack.of(ModItems.UMBRA_INFUSER.get());
        EmiRecipeCategory infusing = new EmiRecipeCategory(InfuserViewerData.INFUSING_ID, infuser) {
            @Override public Component getName() { return InfuserViewerData.title(); }
        };
        EmiRecipeCategory fuel = new EmiRecipeCategory(InfuserViewerData.FUEL_ID,
                EmiStack.of(ModItems.SHADOW_SHARD.get())) {
            @Override public Component getName() { return InfuserViewerData.fuelTitle(); }
        };
        registry.addCategory(infusing);
        registry.addCategory(fuel);
        registry.addWorkstation(infusing, infuser);
        registry.addWorkstation(fuel, infuser);
        registry.addWorkstation(fuel, EmiStack.of(ModItems.UMBRA_INFUSER_TANK.get()));

        // EMI repeats registration when the connected server's recipes are rebuilt.
        for (var holder : InfuserViewerData.recipes(registry.getRecipeManager())) {
            registry.addRecipe(new UmbraInfusingEmiRecipe(infusing, holder));
        }
        for (var entry : InfuserViewerData.fuels(registry.getRecipeManager())) {
            registry.addRecipe(new InfuserFuelEmiRecipe(fuel, entry));
        }
        registry.addRecipe(new EmiInfoRecipe(List.of(infuser), InfuserViewerData.conversionInfo(),
                ResourceLocation.fromNamespaceAndPath("shadowedhearts", "/info/pokemon_infusion")));

        registerScreen(registry, UmbraInfuserScreen.class);
        registerScreen(registry, PurificationChamberGUI.class);
    }

    private static <T extends Screen & RecipeViewerScreen> void registerScreen(EmiRegistry registry, Class<T> screenClass) {
        registry.addScreenBoundsProvider(screenClass, screen -> bounds(screen.recipeViewerBounds()));
        registry.addExclusionArea(screenClass, (screen, consumer) -> consumer.accept(bounds(screen.recipeViewerBounds())));
        registry.addStackProvider(screenClass, (screen, x, y) -> {
            var hovered = screen.recipeViewerStackAt(x, y);
            if (hovered == null || hovered.stack().isEmpty()) return EmiStackInteraction.EMPTY;
            // Inventory slots retain their ordinary clicks; EMI's recipe/usage keys still work.
            return new EmiStackInteraction(EmiStack.of(hovered.stack().copy()), null, false);
        });
    }

    private static Bounds bounds(ScreenRectangle rectangle) {
        return new Bounds(rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height());
    }
}
