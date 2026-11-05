package com.jayemceekay.shadowedhearts.network;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.HeldItemEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonData;
import dev.architectury.event.events.common.TickEvent;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.jayemceekay.shadowedhearts.network.aura.AuraLifecyclePacket;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side sync that periodically broadcasts authoritative positions for Shadow Pokémon auras.
 * Uses Architectury events to remain platform-agnostic.
 */
public final class AuraServerSync {
    private AuraServerSync() {
    }

    private static final Map<Integer, WeakReference<PokemonEntity>> TRACKING = new ConcurrentHashMap<>();
    private static final Map<Integer, LastSentState> LAST_SENT = new ConcurrentHashMap<>();
    private static final Map<Integer, WeakReference<PokemonEntity>> PENDING_SPAWNS = new ConcurrentHashMap<>();
    private static final Map<Integer, PendingShadowChange> PENDING_CONVERSIONS = new ConcurrentHashMap<>();
    // Accessed on the server thread; an entity's reveal must not retain it after removal.
    private static final Set<PokemonEntity> REVEAL_REQUESTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<PokemonEntity> DEFERRED_SEND_OUTS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Pokemon, WeakReference<PokemonEntity>> LIVE_ENTITIES = new WeakHashMap<>();

    private static final class PendingShadowChange {
        final WeakReference<PokemonEntity> entity;
        final boolean wasShadow;
        boolean revealRequested;

        PendingShadowChange(PokemonEntity entity, boolean wasShadow) {
            this.entity = new WeakReference<>(entity);
            this.wasShadow = wasShadow;
        }
    }

    /** Successful fresh additions include native commands, natural spawns and other mods. */
    public static void onEntityAdded(PokemonEntity entity) {
        if (entity.level() instanceof ServerLevel) {
            LIVE_ENTITIES.put(entity.getPokemon(), new WeakReference<>(entity));
            PENDING_SPAWNS.put(entity.getId(), new WeakReference<>(entity));
        }
    }

    /** Native aspect updates also cover wild Pokemon and direct forced-aspect conversions. */
    public static void onShadowStateChanged(Pokemon pokemon, boolean wasShadow) {
        PokemonEntity entity = pokemon.getEntity();
        if (!isLivePokemon(entity, pokemon)) {
            WeakReference<PokemonEntity> reference = LIVE_ENTITIES.get(pokemon);
            entity = reference == null ? null : reference.get();
        }
        if (!isLivePokemon(entity, pokemon) || PENDING_SPAWNS.containsKey(entity.getId())) return;

        PendingShadowChange pending = PENDING_CONVERSIONS.get(entity.getId());
        if (pending == null || pending.entity.get() != entity) {
            // Compare the first state this tick with the final one. Temporary form/aspect
            // rebuilds must not replay a reveal when they restore an existing Shadow.
            PENDING_CONVERSIONS.put(entity.getId(), new PendingShadowChange(entity, wasShadow));
        }
    }

    private static boolean isLivePokemon(@Nullable PokemonEntity entity, Pokemon pokemon) {
        return entity != null && entity.getPokemon() == pokemon && entity.isAlive()
                && entity.level() instanceof ServerLevel level && level.getEntity(entity.getId()) == entity;
    }

    /** Animated and shoulder send-outs start their aura when the delayed POST event arrives. */
    public static void deferToSendOutEvent(PokemonEntity entity) {
        if (entity.level() instanceof ServerLevel) {
            DEFERRED_SEND_OUTS.add(entity);
            REVEAL_REQUESTED.add(entity);
        }
    }

    /** Preserve explicit wild/send-out/raid reveals instead of also scheduling the fallback. */
    public static void onAuraStartRequested(Entity entity) {
        if (entity instanceof PokemonEntity pokemon && entity.level() instanceof ServerLevel) {
            REVEAL_REQUESTED.add(pokemon);
            PendingShadowChange pending = PENDING_CONVERSIONS.get(entity.getId());
            if (pending != null && pending.entity.get() == entity) pending.revealRequested = true;
            if (ShadowAspectUtil.shouldHaveShadowAura(pokemon.getPokemon())) {
                TRACKING.put(entity.getId(), new WeakReference<>(pokemon));
            }
        }
    }

    /** New viewers receive continuous cosmetic state, never another transient spawn/send-out reveal. */
    public static void onStartTracking(Entity entity, ServerPlayer player) {
        AuraLifecyclePacket packet = persistentAuraForTracking(entity);
        if (packet != null && player.level() == entity.level()) {
            ShadowedHeartsNetwork.sendToPlayer(player, packet);
        }
    }

    @Nullable
    public static AuraLifecyclePacket persistentAuraForTracking(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemon) || !(entity.level() instanceof ServerLevel)
                || !entity.isAlive() || !ShadowAspectUtil.isHoldingShadowShard(pokemon.getPokemon())) return null;
        return S2CUtils.auraStartPacket(pokemon, 1.0f, Integer.MAX_VALUE / 2);
    }

    private static final class LastSentState {
        final double x, y, z;
        final float corruption;
        final long tick;

        LastSentState(double x, double y, double z, float corruption, long tick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.corruption = corruption;
            this.tick = tick;
        }

        boolean shouldResync(double nx, double ny, double nz, float nCorr, long nTick) {
            if (nTick - tick > 100) return true; // Force sync every 5 seconds
            if (Math.abs(nCorr - corruption) > 0.01f) return true;
            double dx = x - nx;
            double dy = y - ny;
            double dz = z - nz;
            return (dx * dx + dy * dy + dz * dz) > 0.01; // Moved more than 0.1 blocks
        }
    }

    /**
     * Call once during common init on both platforms.
     */
    public static void init() {
        // PokemonEntity's backing Pokemon is persisted, but our SynchedEntityData
        // fields are recreated with defaults. Restore them after Cobblemon has
        // decoded the Pokemon so saved shadow Pokemon remain detectable.
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe(Priority.NORMAL, event -> {
            PokemonEntity pe = event.getPokemonEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;

            LIVE_ENTITIES.put(pe.getPokemon(), new WeakReference<>(pe));
            ShadowPokemonData.syncFromPokemon(pe);
            if (ShadowAspectUtil.shouldHaveShadowAura(pe.getPokemon())) {
                TRACKING.put(pe.getId(), new WeakReference<>(pe));
            }
            return Unit.INSTANCE;
        });

        // Track lifecycle via Cobblemon events (platform-agnostic)
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, (PokemonSentEvent.Post e) -> {
            var pe = e.getPokemonEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            // Clear this even for ordinary Pokemon, which can be converted after send-out.
            DEFERRED_SEND_OUTS.remove(pe);
            Pokemon p;
            try {
                p = pe.getPokemon();
                if (p == null) return Unit.INSTANCE;
                // Run occasional validation when sent out
                ShadowAspectUtil.ensureRequiredShadowAspects(p);
                // Send-out creates a fresh entity without the saved-entity load event.
                // Restore the scanner/behavior flags from its backing Pokemon as well.
                ShadowPokemonData.syncFromPokemon(pe);
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
                    LAST_SENT.remove(pe.getId());
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
                // The fresh-add fallback starts the aura unless a spawn listener
                // has already requested a specialized reveal.
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_RECALL_PRE.subscribe(Priority.NORMAL, (PokemonRecallEvent.Pre e) -> {
            var pe = e.getOldEntity();
            if (pe == null || pe.level().isClientSide()) return Unit.INSTANCE;
            TRACKING.remove(pe.getId());
            LAST_SENT.remove(pe.getId());
            // Tell clients to fade out the aura quickly
            S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
            return Unit.INSTANCE;
        });

        // Server tick: broadcast states and prune entries
        TickEvent.SERVER_POST.register(AuraServerSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        // Wait until callers finish their post-add conversion and choose any specialized reveal.
        Iterator<WeakReference<PokemonEntity>> spawns = PENDING_SPAWNS.values().iterator();
        while (spawns.hasNext()) {
            PokemonEntity entity = spawns.next().get();
            spawns.remove();
            if (entity == null || !entity.isAlive() || !(entity.level() instanceof ServerLevel level)
                    || level.getEntity(entity.getId()) != entity) continue;
            ShadowPokemonData.syncFromPokemon(entity);
            if (!ShadowAspectUtil.shouldHaveShadowAura(entity.getPokemon())) continue;
            TRACKING.put(entity.getId(), new WeakReference<>(entity));
            if (!REVEAL_REQUESTED.contains(entity)) {
                int sustain = ShadowAspectUtil.isHoldingShadowShard(entity.getPokemon()) ? Integer.MAX_VALUE / 2 : 600;
                AuraBroadcastQueue.queueBroadcast(entity, 2.5f, sustain);
            }
        }

        Iterator<PendingShadowChange> conversions = PENDING_CONVERSIONS.values().iterator();
        while (conversions.hasNext()) {
            PendingShadowChange pending = conversions.next();
            conversions.remove();
            PokemonEntity entity = pending.entity.get();
            if (entity == null || !isLivePokemon(entity, entity.getPokemon())) continue;
            Pokemon pokemon = entity.getPokemon();
            ShadowPokemonData.syncFromPokemon(entity);
            if (!ShadowAspectUtil.shouldHaveShadowAura(pokemon)) continue;
            TRACKING.put(entity.getId(), new WeakReference<>(entity));
            if (!pending.wasShadow && ShadowAspectUtil.hasShadowAspect(pokemon)
                    && !pending.revealRequested && !DEFERRED_SEND_OUTS.contains(entity)) {
                int sustain = ShadowAspectUtil.isHoldingShadowShard(pokemon) ? Integer.MAX_VALUE / 2 : 600;
                AuraBroadcastQueue.queueBroadcast(entity, 2.5f, sustain);
            }
        }

        Iterator<Map.Entry<Integer, WeakReference<PokemonEntity>>> it = TRACKING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            int id = en.getKey();
            var ref = en.getValue();
            PokemonEntity pe = ref != null ? ref.get() : null;
            if (pe == null || pe.level().isClientSide()) {
                it.remove();
                LAST_SENT.remove(id);
                continue;
            }
            // If the Pokémon has just died or been removed, send a quick fade-out and drop tracking
            if (!pe.isAlive()) {
                S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
                it.remove();
                LAST_SENT.remove(id);
                continue;
            }

            if (!ShadowAspectUtil.shouldHaveShadowAura(pe.getPokemon())) {
                S2CUtils.broadcastAuraFadeOutToTracking(pe, 10);
                it.remove();
                LAST_SENT.remove(id);
                continue;
            }

            // Broadcast to tracking players and the owner if present
            if (pe.level() instanceof ServerLevel level) {
                long now = level.getGameTime();
                float corruption = ShadowAspectUtil.getHeartGauge(pe.getPokemon());
                LastSentState last = LAST_SENT.get(id);
                if (last == null || last.shouldResync(pe.getX(), pe.getY(), pe.getZ(), corruption, now)) {
                    S2CUtils.broadcastStateToTracking(pe, now);
                    LAST_SENT.put(id, new LastSentState(pe.getX(), pe.getY(), pe.getZ(), corruption, now));
                }
            } else {
                it.remove();
                LAST_SENT.remove(id);
            }
        }
    }
}
