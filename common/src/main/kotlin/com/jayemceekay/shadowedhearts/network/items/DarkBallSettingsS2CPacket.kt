package com.jayemceekay.shadowedhearts.network.items

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.capture.DarkBallSettings
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class DarkBallSettingsS2CPacket(val settings: DarkBallSettings) : NetworkPacket<DarkBallSettingsS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(settings.levelBoost())
        buffer.writeDouble(settings.heartGaugeMultiplier())
        buffer.writeBoolean(settings.corruptionEnabled())
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "dark_ball_settings")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf) =
            DarkBallSettingsS2CPacket(DarkBallSettings(buffer.readVarInt(), buffer.readDouble(), buffer.readBoolean()))
    }
}
