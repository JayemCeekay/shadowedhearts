package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Separate from ordinary scans, so their replacement snapshots cannot erase a battle detection. */
data class AuraReaderBattleDetectionS2CPacket(
    val battleId: String,
    val analysis: AuraReaderPulseResultS2CPacket
) : NetworkPacket<AuraReaderBattleDetectionS2CPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(battleId)
        analysis.encode(buffer)
    }
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_battle_detection")
        @JvmStatic fun decode(buffer: RegistryFriendlyByteBuf) =
            AuraReaderBattleDetectionS2CPacket(buffer.readUtf(), AuraReaderPulseResultS2CPacket.decode(buffer))
    }
}
