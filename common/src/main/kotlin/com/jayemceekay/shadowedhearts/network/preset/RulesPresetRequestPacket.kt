package com.jayemceekay.shadowedhearts.network.preset

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class RulesPresetRequestPacket(val json: String) : NetworkPacket<RulesPresetRequestPacket> {
    init { require(json.length <= MAX_JSON_LENGTH) { "Rules preset request is too large." } }
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeUtf(json, MAX_JSON_LENGTH) }

    companion object {
        const val MAX_JSON_LENGTH = 4096
        @JvmField val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "rules_preset_request")
        @JvmStatic fun decode(buffer: RegistryFriendlyByteBuf) = RulesPresetRequestPacket(buffer.readUtf(MAX_JSON_LENGTH))
    }
}
