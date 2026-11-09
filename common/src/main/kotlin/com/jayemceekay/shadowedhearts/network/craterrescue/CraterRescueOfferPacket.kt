package com.jayemceekay.shadowedhearts.network.craterrescue

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.craterrescue.CraterRescuePrompt
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID

data class CraterRescueOfferPacket(val siteId: UUID, val pokemonName: Component, val ball: ItemStack) :
    NetworkPacket<CraterRescueOfferPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(siteId)
        buffer.writeBoolean(!ball.isEmpty)
        if (!ball.isEmpty) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, pokemonName)
            ItemStack.STREAM_CODEC.encode(buffer, ball.copyWithCount(1))
        }
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "crater_rescue_offer")

        @JvmStatic
        fun dismiss(siteId: UUID) = CraterRescueOfferPacket(siteId, Component.empty(), ItemStack.EMPTY)

        fun decode(buffer: RegistryFriendlyByteBuf): CraterRescueOfferPacket {
            val siteId = buffer.readUUID()
            return if (buffer.readBoolean()) {
                CraterRescueOfferPacket(siteId, ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                    ItemStack.STREAM_CODEC.decode(buffer))
            } else dismiss(siteId)
        }
    }
}

object CraterRescueOfferHandler : ClientNetworkPacketHandler<CraterRescueOfferPacket> {
    override fun handle(packet: CraterRescueOfferPacket, client: Minecraft) {
        val connection = client.connection
        client.execute {
            if (connection != null && client.connection === connection) CraterRescuePrompt.receive(client, packet)
        }
    }
}
