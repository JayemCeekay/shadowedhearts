package com.jayemceekay.shadowedhearts.common.snag;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-only capture origin retained across Cobblemon's capture-action cleanup. */
public final class SnagCaptureProvenance {
    private static final Map<EmptyPokeBallEntity, Origin> ORIGINS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SnagCaptureProvenance() {}

    public static void recordAttempt(BattleCaptureAction action) {
        EmptyPokeBallEntity ball = action.getPokeBallEntity();
        if (ORIGINS.containsKey(ball)) return;
        if (ball.level().isClientSide || ball.getCaptureFuture().isDone()
                || !ball.getAspects().contains("snag_ball")
                || !(ball.getOwner() instanceof ServerPlayer player)) return;

        var target = action.getTargetPokemon();
        var pokemon = target.getBattlePokemon();
        var thrower = action.getBattle().getActor(player);
        if (!(thrower instanceof PlayerBattleActor) || target.getActor().getType() != ActorType.NPC
                || target.getSide() != thrower.getSide().getOppositeSide()
                || pokemon == null || pokemon.getOriginalPokemon().isPlayerOwned()
                || pokemon.getEffectedPokemon().isPlayerOwned()
                || !pokemon.getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)
                || !SnagMachineBenefits.canAttempt(player, action.getBattle(), ball)) return;

        SnagMachineTier tier = SnagMachineBenefits.throwTier(ball);
        SnagMachineBenefits.recordAttempt(player, action.getBattle());
        ORIGINS.put(ball, new Origin(player.getUUID(), pokemon.getEffectedPokemon().getUuid(), tier.settings()));
        if (com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs.getInstance().getSnagConfig().deferDeliveryUntilVictory()) {
            DeferredSnagDelivery.rememberAttempt(ball, action.getBattle());
        }
        ball.getCaptureFuture().whenComplete((successful, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(successful)) ORIGINS.remove(ball);
        });
    }

    /** The bonus belongs to the committed NPC snag, never to a later equipment selection or wild catch. */
    public static double catchRateMultiplier(PokemonCatchRateEvent event) {
        Origin origin = ORIGINS.get(event.getPokeBallEntity());
        return origin != null && origin.player().equals(event.getThrower().getUUID())
                && origin.pokemon().equals(event.getPokemonEntity().getPokemon().getUuid())
                && event.getPokeBallEntity().getOwner() instanceof ServerPlayer owner
                && origin.player().equals(owner.getUUID())
                ? origin.settings().catchMultiplier() : 1.0;
    }

    public static boolean consumeSuccessfulNpcSnag(PokemonCapturedEvent event) {
        EmptyPokeBallEntity ball = event.getPokeBallEntity();
        Origin origin = ORIGINS.remove(ball);
        // In 1.8.1 the future finishes before effects, party insertion and this event. The battle
        // action may already be gone, and effects may have converted an ordinary catch to Shadow.
        boolean successful = origin != null
                && !ball.getCaptureFuture().isCompletedExceptionally()
                && Boolean.TRUE.equals(ball.getCaptureFuture().getNow(false))
                && origin.player().equals(event.getPlayer().getUUID())
                && origin.pokemon().equals(event.getPokemon().getUuid())
                && ball.getOwner() instanceof ServerPlayer owner
                && origin.player().equals(owner.getUUID());
        if (successful && origin.settings().gaugeReductionPercent() > 0.0
                && ShadowAspectUtil.hasShadowAspect(event.getPokemon())) {
            int current = ShadowHeartGauge.getHeartGaugeMeter(event.getPokemon());
            ShadowService.setHeartGauge(event.getPokemon(), event.getPokemon().getEntity(),
                    origin.settings().remainingGaugeAfterCapture(current));
        }
        return successful;
    }

    private record Origin(UUID player, UUID pokemon, SnagMachineSettings.TierSettings settings) {}
}
