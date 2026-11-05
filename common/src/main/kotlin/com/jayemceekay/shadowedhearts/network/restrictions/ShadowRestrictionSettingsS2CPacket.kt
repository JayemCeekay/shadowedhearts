package com.jayemceekay.shadowedhearts.network.restrictions

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionSettings
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Complete Heart Gauge policy, sent on join and after live server configuration changes. */
data class ShadowRestrictionSettingsS2CPacket(val settings: ShadowRestrictionSettings) : NetworkPacket<ShadowRestrictionSettingsS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(settings.bufferExperience())
        buffer.writeBoolean(settings.bufferEVs())
        buffer.writeBoolean(settings.blockFriendshipChanges())
        buffer.writeBoolean(settings.blockEvolution())
        buffer.writeBoolean(settings.blockNicknaming())
        buffer.writeBoolean(settings.blockTrading())
        buffer.writeBoolean(settings.blockMoveChanges())
        buffer.writeBoolean(settings.blockMoveTeaching())
        buffer.writeBoolean(settings.hideNature())
        buffer.writeBoolean(settings.hideLevelAndExperience())
        buffer.writeBoolean(settings.hideEVs())
        buffer.writeBoolean(settings.hideIVs())
        buffer.writeBoolean(settings.restrictNonShadowMoves())
        buffer.writeVarInt(settings.natureRevealPercent())
        buffer.writeVarInt(settings.levelExpRevealPercent())
        buffer.writeVarInt(settings.evRevealPercent())
        buffer.writeVarInt(settings.ivRevealPercent())
        buffer.writeVarInt(settings.firstMoveUnlockPercent())
        buffer.writeVarInt(settings.secondMoveUnlockPercent())
        buffer.writeVarInt(settings.thirdMoveUnlockPercent())
        buffer.writeBoolean(settings.exactThresholds())
        buffer.writeBoolean(settings.blockHyperTraining())
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_restriction_settings")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf) = ShadowRestrictionSettingsS2CPacket(
            ShadowRestrictionSettings(
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean()
            )
        )
    }
}
