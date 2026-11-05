package com.jayemceekay.shadowedhearts.integration.recipeviewer.rei;

import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Optional;

/** Keeps the server's recipe identity, ingredient alternatives, and complete output stack. */
public final class InfusionDisplay extends BasicDisplay {
    public static final CategoryIdentifier<InfusionDisplay> CATEGORY =
            CategoryIdentifier.of(InfuserViewerData.INFUSING_ID);

    private final int charge;
    private final int processingTicks;

    public InfusionDisplay(RecipeHolder<UmbraInfuserRecipe> holder) {
        super(List.of(EntryIngredients.ofIngredient(holder.value().ingredient())),
                List.of(EntryIngredients.of(holder.value().output())), Optional.of(holder.id()));
        charge = holder.value().charge();
        processingTicks = holder.value().processingTicks();
    }

    public int charge() { return charge; }
    public int processingTicks() { return processingTicks; }

    @Override
    public CategoryIdentifier<InfusionDisplay> getCategoryIdentifier() { return CATEGORY; }
}
