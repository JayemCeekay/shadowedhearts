package com.jayemceekay.shadowedhearts.fabric;

import com.cobblemon.mod.common.CobblemonItems;
import com.jayemceekay.shadowedhearts.core.ModItems;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;

public final class FabricBrewingRecipes {
    public static void register() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            builder.addContainerRecipe(CobblemonItems.BERRY_JUICE, CobblemonItems.POTION, ModItems.JOY_SCENT.get());
            builder.addContainerRecipe(CobblemonItems.BERRY_JUICE, CobblemonItems.SUPER_POTION, ModItems.EXCITE_SCENT.get());
            builder.addContainerRecipe(CobblemonItems.BERRY_JUICE, CobblemonItems.HYPER_POTION, ModItems.VIVID_SCENT.get());
        });
    }
}
