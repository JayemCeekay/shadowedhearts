package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Client -> Server: player submits a directional input during calibration.
 */
class CalibrationInputC2SPacket(
    val direction: CalibrationSequence.Direction
) : NetworkPacket<CalibrationInputC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(direction.toId())
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "calibration_input")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): CalibrationInputC2SPacket {
            val dir = CalibrationSequence.Direction.fromId(buf.readVarInt())
            return CalibrationInputC2SPacket(dir)
        }
    }
}
