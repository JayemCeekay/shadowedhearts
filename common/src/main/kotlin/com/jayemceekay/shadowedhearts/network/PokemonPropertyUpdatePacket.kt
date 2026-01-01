package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty
import com.cobblemon.mod.common.net.messages.client.pokemon.update.SingleUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class PokemonPropertyUpdatePacket(pokemon: () -> Pokemon?, value: List<CustomPokemonProperty>) :
    SingleUpdatePacket<List<CustomPokemonProperty>, PokemonPropertyUpdatePacket>(pokemon, value) {
    override val id = ID

    override fun encodeValue(buffer: RegistryFriendlyByteBuf) {
        val strings = value.map { it.asString() }
        buffer.writeCollection(strings) { pb, s -> pb.writeUtf(s) }
    }

    override fun set(pokemon: Pokemon, value: List<CustomPokemonProperty>) {
        pokemon.customProperties.clear()
        pokemon.customProperties.addAll(value)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "pokemon_property_update")

        fun decode(buffer: RegistryFriendlyByteBuf): PokemonPropertyUpdatePacket {
            val pokemon = decodePokemon(buffer)
            val strings = buffer.readList { it.readUtf() }
            val props = PokemonProperties.parse(strings.joinToString(" ")).customProperties
            return PokemonPropertyUpdatePacket(pokemon, props)
        }
    }
}
