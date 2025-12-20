package com.jayemceekay.shadowedhearts.storage.purification.link

import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.util.*

/**
 * A PurificationLink tied to a specific in-world Purification Chamber position and world.
 * Mirrors Cobblemon's ProximityPCLink for PC.
 */
class ProximityPurificationLink(
    store: PurificationChamberStore,
    playerID: UUID,
    /** Bottom (base) position of the chamber. */
    val pos: BlockPos,
    /** World where the chamber exists. */
    val world: ServerLevel
) : PurificationLink(store = store, playerID = playerID)
