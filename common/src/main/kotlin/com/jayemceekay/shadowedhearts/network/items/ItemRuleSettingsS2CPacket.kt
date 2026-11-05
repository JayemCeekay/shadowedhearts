package com.jayemceekay.shadowedhearts.network.items

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class ItemRuleSettingsS2CPacket(val settings: ItemRuleSettings) : NetworkPacket<ItemRuleSettingsS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(settings.shadowTMsEnabled())
        buffer.writeVarInt(settings.shadowTMAllowedMoves().size)
        settings.shadowTMAllowedMoves().sorted().forEach { buffer.writeUtf(it, 64) }
        buffer.writeVarInt(settings.disabledEffects().size)
        settings.disabledEffects().sorted().forEach { buffer.writeUtf(it, 64) }
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "item_rule_settings")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): ItemRuleSettingsS2CPacket {
            val enabled = buffer.readBoolean()
            fun readIds(): Set<String> {
                val count = buffer.readVarInt()
                require(count in 0..64) { "Invalid item rule list size" }
                return (0 until count).map { buffer.readUtf(64) }.toSet()
            }
            return ItemRuleSettingsS2CPacket(ItemRuleSettings(enabled, readIds(), readIds()))
        }
    }
}
