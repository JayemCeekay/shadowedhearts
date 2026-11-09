package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature;
import com.cobblemon.mod.common.api.spawning.position.GroundedSpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.feature.SeasonFeatureHandler;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.spawning.LocalSpawnSelection;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.jayemceekay.shadowedhearts.config.CraterCastSource;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** A new event selects roles once. Each actor gets its own Pokémon, stats, level and saved identity. */
final class CraterFieldCast {
    private record Role(PokemonProperties appearance, @Nullable BlockPos localPosition) {
        static Role authored(ResourceLocation species) {
            return new Role(PokemonProperties.Companion.parse(species.toString()), null);
        }

        static Role local(LocalSpawnSelection.Selected entry) {
            var source = entry.properties();
            var appearance = new PokemonProperties();
            appearance.setSpecies(source.getSpecies());
            appearance.setForm(source.getForm());
            appearance.setGender(source.getGender());
            appearance.setAspects(Set.copyOf(source.getAspects()));
            // Regional forms, patterns and other species features use native custom properties.
            // Loot, trainer, status, level, Alpha and shiny overrides belong to the natural spawn.
            source.copy().getCustomProperties().stream().filter(SpeciesFeature.class::isInstance)
                    .forEach(appearance.getCustomProperties()::add);
            return new Role(appearance, entry.position());
        }

        Pokemon create(int encounterLevel, ServerLevel world, ServerPlayer player) {
            var properties = appearance.copy();
            properties.setLevel(encounterLevel);
            Pokemon pokemon = properties.create(localPosition == null ? null : player);
            if (localPosition != null) {
                SeasonFeatureHandler.INSTANCE.updateSeason(pokemon, world, localPosition);
                // Some datapacks specify a form or its aspects directly, without a species feature.
                // Fill only the missing aspects defining that form after native features have applied.
                var species = pokemon.getSpecies();
                var form = species.getForms().stream()
                        .filter(candidate -> candidate.formOnlyShowdownId().equalsIgnoreCase(appearance.getForm()))
                        .findFirst().orElseGet(() -> species.getForm(appearance.getAspects()));
                if (!pokemon.getAspects().containsAll(form.getAspects())) {
                    var forced = new HashSet<>(pokemon.getForcedAspects());
                    for (String aspect : form.getAspects()) {
                        if (!pokemon.getAspects().contains(aspect) && !EVENT_OWNED_ASPECTS.contains(aspect)) forced.add(aspect);
                    }
                    pokemon.setForcedAspects(forced);
                }
            }
            return pokemon;
        }
    }

    private final Role pack;
    private final Role special;
    private static final Set<String> EVENT_OWNED_ASPECTS = Set.of("shiny", "alpha", SHAspects.SHADOW,
            SHAspects.HYPER_MODE, SHAspects.REVERSE_MODE, VoidShadowService.ASPECT);

    private CraterFieldCast(Role pack, Role special) {
        this.pack = pack;
        this.special = special;
    }

    static @Nullable CraterFieldCast select(ServerLevel level, BlockPos origin, ServerPlayer player, CraterEventKind kind) {
        var source = ServerConfig.INSTANCE.craterCastSource();
        if (source != CraterCastSource.DATAPACK) {
            // A private, unregistered spawner supplies the active world pool and this trainer's influences.
            var spawner = PlayerSpawnerFactory.INSTANCE.create(player);
            var local = new LocalSpawnSelection(level, origin, player, spawner, GroundedSpawnablePosition.class::isInstance);
            var pack = local.draw(null);
            if (pack != null) {
                var packRole = Role.local(pack);
                if (kind == CraterEventKind.HOWL) {
                    var first = pack.species();
                    var relative = local.drawMatching(id -> evolvesInto(first, id) || evolvesInto(id, first));
                    if (relative == null) return new CraterFieldCast(packRole, packRole);
                    var relativeRole = Role.local(relative);
                    return evolvesInto(first, relative.species()) ? new CraterFieldCast(packRole, relativeRole)
                            : new CraterFieldCast(relativeRole, packRole);
                }
                var victim = kind == CraterEventKind.RESCUE ? local.draw(pack.species()) : null;
                return new CraterFieldCast(packRole, victim == null ? packRole : Role.local(victim));
            }
            if (source == CraterCastSource.LOCAL_SPAWNS) return null;
        }
        if (kind == CraterEventKind.RESCUE) {
            var cast = CraterRescueCast.select(level, origin);
            return cast == null ? null : new CraterFieldCast(Role.authored(cast.attacker()), Role.authored(cast.victim()));
        }
        if (kind == CraterEventKind.HOWL) {
            var cast = HowlCast.select(level, origin);
            return cast == null ? null : new CraterFieldCast(Role.authored(cast.caller()), Role.authored(cast.responder()));
        }
        var cast = OutbreakCast.select(level, origin);
        return cast == null ? null : new CraterFieldCast(Role.authored(cast.pack()), Role.authored(cast.carrier()));
    }

    private static boolean evolvesInto(ResourceLocation from, ResourceLocation to) {
        var species = PokemonSpecies.INSTANCE.getByIdentifier(from);
        return species != null && species.getEvolutions().stream()
                .anyMatch(evolution -> to.equals(LocalSpawnSelection.species(evolution.getResult())));
    }

    Pokemon create(boolean specialRole, int encounterLevel, ServerLevel level, ServerPlayer player) {
        return (specialRole ? special : pack).create(encounterLevel, level, player);
    }
}
