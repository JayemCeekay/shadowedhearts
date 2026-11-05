package com.jayemceekay.shadowedhearts.integration.recipeviewer.jei;

import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class InfuserFuelCategory implements IRecipeCategory<InfuserViewerData.Fuel> {
    private final IDrawable icon;
    private final IDrawable arrow;

    InfuserFuelCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.SHADOW_SHARD.get()));
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<InfuserViewerData.Fuel> getRecipeType() {
        return ShadowedHeartsJeiPlugin.FUEL;
    }

    @Override
    public Component getTitle() {
        return InfuserViewerData.fuelTitle();
    }

    @Override
    public int getWidth() {
        return 170;
    }

    @Override
    public int getHeight() {
        return 36;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, InfuserViewerData.Fuel fuel, IFocusGroup focuses) {
        builder.addInputSlot(9, 10).setStandardSlotBackground().addItemStack(fuel.stack().copy());
    }

    @Override
    public void draw(InfuserViewerData.Fuel fuel, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 39, 10);
        graphics.drawString(Minecraft.getInstance().font, InfuserViewerData.chargeText(fuel.charge()),
                74, 14, 0xFF404040, false);
    }

    @Override
    public ResourceLocation getRegistryName(InfuserViewerData.Fuel fuel) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(fuel.stack().getItem());
        return ResourceLocation.fromNamespaceAndPath(InfuserViewerData.FUEL_ID.getNamespace(),
                InfuserViewerData.FUEL_ID.getPath() + "/" + itemId.getNamespace() + "/" + itemId.getPath());
    }
}
