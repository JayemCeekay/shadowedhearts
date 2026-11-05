package com.jayemceekay.shadowedhearts.integration.recipeviewer;

import com.jayemceekay.shadowedhearts.content.infusion.InfuserFuelRecipe;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.registry.ModInfusionRecipes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Viewer-neutral presentation. Recipes always come from the connected server's recipe manager. */
public final class InfuserViewerData {
    public static final ResourceLocation INFUSING_ID = id("umbra_infusing");
    public static final ResourceLocation FUEL_ID = id("infuser_fuel");

    private InfuserViewerData() { }

    public static List<RecipeHolder<UmbraInfuserRecipe>> recipes(RecipeManager manager) {
        return manager.getAllRecipesFor(ModInfusionRecipes.UMBRA_INFUSER_TYPE.get()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
    }

    public static List<Fuel> fuels(RecipeManager manager) {
        if (manager == null) return List.of();
        var stacks = new java.util.TreeMap<String, ItemStack>();
        for (var holder : manager.getAllRecipesFor(ModInfusionRecipes.INFUSER_FUEL_TYPE.get())) {
            for (var stack : holder.value().ingredient().getItems()) {
                stacks.put(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.copy());
            }
        }
        return stacks.values().stream().map(stack -> new Fuel(stack, InfuserFuelRecipe.value(manager, stack)))
                .filter(fuel -> fuel.charge() > 0).toList();
    }

    public static Component title() { return text("infusing"); }
    public static Component fuelTitle() { return text("fuel"); }
    public static Component chargeText(int charge) { return text("charge", charge); }
    public static Component timeText(int ticks) { return text("time", seconds(ticks)); }

    /** Exact nominal time at 20 TPS, including datapack durations shorter than a second. */
    public static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    public static List<Component> fuelInfo() {
        return List.of(text("fuel_storage"), text("configured_capacity"));
    }

    public static List<Component> conversionInfo() {
        return List.of(text("conversion"), text("conversion_eligibility"),
                text("configured_conversion_cost"), text("conversion_time"), text("configured_capacity"));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("shadowedhearts.recipe_viewer." + key, args);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("shadowedhearts", path);
    }

    public record Fuel(ItemStack stack, int charge) { }
}
