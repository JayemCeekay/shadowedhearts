package com.jayemceekay.shadowedhearts.content.infusion;

import com.jayemceekay.shadowedhearts.registry.ModInfusionRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.Comparator;
import java.util.Objects;

/** Datapack fuels use native recipe reload/synchronization, including item tags. */
public record InfuserFuelRecipe(Ingredient ingredient, int charge, int priority) implements Recipe<SingleRecipeInput> {
    public InfuserFuelRecipe {
        Objects.requireNonNull(ingredient);
        if (charge < 0 || charge > InfusionProgress.MAX_CAPACITY) throw new IllegalArgumentException("Invalid Infuser fuel charge");
        if (priority < -10000 || priority > 10000) throw new IllegalArgumentException("Invalid Infuser fuel priority");
    }

    public static int value(RecipeManager manager, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return manager.getAllRecipesFor(ModInfusionRecipes.INFUSER_FUEL_TYPE.get()).stream()
                .filter(holder -> holder.value().ingredient().test(stack))
                .sorted(Comparator.<RecipeHolder<InfuserFuelRecipe>>comparingInt(holder -> holder.value().priority()).reversed()
                        .thenComparing(holder -> holder.id().toString()))
                .findFirst().map(holder -> holder.value().charge()).orElse(0);
    }

    @Override public boolean matches(SingleRecipeInput input, Level level) { return !input.item().isEmpty() && ingredient.test(input.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<InfuserFuelRecipe> getSerializer() { return ModInfusionRecipes.INFUSER_FUEL_SERIALIZER.get(); }
    @Override public RecipeType<InfuserFuelRecipe> getType() { return ModInfusionRecipes.INFUSER_FUEL_TYPE.get(); }

    public static final class Serializer implements RecipeSerializer<InfuserFuelRecipe> {
        public static final MapCodec<InfuserFuelRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(InfuserFuelRecipe::ingredient),
                Codec.intRange(0, InfusionProgress.MAX_CAPACITY).fieldOf("charge").forGetter(InfuserFuelRecipe::charge),
                Codec.intRange(-10000, 10000).optionalFieldOf("priority", 0).forGetter(InfuserFuelRecipe::priority)
        ).apply(instance, InfuserFuelRecipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, InfuserFuelRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, InfuserFuelRecipe::ingredient,
                ByteBufCodecs.VAR_INT, InfuserFuelRecipe::charge,
                ByteBufCodecs.VAR_INT, InfuserFuelRecipe::priority, InfuserFuelRecipe::new);
        @Override public MapCodec<InfuserFuelRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, InfuserFuelRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
