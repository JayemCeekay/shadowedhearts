package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.ShadowService
import com.jayemceekay.shadowedhearts.network.purification.PurifyPokemonPacket
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberPosition
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: performs full purification on the Pokémon in the center slot of the purification chamber.
 */
object PurifyPokemonHandler : ServerNetworkPacketHandler<PurifyPokemonPacket> {
    override fun handle(packet: PurifyPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        val registryAccess = player.registryAccess()
        val purification = Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, packet.purificationStoreID, registryAccess)
            ?: Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, player.uuid, registryAccess)
            ?: return

        val target = PurificationChamberPosition(setIndex = packet.setIndex, index = 0, isShadow = true)
        val pokemon = purification[target]
        if (pokemon == null) {
            System.out.println("MISSING")
            return
        }


        // Verify it's actually ready for purification (heart gauge 0)
        if (PokemonAspectUtil.getHeartGauge(pokemon) == 0F) {
            System.out.println("PURIFYING")
            ShadowService.fullyPurify(pokemon, null)
        }
    }
}
