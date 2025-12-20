package com.jayemceekay.shadowedhearts.neoforge.net

import com.cobblemon.mod.neoforge.net.NeoForgePacketInfo
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.HandlerThread

/**
 * Registers ShadowedHearts packets using the same system Cobblemon uses (PacketRegisterInfo + PayloadRegistrar).
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID)
object ShadowedHeartsNeoForgeNetworkManager {
    private const val PROTOCOL_VERSION = "1.0.0"

    @SubscribeEvent
    @JvmStatic
    fun registerMessages(event: RegisterPayloadHandlersEvent) {
        val registrar = event
            .registrar(Shadowedhearts.MOD_ID)
            .versioned(PROTOCOL_VERSION)

        val netRegistrar = event
            .registrar(Shadowedhearts.MOD_ID)
            .versioned(PROTOCOL_VERSION)
            .executesOn(HandlerThread.NETWORK)

        ShadowedHeartsNetwork.s2cPayloads
            .map { NeoForgePacketInfo(it) }
            .forEach { it.registerToClient(registrar) }

        // Register C2S payloads to server
        ShadowedHeartsNetwork.c2sPayloads
            .map { NeoForgePacketInfo(it) }
            .forEach { it.registerToServer(netRegistrar) }
    }
}
