package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.UUID;

/** Viewer-relative ownership used to approve scan details on the server. */
public enum AuraScanOwnership {
    SELF_OWNED,
    OTHER_TRAINER_OWNED,
    WILD;

    public static AuraScanOwnership resolve(UUID viewerId, PokemonEntity entity) {
        Pokemon scanned = entity.getPokemon();
        var battle = entity.getBattle();
        if (battle != null) {
            for (var actor : battle.getActors()) {
                for (var battlePokemon : actor.getPokemonList()) {
                    if (battlePokemon.getEffectedPokemon().getUuid().equals(scanned.getUuid())) {
                        // Cloned battle teams have no store coordinates; their originals retain ownership.
                        Pokemon original = battlePokemon.getOriginalPokemon();
                        return classifyBattle(viewerId, original.getOwnerUUID(), original.isWild(),
                                actor.getType(), actor.getPlayerUUIDs());
                    }
                }
            }
        }
        return classify(viewerId, scanned.getOwnerUUID(), scanned.isWild());
    }

    static AuraScanOwnership classify(UUID viewerId, UUID ownerId, boolean wild) {
        if (ownerId != null && ownerId.equals(viewerId)) {
            return SELF_OWNED;
        }
        // Custom trainer stores may not have a recognized owner UUID.
        return ownerId != null || !wild ? OTHER_TRAINER_OWNED : WILD;
    }

    static AuraScanOwnership classifyBattle(UUID viewerId, UUID ownerId, boolean wild,
                                           ActorType actorType, Iterable<UUID> actorPlayerIds) {
        AuraScanOwnership ownership = classify(viewerId, ownerId, wild);
        if (ownership != WILD) {
            return ownership;
        }
        // Generated trainer teams, including injected pool Pokemon, may have no original store.
        if (actorType == ActorType.NPC) {
            return OTHER_TRAINER_OWNED;
        }
        if (actorType == ActorType.PLAYER) {
            if (viewerId != null && actorPlayerIds != null) {
                for (UUID playerId : actorPlayerIds) {
                    if (viewerId.equals(playerId)) {
                        return SELF_OWNED;
                    }
                }
            }
            return OTHER_TRAINER_OWNED;
        }
        return WILD;
    }

    public AuraHeartGaugeReadout heartGauge(int current, int maximum) {
        return switch (this) {
            case SELF_OWNED -> AuraHeartGaugeReadout.exact(current, maximum);
            case OTHER_TRAINER_OWNED -> AuraHeartGaugeReadout.banded(current, maximum);
            case WILD -> AuraHeartGaugeReadout.hidden();
        };
    }

}
