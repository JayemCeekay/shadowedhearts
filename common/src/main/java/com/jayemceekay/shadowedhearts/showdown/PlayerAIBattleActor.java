package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemon.mod.common.util.LocalizationUtilsKt;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

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
public class PlayerAIBattleActor extends AIBattleActor implements EntityBackedBattleActor<ServerPlayer> {

    private final BattlePokemon pokemon;
    private final Vec3 initialPos;

    public PlayerAIBattleActor(UUID uuid, BattlePokemon pokemon, BattleAI ai) {
        super(uuid, Collections.singletonList(pokemon), ai);
        this.pokemon = pokemon;
        this.initialPos = pokemon.getEntity().position();
    }


    @Override
    public ActorType getType() {
        return ActorType.PLAYER;
    }

    @Override
    public MutableComponent getName() {
        return this.getEntity().getName().copy();
    }

    @Override
    public MutableComponent nameOwned(String name) {
        return LocalizationUtilsKt.battleLang("owned_pokemon", this.getName(), name);
    }

    @Override
    public ServerPlayer getEntity() {
        return pokemon.getEffectedPokemon().getOwnerPlayer();
    }

    @Override
    public Vec3 getInitialPos() {
        return initialPos;
    }

    @Override
    public void sendUpdate(NetworkPacket<?> packet) {
        super.sendUpdate(packet);
        // Mirror PokemonBattleActor behavior: clear entity battleId on battle end
        if (packet instanceof BattleEndPacket) {
            if (pokemon.getEntity() != null) {
              pokemon.getEntity().setBattleId(null);
            }
        }
    }

    @Override
    public void awardExperience(@NotNull BattlePokemon battlePokemon, int experience) {
        if(battle.isPvP() && !Cobblemon.config.getAllowExperienceFromPvP()) {
            return;
        }

        BattleExperienceSource source = new BattleExperienceSource(battle, battlePokemon.getFacedOpponents().stream().toList());
        if(battlePokemon.getEffectedPokemon() == battlePokemon.getOriginalPokemon() && experience > 0) {
            battlePokemon.getEffectedPokemon().addExperienceWithPlayer(getEntity(), source, experience);
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
