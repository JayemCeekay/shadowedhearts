package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraReaderStateS2CPacket(
    val active: Boolean,
    val mode: AuraReaderMode,
    val charge: Int,
    val maxCharge: Int,
    val tracking: Boolean,
    val selectedSignal: String,
    val lockType: AuraReaderLockType,
    val pulseCooldownRemainingTicks: Int,
    val pulseCooldownMaxTicks: Int
) : NetworkPacket<AuraReaderStateS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(active)
        buffer.writeUtf(mode.name)
        buffer.writeVarInt(charge)
        buffer.writeVarInt(maxCharge)
        buffer.writeBoolean(tracking)
        buffer.writeUtf(selectedSignal)
        buffer.writeUtf(lockType.name)
        buffer.writeVarInt(pulseCooldownRemainingTicks)
        buffer.writeVarInt(pulseCooldownMaxTicks)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_state")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): AuraReaderStateS2CPacket {
            return AuraReaderStateS2CPacket(
                buf.readBoolean(),
                AuraReaderMode.fromName(buf.readUtf()),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(),
                AuraReaderLockType.fromName(buf.readUtf()),
                buf.readVarInt(),
                buf.readVarInt()
            )
        }
    }
}
