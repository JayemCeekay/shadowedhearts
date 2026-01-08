package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetworkingUtils;
import kotlin.Unit;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side sync that periodically broadcasts authoritative positions for Shadow Pokémon auras.
 * Uses Architectury events to remain platform-agnostic.
 */
public final class AuraServerSync {
    private AuraServerSync() {
    }

    private static final Map<Integer, WeakReference<PokemonEntity>> TRACKING = new ConcurrentHashMap<>();

    /**
     * Call once during common init on both platforms.
     */
    public static void init() {
        // Track lifecycle via Cobblemon events (platform-agnostic)
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, (PokemonSentEvent.Post e) -> {
            var pe = e.getPokemonEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            try {
                Pokemon p = pe.getPokemon();
                // Run occasional validation when sent out
                PokemonAspectUtil.ensureRequiredShadowAspects(p);
                if (!PokemonAspectUtil.hasShadowAspect(p))
                    return Unit.INSTANCE;
            } catch (Exception ex) {
                return Unit.INSTANCE;
            }
            TRACKING.put(pe.getId(), new WeakReference<>(pe));
            // Notify clients tracking this entity to start rendering the aura
            ShadowedHeartsNetworkingUtils.broadcastAuraStartToTracking(pe);
            ShadowedHeartsNetworkingUtils.broadcastLuminousMoteToTracking(pe);
            return Unit.INSTANCE;
        });

        // Track wild shadow spawns
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, (e) -> {
            var pe = e.getEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            if (PokemonAspectUtil.hasShadowAspect(pe.getPokemon())) {
                TRACKING.put(pe.getId(), new WeakReference<>(pe));
                // We don't broadcast START here because listeners (like WildShadowSpawnListener)
                // might want to broadcast a specialized one. The tick loop will pick it up
                // for regular updates if it's missing.
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_RECALL_PRE.subscribe(Priority.NORMAL, (PokemonRecallEvent.Pre e) -> {
            var pe = e.getOldEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            TRACKING.remove(pe.getId());
            // Tell clients to fade out the aura quickly
            ShadowedHeartsNetworkingUtils.broadcastAuraFadeOutToTracking(pe, 10);
            return Unit.INSTANCE;
        });

        // Server tick: broadcast states and prune entries
       // TickEvent.SERVER_POST.register(AuraServerSync::onServerTick);
    }

    /*private static void onServerTick(MinecraftServer server) {
        // Automatically discover untracked Shadow Pokémon in all levels
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof PokemonEntity pe && pe.isAlive()) {
                    if (PokemonAspectUtil.hasShadowAspect(pe.getPokemon())) {
                        if (!TRACKING.containsKey(pe.getId())) {
                            TRACKING.put(pe.getId(), new WeakReference<>(pe));
                            // If it's a wild spawn or already in world, it might need an initial broadcast
                            // but we usually want the specialized one from SpawnListener if it just spawned.
                            // This serves as a safety catch-all.
                            ShadowedHeartsNetworkingUtils.broadcastAuraStartToTracking(pe);
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<Integer, WeakReference<PokemonEntity>>> it = TRACKING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            var ref = en.getValue();
            PokemonEntity pe = ref != null ? ref.get() : null;
            if (pe == null) {
                it.remove();
                continue;
            }
            if (pe.level().isClientSide()) {
                it.remove();
                continue;
            }
            // If the Pokémon has just died or been removed, send a quick fade-out and drop tracking
            if (!pe.isAlive()) {
                ShadowedHeartsNetworkingUtils.broadcastAuraFadeOutToTracking(pe, 10);
                it.remove();
                continue;
            }
            // Broadcast to tracking players and the owner if present
            if (pe.level() instanceof ServerLevel level) {
                long now = level.getGameTime();
                ShadowedHeartsNetworkingUtils.broadcastStateToTracking(pe, now);
            } else {
                it.remove();
            }
        }
    }*/
}
