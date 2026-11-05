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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** A single item infused using the tank's stored charge. Loaded and synced by the recipe manager. */
public final class UmbraInfuserRecipe implements Recipe<SingleRecipeInput> {
    public static final int MAX_CHARGE = InfusionProgress.MAX_CAPACITY;
    public static final int MAX_PROCESSING_TICKS = 72_000;

    private final Ingredient ingredient;
    private final ItemStack result;
    private final int charge;
    private final int processingTicks;

    public UmbraInfuserRecipe(Ingredient ingredient, ItemStack result, int charge, int processingTicks) {
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(result, "result");
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) {
            throw new IllegalArgumentException("An infusion result must be a nonempty, valid item stack");
        }
        if (charge < 1 || charge > MAX_CHARGE) {
            throw new IllegalArgumentException("Infusion charge must be between 1 and " + MAX_CHARGE);
        }
        if (processingTicks < 1 || processingTicks > MAX_PROCESSING_TICKS) {
            throw new IllegalArgumentException("Infusion processing_ticks must be between 1 and " + MAX_PROCESSING_TICKS);
        }
        this.result = result.copy();
        this.charge = charge;
        this.processingTicks = processingTicks;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public ItemStack output() {
        return result.copy();
    }

    public int charge() {
        return charge;
    }

    public int processingTicks() {
        return processingTicks;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.item().isEmpty() && ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return output();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 1 && height >= 1;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return output();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public boolean isSpecial() {
        // Infusions are operated through their machine, outside the vanilla crafting recipe book.
        return true;
    }

    @Override
    public RecipeSerializer<UmbraInfuserRecipe> getSerializer() {
        return ModInfusionRecipes.UMBRA_INFUSER_SERIALIZER.get();
    }

    @Override
    public RecipeType<UmbraInfuserRecipe> getType() {
        return ModInfusionRecipes.UMBRA_INFUSER_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<UmbraInfuserRecipe> {
        public static final MapCodec<UmbraInfuserRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(UmbraInfuserRecipe::ingredient),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                        Codec.intRange(1, MAX_CHARGE).fieldOf("charge").forGetter(UmbraInfuserRecipe::charge),
                        Codec.intRange(1, MAX_PROCESSING_TICKS).fieldOf("processing_ticks")
                                .forGetter(UmbraInfuserRecipe::processingTicks)
                ).apply(instance, UmbraInfuserRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, UmbraInfuserRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, UmbraInfuserRecipe::ingredient,
                        ItemStack.STREAM_CODEC, recipe -> recipe.result,
                        ByteBufCodecs.VAR_INT, UmbraInfuserRecipe::charge,
                        ByteBufCodecs.VAR_INT, UmbraInfuserRecipe::processingTicks,
                        UmbraInfuserRecipe::new);

        @Override
        public MapCodec<UmbraInfuserRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, UmbraInfuserRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
