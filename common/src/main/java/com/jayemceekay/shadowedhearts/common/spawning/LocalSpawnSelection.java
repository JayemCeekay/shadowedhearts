package com.jayemceekay.shadowedhearts.common.spawning;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.selection.FlatSpawnablePositionWeightedSelector;
import com.cobblemon.mod.common.api.spawning.spawner.Spawner;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowSpawnArea;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowSpawnWeights;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** One read of the loaded area and the natural spawn rules. Sampling never runs a spawn action. */
public final class LocalSpawnSelection {
    /** Native properties belong to the natural spawn pool. Consumers must not mutate them. */
    public record Selected(PokemonProperties properties, BlockPos position) {
        public ResourceLocation species() { return LocalSpawnSelection.species(properties); }
    }

    private final ServerLevel world;
    private final Map<String, Float> bucketsByName;
    private final FlatSpawnablePositionWeightedSelector.SeparatedSelectionData data;

    public LocalSpawnSelection(ServerLevel world, BlockPos origin, Entity causeEntity, Spawner spawner,
                               Predicate<AreaSpawnablePosition> acceptedPosition) {
        this.world = world;
        this.bucketsByName = spawner.getBuckets();
        var cause = new SpawnCause(spawner, causeEntity);
        List<SpawnablePosition> positions = new ArrayList<>();
        for (var position : VoidShadowSpawnArea.resolve(world, origin, spawner, cause)) {
            if (acceptedPosition.test(position)) positions.add(position);
        }
        data = new FlatSpawnablePositionWeightedSelector().getSelectionData(spawner, positions);
    }

    /** Retains native bucket, context, distinct-detail, percentage and herd weights when excluding a prior role. */
    public @Nullable Selected draw(@Nullable ResourceLocation excluded) {
        return drawMatching(id -> !id.equals(excluded));
    }

    public @Nullable Selected drawMatching(Predicate<ResourceLocation> speciesFilter) {
        var buckets = new ArrayList<VoidShadowSpawnWeights.Bucket<SpawnChoice>>();
        for (var bucket : bucketsByName.entrySet()) {
            if (!Float.isFinite(bucket.getValue()) || bucket.getValue() <= 0) continue;
            var contexts = new ArrayList<VoidShadowSpawnWeights.Context<SpawnChoice>>();
            for (var context : data.getDataForBucket(bucket.getKey()).entrySet()) {
                var candidates = new ArrayList<VoidShadowSpawnWeights.Candidate<SpawnChoice>>();
                for (var entry : context.getValue().getSpawnToSpawnablePosition().entrySet()) {
                    SpawnDetail detail = entry.getKey();
                    Map<SpawnablePosition, Float> matching = entry.getValue().getSpawnablePositionWeights();
                    if (matching.isEmpty()) continue;
                    SpawnChoice choice = choice(detail, matching, world, speciesFilter);
                    if (choice == null) continue;
                    double weight = matching.values().stream().mapToDouble(Float::doubleValue).max().orElse(0);
                    candidates.add(new VoidShadowSpawnWeights.Candidate<>(choice, weight, detail.getPercentage()));
                }
                contexts.add(new VoidShadowSpawnWeights.Context<>(context.getKey().getWeight(), candidates));
            }
            buckets.add(new VoidShadowSpawnWeights.Bucket<>(bucket.getValue(), contexts));
        }
        SpawnChoice selected = VoidShadowSpawnWeights.select(buckets, world.getRandom()::nextDouble);
        if (selected == null) return null;
        PokemonProperties source = selected.choose(world);
        if (source == null) return null;
        BlockPos spawnPosition = selected.choosePosition(source, world);
        return spawnPosition == null ? null : new Selected(source, spawnPosition);
    }

    private static SpawnChoice choice(SpawnDetail detail, Map<SpawnablePosition, Float> positions, ServerLevel world,
                                      Predicate<ResourceLocation> speciesFilter) {
        if (detail instanceof PokemonSpawnDetail pokemon) {
            return eligible(pokemon.getPokemon(), speciesFilter)
                    ? new SpawnChoice(List.of(new PropertyChoice(pokemon.getPokemon(), 1)), positions, false) : null;
        }
        if (!(detail instanceof PokemonHerdSpawnDetail herd)) return null;
        var range = herd.getLevelRange();
        if (range.isEmpty() || (long) range.getLast() - range.getFirst() + 1 > Integer.MAX_VALUE) return null;
        int level = Mth.nextInt(world.getRandom(), range.getFirst(), range.getLast());
        var members = herd.getHerdablePokemon().stream()
                .filter(member -> member.getMaxTimes() > 0 && Float.isFinite(member.getWeight()) && member.getWeight() > 0)
                .filter(member -> member.getHerdLevelRange() == null || member.getHerdLevelRange().contains(level))
                .filter(member -> eligible(member.getPokemon(), speciesFilter))
                .filter(member -> hasSpace(member.getPokemon(), positions)).toList();
        boolean leader = members.stream().anyMatch(PokemonHerdSpawnDetail.Herdable::isLeader);
        List<PropertyChoice> choices = members.stream()
                .filter(member -> leader ? member.isLeader() : member.isFollower())
                .map(member -> new PropertyChoice(member.getPokemon(), member.getWeight())).toList();
        return choices.isEmpty() ? null : new SpawnChoice(choices, positions, true);
    }

    private static boolean eligible(PokemonProperties properties, Predicate<ResourceLocation> speciesFilter) {
        var id = species(properties);
        return id != null && !VoidShadowService.SPECIES.equals(id)
                && !properties.getAspects().contains(VoidShadowService.ASPECT)
                && PokemonSpecies.INSTANCE.getByIdentifier(id) != null && speciesFilter.test(id);
    }

    public static @Nullable ResourceLocation species(PokemonProperties properties) {
        String name = properties.getSpecies();
        if (name == null || name.equalsIgnoreCase("random")) return null;
        name = name.toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse(name.contains(":") ? name : "cobblemon:" + name);
    }

    private static boolean hasSpace(PokemonProperties properties, Map<SpawnablePosition, Float> positions) {
        var species = PokemonSpecies.INSTANCE.getByIdentifier(species(properties));
        int width = (int) Math.ceil(species.getHitbox().width() * species.getBaseScale());
        int height = (int) Math.ceil(species.getHitbox().height() * species.getBaseScale());
        return positions.keySet().stream().anyMatch(position -> !(position instanceof AreaSpawnablePosition area)
                || area.hasSpace(width, height));
    }

    private record PropertyChoice(PokemonProperties properties, double weight) { }
    private record SpawnChoice(List<PropertyChoice> properties, Map<SpawnablePosition, Float> positions,
                               boolean checkMemberSpace) {
        PokemonProperties choose(ServerLevel world) {
            var candidates = properties.stream().map(property ->
                    new VoidShadowSpawnWeights.Candidate<>(property.properties, property.weight, 0)).toList();
            return VoidShadowSpawnWeights.select(List.of(new VoidShadowSpawnWeights.Bucket<>(1,
                    List.of(new VoidShadowSpawnWeights.Context<>(1, candidates)))), world.getRandom()::nextDouble);
        }

        BlockPos choosePosition(PokemonProperties properties, ServerLevel world) {
            var candidates = new ArrayList<VoidShadowSpawnWeights.Candidate<BlockPos>>();
            for (var position : positions.entrySet()) {
                if (checkMemberSpace && !hasSpace(properties, Map.of(position.getKey(), position.getValue()))) continue;
                candidates.add(new VoidShadowSpawnWeights.Candidate<>(position.getKey().getPosition(),
                        position.getValue(), 0));
            }
            BlockPos chosen = VoidShadowSpawnWeights.select(List.of(new VoidShadowSpawnWeights.Bucket<>(1,
                    List.of(new VoidShadowSpawnWeights.Context<>(1, candidates)))), world.getRandom()::nextDouble);
            // Percentage-only entries can have no positive position weights.
            return chosen != null ? chosen : candidates.isEmpty() ? null
                    : candidates.get(world.getRandom().nextInt(candidates.size())).value();
        }
    }
}
