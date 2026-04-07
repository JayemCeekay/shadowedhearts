package com.jayemceekay.shadowedhearts.network;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.HeldItemEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import dev.architectury.event.events.common.TickEvent;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.ref.WeakReference;
import java.util.Iterator;
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
            Pokemon p;
            try {
                p = pe.getPokemon();
                if (p == null) return Unit.INSTANCE;
                // Run occasional validation when sent out
                ShadowAspectUtil.ensureRequiredShadowAspects(p);
                if (!ShadowAspectUtil.shouldHaveShadowAura(p))
                    return Unit.INSTANCE;
            } catch (Exception ex) {
                return Unit.INSTANCE;
            }
            TRACKING.put(pe.getId(), new WeakReference<>(pe));
            // Notify clients tracking this entity to start rendering the aura
            int sustain = ShadowAspectUtil.isHoldingShadowShard(p) ? Integer.MAX_VALUE / 2 : -1;
            S2CUtils.broadcastAuraStartToTracking(pe, 1.0f, sustain);
            S2CUtils.broadcastLuminousMoteToTracking(pe);
            return Unit.INSTANCE;
        });

        CobblemonEvents.COSMETIC_ITEM_POST.subscribe(Priority.NORMAL, (HeldItemEvent.Post e) -> {
            Pokemon p = e.getPokemon();
            PokemonEntity pe = p.getEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;

            if (ShadowAspectUtil.shouldHaveShadowAura(p)) {
                TRACKING.put(pe.getId(), new WeakReference<>(pe));
                int sustain = ShadowAspectUtil.isHoldingShadowShard(p) ? Integer.MAX_VALUE / 2 : -1;
                S2CUtils.broadcastAuraStartToTracking(pe, 1.0f, sustain);
                S2CUtils.broadcastLuminousMoteToTracking(pe);
            } else {
                if (TRACKING.containsKey(pe.getId())) {
                    TRACKING.remove(pe.getId());
                    S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
                }
            }
            return Unit.INSTANCE;
        });

        // Track wild shadow spawns
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, (e) -> {
            var pe = e.getEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            if (ShadowAspectUtil.shouldHaveShadowAura(pe.getPokemon())) {
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
            S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
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
                S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
                it.remove();
                continue;
            }

            if (!ShadowAspectUtil.shouldHaveShadowAura(pe.getPokemon())) {
                S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
                it.remove();
                continue;
            }

            // Broadcast to tracking players and the owner if present
            if (pe.level() instanceof ServerLevel level) {
                long now = level.getGameTime();
                S2CUtils.broadcastStateToTracking(pe, now);
            } else {
                it.remove();
            }
        }
    }
}
