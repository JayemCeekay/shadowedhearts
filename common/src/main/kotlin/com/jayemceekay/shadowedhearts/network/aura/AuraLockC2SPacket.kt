package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraLockC2SPacket(val action: Action) : NetworkPacket<AuraLockC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(action)
    }

    enum class Action {
        LOCK_FOCUSED,
        CLEAR
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_lock_request")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): AuraLockC2SPacket {
            return AuraLockC2SPacket(buf.readEnum(Action::class.java))
        }
    }
}
