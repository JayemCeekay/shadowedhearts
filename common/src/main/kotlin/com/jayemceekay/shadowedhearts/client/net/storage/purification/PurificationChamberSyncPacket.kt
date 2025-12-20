package com.jayemceekay.shadowedhearts.client.net.storage.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.*

/**
 * Server -> Client: synchronize the contents of a PurificationChamberStore for a player.
 * Carries a compact list of occupied slots as (setIndex, slotIndex 0..4, pokemon NBT).
 */
class PurificationChamberSyncPacket(
    val storeID: UUID,
    val entries: List<Entry>
) : NetworkPacket<PurificationChamberSyncPacket> {
    override val id: ResourceLocation = ID

    data class Entry(val setIndex: Int, val slotIndex: Int, val pokemonNbt: CompoundTag)

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(storeID)
        buffer.writeInt(entries.size)
        entries.forEach { e ->
            buffer.writeInt(e.setIndex)
            buffer.writeInt(e.slotIndex)
            buffer.writeNbt(e.pokemonNbt)
        }
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "sync_purification_chamber")
        fun decode(buffer: RegistryFriendlyByteBuf): PurificationChamberSyncPacket {
            val store = buffer.readUUID()
            val count = buffer.readInt()
            val list = ArrayList<Entry>(count)
            repeat(count) {
                val setIdx = buffer.readInt()
                val slotIdx = buffer.readInt()
                val nbt = buffer.readNbt() ?: CompoundTag()
                list.add(Entry(setIdx, slotIdx, nbt))
            }
            return PurificationChamberSyncPacket(store, list)
        }
    }
}
