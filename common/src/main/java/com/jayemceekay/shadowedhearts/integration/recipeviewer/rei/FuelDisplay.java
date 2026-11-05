package com.jayemceekay.shadowedhearts.integration.recipeviewer.rei;

import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import java.util.List;

/** Fuel adds stored charge; it does not manufacture another item. */
public final class FuelDisplay extends BasicDisplay {
    public static final CategoryIdentifier<FuelDisplay> CATEGORY =
            CategoryIdentifier.of(InfuserViewerData.FUEL_ID);

    private final int charge;

    public FuelDisplay(InfuserViewerData.Fuel fuel) {
        super(List.of(EntryIngredients.of(fuel.stack().copy())), List.of());
        charge = fuel.charge();
    }

    public int charge() { return charge; }

    @Override
    public CategoryIdentifier<FuelDisplay> getCategoryIdentifier() { return CATEGORY; }
}
