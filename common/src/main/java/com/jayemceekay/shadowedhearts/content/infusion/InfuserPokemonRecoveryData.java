package com.jayemceekay.shadowedhearts.content.infusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Detached Pokemon awaiting space in their owner's native storage; survives world/server reloads. */
public final class InfuserPokemonRecoveryData extends SavedData {
    public static final String DATA_ID = "shadowedhearts_infuser_recovery";
    private static final Factory<InfuserPokemonRecoveryData> FACTORY =
            new Factory<>(InfuserPokemonRecoveryData::new, InfuserPokemonRecoveryData::load, null);
    private final Map<UUID, Map<UUID, CompoundTag>> pending = new LinkedHashMap<>();
    // Malformed records remain in the file for repair instead of being erased by the next save.
    private final ListTag rejected = new ListTag();

    public static InfuserPokemonRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    /** Returns only after full serialization succeeds; the caller may then release its original reference. */
    public static boolean recoverLater(ServerLevel level, UUID owner, Pokemon pokemon) {
        Objects.requireNonNull(owner, "Infuser Pokemon owner");
        Objects.requireNonNull(pokemon, "Infuser Pokemon");
        try {
            CompoundTag encoded = (CompoundTag) Pokemon.getCODEC().encodeStart(
                    level.registryAccess().createSerializationContext(NbtOps.INSTANCE), pokemon).getOrThrow();
            if (pokemon.getStoreCoordinates().get() != null) {
                throw new IllegalStateException("Cannot queue a Pokemon that is still in native storage");
            }
            InfuserPokemonRecoveryData data = get(level.getServer());
            data.pending.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(pokemon.getUuid(), encoded.copy());
            data.setDirty();
            return true;
        } catch (RuntimeException exception) {
            Shadowedhearts.LOGGER.error("Unable to preserve infuser Pokemon {} for {}", pokemon.getUuid(), owner, exception);
            throw new IllegalStateException("Infuser Pokemon recovery serialization failed", exception);
        }
    }

    public int getPendingCount(UUID owner) { return pending.getOrDefault(owner, Map.of()).size(); }
    public boolean hasPokemon(UUID owner, UUID pokemon) { return pending.getOrDefault(owner, Map.of()).containsKey(pokemon); }

    /** Immediate retry for tests and callers; normal registration invokes this every 100 server ticks. */
    public static void retry(MinecraftServer server) {
        InfuserPokemonRecoveryData data = get(server);
        var owners = data.pending.entrySet().iterator();
        while (owners.hasNext()) {
            var ownerEntry = owners.next();
            var player = server.getPlayerList().getPlayer(ownerEntry.getKey());
            if (player == null) continue;
            try {
                if (!PokemonInfusionRules.playerAvailable(player)) continue;
                var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
                var records = ownerEntry.getValue().entrySet().iterator();
                while (records.hasNext()) {
                    var record = records.next();
                    try {
                        // A completed native delivery may have saved before this queue's removal did.
                        if (InfuserPokemonStorage.contains(ownerEntry.getKey(), record.getKey(), player.registryAccess())) {
                            records.remove(); data.setDirty(); continue;
                        }
                        // Later records can already be stored even while this undelivered one must wait.
                        if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) continue;
                        Pokemon pokemon = Pokemon.getCODEC().parse(player.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                                record.getValue()).getOrThrow();
                        if (!pokemon.getUuid().equals(record.getKey()) || pokemon.getStoreCoordinates().get() != null) {
                            throw new IllegalStateException("Recovery Pokemon identity or coordinates do not match its record");
                        }
                        if (party.add(pokemon) || pc.add(pokemon)) {
                            records.remove(); data.setDirty();
                            player.displayClientMessage(Component.translatable("message.shadowedhearts.infuser.recovered"), false);
                        }
                    } catch (RuntimeException exception) {
                        Shadowedhearts.LOGGER.error("Retaining infuser recovery Pokemon {} for {} after failed return",
                                record.getKey(), ownerEntry.getKey(), exception);
                    }
                }
                if (ownerEntry.getValue().isEmpty()) { owners.remove(); data.setDirty(); }
            } catch (RuntimeException exception) {
                Shadowedhearts.LOGGER.error("Unable to access infuser recovery storage for {}", ownerEntry.getKey(), exception);
            }
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = rejected.copy();
        pending.forEach((owner, pokemonRecords) -> pokemonRecords.forEach((pokemonId, pokemonTag) -> {
            CompoundTag record = new CompoundTag();
            record.putUUID("Owner", owner); record.putUUID("PokemonId", pokemonId);
            record.put("Pokemon", pokemonTag.copy()); entries.add(record);
        }));
        tag.put("Entries", entries);
        return tag;
    }

    public static InfuserPokemonRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        InfuserPokemonRecoveryData data = new InfuserPokemonRecoveryData();
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag record = entries.getCompound(index);
            if (!record.hasUUID("Owner") || !record.hasUUID("PokemonId") || !record.contains("Pokemon", Tag.TAG_COMPOUND)) {
                data.rejected.add(record.copy());
                Shadowedhearts.LOGGER.error("Retaining malformed infuser recovery record {}", index);
                continue;
            }
            var records = data.pending.computeIfAbsent(record.getUUID("Owner"), ignored -> new LinkedHashMap<>());
            CompoundTag previous = records.putIfAbsent(record.getUUID("PokemonId"), record.getCompound("Pokemon").copy());
            if (previous != null && !previous.equals(record.getCompound("Pokemon"))) data.rejected.add(record.copy());
        }
        return data;
    }
}
