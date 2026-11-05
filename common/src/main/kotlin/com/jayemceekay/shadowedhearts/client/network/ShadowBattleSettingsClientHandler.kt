package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState
import com.jayemceekay.shadowedhearts.network.restrictions.ShadowBattleSettingsS2CPacket
import net.minecraft.client.Minecraft

object ShadowBattleSettingsClientHandler : ClientNetworkPacketHandler<ShadowBattleSettingsS2CPacket> {
    override fun handle(packet: ShadowBattleSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        if (client.isSameThread) ShadowBattleClientState.INSTANCE.receive(connection, packet.settings)
        else client.execute { ShadowBattleClientState.INSTANCE.receive(connection, packet.settings) }
    }
}
