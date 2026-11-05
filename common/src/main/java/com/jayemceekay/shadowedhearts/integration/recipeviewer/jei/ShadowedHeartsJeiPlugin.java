package com.jayemceekay.shadowedhearts.integration.recipeviewer.jei;

import com.jayemceekay.shadowedhearts.client.gui.PurificationChamberGUI;
import com.jayemceekay.shadowedhearts.client.gui.UmbraInfuserScreen;
import com.jayemceekay.shadowedhearts.client.purification.ChamberRecipeViewerSupport;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** Loaded only through JEI's optional plugin discovery. */
@JeiPlugin
public final class ShadowedHeartsJeiPlugin implements IModPlugin {
    static final RecipeType<RecipeHolder<UmbraInfuserRecipe>> INFUSING =
            RecipeType.createRecipeHolderType(InfuserViewerData.INFUSING_ID);
    static final RecipeType<InfuserViewerData.Fuel> FUEL =
            new RecipeType<>(InfuserViewerData.FUEL_ID, InfuserViewerData.Fuel.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("shadowedhearts", "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new InfusingCategory(guiHelper), new InfuserFuelCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(INFUSING, InfuserViewerData.recipes(level.getRecipeManager()));
        }

        List<InfuserViewerData.Fuel> fuels = InfuserViewerData.fuels(level == null ? null : level.getRecipeManager());
        registration.addRecipes(FUEL, fuels);
        registration.addItemStackInfo(List.of(new ItemStack(ModItems.UMBRA_INFUSER.get()),
                        new ItemStack(ModItems.UMBRA_INFUSER_TANK.get())),
                InfuserViewerData.conversionInfo().toArray(Component[]::new));

        List<ItemStack> fuelInformationItems = new ArrayList<>();
        fuels.forEach(fuel -> fuelInformationItems.add(fuel.stack().copy()));
        fuelInformationItems.add(new ItemStack(ModItems.UMBRA_INFUSER_TANK.get()));
        registration.addItemStackInfo(fuelInformationItems,
                InfuserViewerData.fuelInfo().toArray(Component[]::new));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(INFUSING, ModItems.UMBRA_INFUSER.get());
        registration.addRecipeCatalysts(FUEL, ModItems.UMBRA_INFUSER.get());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        ChamberRecipeViewerSupport.register("jei", ShadowedHeartsJeiPlugin::returnsTo);
        registration.addGuiScreenHandler(UmbraInfuserScreen.class, FittedMachineGuiHandler::properties);
        registration.addGuiScreenHandler(PurificationChamberGUI.class, FittedMachineGuiHandler::properties);
        registration.addGlobalGuiHandler(new FittedMachineGuiHandler(
                registration.getJeiHelpers().getIngredientManager()));
    }

    static boolean returnsTo(Screen screen, Screen origin) {
        return origin != null && screen instanceof IRecipesGui recipes
                && recipes.getParentScreen().orElse(null) == origin;
    }
}
