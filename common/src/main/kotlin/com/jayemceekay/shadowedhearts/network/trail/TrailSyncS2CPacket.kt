package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventType
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server -> Client: sync current trail nodes, active evidence hotspot,
 * tier metadata, event types, tension, and trail quality.
 */
class TrailSyncS2CPacket(
    val nodes: List<BlockPos>,
    val hotspot: BlockPos?,
    val tier: ShadowSignalTier = ShadowSignalTier.FAINT,
    val eventTypes: List<NodeEventType> = emptyList(),
    val tension: Float = 0.0f,
    val trailQuality: Float = 1.0f,
    val currentNodeIndex: Int = 0,
    val huntSeed: Long = 0L,
    val falseTrails: List<List<BlockPos>> = emptyList(),
    val speciesTraitId: Int = 4 // ShadowSpeciesTrait.NEUTRAL ordinal
) : NetworkPacket<TrailSyncS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(nodes.size)
        for (p in nodes) {
            buf.writeBlockPos(p)
        }
        if (hotspot != null) {
            buf.writeBoolean(true)
            buf.writeBlockPos(hotspot)
        } else {
            buf.writeBoolean(false)
        }
        // Tier and hunt metadata
        buf.writeVarInt(tier.tier)
        buf.writeVarInt(eventTypes.size)
        for (evt in eventTypes) {
            buf.writeVarInt(evt.toId())
        }
        buf.writeFloat(tension)
        buf.writeFloat(trailQuality)
        buf.writeVarInt(currentNodeIndex)
        buf.writeLong(huntSeed)
        // False trails
        buf.writeVarInt(falseTrails.size)
        for (trail in falseTrails) {
            buf.writeVarInt(trail.size)
            for (p in trail) {
                buf.writeBlockPos(p)
            }
        }
        buf.writeVarInt(speciesTraitId)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "trail_sync")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): TrailSyncS2CPacket {
            val count = buf.readVarInt()
            val nodes = ArrayList<BlockPos>(count)
            repeat(count) {
                nodes.add(buf.readBlockPos())
            }
            val hasHotspot = buf.readBoolean()
            val hotspot = if (hasHotspot) buf.readBlockPos() else null
            // Tier and hunt metadata
            val tier = ShadowSignalTier.fromTier(buf.readVarInt())
            val eventCount = buf.readVarInt()
            val eventTypes = ArrayList<NodeEventType>(eventCount)
            repeat(eventCount) {
                eventTypes.add(NodeEventType.fromId(buf.readVarInt()))
            }
            val tension = buf.readFloat()
            val trailQuality = buf.readFloat()
            val currentNodeIndex = buf.readVarInt()
            val huntSeed = buf.readLong()
            // False trails
            val falseTrailCount = buf.readVarInt()
            val falseTrails = ArrayList<List<BlockPos>>(falseTrailCount)
            repeat(falseTrailCount) {
                val trailSize = buf.readVarInt()
                val trail = ArrayList<BlockPos>(trailSize)
                repeat(trailSize) {
                    trail.add(buf.readBlockPos())
                }
                falseTrails.add(trail)
            }
            val speciesTraitId = buf.readVarInt()
            return TrailSyncS2CPacket(nodes, hotspot, tier, eventTypes, tension, trailQuality, currentNodeIndex, huntSeed, falseTrails, speciesTraitId)
        }
    }
}
