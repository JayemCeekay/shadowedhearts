package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.jayemceekay.shadowedhearts.client.network.*
import com.jayemceekay.shadowedhearts.network.aura.*
import com.jayemceekay.shadowedhearts.network.purification.*
import com.jayemceekay.shadowedhearts.network.purification.client.*
import com.jayemceekay.shadowedhearts.network.purification.server.MovePCToPurificationHandler
import com.jayemceekay.shadowedhearts.network.purification.server.MovePurificationToPCHandler
import com.jayemceekay.shadowedhearts.network.purification.server.PurifyPokemonHandler
import com.jayemceekay.shadowedhearts.network.purification.server.UnlinkPlayerFromPurificationChamberHandler
import com.jayemceekay.shadowedhearts.network.snag.*
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
        add(PacketRegisterInfo(PokemonPurifiedPacket.ID, PokemonPurifiedPacket::decode, PokemonPurifiedHandler))
        add(PacketRegisterInfo(AuraStatePacket.ID, AuraStatePacket::decode, AuraStateHandler))
        add(PacketRegisterInfo(AuraLifecyclePacket.ID, AuraLifecyclePacket::decode, AuraLifecycleHandler))
        add(PacketRegisterInfo(AuraReaderStateS2CPacket.ID, AuraReaderStateS2CPacket::decode, AuraReaderStateClientHandler))
        add(PacketRegisterInfo(AuraReaderPulseResultS2CPacket.ID, AuraReaderPulseResultS2CPacket::decode, AuraReaderPulseResultClientHandler))
        add(PacketRegisterInfo(LuminousMotePacket.ID, LuminousMotePacket::decode, LuminousMoteHandler))
        add(PacketRegisterInfo(RelicStoneMotePacket.ID, RelicStoneMotePacket::decode, RelicStoneMoteHandler))
        add(PacketRegisterInfo(SnagArmedPacket.ID, SnagArmedPacket::decode, SnagArmedHandler))
        add(PacketRegisterInfo(SnagEligibilityPacket.ID, SnagEligibilityPacket::decode, SnagEligibilityHandler))
        add(PacketRegisterInfo(SnagResultPacket.ID, SnagResultPacket::decode, SnagResultHandler))
        add(PacketRegisterInfo(PokemonPropertyUpdatePacket.ID, PokemonPropertyUpdatePacket::decode, PokemonPropertyUpdateHandler))
        add(PacketRegisterInfo(AuraScannerS2CPacket.ID, AuraScannerS2CPacket::decode, AuraScannerClientHandler))
        add(PacketRegisterInfo(MeteoroidScanResultPacket.ID, MeteoroidScanResultPacket::decode, MeteoroidScanResultClientHandler))
        add(PacketRegisterInfo(PlaySoundPacket.ID, PlaySoundPacket::decode, PlaySoundHandler))
    }

    /**
     * Client -> Server payloads (playToServer)
     */
    @JvmStatic
    val c2sPayloads: List<PacketRegisterInfo<*>> = buildList {
        add(
            PacketRegisterInfo(
                MovePCToPurificationPacket.ID,
                MovePCToPurificationPacket::decode,
                MovePCToPurificationHandler
            )
        )
        add(
            PacketRegisterInfo(
                MovePurificationToPCPacket.ID,
                MovePurificationToPCPacket::decode,
                MovePurificationToPCHandler
            )
        )
        add(
            PacketRegisterInfo(
                UnlinkPlayerFromPurificationChamberPacket.ID,
                UnlinkPlayerFromPurificationChamberPacket::decode,
                UnlinkPlayerFromPurificationChamberHandler
            )
        )
        add(
            PacketRegisterInfo(
                PurifyPokemonPacket.ID,
                PurifyPokemonPacket::decode,
                PurifyPokemonHandler
            )
        )
        add(PacketRegisterInfo(SnagArmPacket.ID, SnagArmPacket::decode, SnagArmHandler))
        add(PacketRegisterInfo(AuraPulsePacket.ID, AuraPulsePacket::decode, AuraPulseHandler))
        add(PacketRegisterInfo(AuraScannerC2SPacket.ID, AuraScannerC2SPacket::decode, AuraScannerHandler))
        add(PacketRegisterInfo(AuraReaderModeC2SPacket.ID, AuraReaderModeC2SPacket::decode, AuraReaderModeHandler))
        add(PacketRegisterInfo(AuraReaderSelectSignalC2SPacket.ID, AuraReaderSelectSignalC2SPacket::decode, AuraReaderSelectSignalHandler))
        add(PacketRegisterInfo(MeteoroidScanRequestPacket.ID, MeteoroidScanRequestPacket::decode, MeteoroidScanRequestHandler))
        add(PacketRegisterInfo(AuraLockC2SPacket.ID, AuraLockC2SPacket::decode, AuraLockHandler))
        add(PacketRegisterInfo(AuraTrackingStateC2SPacket.ID, AuraTrackingStateC2SPacket::decode, AuraTrackingStateHandler))
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
