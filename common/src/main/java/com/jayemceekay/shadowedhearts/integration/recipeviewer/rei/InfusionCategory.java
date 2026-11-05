package com.jayemceekay.shadowedhearts.integration.recipeviewer.rei;

import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class InfusionCategory implements DisplayCategory<InfusionDisplay> {
    @Override
    public CategoryIdentifier<InfusionDisplay> getCategoryIdentifier() { return InfusionDisplay.CATEGORY; }

    @Override
    public Component getTitle() { return InfuserViewerData.title(); }

    @Override
    public Renderer getIcon() { return EntryStacks.of(new ItemStack(ModItems.UMBRA_INFUSER.get())); }

    @Override
    public int getDisplayWidth(InfusionDisplay display) { return 164; }

    @Override
    public int getDisplayHeight() { return 74; }

    @Override
    public List<Widget> setupDisplay(InfusionDisplay display, Rectangle bounds) {
        int centerX = bounds.getCenterX();
        return List.of(
                Widgets.createRecipeBase(bounds),
                Widgets.createSlot(new Point(centerX - 60, bounds.y + 17))
                        .entries(display.getInputEntries().getFirst()).markInput(),
                Widgets.createArrow(new Point(centerX - 12, bounds.y + 18)),
                Widgets.createSlot(new Point(centerX + 42, bounds.y + 17))
                        .entries(display.getOutputEntries().getFirst()).markOutput(),
                Widgets.createLabel(new Point(centerX, bounds.y + 45),
                        InfuserViewerData.chargeText(display.charge())).centered().noShadow()
                        .color(0xFF404040, 0xFFBBBBBB),
                Widgets.createLabel(new Point(centerX, bounds.y + 58),
                        InfuserViewerData.timeText(display.processingTicks())).centered().noShadow()
                        .color(0xFF404040, 0xFFBBBBBB));
    }
}
