package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.core.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries

object PlaySoundHandler : ClientNetworkPacketHandler<PlaySoundPacket> {
    override fun handle(packet: PlaySoundPacket, client: Minecraft) {
        val level = client.level ?: return
        val soundEvent = BuiltInRegistries.SOUND_EVENT.get(packet.soundId) ?: return

        val volume = when (packet.soundId) {
            ModSounds.SHADOW_AURA_INITIAL_BURST.id -> ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().shadowAuraInitialBurstVolume()
            ModSounds.SHADOW_AURA_LOOP.id -> ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().shadowAuraLoopVolume()
            ModSounds.AURA_SCANNER_BEEP.id -> ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().auraScannerBeepVolume()
            ModSounds.RELIC_SHRINE_LOOP.id -> ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().relicShrineLoopVolume()
            ModSounds.AURA_READER_EQUIP.id -> ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().auraReaderEquipVolume()
            ModSounds.AURA_READER_UNEQUIP.id -> ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().auraReaderUnequipVolume()
            else -> 1.0f
        }

        level.playSound(client.player, packet.x, packet.y, packet.z, soundEvent, packet.source, volume, packet.pitch)
    }
}
