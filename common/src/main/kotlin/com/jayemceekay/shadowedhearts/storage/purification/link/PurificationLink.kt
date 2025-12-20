package com.jayemceekay.shadowedhearts.storage.purification.link

import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * A registered connection from a UUID to a specific Purification Chamber store.
 * Mirrors Cobblemon's PCLink for ShadowedHearts purification system.
 */
open class PurificationLink(
    /** The store it links to. */
    open val store: PurificationChamberStore,
    /** The player it's for. */
    open val playerID: UUID
) {
    /** Whether the given player can use this link. */
    open fun isPermitted(player: ServerPlayer): Boolean = true
}
