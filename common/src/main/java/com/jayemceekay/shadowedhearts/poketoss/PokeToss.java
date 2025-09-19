package com.jayemceekay.shadowedhearts.poketoss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Central manager and small API surface for the initial PokeTOSS framework.
 * Holds lightweight per-entity order data in-memory. Future iterations may
 * move to components/capabilities and network sync.
 */
public final class PokeToss {
    private PokeToss() {}

    /** Current orders keyed by the entity UUID. */
    private static final Map<UUID, TacticalOrder> ORDERS = new Object2ObjectOpenHashMap<>();
    /** Per-player temporary selection (UUIDs of selected entities). */
    private static final Map<UUID, Set<UUID>> SELECTIONS = new Object2ObjectOpenHashMap<>();

    /** Simple receiver backed by the ORDERS map. */
    private static final class ReceiverImpl implements TacticalOrderReceiver {
        private final LivingEntity entity;
        private ReceiverImpl(LivingEntity entity) { this.entity = entity; }

        @Override
        public boolean acceptOrder(ServerLevel level, TacticalOrder order, @Nullable ServerPlayer commander) {
            // Initial acceptance rules: only allow on PokemonEntity for now.
            if (!(entity instanceof PokemonEntity)) {
                if (commander != null) commander.displayClientMessage(Component.literal("PokeTOSS: Target is not a Pokémon."), true);
                return false;
            }
            ORDERS.put(entity.getUUID(), order);
            if (commander != null) commander.displayClientMessage(describeApply(order, entity), true);
            return true;
        }

        @Override
        public @Nullable TacticalOrder getCurrentOrder() {
            return ORDERS.get(entity.getUUID());
        }

        @Override
        public void clearOrder() {
            ORDERS.remove(entity.getUUID());
        }
    }

    /** Obtain or adapt a TacticalOrderReceiver for any living entity. */
    public static TacticalOrderReceiver getOrCreateReceiver(LivingEntity entity) {
        return new ReceiverImpl(entity);
    }

    /** Convenience to issue an order to an entity. */
    public static boolean issueOrder(ServerLevel level, LivingEntity entity, TacticalOrder order, @Nullable ServerPlayer commander) {
        return getOrCreateReceiver(entity).acceptOrder(level, order, commander);
    }

    public static void clearOrder(LivingEntity entity) {
        ORDERS.remove(entity.getUUID());
    }

    public static @Nullable TacticalOrder getOrder(LivingEntity entity) {
        return ORDERS.get(entity.getUUID());
    }

    /** Very small feedback message. */
    public static Component describeApply(TacticalOrder order, LivingEntity entity) {
        String name = entity.getName().getString();
        return Component.literal("[TOSS] " + name + " -> " + order.type.name());
    }

    /** Eligibility helper (will expand later). */
    public static boolean isEligible(LivingEntity e, @Nullable Player commander) {
        // For now: must be a Cobblemon PokemonEntity.
        return e instanceof PokemonEntity;
    }

    // --- Selection API ---
    public static void setSelection(ServerPlayer player, java.util.Collection<UUID> uuids) {
        Set<UUID> set = new HashSet<>(uuids);
        SELECTIONS.put(player.getUUID(), set);
    }

    public static void setSelectionEntities(ServerPlayer player, java.util.Collection<? extends LivingEntity> entities) {
        Set<UUID> set = new HashSet<>();
        for (LivingEntity e : entities) set.add(e.getUUID());
        SELECTIONS.put(player.getUUID(), set);
    }

    public static java.util.Set<UUID> getSelection(ServerPlayer player) {
        return SELECTIONS.getOrDefault(player.getUUID(), java.util.Set.of());
    }

    public static void clearSelection(ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
    }
}
