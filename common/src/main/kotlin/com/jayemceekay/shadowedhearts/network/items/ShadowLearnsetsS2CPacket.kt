package com.jayemceekay.shadowedhearts.network.items

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.data.ShadowLearnset
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

data class ShadowLearnsetsS2CPacket(
    val first: Boolean,
    val last: Boolean,
    val profiles: Map<ResourceLocation, ShadowLearnset>
) : NetworkPacket<ShadowLearnsetsS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(first)
        buffer.writeBoolean(last)
        buffer.writeVarInt(profiles.size)
        for ((species, profile) in profiles) {
            buffer.writeResourceLocation(species)
            buffer.writeByte(profile.startingShadowSlots())
            fun writeEntries(entries: List<ShadowLearnset.Entry>, levelUp: Boolean) {
                buffer.writeVarInt(entries.size)
                for (entry in entries) {
                    buffer.writeUtf(entry.move())
                    if (levelUp) buffer.writeVarInt(entry.level())
                    buffer.writeByte((if (entry.requiresShadow()) 1 else 0) or (if (entry.retainOnPurification()) 2 else 0))
                }
            }
            writeEntries(profile.levelUp(), true)
            writeEntries(profile.tm(), false)
        }
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadow_learnsets")
        private const val BATCH_BYTES = 512 * 1024

        /** ASCII IDs, at most five bytes per VarInt. Keep full regional dexes below the payload limit. */
        private fun sizeBound(species: ResourceLocation, profile: ShadowLearnset): Long {
            fun entries(list: List<ShadowLearnset.Entry>) = list.sumOf { it.move().length.toLong() + 11L }
            return species.toString().length + 16L + entries(profile.levelUp()) + entries(profile.tm())
        }

        @JvmStatic
        fun checkSize(species: ResourceLocation, profile: ShadowLearnset) {
            require(species.toString().length <= 32767) { "Species ID exceeds the UTF string limit" }
            require(sizeBound(species, profile) <= BATCH_BYTES) { "Profile is too large to synchronize" }
        }

        @JvmStatic
        fun send(player: ServerPlayer, profiles: Map<ResourceLocation, ShadowLearnset>) {
            var batch = mutableMapOf<ResourceLocation, ShadowLearnset>()
            var bytes = 0L
            var first = true
            for ((id, profile) in profiles) {
                val size = sizeBound(id, profile)
                if (batch.isNotEmpty() && bytes + size > BATCH_BYTES) {
                    ShadowLearnsetsS2CPacket(first, false, batch).sendToPlayer(player)
                    first = false
                    batch = mutableMapOf()
                    bytes = 0L
                }
                batch[id] = profile
                bytes += size
            }
            ShadowLearnsetsS2CPacket(first, true, batch).sendToPlayer(player)
        }

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf): ShadowLearnsetsS2CPacket {
            val first = buffer.readBoolean()
            val last = buffer.readBoolean()
            fun count(): Int = buffer.readVarInt().also { require(it in 0..buffer.readableBytes()) }
            fun readEntries(levelUp: Boolean): List<ShadowLearnset.Entry> = List(count()) {
                val move = buffer.readUtf()
                val level = if (levelUp) buffer.readVarInt() else 1
                val flags = buffer.readUnsignedByte().toInt()
                require(flags in 0..3)
                ShadowLearnset.Entry(level, move, flags and 1 != 0, flags and 2 != 0)
            }
            val profiles = mutableMapOf<ResourceLocation, ShadowLearnset>()
            repeat(count()) {
                val species = buffer.readResourceLocation()
                val slots = buffer.readUnsignedByte().toInt()
                profiles[species] = ShadowLearnset(slots, readEntries(true), readEntries(false))
            }
            return ShadowLearnsetsS2CPacket(first, last, profiles)
        }
    }
}
