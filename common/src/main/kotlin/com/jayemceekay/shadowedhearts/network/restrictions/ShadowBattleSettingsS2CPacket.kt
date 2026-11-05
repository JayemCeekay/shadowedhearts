package com.jayemceekay.shadowedhearts.network.restrictions

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleSettings
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class ShadowBattleSettingsS2CPacket(val settings: ShadowBattleSettings) : NetworkPacket<ShadowBattleSettingsS2CPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(settings.colosseumRush())
        buffer.writeBoolean(settings.unlimitedShadowPP())
        buffer.writeBoolean(settings.hyperShadowRushCrit())
    }
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_battle_settings")
        @JvmStatic fun decode(buffer: RegistryFriendlyByteBuf) = ShadowBattleSettingsS2CPacket(
            ShadowBattleSettings(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()))
    }
}
