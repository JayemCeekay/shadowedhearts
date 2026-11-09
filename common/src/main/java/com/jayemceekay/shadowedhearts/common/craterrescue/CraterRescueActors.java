package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

/** A site's saved Pokémon and its issued wild entity share a UUID for the duration of the encounter. */
public final class CraterRescueActors {
    private static final String KEY = "shadowedhearts:crater_rescue";
    private static final String LEGACY_CARRIER_NAME = "message.shadowedhearts.crater_outbreak.carrier";

    private CraterRescueActors() {}

    public static Pokemon create(UUID eventId, UUID pokemonId, ResourceLocation species, int level, boolean victim) {
        Pokemon pokemon = PokemonProperties.Companion.parse(species + " level=" + level).create();
        return create(eventId, pokemonId, pokemon, victim);
    }

    static Pokemon create(UUID eventId, UUID pokemonId, Pokemon pokemon, boolean victim) {
        pokemon.setUuid(pokemonId);
        CompoundTag marker = new CompoundTag();
        marker.putUUID("Site", eventId);
        marker.putBoolean("Victim", victim);
        pokemon.getPersistentData().put(KEY, marker);
        if (victim) {
            UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
        } else {
            ShadowService.fullyCorrupt(pokemon, null, false);
            ShadowMoveUtil.assignNewEncounterMoves(pokemon);
        }
        pokemon.heal();
        return pokemon;
    }

    public static @Nullable UUID site(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag marker = data.getCompound(KEY);
        return marker.hasUUID("Site") ? marker.getUUID("Site") : null;
    }

    static boolean victim(Pokemon pokemon) {
        return pokemon.getPersistentData().getCompound(KEY).getBoolean("Victim");
    }

    static void markHowl(Pokemon pokemon) {
        pokemon.getPersistentData().getCompound(KEY).putBoolean("Howl", true);
    }

    public static boolean isHowl(Pokemon pokemon) {
        return pokemon.getPersistentData().getCompound(KEY).getBoolean("Howl");
    }

    /** Older site snapshots and chunk saves wrapped the carrier's original nickname. */
    private static boolean restoreLegacyCarrierName(Pokemon pokemon) {
        var marker = pokemon.getPersistentData().getCompound(KEY);
        if (!marker.getBoolean("CarrierName")) return false;
        boolean hadNickname = marker.getBoolean("HadNickname");
        marker.remove("CarrierName");
        marker.remove("HadNickname");
        var nickname = pokemon.getNickname();
        if (nickname == null || !(nickname.getContents() instanceof TranslatableContents text)
                || !LEGACY_CARRIER_NAME.equals(text.getKey()) || text.getArgs().length != 1) return false;
        if (hadNickname) {
            Object original = text.getArgs()[0];
            pokemon.setNickname(original instanceof Component component ? component.copy() : Component.literal(original.toString()));
        } else {
            pokemon.setNickname(null);
        }
        return true;
    }

    public static void clearForAdoption(Pokemon pokemon) {
        if (victim(pokemon)) UncatchableProperty.INSTANCE.catchable().apply(pokemon);
        restoreLegacyCarrierName(pokemon);
        pokemon.getPersistentData().remove(KEY);
    }

    public static @Nullable PokemonEntity ensure(ServerLevel world, BlockPos feet, Pokemon pokemon) {
        return ensure(world, feet, pokemon, pos -> true);
    }

    static @Nullable PokemonEntity ensure(ServerLevel world, BlockPos feet, Pokemon pokemon, Predicate<BlockPos> allowed) {
        UUID id = pokemon.getUuid();
        UUID event = site(pokemon);
        if (event == null || pokemon.isPlayerOwned() || !CraterRescueBattles.active(world, event, id)) return null;
        Vec3 position = Vec3.atBottomCenterOf(feet);
        PokemonEntity entity = find(world, id);
        if (entity != null && event.equals(site(entity.getPokemon()))) {
            maintain(world, feet, entity);
            return entity;
        }
        // Cobblemon's native send-out can probe several blocks around its requested position.
        if (world.getEntity(id) != null || CraterRescueBattles.busy(id)
                || !world.hasChunksAt(feet.offset(-8, -1, -8), feet.offset(8, 4, 8))) return null;
        PokemonEntity preview = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
        preview.refreshDimensions();
        if (!safe(world, preview, position)) return null;
        try {
            PokemonEntity spawned = pokemon.sendOut(world, position, null, fresh -> {
                if (!world.isPositionEntityTicking(fresh.blockPosition()) || !allowed.test(fresh.blockPosition())
                        || !safe(world, fresh, fresh.position())) throw SpawnDenied.INSTANCE;
                fresh.setUUID(id);
                protect(fresh);
                return Unit.INSTANCE;
            });
            return spawned != null && world.getEntity(id) == spawned ? spawned : null;
        } catch (SpawnDenied ignored) {
            return null;
        }
    }

    public static @Nullable PokemonEntity find(ServerLevel world, UUID id) {
        Entity existing = world.getEntity(id);
        return existing instanceof PokemonEntity entity && !entity.isRemoved() && !entity.getPokemon().isPlayerOwned()
                && id.equals(entity.getPokemon().getUuid()) && site(entity.getPokemon()) != null ? entity : null;
    }

    public static void maintain(ServerLevel world, BlockPos feet, PokemonEntity entity) {
        if (entity.isBattling() || entity.isBusy() || entity.getBeamMode() != 0
                || CraterRescueBattles.busy(entity.getPokemon().getUuid())) return;
        entity.restrictTo(feet, 2);
        var brain = entity.getBrain();
        var nav = entity.getNavigation();
        var target = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        var pathEnd = nav.isDone() ? null : nav.getTargetPos();
        if ((target != null && feet.distSqr(target.getTarget().currentBlockPosition()) > 4)
                || (pathEnd != null && feet.distSqr(pathEnd) > 4)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            nav.stop();
            target = null;
        }
        Vec3 position = Vec3.atBottomCenterOf(feet);
        double distance = entity.distanceToSqr(position);
        if (distance > 36 && safe(world, entity, position)) {
            nav.stop();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            entity.teleportTo(position.x, position.y, position.z);
        } else if (distance > 4 && (target == null || !target.getTarget().currentBlockPosition().equals(feet))) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(feet, 0.9F, 1));
        }
    }

    static void releaseRoamingLimit(PokemonEntity entity) {
        if (entity.hasRestriction()) {
            entity.clearRestriction();
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            entity.getNavigation().stop();
        }
    }

    static void protect(PokemonEntity entity) {
        if (restoreLegacyCarrierName(entity.getPokemon())) {
            // Native chunk loading initializes the tracked name before its entity-load callback.
            var nickname = entity.getPokemon().getNickname();
            entity.getEntityData().set(PokemonEntity.getNICKNAME(), nickname == null ? Component.empty() : nickname);
        }
        entity.setPersistenceRequired();
        entity.setInvulnerable(true);
        // The optional add-on checks a forced random horde before its herd route. A datapack
        // can give this feature a nonzero default; the rescue always supplies its own roster.
        if (entity.getPokemon().getFeature("horde_size") instanceof IntSpeciesFeature forcedHorde) {
            forcedHorde.setValue(0);
        }
    }

    static boolean safe(ServerLevel world, PokemonEntity entity, Vec3 position) {
        BlockPos ground = BlockPos.containing(position).below();
        AABB bounds = entity.getDimensions(entity.getPose()).makeBoundingBox(position);
        return world.getWorldBorder().isWithinBounds(bounds) && !world.isOutsideBuildHeight(ground)
                && bounds.maxY <= world.getMaxBuildHeight()
                && world.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY - 1, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                && world.getBlockState(ground).isFaceSturdy(world, ground, Direction.UP)
                && world.noCollision(entity, bounds) && !world.containsAnyLiquid(bounds);
    }

    public static void discard(ServerLevel world, UUID pokemonId) {
        CraterRescueBattles.cancelCapture(pokemonId);
        if (world.getEntity(pokemonId) instanceof PokemonEntity entity && site(entity.getPokemon()) != null
                && pokemonId.equals(entity.getPokemon().getUuid()) && !entity.getPokemon().isPlayerOwned()) {
            if (entity.getBattle() != null && !entity.getBattle().getEnded()) entity.getBattle().stop();
            entity.discard();
        }
    }

    /** Native send-out's mutation hook cannot otherwise veto an adjusted, obstructed location. */
    private static final class SpawnDenied extends RuntimeException {
        private static final SpawnDenied INSTANCE = new SpawnDenied();
        private SpawnDenied() { super(null, null, false, false); }
    }
}
