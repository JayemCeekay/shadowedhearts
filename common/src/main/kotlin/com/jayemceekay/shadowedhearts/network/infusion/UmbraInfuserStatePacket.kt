package com.jayemceekay.shadowedhearts.network.infusion

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserMenu
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import io.netty.handler.codec.DecoderException
import java.util.UUID

/** Machine state is sent only to the player who currently has this native menu open. */
data class UmbraInfuserStatePacket @JvmOverloads constructor(
    val containerId: Int,
    val ownerUUID: UUID?,
    val charge: Int,
    val capacity: Int,
    val progress: Int,
    val duration: Int,
    val requiredCharge: Int,
    val expansionCount: Int,
    val statusKey: String,
    val refreshPokemon: Boolean,
    val pokemonNbt: CompoundTag?,
    val itemsEnabled: Boolean = true,
    val eligiblePartyPokemon: List<UUID> = emptyList()
) : NetworkPacket<UmbraInfuserStatePacket> {
    init {
        require(eligiblePartyPokemon.size <= MAX_ELIGIBLE_PARTY_POKEMON) { "Too many eligible party Pokemon" }
        require(eligiblePartyPokemon.distinct().size == eligiblePartyPokemon.size) { "Duplicate eligible party Pokemon" }
    }
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(containerId)
        buffer.writeBoolean(ownerUUID != null)
        ownerUUID?.let(buffer::writeUUID)
        buffer.writeVarInt(charge)
        buffer.writeVarInt(capacity)
        buffer.writeVarInt(progress)
        buffer.writeVarInt(duration)
        buffer.writeVarInt(requiredCharge)
        buffer.writeVarInt(expansionCount)
        buffer.writeUtf(statusKey, 128)
        buffer.writeBoolean(refreshPokemon)
        if (refreshPokemon) buffer.writeNbt(pokemonNbt)
        buffer.writeBoolean(itemsEnabled)
        buffer.writeVarInt(eligiblePartyPokemon.size)
        eligiblePartyPokemon.forEach(buffer::writeUUID)
    }

    companion object {
        const val MAX_ELIGIBLE_PARTY_POKEMON = 6
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "umbra_infuser_state")
        fun decode(buffer: RegistryFriendlyByteBuf): UmbraInfuserStatePacket {
            val containerId = buffer.readVarInt()
            val owner = if (buffer.readBoolean()) buffer.readUUID() else null
            val charge = buffer.readVarInt()
            val capacity = buffer.readVarInt()
            val progress = buffer.readVarInt()
            val duration = buffer.readVarInt()
            val requiredCharge = buffer.readVarInt()
            val expansionCount = buffer.readVarInt()
            val status = buffer.readUtf(128)
            val refresh = buffer.readBoolean()
            val pokemonNbt = if (refresh) buffer.readNbt() else null
            val itemsEnabled = buffer.readBoolean()
            val eligibleCount = buffer.readVarInt()
            if (eligibleCount !in 0..MAX_ELIGIBLE_PARTY_POKEMON) throw DecoderException("Invalid eligible party Pokemon count")
            val eligible = List(eligibleCount) { buffer.readUUID() }
            if (eligible.distinct().size != eligible.size) throw DecoderException("Duplicate eligible party Pokemon")
            return UmbraInfuserStatePacket(containerId, owner, charge, capacity, progress, duration,
                requiredCharge, expansionCount, status, refresh, pokemonNbt, itemsEnabled, eligible)
        }
    }
}

object UmbraInfuserStateHandler : ClientNetworkPacketHandler<UmbraInfuserStatePacket> {
    override fun handle(packet: UmbraInfuserStatePacket, client: Minecraft) {
        val menu = client.player?.containerMenu as? UmbraInfuserMenu ?: return
        if (menu.containerId != packet.containerId) return
        val registries = client.level?.registryAccess() ?: return
        val pokemon = packet.pokemonNbt?.let {
            try {
                Pokemon.CLIENT_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), it).orThrow
            } catch (exception: Exception) {
                Shadowedhearts.LOGGER.warn("Unable to display Umbra Infuser Pokemon", exception)
                null
            }
        }
        menu.applyState(packet, pokemon)
    }
}
