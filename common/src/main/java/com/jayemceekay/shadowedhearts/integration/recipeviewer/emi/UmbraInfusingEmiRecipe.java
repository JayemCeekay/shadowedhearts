package com.jayemceekay.shadowedhearts.integration.recipeviewer.emi;

import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/** A view of one synchronized recipe, including datapack tags and result components. */
final class UmbraInfusingEmiRecipe implements EmiRecipe {
    private final EmiRecipeCategory category;
    private final RecipeHolder<UmbraInfuserRecipe> holder;
    private final EmiIngredient input;
    private final EmiStack output;

    UmbraInfusingEmiRecipe(EmiRecipeCategory category, RecipeHolder<UmbraInfuserRecipe> holder) {
        this.category = category;
        this.holder = holder;
        this.input = EmiIngredient.of(holder.value().ingredient(), 1);
        this.output = EmiStack.of(holder.value().output());
    }

    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public ResourceLocation getId() { return holder.id(); }
    @Override public List<EmiIngredient> getInputs() { return List.of(input); }
    @Override public List<EmiStack> getOutputs() { return List.of(output); }
    @Override public RecipeHolder<?> getBackingRecipe() { return holder; }
    @Override public int getDisplayWidth() { return 144; }
    @Override public int getDisplayHeight() { return 60; }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(input, 26, 4);
        widgets.addFillingArrow(58, 5, holder.value().processingTicks() * 50);
        widgets.addSlot(output, 94, 0).large(true).recipeContext(this);
        widgets.addText(InfuserViewerData.chargeText(holder.value().charge()), 2, 32, 0x404040, false);
        widgets.addText(InfuserViewerData.timeText(holder.value().processingTicks()), 2, 45, 0x404040, false);
        widgets.addTooltipText(InfuserViewerData.fuelInfo(), 0, 30, getDisplayWidth(), 29);
    }
}
