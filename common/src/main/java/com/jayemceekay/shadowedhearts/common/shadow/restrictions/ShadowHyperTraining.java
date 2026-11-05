package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Independent Shadow eligibility rule for native IV candies and Hyper Training integrations. */
public final class ShadowHyperTraining {
    private ShadowHyperTraining() {}

    public static void init() {
        // Integrations using Pokemon.hyperTrainIV must honor the rule even without a native item.
        CobblemonEvents.HYPER_TRAINED_IV_PRE.subscribe(Priority.NORMAL, event -> {
            if (blocksServer(event.getPokemon())) event.cancel();
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Native selection previews use the connected server's snapshot for client Pokemon. */
    public static boolean blocks(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.forPokemon(pokemon).blockHyperTraining();
    }

    private static boolean blocksServer(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowRestrictionSettings.serverCurrent().blockHyperTraining();
    }

    /** Recheck before a delayed selection callback or direct application can consume an item. */
    public static boolean reject(ServerPlayer player, Pokemon pokemon) {
        if (!blocksServer(pokemon)) return false;
        player.sendSystemMessage(Component.translatable("message.shadowedhearts.hyper_training_blocked",
                pokemon.getDisplayName(false)).withStyle(ChatFormatting.RED));
        return true;
    }
}
