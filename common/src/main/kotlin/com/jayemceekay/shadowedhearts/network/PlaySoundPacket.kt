package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource

data class PlaySoundPacket(
    val soundId: ResourceLocation,
    val source: SoundSource,
    val x: Double,
    val y: Double,
    val z: Double,
    val pitch: Float
) : NetworkPacket<PlaySoundPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeResourceLocation(soundId)
        buf.writeEnum(source)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        buf.writeFloat(pitch)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "play_sound")

        fun decode(buf: RegistryFriendlyByteBuf): PlaySoundPacket {
            return PlaySoundPacket(
                buf.readResourceLocation(),
                buf.readEnum(SoundSource::class.java),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat()
            )
        }
    }
}
