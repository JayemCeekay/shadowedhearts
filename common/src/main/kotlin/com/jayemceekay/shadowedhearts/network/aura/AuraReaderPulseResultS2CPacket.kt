package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.aura.AuraHeartGaugeReadout
import com.jayemceekay.shadowedhearts.common.aura.AuraReading
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.ResourceLocation

data class AuraReaderPulseResultS2CPacket(
    val pulseScan: Boolean,
    val pulseAccepted: Boolean,
    val readings: List<ReadingSummary>,
    val scanRange: Int = 0
) : NetworkPacket<AuraReaderPulseResultS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(pulseScan)
        buffer.writeBoolean(pulseAccepted)
        buffer.writeVarInt(scanRange)
        buffer.writeVarInt(readings.size)
        readings.forEach { reading ->
            buffer.writeUtf(reading.id)
            buffer.writeEnum(reading.type)
            buffer.writeEnum(reading.source)
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, reading.labelText)
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, reading.statusText)
            buffer.writeDouble(reading.directionX)
            buffer.writeDouble(reading.directionY)
            buffer.writeDouble(reading.directionZ)
            buffer.writeFloat(reading.bearingUncertainty)
            buffer.writeUtf(reading.distanceBand)
            buffer.writeUtf(reading.verticalHint)
            buffer.writeFloat(reading.strength)
            buffer.writeFloat(reading.confidence)
            buffer.writeFloat(reading.interference)
            buffer.writeVarInt(reading.priority)
            buffer.writeVarLong(reading.expiryTick)
            buffer.writeBoolean(reading.selected)
            buffer.writeBoolean(reading.locked)
            buffer.writeBoolean(reading.outOfDimension)
            val heart = reading.heartGauge
            buffer.writeBoolean(heart != null)
            if (heart != null) {
                buffer.writeEnum(heart.disclosure())
                when (heart.disclosure()) {
                    AuraHeartGaugeReadout.Disclosure.BANDED -> buffer.writeEnum(heart.band())
                    AuraHeartGaugeReadout.Disclosure.EXACT -> {
                        buffer.writeVarInt(heart.current())
                        buffer.writeVarInt(heart.maximum())
                    }
                    else -> Unit
                }
            }
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
            return fromReadings(readings, pulseScan, pulseAccepted, 0)
        }

        @JvmStatic
        fun fromReadings(readings: List<AuraReading>, pulseScan: Boolean, pulseAccepted: Boolean, scanRange: Int): AuraReaderPulseResultS2CPacket {
            return AuraReaderPulseResultS2CPacket(pulseScan, pulseAccepted, readings.map(ReadingSummary::from), scanRange.coerceAtLeast(0))
        }

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): AuraReaderPulseResultS2CPacket {
            val pulseScan = buffer.readBoolean()
            val pulseAccepted = buffer.readBoolean()
            val scanRange = buffer.readVarInt()
            val count = buffer.readVarInt()
            val readings = ArrayList<ReadingSummary>(count)
            repeat(count) {
                readings.add(
                    ReadingSummary(
                        buffer.readUtf(),
                        buffer.readEnum(AuraReadingType::class.java),
                        buffer.readEnum(AuraReadingSource::class.java),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readFloat(),
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readFloat(),
                        buffer.readFloat(),
                        buffer.readFloat(),
                        buffer.readVarInt(),
                        buffer.readVarLong(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        decodeHeartGauge(buffer)
                    )
                )
            }
            return AuraReaderPulseResultS2CPacket(pulseScan, pulseAccepted, readings, scanRange)
        }

        private fun decodeHeartGauge(buffer: RegistryFriendlyByteBuf): AuraHeartGaugeReadout? {
            if (!buffer.readBoolean()) return null
            return when (buffer.readEnum(AuraHeartGaugeReadout.Disclosure::class.java)) {
                AuraHeartGaugeReadout.Disclosure.HIDDEN -> AuraHeartGaugeReadout.hidden()
                AuraHeartGaugeReadout.Disclosure.BANDED -> AuraHeartGaugeReadout(
                    AuraHeartGaugeReadout.Disclosure.BANDED,
                    buffer.readEnum(AuraHeartGaugeReadout.Band::class.java), 0, 0
                )
                AuraHeartGaugeReadout.Disclosure.EXACT -> AuraHeartGaugeReadout.exact(
                    buffer.readVarInt(), buffer.readVarInt()
                )
            }
        }
    }

    data class ReadingSummary(
        val id: String,
        val type: AuraReadingType,
        val source: AuraReadingSource,
        val labelText: Component,
        val statusText: Component,
        val directionX: Double,
        val directionY: Double,
        val directionZ: Double,
        val bearingUncertainty: Float,
        val distanceBand: String,
        val verticalHint: String,
        val strength: Float,
        val confidence: Float,
        val interference: Float,
        val priority: Int,
        val expiryTick: Long,
        val selected: Boolean,
        val locked: Boolean,
        val outOfDimension: Boolean,
        val heartGauge: AuraHeartGaugeReadout? = null
    ) {
        constructor(
            id: String, type: AuraReadingType, source: AuraReadingSource, label: String, status: String,
            directionX: Double, directionY: Double, directionZ: Double, bearingUncertainty: Float,
            distanceBand: String, verticalHint: String, strength: Float, confidence: Float,
            interference: Float, priority: Int, expiryTick: Long, selected: Boolean, locked: Boolean,
            outOfDimension: Boolean, heartGauge: AuraHeartGaugeReadout? = null
        ) : this(id, type, source, Component.literal(label), Component.literal(status), directionX,
            directionY, directionZ, bearingUncertainty, distanceBand, verticalHint, strength, confidence,
            interference, priority, expiryTick, selected, locked, outOfDimension, heartGauge)

        val label: String get() = labelText.string
        val status: String get() = statusText.string

        companion object {
            @JvmStatic
            fun from(reading: AuraReading): ReadingSummary {
                return ReadingSummary(
                    reading.id(),
                    reading.type(),
                    reading.source(),
                    reading.labelText(),
                    reading.statusText(),
                    reading.directionX(),
                    reading.directionY(),
                    reading.directionZ(),
                    reading.bearingUncertainty(),
                    reading.distanceBand(),
                    reading.verticalHint(),
                    reading.strength(),
                    reading.confidence(),
                    reading.interference(),
                    reading.priority(),
                    reading.expiryTick(),
                    reading.selected(),
                    reading.locked(),
                    reading.outOfDimension(),
                    reading.heartGauge()
                )
            }
        }
    }
}
