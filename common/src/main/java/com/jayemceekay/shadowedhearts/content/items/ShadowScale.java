package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;

import com.jayemceekay.shadowedhearts.network.PokemonStateSync;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.MoveSelectCallbacks;
import com.cobblemon.mod.common.api.callback.MoveSelectDTO;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.TradeManager;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowLearnsetMoves;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleFeedback;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShadowScale extends Item {
    private static final String MESSAGE = "message.shadowedhearts.shadow_scale.";
    private static final long SELECTION_LIFETIME_TICKS = 20 * 60;

    public ShadowScale(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (ItemRuleFeedback.appendDisabled(stack, tooltip)) return;
        tooltip.add(Component.translatable("tooltip.shadowedhearts.shadow_scale.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowedhearts.shadow_scale.moves").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack interactionStack, Player user, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof PokemonEntity entity)) return InteractionResult.PASS;
        if (user.level().isClientSide) return ItemRuleFeedback.permitsClient("shadowScale") ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        if (!(user instanceof ServerPlayer player)) return InteractionResult.FAIL;
        // Vanilla supplies a temporary stack copy for creative entity interactions.
        // Bind the delayed selection to the real held stack, including in creative.
        ItemStack stack = player.getItemInHand(hand);
        if (interactionStack != stack && (!player.getAbilities().instabuild
                || !ItemStack.isSameItemSameComponents(interactionStack, stack))) return refuse(player, "changed");
        Pokemon pokemon = entity.getPokemon();
        String reason = refusal(player, stack, hand, entity, pokemon);
        if (reason != null) return refuse(player, reason);

        List<Selection> choices = new ArrayList<>();
        List<MoveSelectDTO> shown = new ArrayList<>();
        // Display indices are compact; retain the actual slot for sparse movesets.
        for (int slot = 0; slot < MoveSet.MOVE_COUNT; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null) continue;
            choices.add(new Selection(slot, move));
            if (ShadowUnlockPolicy.shouldMaskMove(pokemon, move)) {
                shown.add(new MoveSelectDTO(Moves.getByNameOrDummy("shadow-locked"), false, 0, 0));
            } else {
                shown.add(new MoveSelectDTO(move, !replacements(pokemon, move).isEmpty()));
            }
        }
        if (shown.stream().noneMatch(MoveSelectDTO::getEnabled)) return refuse(player, "no_moves");

        Level originalLevel = player.level();
        long expiresAt = originalLevel.getGameTime() + SELECTION_LIFETIME_TICKS;
        AtomicBoolean finished = new AtomicBoolean();
        MoveSelectCallbacks.INSTANCE.create(player, Component.translatable("gui.shadowedhearts.shadow_scale.title"), shown,
                ignored -> {
                    finished.set(true);
                    return Unit.INSTANCE;
                },
                (respondingPlayer, index, dto) -> {
                    if (!finished.compareAndSet(false, true)) return Unit.INSTANCE;
                    if (respondingPlayer != player || index < 0 || index >= choices.size()
                            || !shown.get(index).getEnabled()) return Unit.INSTANCE;
                    String changed = refusal(player, stack, hand, entity, pokemon);
                    if (changed != null) {
                        refuse(player, changed);
                        return Unit.INSTANCE;
                    }
                    Selection selected = choices.get(index);
                    if (player.level() != originalLevel || originalLevel.getGameTime() > expiresAt
                            || pokemon.getMoveSet().get(selected.slot()) != selected.move()
                            || ShadowUnlockPolicy.shouldMaskMove(pokemon, selected.move())) {
                        refuse(player, "changed");
                        return Unit.INSTANCE;
                    }

                    // Rebuild the pool at confirmation: the bench or other active slots may have changed.
                    List<Replacement> available = replacements(pokemon, selected.move());
                    if (available.isEmpty()) {
                        refuse(player, "no_replacement");
                        return Unit.INSTANCE;
                    }
                    Replacement replacement = available.get(player.getRandom().nextInt(available.size()));
                    applyReplacement(pokemon, selected, replacement);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    PokemonStateSync.syncMoveSet(pokemon);
                    PokemonStateSync.syncBenchedMoves(pokemon);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    player.displayClientMessage(Component.translatable(MESSAGE + "replaced",
                            pokemon.getDisplayName(false), replacement.template().getDisplayName())
                            .withStyle(ChatFormatting.GREEN), true);
                    return Unit.INSTANCE;
                });
        return InteractionResult.SUCCESS;
    }

    private String refusal(ServerPlayer player, ItemStack stack, InteractionHand hand,
                           PokemonEntity entity, Pokemon pokemon) {
        if (!ItemRuleSettings.serverCurrent().effectEnabled("shadowScale")) return "disabled";
        if (!player.isAlive() || player.isRemoved() || stack.isEmpty() || stack.getItem() != this
                || player.getItemInHand(hand) != stack || entity.isRemoved() || !entity.isAlive()
                || entity.level() != player.level() || entity.getPokemon() != pokemon) return "changed";
        if (!player.getUUID().equals(pokemon.getOwnerUUID())
                || Cobblemon.INSTANCE.getStorage().getParty(player).get(pokemon.getUuid()) != pokemon) return "not_owned";
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return "not_shadow";
        if (pokemon.isBattleClone() || entity.isBattling()
                || BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) return "battle_clone";
        if (TradeManager.INSTANCE.getActiveTrade(player.getUUID()) != null) return "in_trade";
        return null;
    }

    /** Every candidate changes the move and avoids duplicates in the active moveset. */
    private static List<Replacement> replacements(Pokemon pokemon, Move current) {
        Set<String> activeIds = new HashSet<>();
        for (Move move : pokemon.getMoveSet()) activeIds.add(move.getName().toLowerCase(Locale.ROOT));
        List<Replacement> result = new ArrayList<>();
        if (ShadowMoveUtil.isShadowMove(current)) {
            var profile = ShadowLearnsets.get(pokemon);
            var candidates = new LinkedHashSet<String>();
            Map<String, Integer> ppRaises = new HashMap<>();
            if (profile == null) candidates.addAll(List.of(ShadowMoveUtil.SHADOW_IDS));
            else {
                candidates.addAll(profile.levelMoves(pokemon.getLevel(), true));
                for (var entry : profile.tm()) candidates.add(entry.move());
                for (BenchedMove benched : pokemon.getBenchedMoves()) {
                    ppRaises.put(benched.getMoveTemplate().getName(), benched.getPpRaisedStages());
                }
            }
            for (String id : candidates) {
                MoveTemplate template = Moves.getByName(id);
                if (ShadowMoveUtil.isShadowMove(template) && !activeIds.contains(id.toLowerCase(Locale.ROOT))) {
                    result.add(new Replacement(template, ppRaises.getOrDefault(id, 0)));
                }
            }
        } else {
            for (BenchedMove benched : pokemon.getBenchedMoves()) {
                MoveTemplate template = benched.getMoveTemplate();
                if (!ShadowMoveUtil.isShadowMove(template) && !(template instanceof MoveTemplate.Dummy)
                        && !activeIds.contains(template.getName().toLowerCase(Locale.ROOT))) {
                    result.add(new Replacement(template, benched.getPpRaisedStages()));
                }
            }
        }
        return result;
    }

    private static void applyReplacement(Pokemon pokemon, Selection selected, Replacement replacement) {
        if (!ShadowMoveUtil.isShadowMove(selected.move())) {
            pokemon.getBenchedMoves().doWithoutEmitting(() -> {
                pokemon.getBenchedMoves().remove(replacement.template());
                // Replace any stale bench copy so the outgoing move keeps its current PP Ups.
                pokemon.getBenchedMoves().remove(selected.move().getTemplate());
                pokemon.getBenchedMoves().add(new BenchedMove(selected.move().getTemplate(), selected.move().getRaisedPpStages()));
                return Unit.INSTANCE;
            });
        } else if (ShadowLearnsets.get(pokemon) != null) {
            ShadowLearnsetMoves.updateShadowSlotBench(pokemon, selected.move(), replacement.template());
        }
        pokemon.getMoveSet().doWithoutEmitting(() -> {
            pokemon.getMoveSet().setMove(selected.slot(), replacement.template().create(
                    replacement.template().getPp(), replacement.raisedPpStages()));
            return Unit.INSTANCE;
        });
    }

    private static InteractionResult refuse(Player player, String reason) {
        player.displayClientMessage(Component.translatable(MESSAGE + reason).withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }

    private record Selection(int slot, Move move) { }
    private record Replacement(MoveTemplate template, int raisedPpStages) { }
}
