package com.jayemceekay.shadowedhearts.common.voidshadow;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnRules;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.selection.FlatSpawnablePositionWeightedSelector;
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.feature.SeasonFeatureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Queries local natural spawn rules, without selecting a spawn action or adding an entity. */
public final class VoidShadowSpawnTemplates {
    private VoidShadowSpawnTemplates() { }

    public static Pokemon select(PokemonEntity entity, Entity causeEntity) {
        if (!(entity.level() instanceof ServerLevel world)) return null;
        return select(world, entity.blockPosition(), entity.getPokemon().getLevel(), causeEntity,
                PlayerSpawnerFactory.INSTANCE.getSpawnPool());
    }

    static Pokemon select(ServerLevel world, BlockPos origin, int encounterLevel, Entity causeEntity, SpawnPool pool) {
        var spawner = new BasicSpawner("shadowedhearts:void_shadow_template", pool, 0,
                new LinkedHashMap<>(BestSpawner.INSTANCE.getConfig().getWorldBuckets())) {
            @Override public List<SpawnDetail> getMatchingSpawns(String bucket, SpawnablePosition position) {
                var entries = new java.util.LinkedHashSet<>(getSpawnPool().retrieve(bucket, position));
                for (var influence : position.getInfluences()) {
                    var injected = influence.injectSpawns(bucket, position);
                    if (injected != null) entries.addAll(injected);
                }
                // Apply conditions once, including to rule-injected entries. No spawn actions.
                return entries.stream().filter(detail -> detail instanceof PokemonSpawnDetail
                        || detail instanceof PokemonHerdSpawnDetail).filter(detail -> detail.isSatisfiedBy(position)).toList();
            }
        };
        for (var rule : CobblemonSpawnRules.INSTANCE.getRules().values()) {
            if (rule.getEnabled()) spawner.getInfluences().addAll(rule.getComponents());
        }
        var cause = new SpawnCause(spawner, causeEntity);
        List<SpawnablePosition> positions = new ArrayList<>(VoidShadowSpawnArea.resolve(world, origin, spawner, cause));
        if (positions.isEmpty()) return null;

        // Native selection data checks biome tags, conditions/anticonditions, terrain,
        // dimensions, weather/time and per-position weight multipliers. Its probability
        // summary is not equivalent to selection, so sample its distinct context data.
        var data = new FlatSpawnablePositionWeightedSelector().getSelectionData(spawner, positions);
        var buckets = new ArrayList<VoidShadowSpawnWeights.Bucket<SpawnChoice>>();
        for (var bucket : spawner.getBuckets().entrySet()) {
            if (!Float.isFinite(bucket.getValue()) || bucket.getValue() <= 0) continue;
            var contexts = new ArrayList<VoidShadowSpawnWeights.Context<SpawnChoice>>();
            for (var context : data.getDataForBucket(bucket.getKey()).entrySet()) {
                var candidates = new ArrayList<VoidShadowSpawnWeights.Candidate<SpawnChoice>>();
                for (var entry : context.getValue().getSpawnToSpawnablePosition().entrySet()) {
                    SpawnDetail detail = entry.getKey();
                    Map<SpawnablePosition, Float> matching = entry.getValue().getSpawnablePositionWeights();
                    if (matching.isEmpty()) continue;
                    SpawnChoice choice = choice(detail, matching, world);
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
        if (spawnPosition == null) return null;
        // The live/datapack property object is shared by the natural spawner. Never change it.
        PokemonProperties properties = source.copy();
        // Native JSON copy does not carry explicitly forced aspects.
        properties.setAspects(Set.copyOf(source.getAspects()));
        properties.setLevel(encounterLevel);
        properties.setHeldItem(null);
        properties.setCosmeticItem(null);
        Pokemon template = properties.create(causeEntity instanceof ServerPlayer player ? player : null);
        SeasonFeatureHandler.INSTANCE.updateSeason(template, world, spawnPosition);
        return VoidShadowService.isVoidShadow(template) || template.getCurrentHealth() <= 0 ? null : template;
    }

    private static SpawnChoice choice(SpawnDetail detail, Map<SpawnablePosition, Float> positions, ServerLevel world) {
        if (detail instanceof PokemonSpawnDetail pokemon) {
            return eligible(pokemon.getPokemon())
                    ? new SpawnChoice(List.of(new PropertyChoice(pokemon.getPokemon(), 1)), positions, false) : null;
        }
        if (!(detail instanceof PokemonHerdSpawnDetail herd)) return null;
        var range = herd.getLevelRange();
        if (range.isEmpty() || (long) range.getLast() - range.getFirst() + 1 > Integer.MAX_VALUE) return null;
        int level = Mth.nextInt(world.getRandom(), range.getFirst(), range.getLast());
        var members = herd.getHerdablePokemon().stream()
                .filter(member -> member.getMaxTimes() > 0 && Float.isFinite(member.getWeight()) && member.getWeight() > 0)
                .filter(member -> member.getHerdLevelRange() == null || member.getHerdLevelRange().contains(level))
                .filter(member -> eligible(member.getPokemon()))
                .filter(member -> hasSpace(member.getPokemon(), positions)).toList();
        boolean leader = members.stream().anyMatch(PokemonHerdSpawnDetail.Herdable::isLeader);
        List<PropertyChoice> choices = members.stream()
                .filter(member -> leader ? member.isLeader() : member.isFollower())
                .map(member -> new PropertyChoice(member.getPokemon(), member.getWeight())).toList();
        return choices.isEmpty() ? null : new SpawnChoice(choices, positions, true);
    }

    private static boolean eligible(PokemonProperties properties) {
        var id = species(properties);
        return id != null && !VoidShadowService.SPECIES.equals(id)
                && !properties.getAspects().contains(VoidShadowService.ASPECT)
                && PokemonSpecies.INSTANCE.getByIdentifier(id) != null;
    }

    private static ResourceLocation species(PokemonProperties properties) {
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
