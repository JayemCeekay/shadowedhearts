package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.jayemceekay.shadowedhearts.client.net.storage.purification.OpenPurificationChamberHandler
import com.jayemceekay.shadowedhearts.client.net.storage.purification.OpenPurificationChamberPacket
import com.jayemceekay.shadowedhearts.client.net.storage.purification.PurificationChamberSyncHandler
import com.jayemceekay.shadowedhearts.client.net.storage.purification.PurificationChamberSyncPacket
import net.minecraft.server.level.ServerPlayer

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
        add(PacketRegisterInfo(AuraStatePacket.ID, AuraStatePacket::decode, AuraStateHandler))
        add(PacketRegisterInfo(AuraLifecyclePacket.ID, AuraLifecyclePacket::decode, AuraLifecycleHandler))
        add(PacketRegisterInfo(LuminousMotePacket.ID, LuminousMotePacket::decode, LuminousMoteHandler))
        add(PacketRegisterInfo(SnagArmedPacket.ID, SnagArmedPacket::decode, SnagArmedHandler))
        add(PacketRegisterInfo(SnagEligibilityPacket.ID, SnagEligibilityPacket::decode, SnagEligibilityHandler))
        add(PacketRegisterInfo(SnagResultPacket.ID, SnagResultPacket::decode, SnagResultHandler))
        add(PacketRegisterInfo(PokemonPropertyUpdatePacket.ID, PokemonPropertyUpdatePacket::decode, com.cobblemon.mod.common.client.net.pokemon.update.PokemonUpdatePacketHandler()))
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
        add(
            PacketRegisterInfo(
                com.jayemceekay.shadowedhearts.network.purification.PurifyPokemonPacket.ID,
                com.jayemceekay.shadowedhearts.network.purification.PurifyPokemonPacket::decode,
                com.jayemceekay.shadowedhearts.network.purification.server.PurifyPokemonHandler
            )
        )
        add(PacketRegisterInfo(SnagArmPacket.ID, SnagArmPacket::decode, SnagArmHandler))
    }

    @JvmStatic
    fun sendToPlayer(player: ServerPlayer, packet: NetworkPacket<*>) {
        packet.sendToPlayer(player)
    }

    @JvmStatic
    fun sendToServer(packet: NetworkPacket<*>) {
        packet.sendToServer()
    }
}
