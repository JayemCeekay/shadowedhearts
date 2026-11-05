package com.jayemceekay.shadowedhearts.network.voidshadow

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowTransformationAudio
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/** A live transformation cue waits for this exact entity's synchronized target appearance. */
data class VoidShadowTransformPacket(
    val eventId: UUID,
    val dimension: ResourceLocation,
    val entityId: Int,
    val entityUuid: UUID,
    val expectedSpecies: ResourceLocation,
    val expectedAspects: Set<String>
) : NetworkPacket<VoidShadowTransformPacket> {
    override val id: ResourceLocation = ID

    init {
        require(expectedAspects.size <= MAX_ASPECTS) { "Too many Void Shadow appearance aspects" }
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(expectedAspects.size <= MAX_ASPECTS) { "Too many Void Shadow appearance aspects" }
        buffer.writeUUID(eventId)
        buffer.writeResourceLocation(dimension)
        buffer.writeVarInt(entityId)
        buffer.writeUUID(entityUuid)
        buffer.writeResourceLocation(expectedSpecies)
        buffer.writeVarInt(expectedAspects.size)
        expectedAspects.forEach { buffer.writeUtf(it) }
    }

    companion object {
        const val MAX_ASPECTS = 256
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "void_shadow_transform")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): VoidShadowTransformPacket {
            val eventId = buffer.readUUID()
            val dimension = buffer.readResourceLocation()
            val entityId = buffer.readVarInt()
            val entityUuid = buffer.readUUID()
            val species = buffer.readResourceLocation()
            val count = buffer.readVarInt()
            require(count in 0..MAX_ASPECTS) { "Invalid Void Shadow appearance aspect count" }
            val aspects = LinkedHashSet<String>(count)
            repeat(count) { aspects.add(buffer.readUtf()) }
            return VoidShadowTransformPacket(eventId, dimension, entityId, entityUuid, species, aspects.toSet())
        }
    }
}

object VoidShadowTransformHandler : ClientNetworkPacketHandler<VoidShadowTransformPacket> {
    override fun handle(packet: VoidShadowTransformPacket, client: Minecraft) {
        val connection = client.connection
        client.execute {
            if (connection != null && client.connection === connection) {
                VoidShadowTransformationAudio.accept(packet, client)
            }
        }
    }
}
