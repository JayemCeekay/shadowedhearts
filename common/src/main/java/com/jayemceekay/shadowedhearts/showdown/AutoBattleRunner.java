package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Wires overworld melee interactions into a Cobblemon battle powered by Strong NPC AI.
 * Also supports a player proxy target to allow wild Pokémon to "hit" players via micro battles.
 * <p>
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class AutoBattleRunner {
    private AutoBattleRunner() {
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, Long> LAST_START = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 150L;

    private static long pairKey(int aId, int bId) {
        return (((long) aId) << 32) ^ (bId & 0xFFFFFFFFL);
    }

    private static boolean isDebouncedAndMark(int aId, int bId) {
        final long now = System.currentTimeMillis();
        final long key = pairKey(aId, bId);
        Long prev = LAST_START.get(key);
        if (prev != null && (now - prev) < COOLDOWN_MS) return true;
        LAST_START.put(key, now);
        return false;
    }

    /**
     * Fire-and-forget micro battle when attacker swings at defender in the overworld.
     */
    public static void fire(PokemonEntity attacker, PokemonEntity defender) {
        if (attacker == null || defender == null) return;
        if (!(attacker.level() instanceof ServerLevel)) return;
        if (attacker.isBattling() || defender.isBattling()) {
            return;
        }
        if (!attacker.isAlive() || !defender.isAlive()) return;
        if (isDebouncedAndMark(attacker.getId(), defender.getId())) return;
        try {
            if (attacker.getBattleId() != null || defender.getBattleId() != null) return;
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }

        try {

            // Build AI actors (no players) so no UI is shown; set fleeDistance to -1 to avoid auto-flee.
            var atkBP = new BattlePokemon(attacker.getPokemon(), attacker.getPokemon(), pokemonEntity -> Unit.INSTANCE);

            var defBP = new BattlePokemon(defender.getPokemon(), defender.getPokemon(), pokemonEntity -> Unit.INSTANCE);

            BattleActor atkActor = null;
            if(atkBP.getOriginalPokemon().isPlayerOwned()) {
                atkActor = new PlayerAIBattleActor(attacker.getPokemon().getOwnerUUID(), atkBP, new StrongBattleAI(5));
            } else if(atkBP.getOriginalPokemon().isNPCOwned() || atkBP.getOriginalPokemon().isWild()) {
                atkActor = new PokemonBattleActor(attacker.getPokemon().getUuid(), atkBP, -1F, new RandomBattleAI());
            }

            BattleActor defActor = null;
            if(defender.getPokemon().isPlayerOwned()) {
                defActor = new PlayerAIBattleActor(defender.getPokemon().getOwnerUUID(), defBP, new StrongBattleAI(5));
            } else if(defender.getPokemon().isNPCOwned() || defender.getPokemon().isWild()) {
                defActor = new PokemonBattleActor(defender.getPokemon().getUuid(), defBP, -1F, new RandomBattleAI());
            }

            // Start an actual Cobblemon battle; Showdown service underneath will drive mechanics+particles.
            BattleRegistry.INSTANCE.startBattle(
                    BattleFormat.Companion.getGEN_9_SINGLES(),
                    new BattleSide(atkActor),
                    new BattleSide(defActor),
                    true
            ).ifSuccessful(battle -> {
                AutoBattleController.mark(battle.getBattleId());
                return kotlin.Unit.INSTANCE;
            });

            // Basic self-defense: if defender has no attack target, set it to the attacker (outside of battle cases)
            tryMarkDefend(defender, attacker);
        } catch (Throwable t) {
            t.printStackTrace();
            // Swallow to avoid crashing the server tick; can add a logger if desired
        }
    }

    /**
     * Starts a one-turn micro battle between a wild/NPC Pokémon and a player proxy.
     * The proxy's HP is proportionally mapped to the player's HP, and damage dealt
     * in the micro battle is translated back to the player's Minecraft health.
     */
    public static void fireAgainstPlayer(PokemonEntity attacker, ServerPlayer player) {
        if (attacker == null || player == null) return;
        if (!(attacker.level() instanceof ServerLevel)) return;
        if (!attacker.isAlive() || player.isDeadOrDying()) return;
        if (player.isSpectator() || player.isCreative()) return;
        try {
            if (((ServerLevel) attacker.level()).getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        } catch (Throwable ignored) {
        }
        if (isDebouncedAndMark(attacker.getId(), player.getId())) return;

        // Do not start if either is already battling
        try {
            if (attacker.getBattleId() != null) return;
            if (com.cobblemon.mod.common.battles.BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) != null)
                return;
        } catch (Throwable ignored) {
        }

        try {
            // Build attacker actor
            var atkBP = BattlePokemon.Companion.playerOwned(attacker.getPokemon());
            // Use PLAYER-typed AI actor for owned attacker to avoid wild auto-heal and enable proper PvW semantics
            com.cobblemon.mod.common.pokemon.OriginalTrainerType otType2 = attacker.getPokemon().getOriginalTrainerType();
            boolean isOwned2 = otType2 == com.cobblemon.mod.common.pokemon.OriginalTrainerType.PLAYER || attacker.getPokemon().getOwnerPlayer() != null;
            var atkActor = isOwned2
                    ? new PlayerAIBattleActor(attacker.getPokemon().getUuid(), atkBP, new StrongBattleAI(5))
                    : new PokemonBattleActor(attacker.getPokemon().getUuid(), atkBP, -1F, new StrongBattleAI(5));

            // Create a proxy Pokémon to represent the player, and anchor visuals to the player by setting OT
            Pokemon proxy = new Pokemon();
            try {
                proxy.setOriginalTrainer(player.getUUID());
            } catch (Throwable ignored) {
            }

            // Map player HP ratio to proxy HP
            float playerMax = player.getMaxHealth();
            float playerCur = player.getHealth();
            float ratio = playerMax > 0 ? (playerCur / playerMax) : 1F;
            int proxyMax = proxy.getMaxHealth();
            int startHP = Math.max(1, Math.round(ratio * proxyMax));
            try {
                proxy.setCurrentHealth(startHP);
            } catch (Throwable ignored) {
            }

            var proxyBP = BattlePokemon.Companion.playerOwned(proxy);
            var proxyActor = new PokemonBattleActor(player.getUUID(), proxyBP, -1F, new NoOpBattleAI());

            // Start the battle and mark for one-turn close; on end, translate HP delta back to player damage
            BattleRegistry.INSTANCE.startBattle(
                    BattleFormat.Companion.getGEN_9_SINGLES(),
                    new BattleSide(atkActor),
                    new BattleSide(proxyActor),
                    true
            ).ifSuccessful(battle -> {
                AutoBattleController.mark(battle.getBattleId());
                // Capture by value for handler
                final int proxyStartHP = proxy.getCurrentHealth();
                final int proxyMaxHP = proxy.getMaxHealth();
                battle.getOnEndHandlers().add(b -> {
                    int endHP = proxy.getCurrentHealth();
                    int delta = Math.max(0, proxyStartHP - endHP);
                    if (delta > 0 && proxyMaxHP > 0) {
                        float hearts = (delta / (float) proxyMaxHP) * player.getMaxHealth();
                        try {
                            player.hurt(player.damageSources().mobAttack(attacker), Math.max(0.0F, hearts));
                        } catch (Throwable ignored) {
                        }
                    }
                    return kotlin.Unit.INSTANCE;
                });
                return kotlin.Unit.INSTANCE;
            });
        } catch (Throwable t) {
            // Avoid crashing server tick
        }
    }

    private static void tryMarkDefend(PokemonEntity defender, LivingEntity attacker) {
        try {
            defender.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
        } catch (Throwable ignored) {
        }
    }
}
