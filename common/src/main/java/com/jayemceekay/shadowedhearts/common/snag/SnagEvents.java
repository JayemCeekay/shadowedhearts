// common/src/main/java/com/jayemceekay/shadowedhearts/snag/SnagEvents.java
package com.jayemceekay.shadowedhearts.common.snag;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.snag.SnagArmedPacket;
import com.jayemceekay.shadowedhearts.network.snag.SnagEligibilityPacket;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

public final class SnagEvents {
    private static final Map<Player, EligibilityState> LAST_SYNCED_ELIGIBILITY = new WeakHashMap<>();
    private SnagEvents() {}

    /** Call once during common init on both loaders. */
    public static void init() {
        // Runs after each player's tick on both client and server.
        TickEvent.PLAYER_POST.register(SnagEvents::onPlayerPostTick);

        // Defeating a wild Shadow remains progression even without a Snag Machine.
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, (BattleVictoryEvent e) -> {
            boolean wildShadowDefeated = false;
            for (BattleActor loser : e.getLosers()) {
                if (loser == null || loser.getType() != ActorType.WILD) continue;
                for (var pokemon : loser.getPokemonList()) {
                    if (pokemon.getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)) {
                        wildShadowDefeated = true;
                        break;
                    }
                }
            }

            if (wildShadowDefeated) {
                for (BattleActor winner : e.getWinners()) {
                    if (winner instanceof PlayerBattleActor pba && pba.getEntity() != null) {
                        ModCriteriaTriggers.triggerWildShadowDefeated(pba.getEntity());
                    }
                }
            }
            return kotlin.Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, (PokemonCapturedEvent e) -> {
            if (SnagCaptureProvenance.consumeSuccessfulNpcSnag(e)) {
                ModCriteriaTriggers.triggerSnagFromNpc(e.getPlayer());
            }
            if (e.getPokemon().getAspects().contains(SHAspects.SHADOW)) {
                if (!e.getPokeBallEntity().getCaptureFuture().isCompletedExceptionally()
                        && Boolean.TRUE.equals(e.getPokeBallEntity().getCaptureFuture().getNow(false))) {
                    ModCriteriaTriggers.triggerProgression(e.getPlayer(), "shadow_captured");
                }
                // Remove battleClone and uncatchable properties so the Pokemon is functional for the player
                e.getPokemon().getCustomProperties().removeIf(p -> p.asString().equals("battleClone") || p.asString().equals("uncatchable"));
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private static void onPlayerPostTick(Player player) {
        if (player == null || player.level().isClientSide) return;
        var cap = SnagCaps.get(player);
        int cd = cap.cooldown();
        if (cd > 0) cap.setCooldown(cd - 1);

        // Sync snag eligibility to client
        if (player instanceof ServerPlayer sp) {
            boolean eligible = SnagBattleUtil.canShowSnagButton(sp);
            int rechargeTurns = SnagMachineBenefits.rechargeTurnsRemaining(sp);
            EligibilityState next = new EligibilityState(eligible, rechargeTurns);
            if (!next.equals(LAST_SYNCED_ELIGIBILITY.get(sp))) {
                cap.setLastSyncedEligibility(eligible);
                LAST_SYNCED_ELIGIBILITY.put(sp, next);
                ShadowedHeartsNetwork.sendToPlayer(sp, new SnagEligibilityPacket(eligible, rechargeTurns));
            }
        }

        // Disarm when leaving battle
        if (cap.isArmed() && (!SnagBattleUtil.isInTrainerBattle(player) || !SnagMachineBenefits.canAttempt(player))) {
            cap.setArmed(false);
            if (player instanceof ServerPlayer sp) {
                ShadowedHeartsNetwork.sendToPlayer(sp, new SnagArmedPacket(false));
            }
        } else if (cap.isArmed()) {
            // Also disarm at the end of the player's turn if they didn't throw a Poké Ball.
            // We approximate end-of-turn by when the actor cannot fit a forced action anymore
            // (i.e., they've already committed their action for this turn).
            if (player instanceof ServerPlayer sp) {
                var battle = BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID());
                if (battle != null) {
                    var actor = battle.getActor(sp);
                    if (actor != null) {
                        try {
                            if (!actor.canFitForcedAction()) {
                                cap.setArmed(false);
                                ShadowedHeartsNetwork.sendToPlayer(sp, new SnagArmedPacket(false));
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    private record EligibilityState(boolean eligible, int rechargeTurns) {}
}
