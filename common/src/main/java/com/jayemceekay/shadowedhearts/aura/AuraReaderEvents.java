package com.jayemceekay.shadowedhearts.aura;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.jayemceekay.shadowedhearts.config.ISnagConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModItemComponents;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.network.AuraScannerS2CPacket;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
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

                ItemStack auraReader = ItemStack.EMPTY;
                ItemStack helmet = sp.getItemBySlot(EquipmentSlot.HEAD);
                if (!helmet.isEmpty() && helmet.getItem() instanceof AuraReaderItem) {
                    auraReader = helmet;
                } else {
                    ItemStack accessory = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(sp);
                    if (!accessory.isEmpty() && accessory.getItem() instanceof AuraReaderItem) {
                        auraReader = accessory;
                    }
                }

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
        ItemStack auraReader = ItemStack.EMPTY;
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty() && helmet.getItem() instanceof AuraReaderItem) {
            auraReader = helmet;
        } else {
            ItemStack accessory = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(player);
            if (!accessory.isEmpty() && accessory.getItem() instanceof AuraReaderItem) {
                auraReader = accessory;
            }
        }

        if (!auraReader.isEmpty()) {
            tickAuraReader(player, auraReader);
        }
    }

    private static void tickAuraReader(Player player, ItemStack stack) {
        Boolean active = stack.get(ModItemComponents.AURA_SCANNER_ACTIVE.get());
        if (active != null && active) {
            boolean hasCharge = AuraReaderCharge.consume(stack, 1, AuraReaderItem.MAX_CHARGE);
            if (!hasCharge) {
                stack.set(ModItemComponents.AURA_SCANNER_ACTIVE.get(), false);
                if (player instanceof ServerPlayer sp) {
                    ShadowedHeartsNetwork.sendToPlayer(sp, new AuraScannerS2CPacket(false));
                }
            }
        }
    }
}
