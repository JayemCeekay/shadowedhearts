// common/src/main/java/com/jayemceekay/shadowedhearts/snag/SnagEvents.java
package com.jayemceekay.shadowedhearts.snag;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class SnagEvents {
    private SnagEvents() {}

    /** Call once during common init on both loaders. */
    public static void init() {
        // Runs after each player's tick on both client and server.
        TickEvent.PLAYER_POST.register(SnagEvents::onPlayerPostTick);

        // Recharge Snag Machines on battle victory, scaled by defeated opponents' difficulty
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, (BattleVictoryEvent e) -> {
            if (!SnagConfig.RECHARGE_ON_VICTORY) return kotlin.Unit.INSTANCE;
            var battle = e.getBattle();
            if (battle == null) return kotlin.Unit.INSTANCE;
            if (battle.isPvP() && !SnagConfig.RECHARGE_IN_PVP) return kotlin.Unit.INSTANCE;

            // Compute difficulty from non-player losers
            int nonPlayerActorCount = 0;
            int levelSum = 0;
            int levelCount = 0;
            for (BattleActor loser : e.getLosers()) {
                if (loser == null) continue;
                if (loser.getType() == ActorType.PLAYER) continue;
                nonPlayerActorCount++;
                var list = loser.getPokemonList();
                if (list == null) continue;
                for (var bp : list) {
                    if (bp == null) continue;
                    try {
                        int lvl = bp.getEffectedPokemon().getLevel();
                        if (lvl > 0) {
                            levelSum += lvl;
                            levelCount++;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (nonPlayerActorCount <= 0 && !battle.isPvP()) {
                // Nothing meaningful to scale against (e.g., empty losers); skip non-PvP edge cases
                return kotlin.Unit.INSTANCE;
            }

            double avgLevel = levelCount > 0 ? (double) levelSum / (double) levelCount : 1.0;
            double raw = SnagConfig.RECHARGE_BASE
                    + (avgLevel * SnagConfig.RECHARGE_PER_LEVEL)
                    + (nonPlayerActorCount * SnagConfig.RECHARGE_PER_NPC);
            int award = (int) Math.round(raw);
            award = Math.max(SnagConfig.RECHARGE_MIN, Math.min(award, SnagConfig.RECHARGE_MAX));
            if (award <= 0) return kotlin.Unit.INSTANCE;

            // Award to each winning player with a Snag Machine
            for (BattleActor winner : e.getWinners()) {
                if (winner == null || winner.getType() != ActorType.PLAYER) continue;
                if (!(winner instanceof PlayerBattleActor pba)) continue;
                ServerPlayer sp = pba.getEntity();
                if (sp == null) continue;
                var cap = SnagCaps.get(sp);
                if (!cap.hasSnagMachine()) continue;
                int before = cap.energy();
                cap.addEnergy(award);
                int after = cap.energy();
                int gained = Math.max(0, after - before);
                if (gained > 0) {
                    sp.sendSystemMessage(Component.translatable(
                            "message.shadowedhearts.snag_machine.recharged",
                            gained, after
                    ));
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private static void onPlayerPostTick(Player player) {
        if (player == null || player.level().isClientSide) return;
        var cap = SnagCaps.get(player);
        int cd = cap.cooldown();
        if (cd > 0) cap.setCooldown(cd - 1);
        // Disarm when leaving battle
        if (cap.isArmed() && !SnagBattleUtil.isInTrainerBattle(player)) {
            cap.setArmed(false);
            // minimal debug log could be added here if needed
        } else if (cap.isArmed()) {
            // Also disarm at the end of the player's turn if they didn't throw a Poké Ball.
            // We approximate end-of-turn by when the actor cannot fit a forced action anymore
            // (i.e., they've already committed their action for this turn).
            if (player instanceof ServerPlayer sp) {
                var battle = SnagBattleUtil.getBattle(player);
                if (battle != null) {
                    var actor = battle.getActor(sp);
                    if (actor != null) {
                        try {
                            if (!actor.canFitForcedAction()) {
                                cap.setArmed(false);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }
}
