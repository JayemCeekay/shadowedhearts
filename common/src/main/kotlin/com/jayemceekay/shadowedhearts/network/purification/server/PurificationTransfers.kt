package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.StorePosition
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberPosition
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import java.util.UUID

/** Shared server-side rules for the two chamber transfer packets. */
internal object PurificationTransfers {
    /** Reject foreign store IDs before resolving storage, and never clamp untrusted slot indices. */
    fun position(playerID: UUID, storeID: UUID, setIndex: Int, slotIndex: Int): PurificationChamberPosition? {
        if (storeID != playerID || setIndex !in 0 until PurificationChamberStore.SETS ||
            slotIndex !in 0..PurificationChamberStore.SUPPORT_SLOTS) return null
        return PurificationChamberPosition(setIndex, if (slotIndex == 0) 0 else slotIndex - 1, slotIndex == 0)
    }

    interface Slot<T : Any> {
        fun isValid(): Boolean
        fun get(): T?
        fun accepts(value: T): Boolean
        fun remove(value: T): Boolean
        fun set(value: T)
        fun restore(value: T) = set(value)
    }

    fun <P : StorePosition> slot(
        store: PokemonStore<P>,
        position: P,
        accepts: (Pokemon) -> Boolean = { true }
    ): Slot<Pokemon> = object : Slot<Pokemon> {
        override fun isValid() = store.isValidPosition(position)
        override fun get() = store[position]
        override fun accepts(value: Pokemon) = accepts.invoke(value)
        override fun remove(value: Pokemon) = store[position] === value && store.remove(value)
        override fun set(value: Pokemon) { store[position] = value }
        override fun restore(value: Pokemon) {
            if (store is PurificationChamberStore && position is PurificationChamberPosition) {
                store.restoreAfterFailedTransfer(position, value)
            } else {
                set(value)
            }
        }
    }

    /**
     * Runs on the server thread. Validate both ends before removing anything, then detach both
     * occupants before assigning new coordinates. PokemonStore.set does not detach from other
     * stores, so overwriting a destination during a swap would leave its UUID index stale.
     */
    fun <T : Any> transfer(
        source: Slot<T>,
        destination: Slot<T>,
        allowSwap: Boolean,
        expectedSource: (T) -> Boolean = { true }
    ): Boolean {
        if (!source.isValid() || !destination.isValid()) return false
        val incoming = source.get() ?: return false
        if (!expectedSource(incoming) || !destination.accepts(incoming)) return false
        val outgoing = destination.get()
        if (outgoing != null && (!allowSwap || outgoing === incoming || !source.accepts(outgoing))) return false

        if (!source.remove(incoming)) return false
        if (outgoing != null && !destination.remove(outgoing)) {
            source.restore(incoming)
            return false
        }

        try {
            destination.set(incoming)
            check(destination.get() === incoming) { "Transfer destination did not retain the Pokemon" }
            if (outgoing != null) {
                source.set(outgoing)
                check(source.get() === outgoing) { "Transfer source did not retain the swapped Pokemon" }
            }
        } catch (failure: RuntimeException) {
            // Restore both originals if an unexpected store setter rejects the validated move.
            // Remove partial placements before restoration so native coordinates/UUID indexes agree.
            try {
                if (destination.get() === incoming) check(destination.remove(incoming))
                if (outgoing != null && source.get() === outgoing) check(source.remove(outgoing))
                source.restore(incoming)
                if (outgoing != null) destination.restore(outgoing)
            } catch (rollbackFailure: RuntimeException) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
        return true
    }
}
