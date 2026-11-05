package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared policy for the native TM item, machine and its client preview. */
public final class ShadowMoveTeachingPolicy {
    private ShadowMoveTeachingPolicy() {}

    public static boolean isShadowBlocked(Pokemon pokemon) {
        return ShadowRestrictionPolicy.blocksMoveTeaching(pokemon);
    }

    /** Runs before native item consumption or move mutation on the server. */
    public static boolean rejectNativeTM(ServerPlayer player, Pokemon pokemon, MoveTemplate move) {
        if (pokemon == null || move == null) return true;
        if (com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle.blocksLearning(pokemon, move)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.move_lifecycle.cannot_relearn")
                    .withStyle(ChatFormatting.RED), true);
            return true;
        }
        if (isShadowBlocked(pokemon)) {
            player.displayClientMessage(Component.translatable("shadowedhearts.shadow.tm_blocked")
                    .withStyle(ChatFormatting.RED), true);
            return true;
        }
        // Keep ordinary teaching subject to the form's native TM learnset on both server paths.
        if (!pokemon.getForm().getMoves().tmLearnableMoves().contains(move)) {
            player.displayClientMessage(Component.translatable("cobblemon.tms.cannot_learn",
                    pokemon.getDisplayName(false), move.getDisplayName()), true);
            return true;
        }
        return false;
    }
}
