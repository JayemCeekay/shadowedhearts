package com.jayemceekay.shadowedhearts.network.snag

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.snag.SimplePlayerSnagData
import com.jayemceekay.shadowedhearts.common.snag.SnagBattleUtil
import com.jayemceekay.shadowedhearts.common.snag.SnagCaps
import com.jayemceekay.shadowedhearts.common.snag.SnagMachineBenefits
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig
import com.jayemceekay.shadowedhearts.content.items.SnagMachineItem
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SnagArmHandler : ServerNetworkPacketHandler<SnagArmPacket> {
    override fun handle(packet: SnagArmPacket, server: MinecraftServer, player: ServerPlayer) {
        val cap = SnagCaps.get(player)
        if (!GameplayTuningConfig.INSTANCE.snaggingEnabled()) {
            cap.isArmed = false
            ShadowedHeartsNetwork.sendToPlayer(player, SnagArmedPacket(false))
            if (packet.armed) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.shadowedhearts.snag_machine.disabled"), true)
            return
        }
        // must be holding a Snag Machine or have it equipped as an accessory
        if (!cap.hasSnagMachine()) return
        if (cap.cooldown() > 0) return

        val requestArmed = packet.armed

        if (requestArmed) {
            // Only arm inside trainer battle with eligible shadow target
            if (!SnagBattleUtil.isInTrainerBattle(player) || !SnagBattleUtil.hasEligibleShadowOpponent(player)
                || !SnagMachineBenefits.canAttempt(player)) {
                return // silently ignore invalid arm
            }
            val machineStack = if (cap is SimplePlayerSnagData) cap.machineStack else net.minecraft.world.item.ItemStack.EMPTY

            if (!machineStack.isEmpty && machineStack.item is SnagMachineItem) {
                val cfg = ShadowedHeartsConfigs.getInstance().snagConfig

                cap.isArmed = true
                cap.setCooldown(cfg.toggleCooldownTicks())
                ShadowedHeartsNetwork.sendToPlayer(player, SnagArmedPacket(true))
            }
        } else {
            // Disarm request; keep the existing toggle cooldown.
            val cfg = ShadowedHeartsConfigs.getInstance().snagConfig
            cap.isArmed = false
            cap.setCooldown(cfg.toggleCooldownTicks())
            ShadowedHeartsNetwork.sendToPlayer(player, SnagArmedPacket(false))
        }
    }
}
