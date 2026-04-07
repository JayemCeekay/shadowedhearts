package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.trail.TrailClientState
import com.jayemceekay.shadowedhearts.network.trail.TrailSyncS2CPacket
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes

/**
 * Applies TrailSyncS2CPacket to client state.
 */
object TrailSyncClientHandler : ClientNetworkPacketHandler<TrailSyncS2CPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: TrailSyncS2CPacket, client: Minecraft) {
        client.execute {
            LOG.debug("[ShadowHunt][Client] TrailSync: nodes={}, hotspot={}, tier={}, tension={:.2f}, quality={:.2f}, index={}",
                packet.nodes.size, packet.hotspot, packet.tier, packet.tension, packet.trailQuality, packet.currentNodeIndex)

            // If server sends an empty trail (e.g. after final manifestation spawn), fully clear all client state
            if (packet.nodes.isEmpty() && packet.hotspot == null) {
                LOG.debug("[ShadowHunt][Client] Empty trail sync received — clearing all hunt state")
                TrailClientState.clear()
                return@execute
            }

            TrailClientState.sync(packet.nodes, packet.hotspot)
            // Sync hunt metadata (tier, events, tension, quality)
            TrailClientState.syncHuntData(
                tier = packet.tier,
                eventTypes = packet.eventTypes,
                tension = packet.tension,
                trailQuality = packet.trailQuality,
                currentNodeIndex = packet.currentNodeIndex,
                huntSeed = packet.huntSeed
            )
            // Sync false trails and species trait
            TrailClientState.syncFalseTrails(packet.falseTrails)
            TrailClientState.syncSpeciesTrait(packet.speciesTraitId)
            // Small immediate visual hint so players see something right away
            val level = client.level ?: return@execute
            // Sprinkle a couple particles at each node (clamped for safety)
            val nodes = packet.nodes.take(12)
            nodes.forEach { p ->
                repeat(2) {
                    level.addParticle(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        p.x + 0.5 + (Math.random() - 0.5) * 0.2,
                        p.y + 0.4 + Math.random() * 0.6,
                        p.z + 0.5 + (Math.random() - 0.5) * 0.2,
                        0.0, 0.02, 0.0
                    )
                }
            }
            packet.hotspot?.let { h ->
                repeat(6) {
                    level.addParticle(
                        ParticleTypes.END_ROD,
                        h.x + 0.5 + (Math.random() - 0.5) * 0.4,
                        h.y + 0.8 + Math.random() * 0.4,
                        h.z + 0.5 + (Math.random() - 0.5) * 0.4,
                        0.0, 0.02, 0.0
                    )
                }
            }
        }
    }
}
