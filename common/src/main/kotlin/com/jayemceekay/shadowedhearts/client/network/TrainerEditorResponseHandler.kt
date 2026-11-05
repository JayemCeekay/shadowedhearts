package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorClient
import com.jayemceekay.shadowedhearts.network.trainer.TrainerEditorResponsePacket
import com.jayemceekay.shadowedhearts.network.trainer.OpenTrainerEditorPacket
import net.minecraft.client.Minecraft

object TrainerEditorResponseHandler : ClientNetworkPacketHandler<TrainerEditorResponsePacket> {
    override fun handle(packet: TrainerEditorResponsePacket, client: Minecraft) {
        client.execute { if (client.level != null) TrainerEditorClient.receive(packet.json) }
    }
}

object OpenTrainerEditorHandler : ClientNetworkPacketHandler<OpenTrainerEditorPacket> {
    override fun handle(packet: OpenTrainerEditorPacket, client: Minecraft) {
        client.execute {
            if (client.level != null && client.player != null) TrainerEditorClient.open(client.screen, packet.target)
        }
    }
}
