package com.jayemceekay.shadowedhearts.common.snag;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.WeakHashMap;

/** XD capture commitment: detach now, award only when the capturing side wins. Server thread only. */
public final class DeferredSnagDelivery {
    private static final Map<EmptyPokeBallEntity, PokemonBattle> ATTEMPTS = new WeakHashMap<>();
    private static final Map<EmptyPokeBallEntity, Pending> PENDING = new IdentityHashMap<>();
    private static final Map<UUID, LiveDelivery> LIVE = new LinkedHashMap<>();
    private static final Set<UUID> DELIVERING = new HashSet<>();

    private DeferredSnagDelivery() {}

    public static void init() {
        CobblemonEvents.BATTLE_ENDED.subscribe(Priority.LOW, event -> {
            onBattleEnded(event.getBattle());
            return kotlin.Unit.INSTANCE;
        });
        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % 100 != 0) return;
            // A winning battle can finish before the native capture callback reaches insertion.
            for (Pending pending : new ArrayList<>(PENDING.values())) {
                if (pending.eventReceived && pending.battle.getEnded()) resolve(pending);
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) retry(player);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            // Battles cannot resume after a restart. Only already-won deliveries are persisted.
            ATTEMPTS.clear(); PENDING.clear(); LIVE.clear(); DELIVERING.clear();
        });
    }

    /** Called only after NPC, opposing side, Shadow, ownership and machine eligibility validation. */
    public static void rememberAttempt(EmptyPokeBallEntity ball, PokemonBattle battle) {
        ATTEMPTS.put(ball, battle);
        ball.getCaptureFuture().whenComplete((success, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(success)) ATTEMPTS.remove(ball);
        });
    }

    /** True means native insertion is replaced; ordinary catches and legacy rules return false. */
    public static boolean deferInsertion(PlayerPartyStore party, Pokemon pokemon, EmptyPokeBallEntity ball) {
        PokemonBattle battle = ATTEMPTS.remove(ball);
        if (battle == null || !(ball.getOwner() instanceof ServerPlayer player)) return false;
        PokemonCapturedEvent event = new PokemonCapturedEvent(pokemon, player, ball);
        if (!SnagCaptureProvenance.consumeSuccessfulNpcSnag(event)) return false;
        PENDING.put(ball, new Pending(battle, player, pokemon, ball));
        if (!battle.getEnded()) player.displayClientMessage(
                Component.translatable("message.shadowedhearts.snag.awaiting_victory"), false);
        return true;
    }

    /** Hold capture progression and integrations together with delivery, after native ball effects. */
    public static boolean deferCaptureEvent(PokemonCapturedEvent event) {
        Pending pending = PENDING.get(event.getPokeBallEntity());
        if (pending == null || pending.pokemon != event.getPokemon()
                || !pending.player.getUUID().equals(event.getPlayer().getUUID())) return false;
        pending.eventReceived = true;
        if (pending.battle.getEnded()) resolve(pending);
        return true;
    }

    public static void onBattleEnded(PokemonBattle battle) {
        for (Pending pending : new ArrayList<>(PENDING.values())) {
            if (pending.battle == battle && pending.eventReceived) resolve(pending);
        }
    }

    private static void resolve(Pending pending) {
        var actor = pending.battle.getActor(pending.player);
        boolean won = actor != null && pending.battle.getWinners().stream()
                .anyMatch(winner -> winner.getSide() == actor.getSide());
        if (!won) {
            PENDING.remove(pending.ball);
            pending.player.displayClientMessage(Component.translatable(
                    "message.shadowedhearts.snag.lost_after_capture"), false);
            return;
        }
        MinecraftServer server = pending.player.getServer();
        if (server == null) return;
        try {
            var source = pending.pokemon.getStoreCoordinates().get();
            if (source != null && !(source.getStore() instanceof NPCPartyStore)) {
                throw new IllegalStateException("Pending snag moved into non-NPC storage before victory");
            }
            // Persist the complete captured result before releasing the in-memory battle commitment.
            CompoundTag encoded = (CompoundTag) Pokemon.getCODEC().encodeStart(
                    server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pending.pokemon).getOrThrow();
            DeferredSnagSavedData data = DeferredSnagSavedData.get(server);
            boolean inserted = data.queue(pending.player.getUUID(), pending.pokemon.getUuid(), encoded);
            if (!inserted && data.entries(pending.player.getUUID()).stream()
                    .noneMatch(entry -> entry.pokemon().equals(pending.pokemon.getUuid()) && entry.data().equals(encoded))) {
                throw new IllegalStateException("Snag UUID is reserved by an incompatible saved record");
            }
            // Native NPC battles can use the NPC's actual party Pokemon. Leave that encounter
            // intact on a loss; transfer out of its source store only after victory is committed.
            if (source != null && !source.getStore().remove(pending.pokemon)) {
                if (inserted) data.remove(pending.pokemon.getUuid());
                throw new IllegalStateException("Unable to detach the won snag from its NPC party");
            }
            if (inserted) LIVE.put(pending.pokemon.getUuid(), new LiveDelivery(pending.pokemon, pending.ball));
            PENDING.remove(pending.ball);
            ServerPlayer online = server.getPlayerList().getPlayer(pending.player.getUUID());
            if (online != null) {
                retry(online);
                if (data.entries(online.getUUID()).stream().anyMatch(entry -> entry.pokemon().equals(pending.pokemon.getUuid()))) {
                    online.displayClientMessage(Component.translatable("message.shadowedhearts.snag.awaiting_storage"), false);
                }
            }
        } catch (RuntimeException exception) {
            Shadowedhearts.LOGGER.error("Retaining won snag {} after delivery preparation failed",
                    pending.pokemon.getUuid(), exception);
        }
    }

    /** Retry after login or newly available storage space; a repeated battle-end cannot duplicate a grant. */
    public static void retry(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !DELIVERING.add(player.getUUID())) return;
        try {
            DeferredSnagSavedData data = DeferredSnagSavedData.get(server);
            var entries = data.entries(player.getUUID());
            if (entries.isEmpty()) return;
            var storage = Cobblemon.INSTANCE.getStorage();
            var party = storage.getParty(player);
            var pc = storage.getPC(player);
            for (var entry : entries) {
                try {
                    var chamber = storage.getCustomStore(PurificationChamberStore.class,
                            player.getUUID(), player.registryAccess());
                    // Native storage may have saved before the queue's removal during an interrupted save.
                    if (party.get(entry.pokemon()) != null || pc.get(entry.pokemon()) != null
                            || chamber != null && chamber.get(entry.pokemon()) != null) {
                        data.remove(entry.pokemon()); LIVE.remove(entry.pokemon()); continue;
                    }
                    if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) continue;
                    LiveDelivery live = LIVE.get(entry.pokemon());
                    Pokemon pokemon = live != null ? live.pokemon : Pokemon.getCODEC().parse(
                            player.registryAccess().createSerializationContext(NbtOps.INSTANCE), entry.data()).getOrThrow();
                    if (!pokemon.getUuid().equals(entry.pokemon()) || pokemon.getStoreCoordinates().get() != null) {
                        throw new IllegalStateException("Deferred snag identity or storage coordinates do not match");
                    }
                    EmptyPokeBallEntity ball = live != null ? live.ball : new EmptyPokeBallEntity(player.serverLevel());
                    if (live == null) {
                        // Recovery replays the successful capture notification, never capture effects or rolls.
                        ball.setPokeBall(pokemon.getCaughtBall());
                        ball.setAspects(Set.of("snag_ball"));
                        ball.getCaptureFuture().complete(true);
                    }
                    ball.setOwner(player);
                    // Keep the delivered Pokemon usable even if another mod's capture listener throws.
                    pokemon.getCustomProperties().removeIf(property -> property.asString().equals("battleClone")
                            || property.asString().equals("uncatchable"));
                    if (!party.add(pokemon)) continue;
                    data.remove(entry.pokemon()); LIVE.remove(entry.pokemon());
                    ModCriteriaTriggers.triggerSnagFromNpc(player);
                    CobblemonEvents.POKEMON_CAPTURED.emit(new PokemonCapturedEvent(pokemon, player, ball));
                    player.displayClientMessage(Component.translatable("message.shadowedhearts.snag.delivered"), false);
                } catch (RuntimeException exception) {
                    Shadowedhearts.LOGGER.error("Unable to finish deferred snag {} for {}",
                            entry.pokemon(), player.getUUID(), exception);
                }
            }
        } catch (RuntimeException exception) {
            Shadowedhearts.LOGGER.error("Retaining deferred snags after storage access failed for {}",
                    player.getUUID(), exception);
        } finally {
            DELIVERING.remove(player.getUUID());
        }
    }

    private static final class Pending {
        private final PokemonBattle battle;
        private final ServerPlayer player;
        private final Pokemon pokemon;
        private final EmptyPokeBallEntity ball;
        private boolean eventReceived;

        private Pending(PokemonBattle battle, ServerPlayer player, Pokemon pokemon, EmptyPokeBallEntity ball) {
            this.battle = battle; this.player = player; this.pokemon = pokemon; this.ball = ball;
        }
    }

    private record LiveDelivery(Pokemon pokemon, EmptyPokeBallEntity ball) {}
}
