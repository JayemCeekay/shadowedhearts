package com.jayemceekay.shadowedhearts.network.trainer

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Empty target opens discovery; otherwise edits a loaded trainer with this UUID. */
data class OpenTrainerEditorPacket(val target: String) : NetworkPacket<OpenTrainerEditorPacket> {
    init { require(target.length <= 36) }
    override val id: ResourceLocation = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeUtf(target, 36) }

    companion object {
        @JvmField val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "open_trainer_editor")
        @JvmStatic fun decode(buf: RegistryFriendlyByteBuf) = OpenTrainerEditorPacket(buf.readUtf(36))
    }
}
