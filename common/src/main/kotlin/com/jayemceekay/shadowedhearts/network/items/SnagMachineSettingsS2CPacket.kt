package com.jayemceekay.shadowedhearts.network.items

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.snag.SnagMachineSettings
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class SnagMachineSettingsS2CPacket(val settings: SnagMachineSettings) : NetworkPacket<SnagMachineSettingsS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        for (tier in listOf(settings.prototype(), settings.advanced())) {
            buffer.writeVarInt(tier.attemptTurnSpacing())
            buffer.writeDouble(tier.catchMultiplier())
            buffer.writeDouble(tier.gaugeReductionPercent())
        }
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_machine_settings")

        private fun decodeTier(buffer: RegistryFriendlyByteBuf) = SnagMachineSettings.TierSettings(
            buffer.readVarInt(), buffer.readDouble(), buffer.readDouble())

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf) = SnagMachineSettingsS2CPacket(
            SnagMachineSettings(decodeTier(buffer), decodeTier(buffer)))
    }
}
