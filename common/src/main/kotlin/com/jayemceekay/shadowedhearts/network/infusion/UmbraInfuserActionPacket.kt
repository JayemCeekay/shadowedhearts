package com.jayemceekay.shadowedhearts.network.infusion

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserMenu
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

data class UmbraInfuserActionPacket(
    val containerId: Int,
    val action: Action,
    val pokemonId: UUID?
) : NetworkPacket<UmbraInfuserActionPacket> {
    enum class Action { INSERT, RETRIEVE, START }
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(containerId)
        buffer.writeEnum(action)
        buffer.writeBoolean(pokemonId != null)
        pokemonId?.let(buffer::writeUUID)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "umbra_infuser_action")
        fun decode(buffer: RegistryFriendlyByteBuf) = UmbraInfuserActionPacket(
            buffer.readVarInt(), buffer.readEnum(Action::class.java),
            if (buffer.readBoolean()) buffer.readUUID() else null
        )
    }
}

object UmbraInfuserActionHandler : ServerNetworkPacketHandler<UmbraInfuserActionPacket> {
    override fun handle(packet: UmbraInfuserActionPacket, server: MinecraftServer, player: ServerPlayer) {
        // Keep direct invocations on the server thread too: Level.getBlockEntity returns null
        // off-thread, and menu validity checks and mutations must run together.
        if (!server.isSameThread) {
            server.execute { handle(packet, server, player) }
            return
        }
        val menu = player.containerMenu as? UmbraInfuserMenu ?: return
        if (packet.containerId != menu.containerId || !menu.canManage(player)) return
        val infuser = menu.blockEntity ?: return
        when (packet.action) {
            UmbraInfuserActionPacket.Action.INSERT -> packet.pokemonId?.let { infuser.insertPokemon(player, it) }
            UmbraInfuserActionPacket.Action.RETRIEVE -> {
                val expectedPokemon = packet.pokemonId ?: return
                if (expectedPokemon != infuser.pokemon?.uuid) return
                infuser.retrievePokemon(player)
            }
            UmbraInfuserActionPacket.Action.START -> {
                val expectedPokemon = packet.pokemonId ?: return
                if (expectedPokemon != infuser.pokemon?.uuid) return
                infuser.startInfusion(player)
            }
        }
        menu.broadcastChanges()
    }
}
