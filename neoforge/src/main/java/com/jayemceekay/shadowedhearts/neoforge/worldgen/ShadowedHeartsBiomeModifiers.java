package com.jayemceekay.shadowedhearts.neoforge.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

public class ShadowedHeartsBiomeModifiers implements BiomeModifier {

    private static final ShadowedHeartsBiomeModifiers INSTANCE = new ShadowedHeartsBiomeModifiers();
    private MapCodec<? extends BiomeModifier> codec;
    private final List<Entry> entries = new ArrayList<>();

    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, helper -> {
            INSTANCE.codec = MapCodec.unit(INSTANCE);
            helper.register(ResourceLocation.fromNamespaceAndPath("shadowedhearts", "inject_coded"),
                    INSTANCE.codec
            );
        });

        event.register(NeoForgeRegistries.Keys.BIOME_MODIFIERS, helper -> {
            helper.register(ResourceLocation.fromNamespaceAndPath("shadowedhearts", "inject_coded"), INSTANCE);
        });
    }

    public static void add(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step, TagKey<Biome> validTag) {
        INSTANCE.entries.add(new Entry(feature, step, validTag));
    }

    @Override
    public void modify(Holder<Biome> arg, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        var registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (Entry entry : entries) {
            if (entry.validTag == null || arg.is(entry.validTag)) {
                var featureHolder = registry.getHolder(entry.feature);
                featureHolder.ifPresent(placedFeatureHolder -> builder.getGenerationSettings().addFeature(entry.step, placedFeatureHolder));
            }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return codec != null ? codec : MapCodec.unit(INSTANCE);
    }

    private record Entry(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step, TagKey<Biome> validTag) {}
}
