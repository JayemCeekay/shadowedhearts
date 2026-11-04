package com.jayemceekay.shadowedhearts.storage.purification.link

import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import com.cobblemon.mod.common.util.isInBattle
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * Manages PurificationLink instances; mirrors Cobblemon's PCLinkManager for the Purification Chamber.
 */
object PurificationLinkManager {
    private val links: MutableMap<UUID, PurificationLink> = mutableMapOf()
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        PlayerEvent.PLAYER_QUIT.register { removeLink(it.uuid) }
        LifecycleEvent.SERVER_STOPPED.register { links.clear() }
        TickEvent.SERVER_POST.register { server ->
            // Expire a session even if its client sends no more actions. Returning to
            // the chamber after leaving range must require opening it again.
            for (id in links.keys.toList()) {
                val player = server.playerList.getPlayer(id)
                if (player == null) removeLink(id) else getStore(player)
            }
        }
    }

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

    fun getStore(player: ServerPlayer): PurificationChamberStore? {
        val link = getLink(player.uuid) ?: return null
        if (link.playerID != player.uuid || link.store.uuid != player.uuid ||
            player.serverLevel().server.playerList.getPlayer(player.uuid) !== player ||
            !player.isAlive || player.isRemoved || player.isSpectator || player.isInBattle() ||
            !link.isPermitted(player)) {
            removeLink(player.uuid)
            return null
        }
        return link.store
    }
}
