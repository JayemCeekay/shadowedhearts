package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Client -> Server: player interaction with an active node event.
 * Used for Evidence Interpretation clue selection.
 */
class NodeEventInputC2SPacket(
    val action: Int, // 0 = select clue by index
    val selectedIndex: Int = -1
) : NetworkPacket<NodeEventInputC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(action)
        buf.writeVarInt(selectedIndex)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "node_event_input")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): NodeEventInputC2SPacket {
            val action = buf.readVarInt()
            val selectedIndex = buf.readVarInt()
            return NodeEventInputC2SPacket(action, selectedIndex)
        }
    }
}
