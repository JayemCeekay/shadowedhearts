package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.core.ModParticleTypes
import net.minecraft.client.Minecraft

object RelicStoneMoteHandler : ClientNetworkPacketHandler<RelicStoneMotePacket> {
    override fun handle(packet: RelicStoneMotePacket, client: Minecraft) {
        val level = client.level ?: return
        val pos = packet.pos
        val random = level.random

        // Spawn particles (moved from server-side block entity to client-side handler)
        // Increased density: from 1-2 to 2-4
        val count = 2 + random.nextInt(3)
        for (i in 0 until count) {
            val ox = (random.nextDouble() - 0.5) * 1.5
            val oz = (random.nextDouble() - 0.5) * 1.5
            val oy = random.nextDouble() * 2.5

            val vx = (random.nextDouble() - 0.5) * 0.15
            val vz = (random.nextDouble() - 0.5) * 0.15
            val vy = 0.02 + random.nextDouble() * 0.05

            level.addParticle(
                ModParticleTypes.RELIC_STONE_MOTE.get(),
                pos.x + 0.5 + ox,
                pos.y + 0.1 + oy,
                pos.z + 0.5 + oz,
                vx, vy, vz
            )
        }
    }
}
