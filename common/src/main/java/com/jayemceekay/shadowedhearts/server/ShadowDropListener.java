package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.core.ModItems;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens for battle victories and drops Shadow Shards if a wild Shadow Pokemon was defeated.
 */
public final class ShadowDropListener {
    private ShadowDropListener() {
    }

    public static void init() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, (BattleVictoryEvent e) -> {
            if (e.getWasWildCapture()) {
                return Unit.INSTANCE;
            }

            int shadowCount = 0;
            for (BattleActor loser : e.getLosers()) {
                if (loser.getType() == ActorType.WILD) {
                    for (BattlePokemon bp : loser.getPokemonList()) {
                        if (bp != null && bp.getEffectedPokemon() != null) {
                            if (PokemonAspectUtil.hasShadowAspect(bp.getEffectedPokemon())) {
                                shadowCount++;
                            }
                        }
                    }
                }
            }

            if (shadowCount > 0) {
                List<ServerPlayer> winners = new ArrayList<>();
                for (BattleActor winner : e.getWinners()) {
                    if (winner instanceof PlayerBattleActor pba) {
                        ServerPlayer sp = pba.getEntity();
                        if (sp != null) {
                            winners.add(sp);
                        }
                    }
                }

                for (ServerPlayer player : winners) {
                    for (int i = 0; i < shadowCount; i++) {
                        ItemStack shard = new ItemStack(ModItems.SHADOW_SHARD.get());
                        ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), shard);
                        itemEntity.setNoPickUpDelay();
                        player.level().addFreshEntity(itemEntity);
                    }
                }
            }

            return Unit.INSTANCE;
        });
    }
}
