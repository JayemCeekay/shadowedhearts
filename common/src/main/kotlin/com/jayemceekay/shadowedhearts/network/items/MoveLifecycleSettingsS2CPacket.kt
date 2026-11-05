package com.jayemceekay.shadowedhearts.network.items

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class MoveLifecycleSettingsS2CPacket(val removalAllowed: Boolean) : NetworkPacket<MoveLifecycleSettingsS2CPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeBoolean(removalAllowed) }
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "move_lifecycle_settings")
        @JvmStatic fun decode(buffer: RegistryFriendlyByteBuf) = MoveLifecycleSettingsS2CPacket(buffer.readBoolean())
    }
}
