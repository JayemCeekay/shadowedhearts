package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Each event supplies its species roles and shares the regional weighting and fallback rules. */
final class CraterCastPool<T> {
    private record Entry<T>(T cast, List<TagKey<Biome>> biomes, int weight) {}

    private final FileToIdConverter files;
    private final Codec<Entry<T>> codec;
    private final Predicate<T> speciesAvailable;
    private final Map<ResourceManager, List<Entry<T>>> loaded = new WeakHashMap<>();

    CraterCastPool(String directory, MapCodec<T> roles, Predicate<T> speciesAvailable) {
        files = FileToIdConverter.json(directory);
        this.speciesAvailable = speciesAvailable;
        codec = RecordCodecBuilder.create(instance -> instance.group(
                roles.forGetter(Entry::cast),
                TagKey.hashedCodec(Registries.BIOME).listOf().optionalFieldOf("biomes", List.of()).forGetter(Entry::biomes),
                Codec.intRange(0, 10000).optionalFieldOf("weight", 1).forGetter(Entry::weight)
        ).apply(instance, Entry::new));
    }

    @Nullable T select(ServerLevel level, BlockPos rim) {
        var biome = level.getBiome(rim);
        var regional = SimpleWeightedRandomList.<T>builder();
        var fallback = SimpleWeightedRandomList.<T>builder();
        for (Entry<T> entry : definitions(level.getServer().getResourceManager())) {
            boolean anyBiome = entry.biomes().isEmpty();
            if (!anyBiome && entry.biomes().stream().noneMatch(biome::is)) continue;
            if (entry.weight() == 0 || !speciesAvailable.test(entry.cast())) continue;
            (anyBiome ? fallback : regional).add(entry.cast(), entry.weight());
        }
        return regional.build().getRandomValue(level.random)
                .orElseGet(() -> fallback.build().getRandomValue(level.random).orElse(null));
    }

    static boolean available(ResourceLocation id) {
        return !id.equals(VoidShadowService.SPECIES) && PokemonSpecies.INSTANCE.getByIdentifier(id) != null;
    }

    private synchronized List<Entry<T>> definitions(ResourceManager manager) {
        // A successful /reload replaces the committed resource manager.
        return loaded.computeIfAbsent(manager, this::read);
    }

    private List<Entry<T>> read(ResourceManager manager) {
        List<Entry<T>> result = new ArrayList<>();
        files.listMatchingResources(manager).forEach((file, resource) -> {
            try (var reader = resource.openAsReader()) {
                result.add(codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow());
            } catch (IOException | RuntimeException ex) {
                Shadowedhearts.LOGGER.warn("Skipping invalid crater event cast {}", file, ex);
            }
        });
        return List.copyOf(result);
    }
}
