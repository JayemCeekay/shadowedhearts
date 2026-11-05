package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.items.ItemRuleClientState
import com.jayemceekay.shadowedhearts.network.items.ItemRuleSettingsS2CPacket
import net.minecraft.client.Minecraft

object ItemRuleSettingsClientHandler : ClientNetworkPacketHandler<ItemRuleSettingsS2CPacket> {
    override fun handle(packet: ItemRuleSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        if (client.isSameThread) {
            ItemRuleClientState.INSTANCE.receive(connection, packet.settings)
        } else {
            client.execute { ItemRuleClientState.INSTANCE.receive(connection, packet.settings) }
        }
    }
}
