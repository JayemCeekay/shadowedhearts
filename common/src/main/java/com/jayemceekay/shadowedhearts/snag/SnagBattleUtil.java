package com.jayemceekay.shadowedhearts.snag;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.SHAspects;
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
            return BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(sp);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True if player is currently in a trainer battle (PvN) per Cobblemon. Null-safe, server-only. */
    public static boolean isInTrainerBattle(Player player) {
        PokemonBattle battle = getBattle(player);
        return battle != null && battle.isPvN();
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
        boolean allNpc = true;
        for (var a : otherSide.getActors()) {
            if (a == null || a.getType() != ActorType.NPC) { allNpc = false; break; }
        }
        if (!allNpc) return false;

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
        // Fallback: scan roster
        for (var a : otherSide.getActors()) {
            if (a == null || a.getPokemonList() == null) continue;
            for (var bp : a.getPokemonList()) {
                if (bp == null) continue;
                var effected = bp.getEffectedPokemon();
                var aspects = effected.getAspects();
                if (aspects == null || !aspects.contains(SHAspects.SHADOW)) continue;
                var entity = effected.getEntity();
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
        if (!SnagCaps.hasMachineAvailable(player)) return false;
        if (!isInTrainerBattle(player)) return false;
        return hasEligibleShadowOpponent(player);
    }
}
