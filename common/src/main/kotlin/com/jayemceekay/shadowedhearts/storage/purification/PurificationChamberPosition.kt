package com.jayemceekay.shadowedhearts.storage.purification

import com.cobblemon.mod.common.api.storage.StorePosition

/**
 * Position inside a Purification Chamber store.
 * setIndex: which of the 9 sets (0..8)
 * isShadow: true means the single shadow slot for the set; false means a support slot with index 0..3
 * index: support slot index when isShadow == false
 */
data class PurificationChamberPosition(
    val setIndex: Int,
    val index: Int,
    val isShadow: Boolean
) : StorePosition
