package com.jayemceekay.shadowedhearts.common.voidshadow;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnRules;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.feature.SeasonFeatureHandler;
import com.jayemceekay.shadowedhearts.common.spawning.LocalSpawnSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.List;
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
        var selected = new LocalSpawnSelection(world, origin, causeEntity, spawner, position -> true).draw(null);
        if (selected == null) return null;
        PokemonProperties source = selected.properties();
        // The live/datapack property object is shared by the natural spawner. Never change it.
        PokemonProperties properties = source.copy();
        // Native JSON copy does not carry explicitly forced aspects.
        properties.setAspects(Set.copyOf(source.getAspects()));
        properties.setLevel(encounterLevel);
        properties.setHeldItem(null);
        properties.setCosmeticItem(null);
        Pokemon template = properties.create(causeEntity instanceof ServerPlayer player ? player : null);
        SeasonFeatureHandler.INSTANCE.updateSeason(template, world, selected.position());
        return VoidShadowService.isVoidShadow(template) || template.getCurrentHealth() <= 0 ? null : template;
    }
}
