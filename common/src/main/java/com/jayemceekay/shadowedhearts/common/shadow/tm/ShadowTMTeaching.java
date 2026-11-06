package com.jayemceekay.shadowedhearts.common.shadow.tm;

import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;

import com.jayemceekay.shadowedhearts.network.PokemonStateSync;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.callback.MoveSelectCallbacks;
import com.cobblemon.mod.common.api.callback.MoveSelectDTO;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.components.TMMoveComponent;
import com.cobblemon.mod.common.net.messages.server.block.TMMachineTeachMovePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.TradeManager;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Native Shadow discs tune an existing Shadow slot; they never grant a remembered move or a new slot. */
public final class ShadowTMTeaching {
    private static final Set<String> MOVE_IDS = Set.of(ShadowMoveUtil.SHADOW_IDS);
    private static final long SELECTION_LIFETIME_TICKS = 20 * 60;
    private static final String MESSAGE = "message.shadowedhearts.shadow_tm.";

    private ShadowTMTeaching() {}

    public static boolean isSupportedMove(MoveTemplate move) {
        return move != null && !(move instanceof MoveTemplate.Dummy)
                && MOVE_IDS.contains(move.getName()) && ShadowMoveUtil.isShadowMove(move);
    }

    public static boolean knowsActiveMove(Pokemon pokemon, MoveTemplate move) {
        if (pokemon == null || move == null) return false;
        for (Move active : pokemon.getMoveSet()) {
            if (active.getName().equals(move.getName())) return true;
        }
        return false;
    }

    /** Also used by client previews, without changing any species' ordinary TM learnset. */
    public static boolean canReplace(Pokemon pokemon, MoveTemplate move) {
        return canReplace(pokemon, move, ItemRuleSettings.serverCurrent());
    }

    public static boolean canReplace(Pokemon pokemon, MoveTemplate move, ItemRuleSettings settings) {
        if (!isSupportedMove(move) || pokemon == null || !ShadowAspectUtil.hasShadowAspect(pokemon)
                || settings == null || !settings.permitsShadowTM(move.getName())
                || knowsActiveMove(pokemon, move)) return false;
        for (Move active : pokemon.getMoveSet()) {
            if (ShadowMoveUtil.isShadowMove(active)) return true;
        }
        return false;
    }

    public static boolean beginItem(ServerPlayer player, PokemonEntity entity, ItemStack interactionStack,
                                    MoveTemplate move) {
        // Creative interactions may supply a copy. Bind the delayed operation to the real held stack.
        InteractionHand hand = heldHand(player, interactionStack);
        if (hand == null) return refuse(player, "changed");
        ItemStack stack = player.getItemInHand(hand);
        Pokemon pokemon = entity.getPokemon();
        return begin(player, pokemon, stack, move, () -> {
            if (player.getItemInHand(hand) != stack || entity.isRemoved() || !entity.isAlive()
                    || entity.level() != player.level() || entity.getPokemon() != pokemon) return "changed";
            if (entity.isBattling()) return "in_battle";
            if (Cobblemon.INSTANCE.getStorage().getParty(player).get(pokemon.getUuid()) != pokemon) return "not_owned";
            return null;
        }, () -> {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    public static void beginMachine(ServerPlayer player, TMMachineTeachMovePacket packet) {
        if (!(player.containerMenu instanceof TMMachineMenu menu) || menu.getInventory() == null
                || !menu.stillValid(player)) return;
        MoveTemplate move = packet.getMoveTemplate();
        ItemStack stack = menu.getCarried();
        if (!ItemStack.isSameItemSameComponents(stack, packet.getHeldStack())
                || stack.getCount() != packet.getHeldStack().getCount()
                || !matchesDisc(stack, move)) {
            refuse(player, "changed");
            return;
        }
        var store = packet.isParty() ? Cobblemon.INSTANCE.getStorage().getParty(player)
                : Cobblemon.INSTANCE.getStorage().getPC(player);
        Pokemon pokemon = store.get(packet.getUuid());
        begin(player, pokemon, stack, move, () -> {
            if (player.containerMenu != menu || menu.getCarried() != stack || !menu.stillValid(player)) return "changed";
            var currentStore = packet.isParty() ? Cobblemon.INSTANCE.getStorage().getParty(player)
                    : Cobblemon.INSTANCE.getStorage().getPC(player);
            return currentStore.get(packet.getUuid()) == pokemon ? null : "not_owned";
        }, () -> {
            menu.incrementStateId();
            menu.getInventory().setChanged();
            player.getInventory().setChanged();
            menu.broadcastChanges();
        });
    }

    private static boolean begin(ServerPlayer player, Pokemon pokemon, ItemStack stack, MoveTemplate move,
                                 Supplier<String> sourceValidation, Runnable updateInventory) {
        String reason = refusal(player, pokemon, stack, move, sourceValidation);
        if (reason != null) return refuse(player, reason);
        List<Selection> selections = new ArrayList<>();
        List<MoveSelectDTO> shown = new ArrayList<>();
        for (int slot = 0; slot < MoveSet.MOVE_COUNT; slot++) {
            Move current = pokemon.getMoveSet().get(slot);
            if (current == null) continue;
            selections.add(new Selection(slot, current));
            if (ShadowUnlockPolicy.shouldMaskMove(pokemon, current)) {
                shown.add(new MoveSelectDTO(Moves.getByNameOrDummy("shadow-locked"), false, 0, 0));
            } else {
                shown.add(new MoveSelectDTO(current, ShadowMoveUtil.isShadowMove(current)));
            }
        }
        var originalLevel = player.level();
        long expiresAt = originalLevel.getGameTime() + SELECTION_LIFETIME_TICKS;
        AtomicBoolean finished = new AtomicBoolean();
        MoveSelectCallbacks.INSTANCE.create(player,
                Component.translatable("gui.shadowedhearts.shadow_tm.title", move.getDisplayName()), shown,
                ignored -> {
                    finished.set(true);
                    return Unit.INSTANCE;
                }, (respondingPlayer, index, dto) -> {
                    if (!finished.compareAndSet(false, true)) return Unit.INSTANCE;
                    if (respondingPlayer != player || index < 0 || index >= selections.size()
                            || !shown.get(index).getEnabled()) return Unit.INSTANCE;
                    String changed = refusal(player, pokemon, stack, move, sourceValidation);
                    if (changed != null) {
                        refuse(player, changed);
                        return Unit.INSTANCE;
                    }
                    Selection selected = selections.get(index);
                    if (player.level() != originalLevel || originalLevel.getGameTime() > expiresAt
                            || pokemon.getMoveSet().get(selected.slot()) != selected.move()
                            || !ShadowMoveUtil.isShadowMove(selected.move())) {
                        refuse(player, "changed");
                        return Unit.INSTANCE;
                    }
                    // Read PP at confirmation, never restore the snapshot displayed when the menu opened.
                    Move replacement = move.create(0, selected.move().getRaisedPpStages());
                    replacement.setCurrentPp(Math.max(0, Math.min(selected.move().getCurrentPp(), replacement.getMaxPp())));
                    pokemon.getMoveSet().doWithoutEmitting(() -> {
                        pokemon.getMoveSet().setMove(selected.slot(), replacement);
                        return Unit.INSTANCE;
                    });
                    pokemon.getBenchedMoves().doWithoutEmitting(() -> {
                        // Do not retain stale copies that could reintroduce either move through the bench.
                        pokemon.getBenchedMoves().remove(selected.move().getTemplate());
                        pokemon.getBenchedMoves().remove(move);
                        return Unit.INSTANCE;
                    });
                    if (!player.getAbilities().instabuild && !Cobblemon.INSTANCE.getConfig().getInfiniteTmUses()) stack.shrink(1);
                    PokemonStateSync.syncMoveSet(pokemon);
                    PokemonStateSync.syncBenchedMoves(pokemon);
                    updateInventory.run();
                    player.displayClientMessage(Component.translatable(MESSAGE + "replaced",
                            pokemon.getDisplayName(false), selected.move().getDisplayName(), move.getDisplayName())
                            .withStyle(ChatFormatting.GREEN), true);
                    player.playNotifySound(CobblemonSounds.MOVE_LEARN, SoundSource.MASTER, 1.0F, 1.0F);
                    return Unit.INSTANCE;
                });
        return true;
    }

    private static String refusal(ServerPlayer player, Pokemon pokemon, ItemStack stack, MoveTemplate move,
                                  Supplier<String> sourceValidation) {
        if (!player.isAlive() || player.isRemoved() || !matchesDisc(stack, move)) return "changed";
        if (!isSupportedMove(move)) return "unsupported";
        if (!ItemRuleSettings.serverCurrent().permitsShadowTM(move.getName())) return "disabled";
        if (pokemon == null || !player.getUUID().equals(pokemon.getOwnerUUID())) return "not_owned";
        String sourceReason = sourceValidation.get();
        if (sourceReason != null) return sourceReason;
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return "not_shadow";
        if (pokemon.isBattleClone() || (pokemon.getEntity() != null && pokemon.getEntity().isBattling())
                || BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) return "in_battle";
        if (TradeManager.INSTANCE.getActiveTrade(player.getUUID()) != null) return "in_trade";
        if (knowsActiveMove(pokemon, move)) return "already_known";
        if (!canReplace(pokemon, move)) return "no_moves";
        return null;
    }

    private static boolean matchesDisc(ItemStack stack, MoveTemplate move) {
        if (stack.isEmpty() || !stack.is(CobblemonItems.TECHNICAL_MACHINE) || move == null) return false;
        MoveTemplate discMove = TMMoveComponent.Companion.getTMMove(stack);
        return discMove != null && discMove.getName().equals(move.getName());
    }

    private static InteractionHand heldHand(ServerPlayer player, ItemStack stack) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand) == stack) return hand;
        }
        if (player.getAbilities().instabuild) {
            for (InteractionHand hand : InteractionHand.values()) {
                if (ItemStack.isSameItemSameComponents(player.getItemInHand(hand), stack)) return hand;
            }
        }
        return null;
    }

    private static boolean refuse(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable(MESSAGE + reason).withStyle(ChatFormatting.RED), true);
        return false;
    }

    private record Selection(int slot, Move move) {}
}
