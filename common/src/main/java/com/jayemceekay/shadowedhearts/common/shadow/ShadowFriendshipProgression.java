package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.FriendshipUpdatedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.jayemceekay.shadowedhearts.config.ProgressionEconomyConfig;
import com.jayemceekay.shadowedhearts.pokemon.properties.FriendshipBufferProperty;
import org.jetbrains.annotations.Nullable;

/** Deferred friendship survives save/reload and is never rescaled by later configuration changes. */
public final class ShadowFriendshipProgression {
    private static final String BUFFER = "shadowedhearts:friendship_buffer";
    private static final String FRACTION = "shadowedhearts:friendship_fraction";
    private ShadowFriendshipProgression() {}

    public static int buffer(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream().filter(FriendshipBufferProperty.class::isInstance)
                .map(FriendshipBufferProperty.class::cast).mapToInt(FriendshipBufferProperty::getValue).findFirst()
                .orElseGet(() -> pokemon.getPersistentData().getInt(BUFFER));
    }

    public static void setBuffer(Pokemon pokemon, int delta) {
        pokemon.getCustomProperties().removeIf(FriendshipBufferProperty.class::isInstance);
        pokemon.getCustomProperties().add(new FriendshipBufferProperty(delta));
        pokemon.getPersistentData().putInt(BUFFER, delta);
        ShadowAspectUtil.syncProperties(pokemon);
    }

    /** Null delegates to Cobblemon, preserving its ordinary semantics and compatibility defaults. */
    public static @Nullable Boolean change(Pokemon pokemon, int amount, boolean increase, boolean coerceSafe) {
        if (ShadowFriendshipSources.isDirect() || !ShadowAspectUtil.hasShadowAspect(pokemon)) return null;
        var policy = ProgressionEconomyConfig.INSTANCE.friendshipPolicy();
        if (policy == ProgressionEconomyConfig.FriendshipPolicy.FOLLOW_RESTRICTIONS) {
            return ShadowRestrictionPolicy.blocksFriendshipChanges(pokemon) ? false : null;
        }
        if (policy == ProgressionEconomyConfig.FriendshipPolicy.BLOCK) return false;
        long requested = Math.abs((long) amount);
        if (requested > Integer.MAX_VALUE) return false;
        return apply(pokemon, (int) requested, increase, coerceSafe, policy);
    }

    public static @Nullable Boolean set(Pokemon pokemon, int value, boolean coerceSafe) {
        if (ShadowFriendshipSources.isDirect() || !ShadowAspectUtil.hasShadowAspect(pokemon)) return null;
        var policy = ProgressionEconomyConfig.INSTANCE.friendshipPolicy();
        if (policy == ProgressionEconomyConfig.FriendshipPolicy.FOLLOW_RESTRICTIONS) {
            return ShadowRestrictionPolicy.blocksFriendshipChanges(pokemon) ? false : null;
        }
        if (policy == ProgressionEconomyConfig.FriendshipPolicy.BLOCK) return false;
        // Absolute assignments also initialize loaded/captured Pokemon. Only attributed item
        // assignments represent earned changes; never turn codec restoration into a new reward.
        if (!ShadowFriendshipSources.isItem()) return null;
        long desired = Math.abs((long) value);
        if (desired > Integer.MAX_VALUE) return false;
        int delta = (int) desired - pokemon.getFriendship();
        return apply(pokemon, Math.abs(delta), delta >= 0, coerceSafe, policy);
    }

    private static boolean apply(Pokemon pokemon, int amount, boolean increase, boolean coerceSafe,
                                 ProgressionEconomyConfig.FriendshipPolicy policy) {
        var config = ProgressionEconomyConfig.INSTANCE;
        boolean buffering = policy == ProgressionEconomyConfig.FriendshipPolicy.BUFFER;
        int max = Math.max(0, Cobblemon.INSTANCE.getConfig().getMaxPokemonFriendship());
        int current = (int) Math.max(0L, Math.min(max, (long) pokemon.getFriendship() + (buffering ? buffer(pokemon) : 0)));
        int room = increase ? max - current : current;
        if (!coerceSafe && amount > room) return false;
        amount = Math.min(amount, room);
        double previousFraction = pokemon.getPersistentData().getDouble(FRACTION);
        double fraction = previousFraction;
        int accepted = amount;
        if (increase) {
            int threshold = ShadowFriendshipSources.isItem() ? config.itemFriendshipStartBelowPercent() : config.friendshipStartBelowPercent();
            if (!ShadowProgressionManager.belowThreshold(pokemon, threshold) || config.friendshipMultiplier() <= 0) return false;
            var gain = ShadowProgressionRules.friendshipGain(amount, config.friendshipMultiplier(), previousFraction, room);
            accepted = gain.accepted();
            fraction = gain.remainder();
        }
        if (accepted == 0 && fraction == previousFraction) return false;
        int next = current + (increase ? accepted : -accepted);
        if (buffering) {
            var event = new FriendshipUpdatedEvent(pokemon, next, increase);
            CobblemonEvents.FRIENDSHIP_UPDATED.emit(event);
            next = event.getNewFriendship();
            if (next == current && fraction == previousFraction) return false;
            setBuffer(pokemon, next - pokemon.getFriendship());
        }
        else {
            int nativeAmount = accepted;
            if (nativeAmount > 0) {
                boolean applied = ShadowFriendshipSources.withDirectAssignment(() -> increase
                        ? pokemon.incrementFriendship(nativeAmount, true) : pokemon.decrementFriendship(nativeAmount, true));
                if (!applied) return false;
            }
        }
        if (fraction > 0) pokemon.getPersistentData().putDouble(FRACTION, fraction);
        else pokemon.getPersistentData().remove(FRACTION);
        return true;
    }

    public static void release(Pokemon pokemon, boolean wasShadow) {
        if (!wasShadow) return;
        long total = (long) pokemon.getFriendship() + buffer(pokemon) + ProgressionEconomyConfig.INSTANCE.purificationFriendshipBonus();
        int next = (int) Math.max(0L, Math.min(Cobblemon.INSTANCE.getConfig().getMaxPokemonFriendship(), total));
        pokemon.getCustomProperties().removeIf(FriendshipBufferProperty.class::isInstance);
        pokemon.getPersistentData().remove(BUFFER);
        pokemon.getPersistentData().remove(FRACTION);
        int delta = next - pokemon.getFriendship();
        if (delta > 0) pokemon.incrementFriendship(delta, true);
        else if (delta < 0) pokemon.decrementFriendship(-delta, true);
    }
}
