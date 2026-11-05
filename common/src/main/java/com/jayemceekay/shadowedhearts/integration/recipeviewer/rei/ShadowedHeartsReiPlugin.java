package com.jayemceekay.shadowedhearts.integration.recipeviewer.rei;

import com.jayemceekay.shadowedhearts.client.gui.RecipeViewerScreen;
import com.jayemceekay.shadowedhearts.client.purification.ChamberRecipeViewerSupport;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.integration.recipeviewer.InfuserViewerData;
import com.jayemceekay.shadowedhearts.registry.ModInfusionRecipes;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import dev.architectury.event.CompoundEventResult;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.screen.DisplayScreen;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.DisplayBoundsProvider;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.FocusedStackProvider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Loaded only by REI's optional client plugin discovery. */
public class ShadowedHeartsReiPlugin implements REIClientPlugin {
    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new InfusionCategory(), config -> {
            config.setPlusButtonArea(bounds -> null);
            config.setQuickCraftingEnabledByDefault(false);
        });
        registry.add(new FuelCategory(), config -> {
            config.setPlusButtonArea(bounds -> null);
            config.setQuickCraftingEnabledByDefault(false);
        });
        EntryStack<ItemStack> infuser = EntryStacks.of(new ItemStack(ModItems.UMBRA_INFUSER.get()));
        registry.addWorkstations(InfusionDisplay.CATEGORY, infuser);
        registry.addWorkstations(FuelDisplay.CATEGORY, infuser);
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var fuels = InfuserViewerData.fuels(level == null ? null : level.getRecipeManager());
        // REI re-fills these holders from the connected server's synchronized recipe manager.
        registry.registerRecipeFiller(UmbraInfuserRecipe.class,
                ModInfusionRecipes.UMBRA_INFUSER_TYPE.get(), InfusionDisplay::new);
        for (InfuserViewerData.Fuel fuel : fuels) {
            registry.add(new FuelDisplay(fuel));
        }
        registry.add(DefaultInformationDisplay.createFromEntry(
                        EntryStacks.of(new ItemStack(ModItems.UMBRA_INFUSER.get())), InfuserViewerData.title())
                .lines(InfuserViewerData.conversionInfo()));
        List<ItemStack> fuelInfoItems = new ArrayList<>();
        fuelInfoItems.add(new ItemStack(ModItems.UMBRA_INFUSER.get()));
        fuelInfoItems.add(new ItemStack(ModItems.UMBRA_INFUSER_TANK.get()));
        fuels.forEach(fuel -> fuelInfoItems.add(fuel.stack().copy()));
        registry.add(DefaultInformationDisplay.createFromEntries(
                        EntryIngredients.ofItemStacks(fuelInfoItems), InfuserViewerData.fuelTitle())
                .lines(InfuserViewerData.fuelInfo()));
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        ChamberRecipeViewerSupport.register("rei", (screen, origin) ->
                returnsTo(screen, origin, () -> REIRuntime.getInstance().getPreviousScreen()));
        registry.registerDecider(new DisplayBoundsProvider<RecipeViewerScreen>() {
            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return RecipeViewerScreen.class.isAssignableFrom(screen);
            }

            @Override
            public Rectangle getScreenBounds(RecipeViewerScreen screen) {
                return rectangle(screen.recipeViewerBounds());
            }

            @Override
            public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
                return InteractionResult.SUCCESS;
            }

            @Override
            public double getPriority() { return 100; }
        });
        registry.registerFocusedStack(new FocusedStackProvider() {
            @Override
            public CompoundEventResult<EntryStack<?>> provide(Screen screen, Point mouse) {
                if (!(screen instanceof RecipeViewerScreen viewerScreen)) return CompoundEventResult.pass();
                RecipeViewerScreen.HoveredStack hovered = viewerScreen.recipeViewerStackAt(mouse.x, mouse.y);
                // Do not fall back to a container's unscaled or previously hovered slot.
                return hovered == null || hovered.stack().isEmpty()
                        ? CompoundEventResult.interruptFalse(null)
                        : CompoundEventResult.interruptTrue(EntryStacks.of(hovered.stack().copy()));
            }

            @Override
            public double getPriority() { return 100; }
        });
    }

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(RecipeViewerScreen.class, screen -> List.of(rectangle(screen.recipeViewerBounds())));
    }

    private static Rectangle rectangle(ScreenRectangle bounds) {
        return new Rectangle(bounds.left(), bounds.top(), bounds.width(), bounds.height());
    }

    /** Recipe history retains REI's original return screen; unrelated screens must not inherit it. */
    static boolean returnsTo(Screen screen, Screen origin, Supplier<Screen> previousScreen) {
        return origin != null && screen instanceof DisplayScreen && previousScreen.get() == origin;
    }
}
