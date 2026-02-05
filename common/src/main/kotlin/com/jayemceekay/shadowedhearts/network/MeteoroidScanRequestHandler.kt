package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.core.ModBlocks
import com.jayemceekay.shadowedhearts.core.ModPoiTypes
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.village.poi.PoiManager
import kotlin.math.roundToInt

/**
 * Handles client requests to scan for meteoroid locations using POI index.
 * Filters out locations where the meteoroid blocks have been removed.
 */
object MeteoroidScanRequestHandler : ServerNetworkPacketHandler<MeteoroidScanRequestPacket> {
    override fun handle(packet: MeteoroidScanRequestPacket, server: MinecraftServer, player: ServerPlayer) {
        val level: ServerLevel = player.serverLevel()

        // Clamp range to server-side config to avoid abuse
        val maxRange = ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerMeteoroidRange()
        val range = packet.range.coerceIn(8, maxRange)
        val center = player.blockPosition()

        val poiManager: PoiManager = level.poiManager
        val poiType = ModPoiTypes.SHADOWFALL_METEOROID.get()

        // Query POIs in range
        val stream: java.util.stream.Stream<net.minecraft.world.entity.ai.village.poi.PoiRecord> =
            poiManager.getInRange({ holder -> holder.value() == poiType }, center, range, PoiManager.Occupancy.ANY)
        val poiPositions: List<BlockPos> = stream.map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
            .collect(java.util.stream.Collectors.toList())

        // Validate each POI still has at least one meteoroid block nearby
        val validPositions = poiPositions.filter { pos ->
            hasAnyMeteoroidBlock(level, pos, 3)
        }

        // Cluster positions that are part of the same meteoroid (radius 3)
        val centers = clusterPositions(validPositions, 3)

        // Send back to requesting player
        ShadowedHeartsNetwork.sendToPlayer(player, MeteoroidScanResultPacket(centers))
    }

    private fun hasAnyMeteoroidBlock(level: ServerLevel, origin: BlockPos, radius: Int): Boolean {
        val block = ModBlocks.SHADOWFALL_METEOROID.get()
        for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
            val p = origin.offset(dx, dy, dz)
            if (level.getBlockState(p).`is`(block)) return true
        }
        return false
    }

    private fun clusterPositions(positions: List<BlockPos>, radius: Int): List<BlockPos> {
        val remaining = positions.toMutableSet()
        val out = mutableListOf<BlockPos>()
        val r2 = radius * radius
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            remaining.remove(start)
            var sumX = 0L
            var sumY = 0L
            var sumZ = 0L
            var count = 0
            val queue: ArrayDeque<BlockPos> = ArrayDeque()
            queue.add(start)
            val visited = mutableSetOf<BlockPos>()
            visited.add(start)

            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                sumX += cur.x
                sumY += cur.y
                sumZ += cur.z
                count++
                // search neighbors inside sphere
                for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
                    val d2 = dx*dx + dy*dy + dz*dz
                    if (d2 <= r2) {
                        val n = cur.offset(dx, dy, dz)
                        if (!visited.contains(n) && remaining.contains(n)) {
                            visited.add(n)
                            remaining.remove(n)
                            queue.add(n)
                        }
                    }
                }
            }
            if (count > 0) {
                val cx = (sumX.toDouble() / count).roundToInt()
                val cy = (sumY.toDouble() / count).roundToInt()
                val cz = (sumZ.toDouble() / count).roundToInt()
                out.add(BlockPos(cx, cy, cz))
            }
        }
        return out
    }
}
