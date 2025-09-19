package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.network.ModNetworking;
import dev.architectury.event.events.common.TickEvent;
import kotlin.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side sync that periodically broadcasts authoritative positions for Shadow Pokémon auras.
 * Uses Architectury events to remain platform-agnostic.
 */
public final class AuraServerSync {
    private AuraServerSync() {}

    private static final Map<Integer, WeakReference<PokemonEntity>> TRACKING = new ConcurrentHashMap<>();

    /** Call once during common init on both platforms. */
    public static void init() {
        // Track lifecycle via Cobblemon events (platform-agnostic)
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, (PokemonSentEvent.Post e) -> {
            var pe = e.getPokemonEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            try {
                if (!PokemonAspectUtil.hasShadowAspect(pe.getPokemon())) return Unit.INSTANCE;
            } catch (Exception ex) {
                return Unit.INSTANCE;
            }
            TRACKING.put(pe.getId(), new WeakReference<>(pe));
            // Notify clients tracking this entity to start rendering the aura
            ModNetworking.broadcastAuraStartToTracking(pe);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_RECALL_PRE.subscribe(Priority.NORMAL, (PokemonRecallEvent.Pre e) -> {
            var pe = e.getOldEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            TRACKING.remove(pe.getId());
            // Tell clients to fade out the aura quickly
            ModNetworking.broadcastAuraFadeOutToTracking(pe, 10);
            return Unit.INSTANCE;
        });

        // Server tick: broadcast states and prune entries
        TickEvent.SERVER_POST.register(AuraServerSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
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
                ModNetworking.broadcastAuraFadeOutToTracking(pe, 10);
                it.remove();
                continue;
            }
            // Broadcast to tracking players and the owner if present
            if (pe.level() instanceof ServerLevel level) {
                long now = level.getGameTime();
                ModNetworking.broadcastStateToTracking(pe, now);
            } else {
                it.remove();
            }
        }
    }
}
