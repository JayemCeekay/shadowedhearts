package com.jayemceekay.shadowedhearts.common.moonwound.raid;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.UUID;

/** No Raid Dens types in this entry point: absent/unsupported installations retain the Alpha finale. */
public final class MoonwoundRaidIntegration {
    public static final String ENCOUNTER_KEY = "shadowedhearts:moonwound_raid";
    private static boolean initialized;
    private static MinecraftServer server;
    private MoonwoundRaidIntegration() { }

    public static boolean isAvailable() {
        return Platform.getOptionalMod(MoonwoundRaidCompatibility.MOD_ID)
                .map(mod -> MoonwoundRaidCompatibility.supportsVersion(mod.getVersion())).orElse(false);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        LifecycleEvent.SERVER_STARTED.register(running -> server = running);
        LifecycleEvent.SERVER_STOPPED.register(stopped -> server = null);
        // This recovery guard deliberately has no Raid Dens linkage: an add-on may be removed between saves.
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getPokemonEntity();
            if (!entity.getPokemon().getPersistentData().contains(ENCOUNTER_KEY)) return Unit.INSTANCE;
            if (entity.getPokemon().isPlayerOwned()) entity.getPokemon().getPersistentData().remove(ENCOUNTER_KEY);
            else if (!isAvailable()) event.cancel();
            return Unit.INSTANCE;
        });
        if (isAvailable()) RaidDensMoonwoundAdapter.init();
    }

    public static void initialize() { init(); }

    public static void tick(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        server = level.getServer();
        if (isAvailable()) RaidDensMoonwoundAdapter.tick(level, site, event);
        else discardLoaded(event.eventId());
    }

    public static void leaveParticipant(UUID eventId, UUID playerId) {
        if (isAvailable()) RaidDensMoonwoundAdapter.leaveParticipant(eventId, playerId);
    }

    public static void cleanupEvent(UUID eventId) {
        if (isAvailable()) RaidDensMoonwoundAdapter.cleanupEvent(eventId);
        discardLoaded(eventId);
    }

    private static void discardLoaded(UUID eventId) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            var discard = new ArrayList<PokemonEntity>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PokemonEntity pokemon)) continue;
                var marker = pokemon.getPokemon().getPersistentData().getCompound(ENCOUNTER_KEY);
                if (!marker.hasUUID("Event") || !eventId.equals(marker.getUUID("Event"))) continue;
                if (pokemon.getPokemon().isPlayerOwned()) pokemon.getPokemon().getPersistentData().remove(ENCOUNTER_KEY);
                else discard.add(pokemon);
            }
            discard.forEach(Entity::discard);
        }
    }

    public static boolean isMoonwoundRaidBattle(ServerPlayer player) {
        return isAvailable() && RaidDensMoonwoundAdapter.isMoonwoundRaidBattle(player);
    }
}
