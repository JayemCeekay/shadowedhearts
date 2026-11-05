package com.jayemceekay.shadowedhearts.content.blocks.entity;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.content.blocks.UmbraInfuserBlock;
import com.jayemceekay.shadowedhearts.content.blocks.UmbraInfuserTankBlock;
import com.jayemceekay.shadowedhearts.content.infusion.InfusionProgress;
import com.jayemceekay.shadowedhearts.content.infusion.InfuserFuelRecipe;
import com.jayemceekay.shadowedhearts.config.InfuserConfig;
import com.jayemceekay.shadowedhearts.content.infusion.InfuserPokemonRecoveryData;
import com.jayemceekay.shadowedhearts.content.infusion.InfuserPokemonStorage;
import com.jayemceekay.shadowedhearts.content.infusion.PokemonInfusionRules;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserMenu;
import com.jayemceekay.shadowedhearts.content.infusion.UmbraInfuserRecipe;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.ChunkClaim;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModInfusionRecipes;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** The lower infuser owns its workpiece. Each external tank owns only its own energy. */
public final class UmbraInfuserBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int MAX_EXPANSIONS = 8;
    private ItemStack item = ItemStack.EMPTY, fuel = ItemStack.EMPTY, output = ItemStack.EMPTY;
    private ItemStack clientDisplayItem = ItemStack.EMPTY;
    @Nullable private ResourceLocation clientDisplayPokeBall;
    private Pokemon pokemon;
    private UUID ownerUUID;
    private boolean infusionRequested, outputReady, contentsDropped;
    private int localCharge;
    private final InfusionProgress process = new InfusionProgress(InfusionProgress.MAX_CAPACITY);
    private int syncedCharge, syncedExpansions;
    private int syncedCapacity = InfusionProgress.CAPACITY;
    private boolean syncedLoaded = true;
    private boolean syncedProtectionAvailable = true;
    private float clientFill, previousClientFill;
    private boolean clientFillInitialized;

    public UmbraInfuserBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.UMBRA_INFUSER_BE.get(), pos, state); }
    public boolean isInfuser() { return getBlockState().is(ModBlocks.UMBRA_INFUSER.get()); }
    @Nullable public Pokemon getPokemon() { return pokemon; }
    @Nullable public UUID getOwnerUUID() { return ownerUUID; }
    public ItemStack getDisplayItem() {
        return isClient() ? clientDisplayItem : pokemon != null ? new ItemStack(pokemon.getCaughtBall().item())
                : !item.isEmpty() ? item : output;
    }
    @Nullable public ResourceLocation getDisplayPokeBall() {
        return isClient() ? clientDisplayPokeBall : pokemon != null ? pokemon.getCaughtBall().getName() : null;
    }
    public int getLocalCharge() { return localCharge; }
    public int getCharge() { return isClient() ? syncedCharge : network().charge(); }
    public int getCapacity() { return isClient() ? syncedCapacity : network().capacity(); }
    public int getExpansionCount() { return isClient() ? syncedExpansions : network().stores().size() - 1; }
    public int getProgress() { return process.progress(); }
    public int getDuration() {
        if (process.duration() > 0) return process.duration();
        if (outputReady) return 0;
        if (pokemon != null) return PokemonInfusionRules.duration(pokemon);
        var recipe = recipe();
        return recipe == null ? 0 : recipe.value().processingTicks();
    }
    public int getRequiredCharge() {
        if (outputReady) return 0;
        if (pokemon != null) return PokemonInfusionRules.cost(pokemon);
        var recipe = recipe();
        return recipe == null ? 0 : recipe.value().charge();
    }
    public boolean isProcessing() { return process.progress() > 0; }
    private boolean isClient() { return level != null && level.isClientSide; }
    public boolean canAccess(Player player) {
        if (!player.mayBuild() || (ownerUUID != null && !ownerUUID.equals(player.getUUID()))) return false;
        if (isClient()) return true;
        return level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && serverPlayer.serverLevel() == serverLevel && serverLevel.hasChunkAt(worldPosition)
                && FTBChunksClaimBridgeHolder.INSTANCE.canInteractBlock(serverPlayer, worldPosition, InteractionHand.MAIN_HAND);
    }
    public float getClientFill(float partialTick) {
        return clientFillInitialized ? Mth.lerp(partialTick, previousClientFill, clientFill) : (float) getCharge() / getCapacity();
    }
    private boolean complete() {
        if (level == null || !level.hasChunkAt(worldPosition.above())) return false;
        BlockState upper = level.getBlockState(worldPosition.above());
        return isInfuser() ? upper.is(ModBlocks.UMBRA_INFUSER.get()) && upper.getValue(UmbraInfuserBlock.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(UmbraInfuserBlock.FACING) == getBlockState().getValue(UmbraInfuserBlock.FACING)
                : upper.is(ModBlocks.UMBRA_INFUSER_TANK.get()) && upper.getValue(UmbraInfuserTankBlock.HALF) == DoubleBlockHalf.UPPER;
    }
    /** Compatibility for callers of the original controller/tank implementation. */
    @Nullable public BlockPos controllerPosition() { return isInfuser() && complete() ? worldPosition : null; }
    private record EnergyNetwork(List<UmbraInfuserBlockEntity> stores, boolean loaded, boolean protectionAvailable) {
        int charge() { return stores.stream().mapToInt(store -> Math.min(store.localCharge, InfuserConfig.INSTANCE.reservoirCapacity())).sum(); }
        int capacity() { return stores.size() * InfuserConfig.INSTANCE.reservoirCapacity(); }
        boolean usable() { return loaded && protectionAvailable; }
    }
    /** Bounded horizontal flood fill of loaded lower tank halves within the controller's claim ownership. */
    private EnergyNetwork network() {
        List<UmbraInfuserBlockEntity> stores = new ArrayList<>();
        stores.add(this);
        if (!isInfuser() || !(level instanceof ServerLevel serverLevel)) return new EnergyNetwork(stores, true, true);
        // Query again for every network operation so claiming/unclaiming a chunk immediately
        // affects both fuel distribution and consumption. This cache lasts only for this scan.
        var claims = FTBChunksClaimBridgeHolder.INSTANCE;
        HashMap<ChunkPos, ChunkClaim> checkedChunks = new HashMap<>();
        ChunkPos controllerChunk = new ChunkPos(worldPosition);
        ChunkClaim controllerClaim = claims.lookupClaim(serverLevel, controllerChunk);
        if (controllerClaim.isUnavailable()) return new EnergyNetwork(stores, true, false);
        checkedChunks.put(controllerChunk, controllerClaim);
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();
        pending.add(worldPosition); visited.add(worldPosition);
        boolean loaded = level.hasChunkAt(worldPosition.above());
        boolean protectionAvailable = true;
        while (!pending.isEmpty()) {
            if (stores.size() > InfuserConfig.INSTANCE.maxExpansions()) break;
            BlockPos current = pending.removeFirst();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (stores.size() > InfuserConfig.INSTANCE.maxExpansions()) break;
                BlockPos next = current.relative(side);
                if (!visited.add(next)) continue;
                if (!level.hasChunkAt(next)) { loaded = false; continue; }
                if (!UmbraInfuserTankBlock.isLower(level.getBlockState(next))) continue;
                if (!level.hasChunkAt(next.above())) { loaded = false; continue; }
                ChunkClaim tankClaim = checkedChunks.computeIfAbsent(new ChunkPos(next),
                        chunk -> claims.lookupClaim(serverLevel, chunk));
                if (tankClaim.isUnavailable()) { protectionAvailable = false; continue; }
                // Automated energy sharing has no acting player: allies, public interaction
                // permissions and admin bypass must not grant access to another team's fuel.
                if (!controllerClaim.canShareEnergyWith(tankClaim)) continue;
                if (level.getBlockEntity(next) instanceof UmbraInfuserBlockEntity tank && tank.complete()) {
                    stores.add(tank); pending.addLast(next);
                }
            }
        }
        return new EnergyNetwork(stores, loaded, protectionAvailable);
    }
    @Nullable private RecipeHolder<UmbraInfuserRecipe> recipe() {
        if (level == null || item.isEmpty()) return null;
        return level.getRecipeManager().getRecipeFor(ModInfusionRecipes.UMBRA_INFUSER_TYPE.get(), new SingleRecipeInput(item), level).orElse(null);
    }
    private boolean canAcceptOutput(ItemStack result) {
        if (result.isEmpty()) return false;
        if (output.isEmpty()) return result.getCount() <= getMaxStackSize(result);
        return ItemStack.isSameItemSameComponents(output, result)
                && result.getCount() <= getMaxStackSize(output) - output.getCount();
    }
    public void insert(Player player, ItemStack held) {
        if (level == null || level.isClientSide || !canAccess(player) || held.isEmpty()) return;
        if (!isInfuser() || !item.isEmpty() || pokemon != null) { message(player, "occupied"); return; }
        if (!canPlaceItem(0, held)) { message(player, "invalid_input"); return; }
        int count = Math.min(held.getCount(), getMaxStackSize(held));
        setItem(0, held.copyWithCount(count));
        if (!player.isCreative()) held.shrink(count);
    }
    public void extract(Player player) {
        if (level == null || level.isClientSide || !canAccess(player)) return;
        int slot = !item.isEmpty() ? 0 : !output.isEmpty() ? 2 : -1;
        if (slot < 0) { showStatus(player); return; }
        ItemStack returned = removeItemNoUpdate(slot); sync();
        if (slot == 2) onItemOutputCollected(player, returned);
        if (!player.getInventory().add(returned)) player.drop(returned, false);
    }
    /** Award only from player output transfers; input cancellation and automated access do not count. */
    public void onItemOutputCollected(Player player, ItemStack collected) {
        if (level != null && !level.isClientSide && !collected.isEmpty() && canAccess(player)
                && player instanceof ServerPlayer serverPlayer) {
            ModCriteriaTriggers.triggerProgression(serverPlayer, "infusion_collected");
        }
    }
    public static int fuelValue(Level level, ItemStack stack) {
        return level == null ? 0 : InfuserFuelRecipe.value(level.getRecipeManager(), stack);
    }
    /** The whole fuel unit must fit across the physical reservoirs before anything is consumed. */
    private boolean storeEnergy(int amount) {
        return storeEnergy(network(), amount);
    }
    private boolean storeEnergy(EnergyNetwork network, int amount) {
        if (!network.usable() || amount <= 0 || amount > network.capacity() - network.charge()) return false;
        int remaining = amount;
        for (var store : network.stores()) {
            int accepted = Math.min(remaining, InfuserConfig.INSTANCE.reservoirCapacity() - store.localCharge);
            if (accepted > 0) { store.localCharge += accepted; remaining -= accepted; store.sync(); }
        }
        return true;
    }
    public void addFuel(Player player, ItemStack held) {
        if (level == null || level.isClientSide || !canAccess(player) || held.isEmpty()) return;
        int value = fuelValue(level, held);
        if (value == 0) { message(player, "invalid_fuel"); return; }
        EnergyNetwork network = network();
        if (!network.protectionAvailable()) { message(player, "protection_unavailable"); return; }
        if (!network.loaded()) { message(player, "disconnected"); return; }
        if (!storeEnergy(network, value)) { message(player, "full"); return; }
        if (!player.isCreative()) held.shrink(1);
        sync(); showStatus(player);
    }
    public void insertPokemon(ServerPlayer player, UUID expectedPokemon) {
        if (!stillValid(player) || !isInfuser() || !canAccess(player)) return;
        if (!InfuserConfig.INSTANCE.pokemonEnabled()) { message(player, "pokemon_disabled"); return; }
        if (pokemon != null || !item.isEmpty() || !output.isEmpty()) { message(player, "occupied"); return; }
        if (!PokemonInfusionRules.playerAvailable(player)) { message(player, "pokemon_busy"); return; }
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon incoming = party.get(expectedPokemon);
        if (!PokemonInfusionRules.eligible(incoming)) { message(player, "pokemon_invalid"); return; }
        // Native remove recalls the live entity, detaches coordinates, and synchronizes the party.
        if (!party.remove(incoming)) return;
        pokemon = incoming; ownerUUID = player.getUUID();
        outputReady = false; infusionRequested = false; process.cancel(); sync();
    }
    public void retrievePokemon(ServerPlayer player) {
        if (!stillValid(player) || !canAccess(player) || pokemon == null) return;
        if (!PokemonInfusionRules.playerAvailable(player)) { message(player, "pokemon_busy"); return; }
        boolean completedInfusion = outputReady;
        if (!returnPokemon(false)) { message(player, "party_full"); return; }
        process.cancel(); outputReady = false; infusionRequested = false; sync();
        if (completedInfusion) ModCriteriaTriggers.triggerProgression(player, "pokemon_infused");
    }
    public void startInfusion(ServerPlayer player) {
        if (!stillValid(player) || !canAccess(player) || !isInfuser() || pokemon == null || outputReady) return;
        if (!InfuserConfig.INSTANCE.pokemonEnabled()) { message(player, "pokemon_disabled"); return; }
        if (!PokemonInfusionRules.playerAvailable(player)) { message(player, "pokemon_busy"); return; }
        if (!PokemonInfusionRules.eligible(pokemon)) { message(player, "pokemon_invalid"); return; }
        if (PokemonInfusionRules.cost(pokemon) > getCapacity()) { message(player, "capacity"); return; }
        // Request persists while waiting for charge. Energy is only spent at successful completion.
        infusionRequested = true; sync();
    }
    /** Return to the actual owner's storage, including when they are offline during block breaking. */
    private boolean returnPokemon(boolean breaking) {
        if (pokemon == null) return true;
        if (ownerUUID == null || level == null || level.isClientSide) return false;
        var storage = Cobblemon.INSTANCE.getStorage();
        var party = storage.getParty(ownerUUID, level.registryAccess());
        var pc = storage.getPC(ownerUUID, level.registryAccess());
        // An interrupted save may leave an older machine snapshot after the storage transfer committed.
        if (InfuserPokemonStorage.contains(ownerUUID, pokemon.getUuid(), level.registryAccess())) {
            pokemon = null;
            ownerUUID = null;
            return true;
        }
        if (!party.add(pokemon)) {
            if (!pc.add(pokemon)) {
                if (!breaking) return false;
                // Persist separately: Cobblemon's PC overflow does not survive its current load path.
                if (!InfuserPokemonRecoveryData.recoverLater((ServerLevel) level, ownerUUID, pokemon)) return false;
            }
        }
        pokemon = null; ownerUUID = null; return true;
    }
    public String getStatusKey() {
        String state;
        EnergyNetwork energy = isClient() ? null : network();
        if (!isInfuser()) state = "storage";
        else if (!complete()) state = "unassembled";
        else if (pokemon != null && outputReady) state = "ready";
        else if (pokemon != null && !InfuserConfig.INSTANCE.pokemonEnabled()) state = "pokemon_disabled";
        else if (pokemon != null && !isClient() && !PokemonInfusionRules.eligible(pokemon)) state = "pokemon_invalid";
        else if (pokemon == null && !item.isEmpty() && !InfuserConfig.INSTANCE.itemsEnabled()) state = "items_disabled";
        else if (isClient() ? !syncedProtectionAvailable : !energy.protectionAvailable()) state = "protection_unavailable";
        else if (isClient() ? !syncedLoaded : !energy.loaded()) state = "disconnected";
        else if (getRequiredCharge() > getCapacity()) state = "capacity";
        else if (pokemon == null && recipe() != null && !canAcceptOutput(recipe().value().output())) state = "output_full";
        else if (item.isEmpty() && pokemon == null) state = output.isEmpty() ? "empty" : "ready";
        else if (pokemon != null && !infusionRequested) state = "select";
        else if (getCharge() < getRequiredCharge()) state = "waiting";
        else if (isProcessing()) state = "processing";
        else state = "waiting";
        return "message.shadowedhearts.infuser." + state;
    }
    public void showStatus(Player player) {
        player.displayClientMessage(Component.translatable("message.shadowedhearts.infuser.status",
                Component.translatable(getStatusKey()), getCharge(), getCapacity()), true);
    }
    private static void message(Player player, String key) { player.displayClientMessage(Component.translatable("message.shadowedhearts.infuser." + key), true); }
    public static void tick(Level level, UmbraInfuserBlockEntity infuser) {
        if (level.isClientSide) {
            float target = (float) infuser.getCharge() / infuser.getCapacity();
            if (!infuser.clientFillInitialized) { infuser.clientFill = target; infuser.clientFillInitialized = true; }
            infuser.previousClientFill = infuser.clientFill;
            infuser.clientFill += (target - infuser.clientFill) * 0.25F; return;
        }
        if (!infuser.isInfuser()) return;
        EnergyNetwork energy = infuser.network();
        boolean changedNetwork = infuser.syncedCharge != energy.charge() || infuser.syncedCapacity != energy.capacity()
                || infuser.syncedLoaded != energy.loaded() || infuser.syncedProtectionAvailable != energy.protectionAvailable();
        infuser.syncedCharge = energy.charge(); infuser.syncedCapacity = energy.capacity();
        infuser.syncedExpansions = energy.stores().size() - 1; infuser.syncedLoaded = energy.loaded();
        infuser.syncedProtectionAvailable = energy.protectionAvailable();
        if (changedNetwork) infuser.sync();
        if (!energy.usable()) return;
        boolean fuelingEnabled = InfuserConfig.INSTANCE.pokemonEnabled() || InfuserConfig.INSTANCE.itemsEnabled();
        if (fuelingEnabled && !infuser.fuel.isEmpty() && infuser.storeEnergy(fuelValue(level, infuser.fuel))) { infuser.fuel.shrink(1); infuser.sync(); energy = infuser.network(); }
        if (!energy.usable()) return;
        boolean wasProcessing = infuser.isProcessing();
        if (!infuser.complete() || (infuser.pokemon != null && infuser.outputReady)
                || (infuser.pokemon == null && !InfuserConfig.INSTANCE.itemsEnabled())
                || (infuser.pokemon != null && !InfuserConfig.INSTANCE.pokemonEnabled())
                || (infuser.item.isEmpty() && infuser.pokemon == null) || (infuser.pokemon != null && !infuser.infusionRequested)) {
            infuser.process.cancel(); if (wasProcessing) infuser.sync(); return;
        }
        var holder = infuser.recipe();
        Pokemon target = infuser.pokemon;
        if ((target == null && holder == null) || (target != null && !PokemonInfusionRules.eligible(target))) { infuser.process.cancel(); if (wasProcessing) infuser.sync(); return; }
        int cost = target != null ? PokemonInfusionRules.cost(target) : holder.value().charge();
        // Keep a started Pokemon's saved duration, avoiding a capacity-config lookup every tick.
        int duration = target != null ? infuser.getDuration() : holder.value().processingTicks();
        String job = target != null ? "pokemon:" + target.getUuid() : holder.id() + "|" + holder.value().output().save(level.registryAccess());
        ItemStack result = target == null ? holder.value().assemble(new SingleRecipeInput(infuser.item), level.registryAccess()) : ItemStack.EMPTY;
        if (target == null) {
            // A reload may change the recipe while a batch waits for fuel or output space.
            if (!infuser.process.job().isEmpty() && (!job.equals(infuser.process.job())
                    || cost != infuser.process.cost() || duration != infuser.process.duration())) {
                infuser.process.cancel();
            }
            if (cost > energy.capacity() || energy.charge() < cost || !infuser.canAcceptOutput(result)) {
                if (wasProcessing != infuser.isProcessing()) infuser.sync();
                return;
            }
        }
        // The tracker's charge is a view of physical stores, never another persisted reservoir.
        infuser.process.restore(energy.capacity(), energy.charge(), infuser.process.progress(), infuser.process.job(), infuser.process.cost(), infuser.process.duration());
        boolean finished = infuser.process.tick(true, job, cost, duration);
        if (finished) {
            int remaining = cost;
            for (var store : energy.stores()) { int spent = Math.min(remaining, store.localCharge); if (spent > 0) { store.localCharge -= spent; remaining -= spent; store.sync(); } }
            if (target != null) {
                PokemonInfusionRules.convert(target);
                infuser.outputReady = true;
                infuser.infusionRequested = false;
            } else {
                // Both stacks and every physical energy store commit on this server tick.
                infuser.item.shrink(1);
                if (infuser.output.isEmpty()) infuser.output = result;
                else infuser.output.grow(result.getCount());
            }
            infuser.sync();
        } else if (wasProcessing != infuser.isProcessing() || (infuser.isProcessing() && level.getGameTime() % 10 == 0)) infuser.sync();
        else if (infuser.isProcessing()) infuser.setChanged();
    }
    public void dropContents() {
        if (level == null || level.isClientSide || contentsDropped) return;
        if (!returnPokemon(true)) throw new IllegalStateException("Infuser Pokemon has no owning trainer");
        contentsDropped = true;
        if (!item.isEmpty()) Block.popResource(level, worldPosition, item);
        if (!fuel.isEmpty()) Block.popResource(level, worldPosition, fuel);
        if (!output.isEmpty()) Block.popResource(level, worldPosition, output);
        // Refund only whole fuel units at the current datapack yield; never create energy when dust is rebalanced.
        int dustYield = fuelValue(level, new ItemStack(ModItems.SHADOW_DUST.get()));
        int dustCount = dustYield > 0 ? localCharge / dustYield : 0;
        while (dustCount > 0) {
            ItemStack refund = new ItemStack(ModItems.SHADOW_DUST.get(), Math.min(dustCount, 64));
            Block.popResource(level, worldPosition, refund);
            dustCount -= refund.getCount();
        }
        item = ItemStack.EMPTY; fuel = ItemStack.EMPTY; output = ItemStack.EMPTY; localCharge = 0; process.cancel();
    }
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return item.isEmpty() && fuel.isEmpty() && output.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? item : slot == 1 ? fuel : slot == 2 ? output : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = getItem(slot).split(count);
        if (!removed.isEmpty()) { if (slot == 0) cancelItemJob(); sync(); } return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = getItem(slot);
        if (slot == 0) { item = ItemStack.EMPTY; cancelItemJob(); }
        else if (slot == 1) fuel = ItemStack.EMPTY;
        else if (slot == 2) output = ItemStack.EMPTY;
        return removed;
    }
    private void cancelItemJob() { outputReady = false; process.cancel(); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot == 0) { item = stack; cancelItemJob(); }
        else if (slot == 1) { fuel = stack; fuel.limitSize(getMaxStackSize(stack)); }
        else if (slot == 2) output = stack;
        sync();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 1) return fuelValue(level, stack) > 0;
        return slot == 0 && InfuserConfig.INSTANCE.itemsEnabled() && isInfuser() && pokemon == null && level != null && !stack.isEmpty()
                && level.getRecipeManager().getRecipeFor(ModInfusionRecipes.UMBRA_INFUSER_TYPE.get(), new SingleRecipeInput(stack), level).isPresent();
    }
    // Workpiece transfers must pass through the menu's stack limits and Pokemon owner checks.
    @Override public int[] getSlotsForFace(Direction direction) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return false; }
    @Override public boolean stillValid(Player player) {
        return level != null && player.level() == level && player.isAlive() && !isRemoved()
                && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64
                && canAccess(player);
    }
    @Override public void clearContent() { item = ItemStack.EMPTY; fuel = ItemStack.EMPTY; output = ItemStack.EMPTY; cancelItemJob(); sync(); }
    @Override public Component getDisplayName() { return Component.translatable("block.shadowedhearts.umbra_infuser"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new UmbraInfuserMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!item.isEmpty()) tag.put("Item", item.save(registries));
        if (!fuel.isEmpty()) tag.put("Fuel", fuel.save(registries));
        if (!output.isEmpty()) tag.put("Output", output.save(registries));
        if (pokemon != null) tag.put("Pokemon", Pokemon.getCODEC().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), pokemon).getOrThrow());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putBoolean("InfusionRequested", infusionRequested); tag.putBoolean("OutputReady", outputReady);
        tag.putInt("Charge", localCharge); tag.putInt("Progress", process.progress()); tag.putString("Job", process.job());
        tag.putInt("Cost", process.cost()); tag.putInt("Duration", process.duration());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.getBoolean("ClientSnapshot")) {
            clientDisplayItem = ItemStack.parseOptional(registries, tag.getCompound("DisplayItem"));
            clientDisplayPokeBall = tag.contains("DisplayPokeBall") ? ResourceLocation.tryParse(tag.getString("DisplayPokeBall")) : null;
            syncedCharge = tag.getInt("NetworkCharge");
            syncedCapacity = Math.clamp(tag.getInt("NetworkCapacity"), 1, InfusionProgress.MAX_CAPACITY);
            syncedExpansions = Math.clamp(tag.getInt("ExpansionCount"), 0, MAX_EXPANSIONS);
            syncedLoaded = tag.getBoolean("NetworkLoaded");
            syncedProtectionAvailable = !tag.contains("ProtectionAvailable") || tag.getBoolean("ProtectionAvailable");
            return;
        }
        item = ItemStack.parseOptional(registries, tag.getCompound("Item")); fuel = ItemStack.parseOptional(registries, tag.getCompound("Fuel"));
        output = ItemStack.parseOptional(registries, tag.getCompound("Output"));
        pokemon = tag.contains("Pokemon") ? Pokemon.getCODEC()
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Pokemon")).getOrThrow() : null;
        ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        infusionRequested = tag.getBoolean("InfusionRequested"); outputReady = tag.getBoolean("OutputReady");
        // The previous version stored a completed item in its input slot.
        if (pokemon == null && outputReady && output.isEmpty() && !item.isEmpty()) {
            output = item;
            item = ItemStack.EMPTY;
        }
        if (pokemon == null) outputReady = false;
        // Keep surplus from a previous capacity setting across saves. It cannot accept more fuel until drained.
        localCharge = Math.clamp(tag.getInt("Charge"), 0, InfuserConfig.MAX_RESERVOIR_CAPACITY);
        process.restore(InfusionProgress.MAX_CAPACITY, 0, tag.getInt("Progress"), tag.getString("Job"), tag.getInt("Cost"), tag.getInt("Duration"));
        syncedCharge = tag.contains("NetworkCharge") ? tag.getInt("NetworkCharge") : localCharge;
        syncedCapacity = Math.clamp(tag.getInt("NetworkCapacity"), 1, InfusionProgress.MAX_CAPACITY);
        syncedExpansions = Math.clamp(tag.getInt("ExpansionCount"), 0, MAX_EXPANSIONS); syncedLoaded = !tag.contains("NetworkLoaded") || tag.getBoolean("NetworkLoaded");
        syncedProtectionAvailable = !tag.contains("ProtectionAvailable") || tag.getBoolean("ProtectionAvailable");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Chunk watchers need only the visible ball/item and liquid. Pokemon data goes privately to menu viewers.
        CompoundTag tag = new CompoundTag();
        EnergyNetwork energy = network();
        tag.putBoolean("ClientSnapshot", true);
        if (!getDisplayItem().isEmpty()) tag.put("DisplayItem", getDisplayItem().save(registries));
        if (pokemon != null) tag.putString("DisplayPokeBall", pokemon.getCaughtBall().getName().toString());
        tag.putInt("NetworkCharge", energy.charge());
        tag.putInt("NetworkCapacity", energy.capacity());
        tag.putInt("ExpansionCount", energy.stores().size() - 1);
        tag.putBoolean("NetworkLoaded", energy.loaded());
        tag.putBoolean("ProtectionAvailable", energy.protectionAvailable());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
