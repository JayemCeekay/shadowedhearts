package com.jayemceekay.shadowedhearts.client.net.storage.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.*

class OpenPurificationChamberPacket : NetworkPacket<OpenPurificationChamberPacket> {
    val storeID: UUID

    @JvmOverloads
    constructor(storeID: UUID) {
        this.storeID = storeID
    }

    @JvmOverloads
    constructor(purificationChamber: PurificationChamberStore): this(purificationChamber.uuid)

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(storeID)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "open_purification_chamber")
        fun decode(buffer: RegistryFriendlyByteBuf) = OpenPurificationChamberPacket(buffer.readUUID())
    }
}