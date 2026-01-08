package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.snag.*
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SnagArmHandler : ServerNetworkPacketHandler<SnagArmPacket> {
    override fun handle(packet: SnagArmPacket, server: MinecraftServer, player: ServerPlayer) {
        val cap = SnagCaps.get(player)
        // must be holding a Snag Machine or have it equipped as an accessory
        if (!cap.hasSnagMachine()) return
        if (cap.cooldown() > 0) return

        val requestArmed = packet.armed

        if (requestArmed) {
            // Only arm inside trainer battle with eligible shadow target
            if (!SnagBattleUtil.isInTrainerBattle(player) && !SnagBattleUtil.hasEligibleShadowOpponent(player)) {
                return // silently ignore invalid arm
            }
            val machineStack = if (cap is SimplePlayerSnagData) cap.machineStack else net.minecraft.world.item.ItemStack.EMPTY

            if (!machineStack.isEmpty && machineStack.item is SnagMachineItem) {
                val sm = machineStack.item as SnagMachineItem
                // Initialize energy store but do NOT consume on arm; consumption happens on throw
                SnagEnergy.ensureInitialized(machineStack, sm.capacity())

                // Prevent arming if there isn't enough energy for an attempt
                val currentEnergy = SnagCaps.get(player).energy()
                val cfg = ShadowedHeartsConfigs.getInstance().snagConfig
                val required = cfg.energyPerAttempt()
                if (currentEnergy < required) {
                    cap.setCooldown(cfg.toggleCooldownTicks())
                    player.sendSystemMessage(
                        Component.translatable(
                            "message.shadowedhearts.snag_machine.not_enough_energy",
                            currentEnergy, required
                        )
                    )
                    return
                }

                cap.isArmed = true
                cap.setCooldown(cfg.toggleCooldownTicks())
                ShadowedHeartsNetwork.sendToPlayer(player, SnagArmedPacket(true))
            }
        } else {
            // Disarm request — allowed anytime; no cost
            val cfg = ShadowedHeartsConfigs.getInstance().snagConfig
            cap.isArmed = false
            cap.setCooldown(cfg.toggleCooldownTicks())
            ShadowedHeartsNetwork.sendToPlayer(player, SnagArmedPacket(false))
        }
    }
}
