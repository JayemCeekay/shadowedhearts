package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.api.mark.Marks
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService
import com.jayemceekay.shadowedhearts.network.purification.PurifyPokemonPacket
import com.jayemceekay.shadowedhearts.network.purification.client.PokemonPurifiedPacket
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberPosition
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: performs full purification on the Pokémon in the center slot of the purification chamber.
 */
object PurifyPokemonHandler : ServerNetworkPacketHandler<PurifyPokemonPacket> {
    override fun handle(packet: PurifyPokemonPacket, server: MinecraftServer, player: ServerPlayer) {
        // Chamber stores are per-player. Never resolve another player's store from a packet.
        if (packet.purificationStoreID != player.uuid || packet.setIndex !in 0 until PurificationChamberStore.SETS) return
        val purification = PurificationLinkManager.getStore(player) ?: return

        val target = PurificationChamberPosition(setIndex = packet.setIndex, index = 0, isShadow = true)
        val pokemon = purification[target]
        if (pokemon == null) {
            return
        }


        // Verify it's actually ready for purification (heart gauge 0)
        if (ShadowAspectUtil.getHeartGauge(pokemon) == 0F) {
            ShadowService.fullyPurify(pokemon, null)
            if (pokemon.getOwnerPlayer() is ServerPlayer) {
                ModCriteriaTriggers.triggerShadowPurified(player)
            }
            Marks.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", "ribbon_event_national"))
                ?.let { pokemon.exchangeMark(it, true) };
            PokemonPurifiedPacket(
                purificationStoreID = packet.purificationStoreID,
                setIndex = packet.setIndex,
                slotIndex = 0
            ).sendToPlayer(player)
        }
    }
}
