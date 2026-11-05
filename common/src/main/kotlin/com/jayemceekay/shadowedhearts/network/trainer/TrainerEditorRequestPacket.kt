package com.jayemceekay.shadowedhearts.network.trainer

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Bounded authoring requests; even three-byte UTF-8 stays below the vanilla C2S payload limit. */
data class TrainerEditorRequestPacket(val json: String) : NetworkPacket<TrainerEditorRequestPacket> {
    init { require(json.length <= MAX_JSON_LENGTH) { "Trainer editor request is too large." } }
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeUtf(json, MAX_JSON_LENGTH) }

    companion object {
        const val MAX_JSON_LENGTH = 8192
        @JvmField val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "trainer_editor_request")
        @JvmStatic fun decode(buf: RegistryFriendlyByteBuf) = TrainerEditorRequestPacket(buf.readUtf(MAX_JSON_LENGTH))
    }
}
