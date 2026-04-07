package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.trail.TrailClientState
import com.jayemceekay.shadowedhearts.network.trail.ManifestationSyncS2CPacket
import net.minecraft.client.Minecraft

/**
 * Applies ManifestationSyncS2CPacket to client state for buildup VFX.
 */
object ManifestationSyncClientHandler : ClientNetworkPacketHandler<ManifestationSyncS2CPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: ManifestationSyncS2CPacket, client: Minecraft) {
        client.execute {
            LOG.debug("[ShadowHunt][Client] ManifestationSync: phase={}, progress={}, target={}, tension={}",
                packet.phase, packet.progress, packet.convergenceTarget, packet.tension)
            TrailClientState.syncManifestation(
                phase = packet.phase,
                progress = packet.progress,
                convergenceTarget = packet.convergenceTarget,
                tension = packet.tension
            )
        }
    }
}
