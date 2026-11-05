package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionClientState
import com.jayemceekay.shadowedhearts.network.restrictions.ShadowRestrictionSettingsS2CPacket
import net.minecraft.client.Minecraft

object ShadowRestrictionSettingsClientHandler : ClientNetworkPacketHandler<ShadowRestrictionSettingsS2CPacket> {
    override fun handle(packet: ShadowRestrictionSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        if (client.isSameThread) {
            ShadowRestrictionClientState.INSTANCE.receive(connection, packet.settings)
        } else {
            client.execute { ShadowRestrictionClientState.INSTANCE.receive(connection, packet.settings) }
        }
    }
}
