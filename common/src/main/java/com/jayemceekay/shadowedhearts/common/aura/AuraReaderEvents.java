package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.jayemceekay.shadowedhearts.config.ISnagConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.content.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderPulseResultS2CPacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraScannerS2CPacket;
import com.jayemceekay.shadowedhearts.registry.util.ModItemComponents;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class AuraReaderEvents {
    private AuraReaderEvents() {}

    public static void init() {
        TickEvent.PLAYER_POST.register(AuraReaderEvents::onPlayerPostTick);

        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, (BattleVictoryEvent e) -> {
            ISnagConfig cfg = ShadowedHeartsConfigs.getInstance().getSnagConfig();
            if (!cfg.auraReaderRechargeOnVictory()) return kotlin.Unit.INSTANCE;
            var battle = e.getBattle();
            if (battle == null) return kotlin.Unit.INSTANCE;
            if (battle.isPvP() && !cfg.auraReaderRechargeInPvp()) return kotlin.Unit.INSTANCE;

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

            if (nonPlayerActorCount <= 0 && !battle.isPvP()) return kotlin.Unit.INSTANCE;

            double avgLevel = levelCount > 0 ? (double) levelSum / (double) levelCount : 1.0;
            double raw = cfg.auraReaderRechargeBase()
                    + (avgLevel * cfg.auraReaderRechargePerLevel())
                    + (nonPlayerActorCount * cfg.auraReaderRechargePerNpc());
            int award = (int) Math.round(raw);
            award = Math.max(cfg.auraReaderRechargeMin(), Math.min(award, cfg.auraReaderRechargeMax()));
            if (award <= 0) return kotlin.Unit.INSTANCE;

            for (BattleActor winner : e.getWinners()) {
                if (winner == null || winner.getType() != ActorType.PLAYER) continue;
                if (!(winner instanceof PlayerBattleActor pba)) continue;
                ServerPlayer sp = pba.getEntity();
                if (sp == null) continue;

                ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(sp);

                if (!auraReader.isEmpty()) {
                    int before = AuraReaderCharge.get(auraReader);
                    AuraReaderCharge.add(auraReader, award, AuraReaderItem.MAX_CHARGE);
                    int after = AuraReaderCharge.get(auraReader);
                    int gained = Math.max(0, after - before);
                    if (gained > 0) {
                        sp.sendSystemMessage(Component.translatable(
                                "message.shadowedhearts.aura_reader.recharged",
                                gained, after
                        ));
                    }
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private static void onPlayerPostTick(Player player) {
        if (player == null || player.level().isClientSide) return;

        // Aura Reader charge drain
        ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player);

        if (!auraReader.isEmpty()) {
            tickAuraReader(player, auraReader);
            if (player instanceof ServerPlayer sp && sp.tickCount % 10 == 0) {
                AuraReaderService.validateLock(sp, auraReader);
                if (AuraReaderPlayerState.isActive(sp)) {
                    ShadowedHeartsNetwork.sendToPlayer(
                            sp,
                            AuraReaderPulseResultS2CPacket.fromReadings(AuraReaderService.targetedScan(sp, auraReader), false)
                    );
                }
                AuraReaderService.sendState(sp, auraReader);
            }
        } else if (player instanceof ServerPlayer sp) {
            if (AuraReaderPlayerState.isActive(sp)) {
                AuraReaderService.setActive(sp, ItemStack.EMPTY, false);
            }
        }
    }

    private static void tickAuraReader(Player player, ItemStack stack) {
        Boolean active = stack.get(ModItemComponents.AURA_SCANNER_ACTIVE.get());
        boolean isActive = active != null && active;
        if (player instanceof ServerPlayer sp && AuraReaderPlayerState.isActive(sp) != isActive) {
            AuraReaderService.syncActiveFromStack(sp, stack);
        }

        if (isActive) {
            if (!(player instanceof ServerPlayer sp)) return;
            boolean hasCharge = AuraReaderService.consumeActiveCharge(sp, stack);
            if (!hasCharge) {
                ShadowedHeartsNetwork.sendToPlayer(sp, new AuraScannerS2CPacket(false));
            }
        }
    }
}
