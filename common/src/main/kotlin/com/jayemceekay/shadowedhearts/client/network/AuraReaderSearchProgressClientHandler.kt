package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderClientState
import com.jayemceekay.shadowedhearts.client.sound.ShadowSignalSearchSoundManager
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderSearchProgressS2CPacket
import net.minecraft.client.Minecraft

object AuraReaderSearchProgressClientHandler : ClientNetworkPacketHandler<AuraReaderSearchProgressS2CPacket> {
    override fun handle(packet: AuraReaderSearchProgressS2CPacket, client: Minecraft) {
        if (client.player == null) return
        // Observe a newly joined/different level before applying the dedicated job snapshot.
        AuraReaderClientState.getReadings()
        ShadowSignalSearchSoundManager.accept(client, packet.sequence, packet.progress)
    }
}
