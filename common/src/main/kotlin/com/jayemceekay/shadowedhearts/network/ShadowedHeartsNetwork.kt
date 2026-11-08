package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.jayemceekay.shadowedhearts.client.network.*
import com.jayemceekay.shadowedhearts.network.aura.*
import com.jayemceekay.shadowedhearts.network.items.NullCharmEffectS2CPacket
import com.jayemceekay.shadowedhearts.network.items.DarkBallSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.items.SnagMachineSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.items.MoveLifecycleSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.items.ItemRuleSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.items.ShadowLearnsetsS2CPacket
import com.jayemceekay.shadowedhearts.network.restrictions.ShadowRestrictionSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.restrictions.HeartGaugeSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.restrictions.ShadowBattleSettingsS2CPacket
import com.jayemceekay.shadowedhearts.network.infusion.*
import com.jayemceekay.shadowedhearts.network.moonwound.*
import com.jayemceekay.shadowedhearts.network.purification.*
import com.jayemceekay.shadowedhearts.network.purification.client.*
import com.jayemceekay.shadowedhearts.network.purification.server.MovePCToPurificationHandler
import com.jayemceekay.shadowedhearts.network.purification.server.MovePurificationToPCHandler
import com.jayemceekay.shadowedhearts.network.purification.server.PurifyPokemonHandler
import com.jayemceekay.shadowedhearts.network.purification.server.UnlinkPlayerFromPurificationChamberHandler
import com.jayemceekay.shadowedhearts.network.snag.*
import com.jayemceekay.shadowedhearts.network.trainer.*
import com.jayemceekay.shadowedhearts.network.preset.*
import com.jayemceekay.shadowedhearts.network.voidshadow.*
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
        add(PacketRegisterInfo(AuraStyleDefaultS2CPacket.ID, AuraStyleDefaultS2CPacket::decode, AuraStyleDefaultClientHandler))
        add(PacketRegisterInfo(NullCharmEffectS2CPacket.ID, NullCharmEffectS2CPacket::decode, NullCharmEffectClientHandler))
        add(PacketRegisterInfo(DarkBallSettingsS2CPacket.ID, DarkBallSettingsS2CPacket::decode, DarkBallSettingsClientHandler))
        add(PacketRegisterInfo(ShadowRestrictionSettingsS2CPacket.ID, ShadowRestrictionSettingsS2CPacket::decode, ShadowRestrictionSettingsClientHandler))
        add(PacketRegisterInfo(HeartGaugeSettingsS2CPacket.ID, HeartGaugeSettingsS2CPacket::decode, HeartGaugeSettingsClientHandler))
        add(PacketRegisterInfo(UmbraInfuserStatePacket.ID, UmbraInfuserStatePacket::decode, UmbraInfuserStateHandler))
        add(PacketRegisterInfo(MoonwoundStatePacket.ID, MoonwoundStatePacket::decode, MoonwoundStateHandler))
        add(PacketRegisterInfo(MoonwoundClosedPacket.ID, MoonwoundClosedPacket::decode, MoonwoundClosedHandler))
        add(PacketRegisterInfo(OpenTrainerEditorPacket.ID, OpenTrainerEditorPacket::decode, OpenTrainerEditorHandler))
        add(PacketRegisterInfo(TrainerEditorResponsePacket.ID, TrainerEditorResponsePacket::decode, TrainerEditorResponseHandler))
        add(PacketRegisterInfo(RulesPresetResponsePacket.ID, RulesPresetResponsePacket::decode, RulesPresetResponseHandler))
        add(PacketRegisterInfo(SnagMachineSettingsS2CPacket.ID, SnagMachineSettingsS2CPacket::decode, SnagMachineSettingsClientHandler))
        add(PacketRegisterInfo(MoveLifecycleSettingsS2CPacket.ID, MoveLifecycleSettingsS2CPacket::decode, MoveLifecycleSettingsClientHandler))
        add(PacketRegisterInfo(ItemRuleSettingsS2CPacket.ID, ItemRuleSettingsS2CPacket::decode, ItemRuleSettingsClientHandler))
        add(PacketRegisterInfo(ShadowLearnsetsS2CPacket.ID, ShadowLearnsetsS2CPacket::decode, ShadowLearnsetsClientHandler))
        add(PacketRegisterInfo(ShadowBattleSettingsS2CPacket.ID, ShadowBattleSettingsS2CPacket::decode, ShadowBattleSettingsClientHandler))
        add(PacketRegisterInfo(OpenPurificationChamberPacket.ID, OpenPurificationChamberPacket::decode, OpenPurificationChamberHandler))
        add(PacketRegisterInfo(PurificationChamberSyncPacket.ID, PurificationChamberSyncPacket::decode, PurificationChamberSyncHandler))
        add(PacketRegisterInfo(PokemonPurifiedPacket.ID, PokemonPurifiedPacket::decode, PokemonPurifiedHandler))
        add(PacketRegisterInfo(AuraStatePacket.ID, AuraStatePacket::decode, AuraStateHandler))
        add(PacketRegisterInfo(AuraLifecyclePacket.ID, AuraLifecyclePacket::decode, AuraLifecycleHandler))
        add(PacketRegisterInfo(AuraReaderStateS2CPacket.ID, AuraReaderStateS2CPacket::decode, AuraReaderStateClientHandler))
        add(PacketRegisterInfo(AuraReaderSearchProgressS2CPacket.ID, AuraReaderSearchProgressS2CPacket::decode, AuraReaderSearchProgressClientHandler))
        add(PacketRegisterInfo(AuraReaderPulseResultS2CPacket.ID, AuraReaderPulseResultS2CPacket::decode, AuraReaderPulseResultClientHandler))
        add(PacketRegisterInfo(AuraReaderBattleDetectionS2CPacket.ID, AuraReaderBattleDetectionS2CPacket::decode, AuraReaderBattleDetectionClientHandler))
        add(PacketRegisterInfo(LuminousMotePacket.ID, LuminousMotePacket::decode, LuminousMoteHandler))
        add(PacketRegisterInfo(RelicStoneMotePacket.ID, RelicStoneMotePacket::decode, RelicStoneMoteHandler))
        add(PacketRegisterInfo(SnagArmedPacket.ID, SnagArmedPacket::decode, SnagArmedHandler))
        add(PacketRegisterInfo(SnagEligibilityPacket.ID, SnagEligibilityPacket::decode, SnagEligibilityHandler))
        add(PacketRegisterInfo(SnagResultPacket.ID, SnagResultPacket::decode, SnagResultHandler))
        add(PacketRegisterInfo(PokemonPropertyUpdatePacket.ID, PokemonPropertyUpdatePacket::decode, PokemonPropertyUpdateHandler))
        add(PacketRegisterInfo(AuraScannerS2CPacket.ID, AuraScannerS2CPacket::decode, AuraScannerClientHandler))
        add(PacketRegisterInfo(PlaySoundPacket.ID, PlaySoundPacket::decode, PlaySoundHandler))
        add(PacketRegisterInfo(VoidShadowTransformPacket.ID, VoidShadowTransformPacket::decode, VoidShadowTransformHandler))
    }

    /**
     * Client -> Server payloads (playToServer)
     */
    @JvmStatic
    val c2sPayloads: List<PacketRegisterInfo<*>> = buildList {
        add(PacketRegisterInfo(UmbraInfuserActionPacket.ID, UmbraInfuserActionPacket::decode, UmbraInfuserActionHandler))
        add(PacketRegisterInfo(MoonwoundActionPacket.ID, MoonwoundActionPacket::decode, MoonwoundActionHandler))
        add(PacketRegisterInfo(TrainerEditorRequestPacket.ID, TrainerEditorRequestPacket::decode, TrainerEditorRequestHandler))
        add(PacketRegisterInfo(RulesPresetRequestPacket.ID, RulesPresetRequestPacket::decode, RulesPresetRequestHandler))
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
