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

public final class FuelCategory implements DisplayCategory<FuelDisplay> {
    @Override
    public CategoryIdentifier<FuelDisplay> getCategoryIdentifier() { return FuelDisplay.CATEGORY; }

    @Override
    public Component getTitle() { return InfuserViewerData.fuelTitle(); }

    @Override
    public Renderer getIcon() { return EntryStacks.of(new ItemStack(ModItems.SHADOW_SHARD.get())); }

    @Override
    public int getDisplayWidth(FuelDisplay display) { return 164; }

    @Override
    public int getDisplayHeight() { return 48; }

    @Override
    public List<Widget> setupDisplay(FuelDisplay display, Rectangle bounds) {
        return List.of(
                Widgets.createRecipeBase(bounds),
                Widgets.createSlot(new Point(bounds.x + 18, bounds.y + 15))
                        .entries(display.getInputEntries().getFirst()).markInput(),
                Widgets.createLabel(new Point(bounds.x + 99, bounds.y + 20),
                        InfuserViewerData.chargeText(display.charge())).centered().noShadow()
                        .color(0xFF404040, 0xFFBBBBBB));
    }
}
