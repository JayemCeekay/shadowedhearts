package com.jayemceekay.shadowedhearts.network.purification.client

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.gui.PurificationChamberGUI
import com.jayemceekay.shadowedhearts.client.purification.PurificationChamberClientSession
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorageManager
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance

object OpenPurificationChamberHandler: ClientNetworkPacketHandler<OpenPurificationChamberPacket> {
    override fun handle(
        packet: OpenPurificationChamberPacket,
        client: Minecraft
    ) {
        val storage = ClientPurificationStorageManager.getOrCreate(packet.storeID)
        PurificationChamberClientSession.open(client, PurificationChamberGUI(storage))
        client.soundManager.play(SimpleSoundInstance.forUI(
            CobblemonSounds.PC_ON, 1.0f))
    }
}
