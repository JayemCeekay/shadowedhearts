package com.jayemceekay.shadowedhearts.integration.recipeviewer.emi;

import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Native paginated information layout; charge is stored energy, not a fictitious item output. */
final class InfuserFuelEmiRecipe extends EmiInfoRecipe {
    private final EmiRecipeCategory category;

    InfuserFuelEmiRecipe(EmiRecipeCategory category, InfuserViewerData.Fuel fuel) {
        super(List.of(EmiStack.of(fuel.stack().copy())), description(fuel.charge()), id(fuel));
        this.category = category;
    }

    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public List<EmiStack> getOutputs() { return List.of(); }

    private static List<Component> description(int charge) {
        var text = new ArrayList<Component>();
        text.add(InfuserViewerData.chargeText(charge));
        text.addAll(InfuserViewerData.fuelInfo());
        return text;
    }

    private static ResourceLocation id(InfuserViewerData.Fuel fuel) {
        var itemId = BuiltInRegistries.ITEM.getKey(fuel.stack().getItem());
        return ResourceLocation.fromNamespaceAndPath("shadowedhearts",
                "/infuser_fuel/" + itemId.getNamespace() + "/" + itemId.getPath());
    }
}
