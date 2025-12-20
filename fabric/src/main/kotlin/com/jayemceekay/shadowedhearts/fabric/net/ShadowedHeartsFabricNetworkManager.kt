package com.jayemceekay.shadowedhearts.fabric.net

import com.cobblemon.mod.fabric.net.FabricPacketInfo
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork

/**
 * Registers ShadowedHearts packets on Fabric using Cobblemon's FabricPacketInfo wrapper.
 */
object ShadowedHeartsFabricNetworkManager {
    @JvmStatic
    fun registerMessages() {
        ShadowedHeartsNetwork.s2cPayloads.map { FabricPacketInfo(it) }.forEach { it.registerPacket(client = true) }
    }

    @JvmStatic
    fun registerClientHandlers() {
        ShadowedHeartsNetwork.s2cPayloads.map { FabricPacketInfo(it) }.forEach { it.registerClientHandler() }
    }

    @JvmStatic
    fun registerServerHandlers() {
        // Register server-side payload types and handlers for C2S
        ShadowedHeartsNetwork.c2sPayloads.map { FabricPacketInfo(it) }.forEach {
            it.registerPacket(client = false)
            it.registerServerHandler()
        }
    }
}
