package com.jayemceekay.shadowedhearts.common.snag;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig;
import com.necro.asymmetric.battles.common.api.actor.DummyBattleActor;
import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-safe utilities for determining trainer battle context and eligible shadow opponents.
 * 02 §5 Mission Entrance flow — Trainer battles only gating for Snag.
 */
public final class SnagBattleUtil {
    private SnagBattleUtil() {}

    /** Returns the Cobblemon battle for the player if present, else null. Server-only helper. */
    public static PokemonBattle getBattle(Player player) {
        if (!(player instanceof ServerPlayer sp)) return null;
        try {
            return BattleRegistry.getBattleByParticipatingPlayer(sp);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True if player is currently in a trainer battle (PvN) per Cobblemon. Null-safe, server-only. */
    public static boolean isInTrainerBattle(Player player) {
        PokemonBattle battle = getBattle(player);
        if (battle == null) return false;
        if (battle.isPvN()) return true;
        if (!Platform.isModLoaded("asymmetricbattles")) return false;
        var actor = battle.getActor((ServerPlayer) player);
        return actor != null && hasOnlyParticipantsOfType(actor.getSide(), ActorType.PLAYER)
                && hasOnlyParticipantsOfType(actor.getSide().getOppositeSide(), ActorType.NPC);
    }

    private static boolean hasOnlyParticipantsOfType(BattleSide side, ActorType type) {
        boolean foundParticipant = false;
        for (var actor : side.getActors()) {
            if (actor != null && actor.getType() == type) {
                foundParticipant = true;
                continue;
            }
            // ABA reserves late-join slots with empty actors whose reported type is WILD.
            if (!Platform.isModLoaded("asymmetricbattles") || !(actor instanceof DummyBattleActor)) return false;
        }
        return foundParticipant;
    }

    /**
     * True if the opposing side has at least one catchable Shadow Pokémon currently active or on the roster.
     * Null-safe, server-only.
     */
    public static boolean hasEligibleShadowOpponent(Player player) {
        PokemonBattle battle = getBattle(player);
        if (battle == null) return false;
        // Find player's actor side
        var actor = battle.getActor((ServerPlayer) player);
        if (actor == null) return false;
        var otherSide = actor.getSide().getOppositeSide();

        // Opponents must be NPCs for our trainer-battle gating
        if (!hasOnlyParticipantsOfType(otherSide, ActorType.NPC)) return false;

        // First check active opponents
        for (ActiveBattlePokemon abp : otherSide.getActivePokemon()) {
            if (abp == null || abp.getBattlePokemon() == null) continue;
            var effected = abp.getBattlePokemon().getEffectedPokemon();
            var aspects = effected.getAspects();
            var entity = effected.getEntity();
            if (aspects != null && aspects.contains(SHAspects.SHADOW)) {
                if (!(entity instanceof PokemonEntity pe) || UncatchableProperty.INSTANCE.isCatchable(pe)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Client-visible predicate to determine whether to show Snag button. Server authoritative. */
    public static boolean canShowSnagButton(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) {
            return com.jayemceekay.shadowedhearts.client.snag.ClientSnagState.isEligible();
        }
        if (!GameplayTuningConfig.INSTANCE.snaggingEnabled()) return false;
        if (!SnagCaps.hasMachineAvailable(player)) return false;
        if (!isInTrainerBattle(player)) return false;
        return hasEligibleShadowOpponent(player);
    }
}
