package com.jayemceekay.shadowedhearts.poketoss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Contract for entities (typically PokemonEntity) that can accept tactical orders.
 *
 * In this initial framework we don't inject mixins; instead, a manager maintains
 * per-entity order state by UUID. Later we may switch to components/capabilities.
 */
public interface TacticalOrderReceiver {

    /**
     * Called when a new order is issued to this entity.
     * Implementations may choose to validate or reject an order.
     *
     * @return true if accepted; false to reject
     */
    boolean acceptOrder(ServerLevel level, TacticalOrder order, @Nullable ServerPlayer commander);

    /** Returns the current order if any. */
    @Nullable TacticalOrder getCurrentOrder();

    /** Clear any current order. */
    void clearOrder();

    /** Utility to adapt any LivingEntity via PokeToss manager. */
    static TacticalOrderReceiver of(LivingEntity entity) {
        return PokeToss.getOrCreateReceiver(entity);
    }
}
