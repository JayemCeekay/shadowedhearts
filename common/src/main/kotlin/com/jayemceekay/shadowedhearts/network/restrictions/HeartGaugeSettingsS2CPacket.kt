package com.jayemceekay.shadowedhearts.network.restrictions

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig
import io.netty.handler.codec.DecoderException
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** A bounded, immutable snapshot of the server's effective species overrides. */
class HeartGaugeSettingsS2CPacket(overrides: Map<String, Int>) : NetworkPacket<HeartGaugeSettingsS2CPacket> {
    val overrides: Map<String, Int>

    init {
        require(overrides.size <= MAX_ENTRIES) { "Too many Heart Gauge overrides" }
        require(overrides.all { (key, value) -> HeartGaugeConfig.isValidSpeciesKey(key) && value > 0 }) {
            "Invalid Heart Gauge override"
        }
        this.overrides = java.util.Map.copyOf(overrides)
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(overrides.size)
        overrides.forEach { (key, value) ->
            buffer.writeUtf(key, MAX_KEY_LENGTH)
            buffer.writeVarInt(value)
        }
    }

    companion object {
        const val MAX_ENTRIES = HeartGaugeConfig.MAX_OVERRIDE_ENTRIES
        const val MAX_KEY_LENGTH = 256
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "heart_gauge_settings")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): HeartGaugeSettingsS2CPacket {
            val count = buffer.readVarInt()
            if (count !in 0..MAX_ENTRIES) throw DecoderException("Invalid Heart Gauge override count: $count")
            val overrides = LinkedHashMap<String, Int>(count)
            repeat(count) {
                val key = buffer.readUtf(MAX_KEY_LENGTH)
                val value = buffer.readVarInt()
                if (!HeartGaugeConfig.isValidSpeciesKey(key) || value <= 0 || overrides.putIfAbsent(key, value) != null) {
                    throw DecoderException("Invalid or duplicate Heart Gauge override")
                }
            }
            return HeartGaugeSettingsS2CPacket(overrides)
        }
    }
}
