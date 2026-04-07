package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventType
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server -> Client: sync the active node event state.
 * Carries event-type-specific data so the client can display the correct HUD.
 */
class NodeEventSyncS2CPacket(
    val eventType: NodeEventType,
    val phase: Int, // NodeEventState.Phase ordinal
    val ticksElapsed: Int,
    val maxTicks: Int,
    // Evidence Interpretation
    val cluePositions: List<BlockPos> = emptyList(),
    val wrongGuesses: Int = 0,
    val requiredValidCount: Int = 1,
    val foundValidCount: Int = 0,
    val selectedClueIndices: List<Int> = emptyList(),
    // Environmental Search
    val searchCenter: BlockPos? = null,
    val searchRadius: Float = 0f,
    val searchSignalStrength: Float = 0f,
    // Wild Interruption
    val wildResolveTimer: Int = 0,
    val wildsResolved: Boolean = false,
    // Provocation
    val signalBuildup: Float = 0f,
    val provocationRequiredTicks: Int = 0,
    // Biome flavor
    val clueDescriptions: List<String> = emptyList(),
    val searchHint: String = ""
) : NetworkPacket<NodeEventSyncS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(eventType.toId())
        buf.writeVarInt(phase)
        buf.writeVarInt(ticksElapsed)
        buf.writeVarInt(maxTicks)

        // Evidence Interpretation
        buf.writeVarInt(cluePositions.size)
        for (pos in cluePositions) {
            buf.writeBlockPos(pos)
        }
        buf.writeVarInt(wrongGuesses)
        buf.writeVarInt(requiredValidCount)
        buf.writeVarInt(foundValidCount)
        buf.writeVarInt(selectedClueIndices.size)
        for (idx in selectedClueIndices) {
            buf.writeVarInt(idx)
        }

        // Environmental Search
        buf.writeBoolean(searchCenter != null)
        if (searchCenter != null) buf.writeBlockPos(searchCenter)
        buf.writeFloat(searchRadius)
        buf.writeFloat(searchSignalStrength)

        // Wild Interruption
        buf.writeVarInt(wildResolveTimer)
        buf.writeBoolean(wildsResolved)

        // Provocation
        buf.writeFloat(signalBuildup)
        buf.writeVarInt(provocationRequiredTicks)

        // Biome flavor
        buf.writeVarInt(clueDescriptions.size)
        for (desc in clueDescriptions) {
            buf.writeUtf(desc)
        }
        buf.writeUtf(searchHint)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "node_event_sync")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): NodeEventSyncS2CPacket {
            val eventType = NodeEventType.fromId(buf.readVarInt())
            val phase = buf.readVarInt()
            val ticksElapsed = buf.readVarInt()
            val maxTicks = buf.readVarInt()

            val clueCount = buf.readVarInt()
            val cluePositions = ArrayList<BlockPos>(clueCount)
            repeat(clueCount) { cluePositions.add(buf.readBlockPos()) }
            val wrongGuesses = buf.readVarInt()
            val requiredValidCount = buf.readVarInt()
            val foundValidCount = buf.readVarInt()
            val selectedCount = buf.readVarInt()
            val selectedClueIndices = ArrayList<Int>(selectedCount)
            repeat(selectedCount) { selectedClueIndices.add(buf.readVarInt()) }

            val hasSearchCenter = buf.readBoolean()
            val searchCenter = if (hasSearchCenter) buf.readBlockPos() else null
            val searchRadius = buf.readFloat()
            val searchSignalStrength = buf.readFloat()

            val wildResolveTimer = buf.readVarInt()
            val wildsResolved = buf.readBoolean()

            val signalBuildup = buf.readFloat()
            val provocationRequiredTicks = buf.readVarInt()

            val descCount = buf.readVarInt()
            val clueDescriptions = ArrayList<String>(descCount)
            repeat(descCount) { clueDescriptions.add(buf.readUtf()) }
            val searchHint = buf.readUtf()

            return NodeEventSyncS2CPacket(
                eventType, phase, ticksElapsed, maxTicks,
                cluePositions, wrongGuesses, requiredValidCount, foundValidCount, selectedClueIndices,
                searchCenter, searchRadius, searchSignalStrength,
                wildResolveTimer, wildsResolved,
                signalBuildup, provocationRequiredTicks,
                clueDescriptions, searchHint
            )
        }
    }
}
