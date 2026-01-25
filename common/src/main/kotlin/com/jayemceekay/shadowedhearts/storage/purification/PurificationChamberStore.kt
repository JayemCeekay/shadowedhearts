package com.jayemceekay.shadowedhearts.storage.purification

import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.StoreCoordinates
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.getPlayer
import com.google.gson.JsonObject
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.ShadowService
import com.jayemceekay.shadowedhearts.client.net.storage.purification.PurificationChamberSyncPacket
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * A per-player store for Purification Chambers.
 * Each store contains 9 sets; each set has 1 shadow slot and up to 4 support (non-shadow) slots.
 * Persistence is handled by Cobblemon's FileBackedPokemonStoreFactory via the UUID constructor.
 */
class PurificationChamberStore(
    override val uuid: UUID
) : PokemonStore<PurificationChamberPosition>() {

    companion object {
        private const val SET_COUNT_KEY = "SetCount"
        private const val SHADOW_KEY_PREFIX = "Set"
        private const val SHADOW_KEY_SUFFIX = "Shadow"
        private const val SUPPORT_KEY_SUFFIX = "Support"
        private const val POS_IS_SHADOW = "IsShadow"
        const val SETS = 9
        const val SUPPORT_SLOTS = 4
    }

    private data class ChamberSet(
        var shadow: Pokemon? = null,
        val supports: Array<Pokemon?> = arrayOfNulls(SUPPORT_SLOTS)
    )

    private val sets: MutableList<ChamberSet> = MutableList(SETS) { ChamberSet() }

    private val observingUUIDs = mutableSetOf(uuid)
    private val changeObservable = SimpleObservable<Unit>()

    // Accumulate world steps; every 161 steps apply chamber purification per spec
    private var stepAccumulator: Int = 0

    /**
     * Marks this store as changed to ensure persistence schedules a save.
     * This is useful right after first construction/open so the flat-file
     * adapter creates an empty file on disk (mirrors PC store behavior).
     */
    fun touch() {
        changeObservable.emit(Unit)
    }

    override fun iterator(): Iterator<Pokemon> = sequence {
        sets.forEach { set ->
            set.shadow?.let { yield(it) }
            set.supports.forEach { it?.let { p -> yield(p) } }
        }
    }.iterator()

    override fun getObservingPlayers(): Iterable<ServerPlayer> = observingUUIDs.mapNotNull { it.getPlayer() }

    override fun sendTo(player: ServerPlayer) {
        // Build a snapshot of all occupied slots and send to the client to populate the UI.
        val entries = mutableListOf<PurificationChamberSyncPacket.Entry>()
        val reg = player.registryAccess()
        sets.forEachIndexed { setIndex, set ->
            set.shadow?.let { mon ->
                entries.add(
                    PurificationChamberSyncPacket.Entry(
                        setIndex = setIndex,
                        slotIndex = 0,
                        pokemonNbt = mon.saveToNBT(reg)
                    )
                )
            }
            for (j in 0 until SUPPORT_SLOTS) {
                set.supports[j]?.let { mon ->
                    entries.add(
                        PurificationChamberSyncPacket.Entry(
                            setIndex = setIndex,
                            slotIndex = j + 1,
                            pokemonNbt = mon.saveToNBT(reg)
                        )
                    )
                }
            }
        }
        PurificationChamberSyncPacket(storeID = uuid, entries = entries).sendToPlayer(player)
    }

    override fun onPokemonChanged(pokemon: Pokemon) {
        changeObservable.emit(Unit)
    }

    override fun initialize() {
        sets.forEachIndexed { setIndex, set ->
            set.shadow?.storeCoordinates?.set(StoreCoordinates(this, PurificationChamberPosition(setIndex, 0, true)))
            set.supports.forEachIndexed { idx, p ->
                p?.storeCoordinates?.set(StoreCoordinates(this, PurificationChamberPosition(setIndex, idx, false)))
            }
        }
    }

    override fun setAtPosition(position: PurificationChamberPosition, pokemon: Pokemon?) {
        val set = sets.getOrNull(position.setIndex) ?: throw IllegalArgumentException("Invalid set ${position.setIndex}")
        if (position.isShadow) {
            set.shadow = pokemon
        } else {
            if (position.index !in 0 until SUPPORT_SLOTS) throw IllegalArgumentException("Invalid support index ${position.index}")
            set.supports[position.index] = pokemon
        }
        changeObservable.emit(Unit)
    }

    override fun isValidPosition(position: PurificationChamberPosition): Boolean {
        if (position.setIndex !in 0 until sets.size) return false
        return if (position.isShadow) true else position.index in 0 until SUPPORT_SLOTS
    }

    override fun getFirstAvailablePosition(): PurificationChamberPosition? {
        // Type-agnostic API cannot decide shadow vs support; return first empty slot by priority: shadow first, then supports.
        sets.forEachIndexed { i, set ->
            if (set.shadow == null) return PurificationChamberPosition(i, 0, true)
            val sIdx = set.supports.indexOfFirst { it == null }
            if (sIdx >= 0) return PurificationChamberPosition(i, sIdx, false)
        }
        return null
    }

    override fun add(pokemon: Pokemon): Boolean {
        // Place based on shadow aspect constraint
        val isShadow = PokemonAspectUtil.hasShadowAspect(pokemon)
        if (isShadow) {
            val pos = sets.indexOfFirst { it.shadow == null }
            if (pos >= 0) {
                this[PurificationChamberPosition(pos, 0, true)] = pokemon
                return true
            }
        } else {
            sets.forEachIndexed { i, set ->
                val idx = set.supports.indexOfFirst { it == null }
                if (idx >= 0) {
                    this[PurificationChamberPosition(i, idx, false)] = pokemon
                    return true
                }
            }
        }
        return false
    }

    override fun set(position: PurificationChamberPosition, pokemon: Pokemon) {
        val isShadow = PokemonAspectUtil.hasShadowAspect(pokemon)
        if (position.isShadow && !isShadow) {
            throw IllegalArgumentException("Support Pokémon cannot occupy shadow slot")
        }
        if (!position.isShadow && isShadow) {
            throw IllegalArgumentException("Shadow Pokémon cannot occupy support slot")
        }
        super.set(position, pokemon)
    }

    override fun swap(position1: PurificationChamberPosition, position2: PurificationChamberPosition) {
        val p1 = get(position1)
        val p2 = get(position2)

        // Enforce constraints after swap
        if (p1 != null) {
            val s1 = PokemonAspectUtil.hasShadowAspect(p1)
            if (position2.isShadow != s1) return // invalid swap
        }
        if (p2 != null) {
            val s2 = PokemonAspectUtil.hasShadowAspect(p2)
            if (position1.isShadow != s2) return
        }

        super.swap(position1, position2)
    }

    override fun get(position: PurificationChamberPosition): Pokemon? {
        val set = sets.getOrNull(position.setIndex) ?: return null
        return if (position.isShadow) set.shadow else set.supports.getOrNull(position.index)
    }

    /**
     * Advance the chamber's purification cadence by a given number of steps.
     * Every 161 accumulated steps, compute and apply heart gauge reductions to each set's shadow.
     * Server-side authority only.
     *
     * Context: Minecraft Cobblemon mod; all shadow/purity/corruption terms are gameplay mechanics.
     */
    fun advanceSteps(steps: Int) {
        if (steps <= 0) return
        stepAccumulator += steps
        val affectedPokemon = mutableSetOf<Pokemon>()
        while (stepAccumulator >= 161) {
            stepAccumulator -= 161
            applyChamberTick(affectedPokemon)
        }
        affectedPokemon.forEach { ShadowService.syncAll(it) }
    }

    /** Compute perfect-set count and apply purification delta to each shadow per formula. */
    private fun applyChamberTick(affectedPokemon: MutableSet<Pokemon>? = null) {
        // Count perfect sets first
        val perfectCount = sets.count { set ->
            val ring = set.supports.filterNotNull()
            PurificationMath.isPerfectSet(ring)
        }

        // Apply per-set
        var anyChanged = false
        sets.forEach { set ->
            val delta = PurificationMath.computePurificationDeltaForSet(set.shadow, set.supports, perfectCount)
            if (delta != 0 && set.shadow != null) {
                val p = set.shadow!!
                val cur = PokemonAspectUtil.getHeartGaugeMeter(p)
                val next = cur + delta // delta negative opens heart
                if (affectedPokemon != null) {
                    ShadowService.setHeartGauge(p, null, next, false)
                    affectedPokemon.add(p)
                } else {
                    ShadowService.setHeartGauge(p, null, next)
                }
                anyChanged = true
            }
        }
        if (anyChanged) changeObservable.emit(Unit)
    }

    override fun saveToNBT(nbt: CompoundTag, registryAccess: RegistryAccess): CompoundTag {
        nbt.putShort(SET_COUNT_KEY, sets.size.toShort())
        sets.forEachIndexed { i, set ->
            set.shadow?.let {
                nbt.put("${SHADOW_KEY_PREFIX}${i}${SHADOW_KEY_SUFFIX}", it.saveToNBT(registryAccess))
            }
            for (j in 0 until SUPPORT_SLOTS) {
                set.supports[j]?.let {
                    nbt.put("${SHADOW_KEY_PREFIX}${i}${SUPPORT_KEY_SUFFIX}${j}", it.saveToNBT(registryAccess))
                }
            }
        }
        return nbt
    }

    override fun loadFromNBT(nbt: CompoundTag, registryAccess: RegistryAccess): PokemonStore<PurificationChamberPosition> {
        val count = if (nbt.contains(SET_COUNT_KEY)) nbt.getShort(SET_COUNT_KEY).toInt() else SETS
        sets.clear()
        repeat(count) { sets.add(ChamberSet()) }
        for (i in 0 until count) {
            val shadowKey = "${SHADOW_KEY_PREFIX}${i}${SHADOW_KEY_SUFFIX}"
            if (nbt.contains(shadowKey)) {
                sets[i].shadow = Pokemon.loadFromNBT(registryAccess, nbt.getCompound(shadowKey))
            }
            for (j in 0 until SUPPORT_SLOTS) {
                val key = "${SHADOW_KEY_PREFIX}${i}${SUPPORT_KEY_SUFFIX}${j}"
                if (nbt.contains(key)) {
                    sets[i].supports[j] = Pokemon.loadFromNBT(registryAccess, nbt.getCompound(key))
                }
            }
        }
        return this
    }

    override fun saveToJSON(json: JsonObject, registryAccess: RegistryAccess): JsonObject {
        json.addProperty(SET_COUNT_KEY, sets.size)
        sets.forEachIndexed { i, set ->
            set.shadow?.let { json.add("${SHADOW_KEY_PREFIX}${i}${SHADOW_KEY_SUFFIX}", it.saveToJSON(registryAccess)) }
            for (j in 0 until SUPPORT_SLOTS) {
                set.supports[j]?.let { json.add("${SHADOW_KEY_PREFIX}${i}${SUPPORT_KEY_SUFFIX}${j}", it.saveToJSON(registryAccess)) }
            }
        }
        return json
    }

    override fun loadFromJSON(json: JsonObject, registryAccess: RegistryAccess): PokemonStore<PurificationChamberPosition> {
        val count = if (json.has(SET_COUNT_KEY)) json.get(SET_COUNT_KEY).asInt else SETS
        sets.clear()
        repeat(count) { sets.add(ChamberSet()) }
        for (i in 0 until count) {
            val shadowKey = "${SHADOW_KEY_PREFIX}${i}${SHADOW_KEY_SUFFIX}"
            if (json.has(shadowKey)) {
                sets[i].shadow = Pokemon.loadFromJSON(registryAccess, json.getAsJsonObject(shadowKey))
            }
            for (j in 0 until SUPPORT_SLOTS) {
                val key = "${SHADOW_KEY_PREFIX}${i}${SUPPORT_KEY_SUFFIX}${j}"
                if (json.has(key)) {
                    sets[i].supports[j] = Pokemon.loadFromJSON(registryAccess, json.getAsJsonObject(key))
                }
            }
        }
        return this
    }

    override fun savePositionToNBT(position: PurificationChamberPosition, nbt: CompoundTag) {
        nbt.putShort(DataKeys.STORE_BOX, position.setIndex.toShort())
        nbt.putByte(DataKeys.STORE_SLOT, position.index.toByte())
        nbt.putBoolean(POS_IS_SHADOW, position.isShadow)
    }

    override fun loadPositionFromNBT(nbt: CompoundTag): StoreCoordinates<PurificationChamberPosition> {
        val set = nbt.getShort(DataKeys.STORE_BOX).toInt()
        val idx = nbt.getByte(DataKeys.STORE_SLOT).toInt()
        val isShadow = nbt.getBoolean(POS_IS_SHADOW)
        return StoreCoordinates(this, PurificationChamberPosition(set, idx, isShadow))
    }

    override fun getAnyChangeObservable() = changeObservable
}
