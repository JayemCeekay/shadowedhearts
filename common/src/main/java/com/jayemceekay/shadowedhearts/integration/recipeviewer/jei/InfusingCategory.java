package com.jayemceekay.shadowedhearts.integration.recipeviewer.jei;

import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

final class InfusingCategory implements IRecipeCategory<RecipeHolder<UmbraInfuserRecipe>> {
    private static final int WIDTH = 170;
    private static final int HEIGHT = 68;
    private final IDrawable icon;
    private final IDrawable arrow;

    InfusingCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.UMBRA_INFUSER.get()));
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<UmbraInfuserRecipe>> getRecipeType() {
        return ShadowedHeartsJeiPlugin.INFUSING;
    }

    @Override
    public Component getTitle() {
        return InfuserViewerData.title();
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<UmbraInfuserRecipe> holder,
                          IFocusGroup focuses) {
        UmbraInfuserRecipe recipe = holder.value();
        builder.addInputSlot(18, 10).setStandardSlotBackground().addIngredients(recipe.ingredient());
        builder.addOutputSlot(136, 10).setOutputSlotBackground().addItemStack(recipe.output());
    }

    @Override
    public void draw(RecipeHolder<UmbraInfuserRecipe> holder, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, (WIDTH - arrow.getWidth()) / 2, 10);
        drawCentered(graphics, InfuserViewerData.chargeText(holder.value().charge()), 39);
        drawCentered(graphics, InfuserViewerData.timeText(holder.value().processingTicks()), 53);
    }

    private static void drawCentered(GuiGraphics graphics, Component text, int y) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, text, (WIDTH - font.width(text)) / 2, y, 0xFF404040, false);
    }
}
