package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import kotlin.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * AIBattleActor variant for entity-backed, player-owned Pokémon.
 * - Reports type PLAYER so PvW/PvP logic is correct and wild auto-heal does not apply to the owner side.
 * - Anchors world/position to the owner player if present, falling back to the Pokémon entity.
 * - Driven by a provided BattleAI (e.g., StrongBattleAI) with no UI.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture/attack/battle/hurt terms are gameplay mechanics.
 */
public class OwnedPokemonAIBattleActor extends AIBattleActor implements EntityBackedBattleActor<PokemonEntity> {

    private final BattlePokemon pokemon;
    private final Vec3 initialPos;

    public OwnedPokemonAIBattleActor(UUID uuid, BattlePokemon pokemon, BattleAI ai) {
        super(uuid, Collections.singletonList(pokemon), ai);
        this.pokemon = pokemon;
        Vec3 init = null;
        try {
            PokemonEntity e = pokemon.getEntity();
            if (e != null) init = e.position();
        } catch (Throwable ignored) {}
        this.initialPos = init;
    }

    @Override
    public ActorType getType() {
        return ActorType.PLAYER;
    }

    @Override
    public MutableComponent getName() {
        try {
            return pokemon.getEffectedPokemon().getDisplayName(false);
        } catch (Throwable t) {
            return Component.literal("Owned Pokémon");
        }
    }

    @Override
    public MutableComponent nameOwned(String name) {
        // For player actors, Showdown will already prefix/format; keep simple.
        return Component.literal(name);
    }

    @Override
    public PokemonEntity getEntity() {
        return pokemon.getEntity();
    }

    @Override
    public Vec3 getInitialPos() {
        return initialPos;
    }

    // Implemented for EntityBackedBattleActor, but some toolchains may not detect interface override from Java -> Kotlin.
    public Pair<ServerLevel, Vec3> getWorldAndPosition() {
        // Prefer anchoring to the owning player when present
        try {
            ServerPlayer owner = pokemon.getEffectedPokemon().getOwnerPlayer();
            if (owner != null) {
                return new Pair<>(owner.serverLevel(), owner.position());
            }
        } catch (Throwable ignored) { }
        try {
            PokemonEntity entity = getEntity();
            if (entity != null && entity.level() instanceof ServerLevel lvl) {
                return new Pair<>(lvl, entity.position());
            }
        } catch (Throwable ignored) { }
        return null;
    }

    @Override
    public void sendUpdate(NetworkPacket<?> packet) {
        super.sendUpdate(packet);
        // Mirror PokemonBattleActor behavior: clear entity battleId on battle end
        if (packet instanceof BattleEndPacket) {
            PokemonEntity entity = getEntity();
            if (entity != null) {
                entity.setBattleId(null);
            }
        }
    }

    @Override
    public Iterable<UUID> getPlayerUUIDs() {
        try {
            ServerPlayer owner = pokemon.getEffectedPokemon().getOwnerPlayer();
            if (owner != null) return List.of(owner.getUUID());
            // Fallback: attempt to parse original trainer UUID if present
            String ot = pokemon.getEffectedPokemon().getOriginalTrainer();
            if (ot != null && ot.length() >= 32) {
                try { return List.of(UUID.fromString(ot)); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) { }
        return List.of();
    }
}
