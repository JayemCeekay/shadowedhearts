package com.jayemceekay.shadowedhearts.content.items;

import com.cobblemon.mod.common.api.callback.MoveSelectCallbacks;
import com.cobblemon.mod.common.api.callback.MoveSelectDTO;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Collectors;

public class ShadowScale extends Item {

    public ShadowScale(Properties properties) {
        super(properties);
    }


    @Override
    public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(livingEntity instanceof PokemonEntity pokemonEntity))
            return InteractionResult.PASS;
        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon == null) return InteractionResult.PASS;

        if (pokemon.getOwnerUUID() == null) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.shadow_scale.not_owned").withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.shadow_scale.not_shadow").withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (player instanceof ServerPlayer serverPlayer) {

            if (!serverPlayer.getUUID().equals(pokemon.getOwnerUUID())) {
                player.displayClientMessage(Component.translatable("message.shadowedhearts.shadow_scale.not_owned").withStyle(net.minecraft.ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            if (pokemon.isBattleClone()) {
                player.displayClientMessage(Component.translatable("message.shadowedhearts.shadow_scale.battle_clone").withStyle(net.minecraft.ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            MoveSet moveSet = pokemon.getMoveSet();
            List<Move> activeMoves = moveSet.getMoves();

            // Build the selectable move list: masked moves get a "shadow-locked" dummy template
            List<MoveSelectDTO> possibleMoves = new ArrayList<>();
            for (Move move : activeMoves) {
                boolean masked = ShadowAspectUtil.shouldMaskMove(pokemon, move);
                if (masked) {
                    var lockedTemplate = Moves.INSTANCE.getByNameOrDummy("shadow-locked");
                    possibleMoves.add(new MoveSelectDTO(lockedTemplate, false, 0, 0));
                } else {
                    possibleMoves.add(new MoveSelectDTO(move, true));
                }
            }

            MoveSelectCallbacks.INSTANCE.create(
                    serverPlayer,
                    Component.translatable("gui.shadowedhearts.shadow_scale.title"),
                    possibleMoves,
                    sp -> Unit.INSTANCE,
                    (sp, index, dto) -> {
                        Move selectedMove = activeMoves.get(index);

                        if (ShadowMoveUtil.isShadowMove(selectedMove)) {
                            rerollShadowMove(pokemon, index, selectedMove);
                        } else {
                            swapWithBenchedMove(pokemon, index, selectedMove);
                        }
                        if (!sp.isCreative()) {
                            itemStack.shrink(1);
                        }

                        sp.server.execute(() -> {
                            ShadowAspectUtil.syncMoveSet(pokemon);
                            ShadowAspectUtil.syncBenchedMoves(pokemon);
                        });

                        return Unit.INSTANCE;
                    }
            );

        }
        return InteractionResult.SUCCESS;
    }

    private void rerollShadowMove(Pokemon pokemon, int slotIndex, Move currentMove) {
        MoveSet moveSet = pokemon.getMoveSet();

        // Collect shadow move IDs already in the moveset to avoid duplicates
        Set<String> existingShadowIds = new HashSet<>();
        for (Move mv : moveSet.getMoves()) {
            if (ShadowMoveUtil.isShadowMove(mv)) {
                existingShadowIds.add(mv.getTemplate().getName().toLowerCase(Locale.ROOT));
            }
        }

        // Build pool excluding all currently active shadow moves
        List<String> pool = Arrays.stream(ShadowMoveUtil.SHADOW_IDS)
                .filter(id -> !existingShadowIds.contains(id.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toCollection(ArrayList::new));

        if (pool.isEmpty()) return;

        Random rng = new Random();
        String newMoveId = ShadowMoveUtil.pickShadow(pool, currentMove.getTemplate().getName(), rng);
        if (newMoveId == null) return;

        var tmpl = Moves.INSTANCE.getByNameOrDummy(newMoveId);
        moveSet.doWithoutEmitting(() -> {
            moveSet.setMove(slotIndex, tmpl.create(tmpl.getPp(), 0));
            return null;
        });
    }

    private void swapWithBenchedMove(Pokemon pokemon, int slotIndex, Move currentMove) {
        var benchedMoves = pokemon.getBenchedMoves();

        List<BenchedMove> nonShadowBenched = new ArrayList<>();
        for (var bm : benchedMoves) {
            if (!ShadowMoveUtil.isShadowMove(bm.getMoveTemplate())) {
                nonShadowBenched.add(bm);
            }
        }

        if (nonShadowBenched.isEmpty()) return;

        Random rng = new Random();
        BenchedMove chosen = nonShadowBenched.get(rng.nextInt(nonShadowBenched.size()));

        // Batch all modifications without emitting intermediate packets
        benchedMoves.doWithoutEmitting(() -> {
            benchedMoves.remove(chosen.getMoveTemplate());
            benchedMoves.add(new BenchedMove(currentMove.getTemplate(), currentMove.getRaisedPpStages()));
            return null;
        });

        // Set the move (will trigger its own update, but moveset is separate from benchedMoves)
        var tmpl = chosen.getMoveTemplate();
        pokemon.getMoveSet().doWithoutEmitting(() -> {
            pokemon.getMoveSet().setMove(slotIndex, tmpl.create(tmpl.getPp(), chosen.getPpRaisedStages()));
            return null;
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        return super.use(level, player, interactionHand);
    }
}
