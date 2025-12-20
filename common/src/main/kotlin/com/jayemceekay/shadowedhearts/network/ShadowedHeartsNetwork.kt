package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.jayemceekay.shadowedhearts.client.net.storage.purification.OpenPurificationChamberHandler
import com.jayemceekay.shadowedhearts.client.net.storage.purification.OpenPurificationChamberPacket
import com.jayemceekay.shadowedhearts.client.net.storage.purification.PurificationChamberSyncHandler
import com.jayemceekay.shadowedhearts.client.net.storage.purification.PurificationChamberSyncPacket

/**
 * ShadowedHearts network payload listings following Cobblemon's PacketRegisterInfo system.
 */
object ShadowedHeartsNetwork {
    /**
     * Server -> Client payloads (playToClient) using Cobblemon's unified registration.
     */
    @JvmStatic
    val s2cPayloads: List<PacketRegisterInfo<*>> = buildList {
        add(PacketRegisterInfo(OpenPurificationChamberPacket.ID, OpenPurificationChamberPacket::decode, OpenPurificationChamberHandler))
        add(PacketRegisterInfo(PurificationChamberSyncPacket.ID, PurificationChamberSyncPacket::decode, PurificationChamberSyncHandler))
    }

    /**
     * Client -> Server payloads (playToServer)
     */
    @JvmStatic
    val c2sPayloads: List<PacketRegisterInfo<*>> = buildList {
        add(
            PacketRegisterInfo(
                com.jayemceekay.shadowedhearts.network.purification.MovePCToPurificationPacket.ID,
                com.jayemceekay.shadowedhearts.network.purification.MovePCToPurificationPacket::decode,
                com.jayemceekay.shadowedhearts.network.purification.server.MovePCToPurificationHandler
            )
        )
        add(
            PacketRegisterInfo(
                com.jayemceekay.shadowedhearts.network.purification.MovePurificationToPCPacket.ID,
                com.jayemceekay.shadowedhearts.network.purification.MovePurificationToPCPacket::decode,
                com.jayemceekay.shadowedhearts.network.purification.server.MovePurificationToPCHandler
            )
        )
        add(
            PacketRegisterInfo(
                com.jayemceekay.shadowedhearts.network.purification.UnlinkPlayerFromPurificationChamberPacket.ID,
                com.jayemceekay.shadowedhearts.network.purification.UnlinkPlayerFromPurificationChamberPacket::decode,
                com.jayemceekay.shadowedhearts.network.purification.server.UnlinkPlayerFromPurificationChamberHandler
            )
        )
    }
}
