package com.jayemceekay.shadowedhearts.storage.purification.link

import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * Manages PurificationLink instances; mirrors Cobblemon's PCLinkManager for the Purification Chamber.
 */
object PurificationLinkManager {
    private val links: MutableMap<UUID, PurificationLink> = mutableMapOf()

    fun getLink(playerID: UUID): PurificationLink? = links[playerID]

    fun addLink(link: PurificationLink) {
        links[link.playerID] = link
    }

    fun addLink(playerID: UUID, store: PurificationChamberStore, condition: (ServerPlayer) -> Boolean = { true }) {
        links[playerID] = object : PurificationLink(store = store, playerID = playerID) {
            override fun isPermitted(player: ServerPlayer): Boolean = condition(player)
        }
    }

    fun removeLink(playerID: UUID) {
        links.remove(playerID)
    }

    fun getStore(player: ServerPlayer): PurificationChamberStore? = getLink(player.uuid)?.takeIf { it.isPermitted(player) }?.store
}
