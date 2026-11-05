package com.jayemceekay.shadowedhearts.common.snag;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-owned turn recovery and immutable tier snapshots for launched snag balls. */
public final class SnagMachineBenefits {
    private static final Map<PokemonBattle, Map<UUID, Integer>> LAST_ATTEMPTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<EmptyPokeBallEntity, SnagMachineTier> THROW_TIERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SnagMachineBenefits() {}

    public static SnagMachineTier selectedTier(Player player) {
        return new SimplePlayerSnagData(player).getMachineStack().is(ModItems.SNAG_MACHINE_ADVANCED.get())
                ? SnagMachineTier.ADVANCED : SnagMachineTier.PROTOTYPE;
    }

    public static void snapshotThrow(Player player, EmptyPokeBallEntity ball) {
        if (!ball.level().isClientSide) THROW_TIERS.put(ball, selectedTier(player));
    }

    public static SnagMachineTier throwTier(EmptyPokeBallEntity ball) {
        return THROW_TIERS.getOrDefault(ball, SnagMachineTier.PROTOTYPE);
    }

    public static boolean canAttempt(Player player) {
        if (!GameplayTuningConfig.INSTANCE.snaggingEnabled()) return false;
        PokemonBattle battle = SnagBattleUtil.getBattle(player);
        return battle != null && remainingTurns(player, battle, selectedTier(player)) == 0;
    }

    public static boolean canAttempt(Player player, PokemonBattle battle, EmptyPokeBallEntity ball) {
        if (!GameplayTuningConfig.INSTANCE.snaggingEnabled()) return false;
        return remainingTurns(player, battle, throwTier(ball)) == 0;
    }

    public static int rechargeTurnsRemaining(Player player) {
        PokemonBattle battle = SnagBattleUtil.getBattle(player);
        return battle == null ? 0 : remainingTurns(player, battle, selectedTier(player));
    }

    private static int remainingTurns(Player player, PokemonBattle battle, SnagMachineTier tier) {
        Map<UUID, Integer> attempts = LAST_ATTEMPTS.get(battle);
        Integer lastTurn = attempts == null ? null : attempts.get(player.getUUID());
        return lastTurn == null ? 0 : Math.max(0, tier.nextEligibleTurn(lastTurn) - battle.getTurn());
    }

    /** Called only when an eligible NPC Shadow capture action is committed. */
    static void recordAttempt(ServerPlayer player, PokemonBattle battle) {
        LAST_ATTEMPTS.computeIfAbsent(battle, ignored -> new HashMap<>())
                .put(player.getUUID(), battle.getTurn());
    }
}
