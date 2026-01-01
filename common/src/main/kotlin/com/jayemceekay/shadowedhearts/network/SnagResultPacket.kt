package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.*

data class SnagResultPacket(val success: Boolean, val targetUuid: UUID) : NetworkPacket<SnagResultPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(success)
        buf.writeUUID(targetUuid)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_result")

        fun decode(buf: RegistryFriendlyByteBuf): SnagResultPacket {
            return SnagResultPacket(buf.readBoolean(), buf.readUUID())
        }
    }
}
