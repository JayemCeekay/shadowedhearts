package com.jayemceekay.shadowedhearts.content.infusion

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.content.blocks.entity.UmbraInfuserBlockEntity
import com.jayemceekay.shadowedhearts.config.InfuserConfig
import com.jayemceekay.shadowedhearts.network.infusion.UmbraInfuserStatePacket
import com.jayemceekay.shadowedhearts.registry.ModMenus
import com.jayemceekay.shadowedhearts.registry.ModInfusionRecipes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.SingleRecipeInput
import java.util.UUID

/** Native item transfers and a server-authoritative Pokemon workpiece share one machine menu. */
class UmbraInfuserMenu private constructor(
    containerId: Int,
    private val inventory: Inventory,
    private val contents: Container,
    val blockEntity: UmbraInfuserBlockEntity?
) : AbstractContainerMenu(ModMenus.UMBRA_INFUSER.get(), containerId) {
    constructor(containerId: Int, inventory: Inventory) : this(containerId, inventory, SimpleContainer(3), null)
    constructor(containerId: Int, inventory: Inventory, infuser: UmbraInfuserBlockEntity) :
        this(containerId, inventory, infuser, infuser)

    var pokemon: Pokemon? = null
        private set
    var ownerUUID: UUID? = null
        private set
    var charge = 0
        private set
    var capacity = 1
        private set
    var progress = 0
        private set
    var duration = 0
        private set
    var requiredCharge = 0
        private set
    var expansionCount = 0
        private set
    var statusKey = "message.shadowedhearts.infuser.empty"
        private set
    var pokemonRevision = 0
        private set
    var stateReceived = false
        private set
    var itemsEnabled = false
        private set
    var eligiblePartyPokemon: Set<UUID> = emptySet()
        private set

    private var lastState: UmbraInfuserStatePacket? = null
    private var lastPokemonFingerprint: String? = null
    private var clientSupplyVisible = true
    private var clientInventoryVisible = false
    private var accessRevoked = false

    init {
        checkContainerSize(contents, 3)
        contents.startOpen(inventory.player)
        addSlot(object : Slot(contents, 0, UmbraInfuserLayout.INPUT_X, UmbraInfuserLayout.INPUT_Y) {
            override fun isActive() = isSlotVisible(UmbraInfuserLayout.INPUT_SLOT)
            override fun mayPlace(stack: ItemStack) = canManage(inventory.player) && acceptsItem(0, stack)
            override fun mayPickup(player: Player) = canManage(player)
        })
        addSlot(object : Slot(contents, 1, UmbraInfuserLayout.FUEL_X, UmbraInfuserLayout.FUEL_Y) {
            override fun isActive() = isSlotVisible(UmbraInfuserLayout.FUEL_SLOT)
            override fun mayPlace(stack: ItemStack) = canManage(inventory.player) && acceptsItem(1, stack)
            override fun mayPickup(player: Player) = canManage(player)
        })
        addSlot(object : Slot(contents, 2, UmbraInfuserLayout.OUTPUT_X, UmbraInfuserLayout.OUTPUT_Y) {
            override fun isActive() = isSlotVisible(UmbraInfuserLayout.OUTPUT_SLOT)
            override fun mayPlace(stack: ItemStack) = false
            override fun mayPickup(player: Player) = canManage(player)
            override fun onTake(player: Player, stack: ItemStack) {
                super.onTake(player, stack)
                blockEntity?.onItemOutputCollected(player, stack)
            }
        })
        for (row in 0..2) for (column in 0..8) {
            val menuSlot = UmbraInfuserLayout.PLAYER_SLOT_START + column + row * 9
            val position = UmbraInfuserLayout.slotPosition(menuSlot)
            addSlot(object : Slot(inventory, column + row * 9 + 9, position.x, position.y) {
                override fun isActive() = isSlotVisible(menuSlot)
            })
        }
        for (column in 0..8) {
            val menuSlot = UmbraInfuserLayout.HOTBAR_SLOT_START + column
            val position = UmbraInfuserLayout.slotPosition(menuSlot)
            addSlot(object : Slot(inventory, column, position.x, position.y) {
                override fun isActive() = isSlotVisible(menuSlot)
            })
        }
    }

    /** The screen enables inventory slots only once the drawer reaches its final position. */
    fun setClientSlotVisibility(supplyVisible: Boolean, inventoryVisible: Boolean) {
        if (blockEntity != null) return
        clientSupplyVisible = supplyVisible
        clientInventoryVisible = inventoryVisible
    }

    private fun isSlotVisible(menuSlot: Int) = UmbraInfuserLayout.isSlotActive(
        menuSlot, blockEntity != null, clientSupplyVisible, clientInventoryVisible
    )

    fun canManage(player: Player): Boolean {
        if (blockEntity != null) return stillValid(player)
        return stateReceived && player.abilities.mayBuild && (ownerUUID == null || ownerUUID == player.uuid)
    }

    override fun stillValid(player: Player): Boolean {
        val infuser = blockEntity ?: return true
        if (player !== inventory.player) return false
        // An expired server session must be reopened even if access is later restored.
        if (!accessRevoked && !infuser.stillValid(player)) accessRevoked = true
        return !accessRevoked
    }

    private fun acceptsItem(slot: Int, stack: ItemStack): Boolean {
        blockEntity?.let { return contents.canPlaceItem(slot, stack) }
        if (slot == 1) return UmbraInfuserBlockEntity.fuelValue(inventory.player.level(), stack) > 0
        return slot == 0 && itemsEnabled && pokemon == null && !stack.isEmpty && inventory.player.level().recipeManager.getRecipeFor(
            ModInfusionRecipes.UMBRA_INFUSER_TYPE.get(), SingleRecipeInput(stack), inventory.player.level()
        ).isPresent
    }

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (!canManage(player)) return
        if (slotId in slots.indices && !slots[slotId].isActive) return
        super.clicked(slotId, button, clickType, player)
    }

    override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
        if (!canManage(player) || slotIndex !in slots.indices) return ItemStack.EMPTY
        val slot = slots[slotIndex]
        if (!slot.isActive || !slot.hasItem()) return ItemStack.EMPTY
        val original = slot.item.copy()
        val stack = slot.item
        val moved = if (slotIndex < 3) {
            moveItemStackTo(stack, 3, slots.size, true)
        } else if (acceptsItem(1, stack)) {
            moveItemStackTo(stack, 1, 2, false)
        } else if (acceptsItem(0, stack)) {
            moveItemStackTo(stack, 0, 1, false)
        } else if (slotIndex < 30) {
            moveItemStackTo(stack, 30, 39, false)
        } else {
            moveItemStackTo(stack, 3, 30, false)
        }
        if (!moved) return ItemStack.EMPTY
        val taken = original.copyWithCount(original.count - stack.count)
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        slot.onTake(player, taken)
        return original
    }

    override fun removed(player: Player) {
        super.removed(player)
        contents.stopOpen(player)
    }

    override fun broadcastChanges() {
        val player = inventory.player as? ServerPlayer
        if (blockEntity != null && player != null && !stillValid(player)) {
            if (player.containerMenu === this) player.closeContainer()
            return
        }
        super.broadcastChanges()
        val infuser = blockEntity ?: return
        if (player == null) return
        val mon = infuser.pokemon
        // Serialize a Pokemon only when its identity/presentation changes; progress packets remain small.
        val fingerprint = mon?.let { "${it.uuid}|${it.aspects.sorted()}|${it.level}|${it.getDisplayName().string}" } ?: "empty"
        val refresh = fingerprint != lastPokemonFingerprint
        // Loader config sync occurs on login; menu policy must follow live server changes and server-only species tags.
        val eligiblePartyPokemon = Cobblemon.storage.getParty(player).toList()
            .filter(PokemonInfusionRules::eligible).map { it.uuid }.distinct()
            .take(UmbraInfuserStatePacket.MAX_ELIGIBLE_PARTY_POKEMON)
        val state = UmbraInfuserStatePacket(
            containerId, infuser.ownerUUID, infuser.charge, infuser.capacity,
            infuser.progress, infuser.duration, infuser.requiredCharge, infuser.expansionCount,
            infuser.statusKey, refresh, if (refresh) mon?.let {
                Pokemon.CLIENT_CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), it)
                    .orThrow as CompoundTag
            } else null,
            InfuserConfig.INSTANCE.itemsEnabled(), eligiblePartyPokemon
        )
        if (refresh || state != lastState) {
            state.sendToPlayer(player)
            lastState = state.copy(refreshPokemon = false, pokemonNbt = null)
            lastPokemonFingerprint = fingerprint
        }
    }

    fun applyState(state: UmbraInfuserStatePacket, pokemonSnapshot: Pokemon?) {
        ownerUUID = state.ownerUUID
        charge = state.charge
        capacity = state.capacity.coerceAtLeast(1)
        progress = state.progress
        duration = state.duration
        requiredCharge = state.requiredCharge
        expansionCount = state.expansionCount
        statusKey = state.statusKey
        itemsEnabled = state.itemsEnabled
        eligiblePartyPokemon = state.eligiblePartyPokemon.toSet()
        stateReceived = true
        if (state.refreshPokemon) {
            pokemon = pokemonSnapshot
            pokemonRevision++
        }
    }
}
