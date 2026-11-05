package com.jayemceekay.shadowedhearts.network.trainer

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class TrainerEditorResponsePacket(val json: String) : NetworkPacket<TrainerEditorResponsePacket> {
    init { require(json.length <= MAX_JSON_LENGTH) { "Trainer editor response is too large." } }
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeUtf(json, MAX_JSON_LENGTH) }

    companion object {
        const val MAX_JSON_LENGTH = 200000
        @JvmField val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "trainer_editor_response")
        @JvmStatic fun decode(buf: RegistryFriendlyByteBuf) = TrainerEditorResponsePacket(buf.readUtf(MAX_JSON_LENGTH))
    }
}
