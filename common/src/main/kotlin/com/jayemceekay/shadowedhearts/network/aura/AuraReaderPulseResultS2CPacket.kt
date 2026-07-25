package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.aura.AuraReading
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraReaderPulseResultS2CPacket(
    val pulseScan: Boolean,
    val pulseAccepted: Boolean,
    val readings: List<ReadingSummary>
) : NetworkPacket<AuraReaderPulseResultS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(pulseScan)
        buffer.writeBoolean(pulseAccepted)
        buffer.writeVarInt(readings.size)
        readings.forEach { reading ->
            buffer.writeUtf(reading.id)
            buffer.writeEnum(reading.type)
            buffer.writeUtf(reading.label)
            buffer.writeUtf(reading.status)
            buffer.writeUtf(reading.distanceBand)
            buffer.writeUtf(reading.verticalHint)
            buffer.writeFloat(reading.strength)
            buffer.writeFloat(reading.confidence)
        }
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_pulse_result")

        @JvmStatic
        fun fromReadings(readings: List<AuraReading>): AuraReaderPulseResultS2CPacket {
            return fromReadings(readings, false)
        }

        @JvmStatic
        fun fromReadings(readings: List<AuraReading>, pulseScan: Boolean): AuraReaderPulseResultS2CPacket {
            return fromReadings(readings, pulseScan, true)
        }

        @JvmStatic
        fun fromReadings(readings: List<AuraReading>, pulseScan: Boolean, pulseAccepted: Boolean): AuraReaderPulseResultS2CPacket {
            return AuraReaderPulseResultS2CPacket(pulseScan, pulseAccepted, readings.map(ReadingSummary::from))
        }

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): AuraReaderPulseResultS2CPacket {
            val pulseScan = buffer.readBoolean()
            val pulseAccepted = buffer.readBoolean()
            val count = buffer.readVarInt()
            val readings = ArrayList<ReadingSummary>(count)
            repeat(count) {
                readings.add(
                    ReadingSummary(
                        buffer.readUtf(),
                        buffer.readEnum(AuraReadingType::class.java),
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readFloat(),
                        buffer.readFloat()
                    )
                )
            }
            return AuraReaderPulseResultS2CPacket(pulseScan, pulseAccepted, readings)
        }
    }

    data class ReadingSummary(
        val id: String,
        val type: AuraReadingType,
        val label: String,
        val status: String,
        val distanceBand: String,
        val verticalHint: String,
        val strength: Float,
        val confidence: Float
    ) {
        companion object {
            @JvmStatic
            fun from(reading: AuraReading): ReadingSummary {
                return ReadingSummary(
                    reading.id(),
                    reading.type(),
                    reading.label(),
                    reading.status(),
                    reading.distanceBand(),
                    reading.verticalHint(),
                    reading.strength(),
                    reading.confidence()
                )
            }
        }
    }
}
