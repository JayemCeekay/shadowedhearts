package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** Scent calming is distinct from Call: it never awards Call progress or an accuracy boost. */
public final class ScentModeRecovery {
    private ScentModeRecovery() {}

    public static boolean hasMode(Pokemon pokemon) {
        return pokemon.getAspects().contains(SHAspects.HYPER_MODE) || pokemon.getAspects().contains(SHAspects.REVERSE_MODE)
                || pokemon.getForcedAspects().contains(SHAspects.HYPER_MODE) || pokemon.getForcedAspects().contains(SHAspects.REVERSE_MODE);
    }

    public static boolean calm(Pokemon pokemon, PokemonEntity live) {
        if (!hasMode(pokemon)) return false;
        PokemonBattle battle = live.getBattleId() != null ? BattleRegistry.getBattle(live.getBattleId()) : null;
        if (battle == null && pokemon.getOwnerPlayer() != null) {
            battle = BattleRegistry.getBattleByParticipatingPlayer(pokemon.getOwnerPlayer());
        }
        Set<Pokemon> affected = Collections.newSetFromMap(new IdentityHashMap<>());
        affected.add(pokemon);
        if (battle != null && !battle.getEnded()) {
            boolean found = false;
            for (var actor : battle.getActors()) for (var member : actor.getPokemonList()) {
                if (member.getUuid().equals(pokemon.getUuid())) {
                    affected.add(member.getOriginalPokemon());
                    affected.add(member.getEffectedPokemon());
                    found = true;
                }
            }
            if (found) battle.writeShowdownAction(">shcalm " + pokemon.getUuid());
        }
        for (Pokemon target : affected) {
            Set<String> aspects = new HashSet<>(target.getAspects());
            aspects.remove(SHAspects.HYPER_MODE);
            aspects.remove(SHAspects.REVERSE_MODE);
            target.setForcedAspects(aspects);
            target.updateAspects();
            ShadowService.syncAll(target);
        }
        return true;
    }
}
