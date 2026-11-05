package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.content.umbrafall.AuraReaderSearchProgress
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraReaderSearchProgressS2CPacket(
    val sequence: Long,
    val progress: AuraReaderSearchProgress
) : NetworkPacket<AuraReaderSearchProgressS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(sequence)
        buffer.writeUtf(progress.tier().name)
        buffer.writeUtf(progress.stage().name)
        buffer.writeVarInt(progress.candidate())
        buffer.writeVarInt(progress.completedWork())
        buffer.writeVarInt(progress.totalWork())
        buffer.writeUtf(progress.waitReason().name)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_search_progress")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): AuraReaderSearchProgressS2CPacket {
            val sequence = buffer.readVarLong()
            val tier = UmbrafallTier.fromId(buffer.readUtf())
            return AuraReaderSearchProgressS2CPacket(sequence, AuraReaderSearchProgress(
                tier,
                AuraReaderSearchProgress.Stage.fromName(buffer.readUtf()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                AuraReaderSearchProgress.WaitReason.fromName(buffer.readUtf())
            ))
        }
    }
}
