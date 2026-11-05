package com.jayemceekay.shadowedhearts.common.voidshadow;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.network.voidshadow.VoidShadowTransformPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import kotlin.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A hostile, uncatchable Cobblemon encounter with a persistent identity independent of its disguise.
 * A local spawn-pool template supplies species, form, combat stats and moves.
 */
public final class VoidShadowService {
    public static final ResourceLocation SPECIES = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "void_shadow");
    public static final String KEY = "shadowedhearts:void_shadow";
    public static final String ASPECT = "void-shadow";
    public static final String BASELINE_KEY = "shadowedhearts:void_shadow_baseline";
    private static final Map<UUID, List<Disguise>> ACTIVE = new HashMap<>();
    private static boolean initialized;

    private VoidShadowService() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getEntity();
            if (!entity.level().isClientSide() && isVoidShadow(entity.getPokemon())) mark(entity.getPokemon());
            return Unit.INSTANCE;
        });
        CobblemonEvents.LOOT_DROPPED.subscribe(Priority.LOWEST, event -> {
            if (event.getEntity() instanceof PokemonEntity entity && isVoidShadow(entity.getPokemon())) event.cancel();
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getPokemonEntity();
            if (!entity.level().isClientSide() && isVoidShadow(entity.getPokemon())) {
                // Battles are not persisted by Cobblemon. A copied species saved mid-battle must not
                // become a permanent Pokemon after a restart or an unloaded encounter.
                if (entity.getBattle() == null) repairLoadedEntity(entity);
                else mark(entity.getPokemon());
            }
            return Unit.INSTANCE;
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    public static boolean isVoidShadow(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(KEY) || SPECIES.equals(pokemon.getSpecies().getResourceIdentifier());
    }

    /** Native command/createEntity paths bypass Cobblemon's natural-spawn event. */
    public static void initializeEntity(PokemonEntity entity) {
        if (!entity.level().isClientSide() && isVoidShadow(entity.getPokemon())) mark(entity.getPokemon());
    }

    public static PokemonEntity spawn(ServerLevel world, Vec3 position, int requestedLevel) {
        Pokemon pokemon = create(requestedLevel);
        PokemonEntity entity = pokemon.sendOut(world, position, null, ignored -> Unit.INSTANCE);
        if (entity == null) throw com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.state(Component.translatable("message.shadowedhearts.command.void_shadow.spawn_unavailable"));
        return entity;
    }

    /** Creates the canonical identity before an event adds its persistent issuance metadata. */
    public static Pokemon create(int requestedLevel) {
        if (PokemonSpecies.INSTANCE.getByIdentifier(SPECIES) == null) {
            throw com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.state(Component.translatable("message.shadowedhearts.command.void_shadow.species_unavailable"));
        }
        int level = Math.max(1, Math.min(Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel(), requestedLevel));
        Pokemon pokemon = PokemonProperties.Companion.parse(SPECIES + " level=" + level + " shiny=false").create();
        pokemon.setNickname(Component.translatable("shadowedhearts.species.voidshadow.name"));
        mark(pokemon);
        return pokemon;
    }

    /** Called only after native start validation and all cancellation listeners have accepted. */
    public static void prepareBattle(PokemonBattle battle) {
        if (ACTIVE.containsKey(battle.getBattleId())) return;
        List<Disguise> disguises = new ArrayList<>();
        ACTIVE.put(battle.getBattleId(), disguises);
        try {
            for (BattleActor actor : battle.getActors()) {
                if (actor.getType() != ActorType.WILD) continue;
                for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                    Pokemon target = battlePokemon.getEffectedPokemon();
                    PokemonEntity entity = battlePokemon.getEntity();
                    if (!isVoidShadow(target) || entity == null || !(entity.level() instanceof ServerLevel world)) continue;
                    if (target.getPersistentData().contains(BASELINE_KEY, Tag.TAG_COMPOUND)) repairLoadedEntity(entity);
                    mark(target);
                    var cause = battle.getPlayers().stream().filter(player -> player.level() == world)
                            .min(java.util.Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
                    Pokemon template = VoidShadowSpawnTemplates.select(entity, cause == null ? entity : cause);
                    // Snapshot before any mutation; the independently generated template
                    // supplies appearance and battle data without spawning another entity.
                    CompoundTag baseline = target.saveToNBT(world.registryAccess(), new CompoundTag()).copy();
                    Disguise disguise = new Disguise(target, entity, world, baseline);
                    disguises.add(disguise);
                    if (template != null) {
                        Pokemon copy = template.clone(true, world.registryAccess());
                        target.getPersistentData().put(BASELINE_KEY, baseline.copy());
                        VoidShadowTransformationSound.afterChange(() -> appearance(target),
                                () -> applyTemplate(target, copy), () -> playTransformation(entity, world));
                    }
                }
            }
            if (disguises.isEmpty()) ACTIVE.remove(battle.getBattleId());
            else battle.getOnEndHandlers().add(ended -> {
                finishBattle(ended);
                return Unit.INSTANCE;
            });
        } catch (RuntimeException | Error failure) {
            abortBattle(battle);
            throw failure;
        }
    }

    private static void applyTemplate(Pokemon target, Pokemon copy) {
        double fraction = healthFraction(target);
        target.setSpecies(copy.getSpecies());
        target.setFeatures(new ArrayList<>(copy.getFeatures()));
        HashSet<String> aspects = new HashSet<>(copy.getAspects());
        // Heart Gauge/aura metadata and item-derived effects belong to the original Pokemon.
        aspects.removeIf(aspect -> aspect.startsWith("shadowedhearts:")
                || aspect.startsWith("cosmetic_item-") || aspect.startsWith("held_item-"));
        aspects.add(ASPECT);
        target.setForcedAspects(aspects);
        target.setForm(copy.getForm());
        target.setGender(copy.getGender());
        target.setScaleModifier(copy.getScaleModifier());
        target.setNature(copy.getEffectiveNature());
        target.setMintedNature(null);
        target.updateAbility(copy.getAbility());
        target.getIvs().getHyperTrainedIVs().clear();
        for (var stat : copy.getIvs()) target.setIV(stat.getKey(), stat.getValue());
        for (var stat : copy.getIvs().getHyperTrainedIVs().entrySet()) target.hyperTrainIV(stat.getKey(), stat.getValue());
        for (var stat : target.getEvs()) target.setEV(stat.getKey(), 0);
        for (var stat : copy.getEvs()) target.setEV(stat.getKey(), stat.getValue());
        target.getMoveSet().copyFrom(copy.getMoveSet());
        target.getMoveSet().heal();
        target.setTeraType(copy.getTeraType());
        target.setStatus(null);
        mark(target);
        target.setCurrentHealth(VoidShadowTemplateSelection.healthAtFraction(target.getMaxHealth(), fraction));
        if (target.getEntity() != null) target.getEntity().refreshDimensions();
    }

    private static void mark(Pokemon pokemon) {
        pokemon.getPersistentData().putBoolean(KEY, true);
        HashSet<String> forced = new HashSet<>(pokemon.getForcedAspects());
        forced.add(ASPECT);
        pokemon.setForcedAspects(forced);
        if (!pokemon.isUncatchable()) UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
        pokemon.setTradeable(false);
        pokemon.updateAspects();
    }

    /** Restore canonical reward species before XP/EV/defeat credit, retaining combat health. */
    public static void captureBattleHealth(PokemonBattle battle) {
        for (Disguise disguise : ACTIVE.getOrDefault(battle.getBattleId(), List.of())) {
            disguise.endHealthFraction = healthFraction(disguise.pokemon);
            restoreLive(disguise, disguise.endHealthFraction);
        }
    }

    public static void finishBattle(PokemonBattle battle) {
        List<Disguise> disguises = ACTIVE.remove(battle.getBattleId());
        if (disguises == null) return;
        for (Disguise disguise : disguises) {
            double fraction = disguise.endHealthFraction == null ? healthFraction(disguise.pokemon) : disguise.endHealthFraction;
            restoreLive(disguise, fraction);
        }
    }

    /** Launch failures restore the exact pre-start health and canonical appearance. */
    public static void abortBattle(PokemonBattle battle) {
        List<Disguise> disguises = ACTIVE.remove(battle.getBattleId());
        if (disguises == null) return;
        for (Disguise disguise : disguises) restore(disguise.pokemon, disguise.entity, disguise.world, disguise.baseline, null);
    }

    public static void repairLoadedEntity(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        if (!isVoidShadow(pokemon) || !(entity.level() instanceof ServerLevel world)) return;
        CompoundTag data = pokemon.getPersistentData();
        if (data.contains(BASELINE_KEY, Tag.TAG_COMPOUND)) {
            restore(pokemon, entity, world, data.getCompound(BASELINE_KEY).copy(), healthFraction(pokemon));
        } else if (!SPECIES.equals(pokemon.getSpecies().getResourceIdentifier())) {
            // Recovery for an identity marker whose disguise snapshot was removed by an external editor.
            double fraction = healthFraction(pokemon);
            var species = PokemonSpecies.INSTANCE.getByIdentifier(SPECIES);
            if (species == null) throw com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.state(Component.translatable("message.shadowedhearts.command.void_shadow.species_unavailable"));
            pokemon.setForcedAspects(new HashSet<>());
            pokemon.setSpecies(species);
            pokemon.setForm(species.getStandardForm());
            pokemon.initializeMoveset(true);
            pokemon.setCurrentHealth(VoidShadowTemplateSelection.healthAtFraction(pokemon.getMaxHealth(), fraction));
            mark(pokemon);
        } else mark(pokemon);
    }

    private static void restoreLive(Disguise disguise, Double fraction) {
        // Capture/finish can both restore one battle. A second unchanged restore stays silent.
        // Load recovery and failed battle rollback use restore directly and never replay a live cue.
        VoidShadowTransformationSound.afterChange(() -> appearance(disguise.pokemon),
                () -> restore(disguise.pokemon, disguise.entity, disguise.world, disguise.baseline, fraction),
                () -> playTransformation(disguise.entity, disguise.world));
    }

    private static VoidShadowTransformationSound.Appearance appearance(Pokemon pokemon) {
        return new VoidShadowTransformationSound.Appearance(pokemon.getSpecies().getResourceIdentifier(),
                pokemon.getForm().getName(), pokemon.getAspects());
    }

    private static void playTransformation(PokemonEntity entity, ServerLevel world) {
        if (entity.isRemoved() || entity.level() != world) return;
        // Cobblemon copies these authoritative values into tracked entity data on its next tick.
        // The client waits for that exact appearance before starting sound and visuals together.
        Pokemon pokemon = entity.getPokemon();
        var packet = new VoidShadowTransformPacket(UUID.randomUUID(), world.dimension().location(),
                entity.getId(), entity.getUUID(), pokemon.getSpecies().getResourceIdentifier(),
                Set.copyOf(pokemon.getAspects()));
        // Every existing tracker keeps the transformation visuals; audio has its own client range gate.
        world.getChunkSource().broadcast(entity, new ClientboundCustomPayloadPacket(packet));
    }

    private static void restore(Pokemon pokemon, PokemonEntity entity, ServerLevel world,
                                CompoundTag baseline, Double fraction) {
        // Keep the live entity/state association; a serialized SentOutState is not a new send-out.
        var liveState = pokemon.getState();
        // Native copyFrom merges hypertraining rather than replacing its map.
        pokemon.getIvs().getHyperTrainedIVs().clear();
        pokemon.loadFromNBT(world.registryAccess(), baseline.copy());
        pokemon.setState(liveState);
        pokemon.getPersistentData().remove(BASELINE_KEY);
        mark(pokemon);
        if (fraction != null) pokemon.setCurrentHealth(VoidShadowTemplateSelection.healthAtFraction(pokemon.getMaxHealth(), fraction));
        entity.refreshDimensions();
    }

    private static double healthFraction(Pokemon pokemon) {
        return pokemon.getMaxHealth() <= 0 ? 0 : (double) pokemon.getCurrentHealth() / pokemon.getMaxHealth();
    }

    private static final class Disguise {
        final Pokemon pokemon;
        final PokemonEntity entity;
        final ServerLevel world;
        final CompoundTag baseline;
        Double endHealthFraction;

        Disguise(Pokemon pokemon, PokemonEntity entity, ServerLevel world, CompoundTag baseline) {
            this.pokemon = pokemon;
            this.entity = entity;
            this.world = world;
            this.baseline = baseline;
        }
    }
}
