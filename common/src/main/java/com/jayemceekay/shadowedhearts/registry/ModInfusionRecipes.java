package com.jayemceekay.shadowedhearts.registry;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.content.infusion.InfuserFuelRecipe;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class ModInfusionRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.RECIPE_TYPE);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Shadowedhearts.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<RecipeType<UmbraInfuserRecipe>> UMBRA_INFUSER_TYPE =
            RECIPE_TYPES.register("umbra_infusing", () -> new RecipeType<UmbraInfuserRecipe>() {
                @Override
                public String toString() {
                    return Shadowedhearts.MOD_ID + ":umbra_infusing";
                }
            });
    public static final RegistrySupplier<UmbraInfuserRecipe.Serializer> UMBRA_INFUSER_SERIALIZER =
            RECIPE_SERIALIZERS.register("umbra_infusing", UmbraInfuserRecipe.Serializer::new);

    public static final RegistrySupplier<RecipeType<InfuserFuelRecipe>> INFUSER_FUEL_TYPE =
            RECIPE_TYPES.register("infuser_fuel", () -> new RecipeType<InfuserFuelRecipe>() {
                @Override public String toString() { return Shadowedhearts.MOD_ID + ":infuser_fuel"; }
            });
    public static final RegistrySupplier<InfuserFuelRecipe.Serializer> INFUSER_FUEL_SERIALIZER =
            RECIPE_SERIALIZERS.register("infuser_fuel", InfuserFuelRecipe.Serializer::new);

    private ModInfusionRecipes() {
    }

    public static void init() {
        RECIPE_TYPES.register();
        RECIPE_SERIALIZERS.register();
    }
}
