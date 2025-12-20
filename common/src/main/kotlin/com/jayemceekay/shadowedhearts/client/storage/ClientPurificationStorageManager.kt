package com.jayemceekay.shadowedhearts.client.storage

import java.util.*

/**
 * Manages client-side Purification Chamber storage instances keyed by UUID.
 * Mirrors Cobblemon's client storage managers but scoped to ShadowedHearts UI needs.
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
object ClientPurificationStorageManager {
    private val stores = mutableMapOf<UUID, ClientPurificationStorage>()

    /** Get an existing store by id or null if none exists. */
    operator fun get(id: UUID): ClientPurificationStorage? = stores[id]

    /** Get or create a store for the given id. */
    fun getOrCreate(id: UUID): ClientPurificationStorage = stores.getOrPut(id) { ClientPurificationStorage(id) }

    /** Register/replace a store instance. */
    fun put(store: ClientPurificationStorage) {
        stores[store.uuid] = store
    }

    /** Remove a store by id. */
    fun remove(id: UUID) {
        stores.remove(id)
    }

    /** Clear all tracked stores (e.g., on login/logout). */
    fun clear() {
        stores.clear()
    }

    fun onLogin() = clear()
    fun onLogout() = clear()
}
