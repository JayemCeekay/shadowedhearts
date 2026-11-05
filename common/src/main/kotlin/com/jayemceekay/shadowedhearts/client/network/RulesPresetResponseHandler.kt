package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.gui.preset.RulesPresetClient
import com.jayemceekay.shadowedhearts.network.preset.RulesPresetResponsePacket
import net.minecraft.client.Minecraft

object RulesPresetResponseHandler : ClientNetworkPacketHandler<RulesPresetResponsePacket> {
    override fun handle(packet: RulesPresetResponsePacket, client: Minecraft) {
        client.execute { if (client.level != null) RulesPresetClient.receive(packet.json) }
    }
}
