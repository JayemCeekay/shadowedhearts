package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.NullCharmClientState
import com.jayemceekay.shadowedhearts.network.items.NullCharmEffectS2CPacket
import net.minecraft.client.Minecraft

object NullCharmEffectClientHandler : ClientNetworkPacketHandler<NullCharmEffectS2CPacket> {
    override fun handle(packet: NullCharmEffectS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        // Preserve the originating connection if a caller must enqueue this update.
        if (client.isSameThread) {
            NullCharmClientState.INSTANCE.receive(connection, packet.effect)
        } else {
            client.execute { NullCharmClientState.INSTANCE.receive(connection, packet.effect) }
        }
    }
}
