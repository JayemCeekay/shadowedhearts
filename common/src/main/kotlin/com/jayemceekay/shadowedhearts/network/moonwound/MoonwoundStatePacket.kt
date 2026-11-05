package com.jayemceekay.shadowedhearts.network.moonwound

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundClient
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Outcome
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.PaceBonus
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEvent.Phase
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.ResourceLocation

data class MoonwoundStatePacket(val snapshot: MoonwoundSnapshot, val openScreen: Boolean) : NetworkPacket<MoonwoundStatePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(snapshot.siteId())
        buffer.writeUUID(snapshot.eventId())
        buffer.writeResourceLocation(snapshot.dimension())
        buffer.writeBlockPos(snapshot.center())
        buffer.writeInt(snapshot.portalY())
        buffer.writeFloat(snapshot.portalSize())
        buffer.writeEnum(snapshot.phase())
        buffer.writeUUID(snapshot.host())
        buffer.writeVarInt(snapshot.members().size)
        snapshot.members().forEach { member ->
            buffer.writeUUID(member.id())
            buffer.writeUtf(member.name(), 64)
            buffer.writeBoolean(member.ready())
            buffer.writeBoolean(member.vote() != null)
            member.vote()?.let(buffer::writeEnum)
            buffer.writeEnum(member.availability())
            buffer.writeVarInt(member.graceRemainingTicks())
            buffer.writeBoolean(member.votingSuspended())
        }
        buffer.writeVarInt(snapshot.requests().size)
        snapshot.requests().forEach { request ->
            buffer.writeUUID(request.id())
            buffer.writeUtf(request.name(), 64)
        }
        buffer.writeVarInt(snapshot.mined())
        buffer.writeVarInt(snapshot.miningTarget())
        buffer.writeVarInt(snapshot.defeated())
        buffer.writeVarInt(snapshot.waveTarget())
        buffer.writeVarInt(snapshot.voteRound())
        buffer.writeBoolean(snapshot.canSeal())
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, snapshot.statusText())
        buffer.writeBoolean(snapshot.raidFinale())
        buffer.writeVarInt(snapshot.portalAgeTicks())
        buffer.writeBoolean(snapshot.recovery().paused())
        buffer.writeVarInt(snapshot.recovery().lobbyRemainingTicks())
        buffer.writeVarInt(snapshot.pace().targetTicks())
        buffer.writeVarInt(snapshot.pace().elapsedTicks())
        buffer.writeEnum(snapshot.pace().bonus())
        buffer.writeBoolean(openScreen)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "moonwound_state")
        fun decode(buffer: RegistryFriendlyByteBuf): MoonwoundStatePacket {
            val site = buffer.readUUID()
            val event = buffer.readUUID()
            val dimension = buffer.readResourceLocation()
            val center = buffer.readBlockPos()
            val portalY = buffer.readInt()
            val size = buffer.readFloat()
            val phase = buffer.readEnum(Phase::class.java)
            val host = buffer.readUUID()
            val count = buffer.readVarInt()
            require(count in 0..MoonwoundSnapshot.MAX_MEMBERS) { "Invalid Moonwound roster size" }
            val members = List(count) {
                MoonwoundSnapshot.Member(buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(),
                    if (buffer.readBoolean()) buffer.readEnum(Outcome::class.java) else null,
                    buffer.readEnum(MoonwoundSnapshot.Availability::class.java), buffer.readVarInt(), buffer.readBoolean())
            }
            val requestCount = buffer.readVarInt()
            require(requestCount in 0..MoonwoundSnapshot.MAX_REQUESTS) { "Invalid Moonwound request count" }
            val requests = List(requestCount) { MoonwoundSnapshot.Applicant(buffer.readUUID(), buffer.readUtf(64)) }
            val snapshot = MoonwoundSnapshot(site, event, dimension, center, portalY, size, phase, host,
                members, requests, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer), buffer.readBoolean(),
                buffer.readVarInt(), MoonwoundSnapshot.Recovery(buffer.readBoolean(), buffer.readVarInt()),
                MoonwoundSnapshot.Pace(buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(PaceBonus::class.java)))
            return MoonwoundStatePacket(snapshot, buffer.readBoolean())
        }
    }
}

object MoonwoundStateHandler : ClientNetworkPacketHandler<MoonwoundStatePacket> {
    override fun handle(packet: MoonwoundStatePacket, client: Minecraft) {
        // Both loader registrars may deliver on their network executor.
        val connection = client.connection
        client.execute {
            if (connection != null && client.connection === connection) {
                MoonwoundClient.accept(packet.snapshot, packet.openScreen)
            }
        }
    }
}
