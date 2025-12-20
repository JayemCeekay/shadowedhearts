package com.jayemceekay.shadowedhearts;
// /shadow set <pokemonEntity> <true|false>
// /shadow corr <pokemonEntity> <0..100>

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.heart.HeartGaugeEvents;
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class ShadowCommands {
    /**
     * Register the /shadow command tree.
     */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("shadow").requires(src -> src.hasPermission(2))
                // Debug: advance the caller's Purification Chamber by a number of walking steps
                // Usage: /shadow steps <count>
                .then(Commands.literal("steps")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player;
                                    try {
                                        player = ctx.getSource().getPlayerOrException();
                                    } catch (Exception ex) {
                                        ctx.getSource().sendFailure(Component.literal("Must be a player to use this command"));
                                        return 0;
                                    }

                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    var reg = player.registryAccess();
                                    PurificationChamberStore store = Cobblemon.INSTANCE.getStorage().getCustomStore(PurificationChamberStore.class, player.getUUID(), reg);
                                    if (store == null) {
                                        ctx.getSource().sendFailure(Component.literal("Purification Chamber store not available for this player"));
                                        return 0;
                                    }
                                    store.advanceSteps(count);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Purification Chamber advanced by " + count + " step(s)"), true);
                                    return 1;
                                })))
                // Debug: simulate overworld walking steps that affect party Pokémon
                // Usage: /shadow partysteps <count>
                .then(Commands.literal("partysteps")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player;
                                    try {
                                        player = ctx.getSource().getPlayerOrException();
                                    } catch (Exception ex) {
                                        ctx.getSource().sendFailure(Component.literal("Must be a player to use this command"));
                                        return 0;
                                    }

                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    int intervals = Math.max(0, count / 256);
                                    if (intervals <= 0) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("No party ticks from " + count + " step(s); need at least 256 for 1 tick."), false);
                                        return 1;
                                    }

                                    var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                    // Take a defensive snapshot to avoid ConcurrentModificationException if any listeners
                                    // mutate the party while we iterate (e.g., aspect application syncing storage)
                                    List<Pokemon> snapshot = new ArrayList<>();
                                    for (Pokemon mon : party) snapshot.add(mon);

                                    int affected = 0;
                                    for (int i = 0; i < intervals; i++) {
                                        for (Pokemon mon : snapshot) {
                                            if (PokemonAspectUtil.hasShadowAspect(mon)) {
                                                HeartGaugeEvents.onPartyStep(mon, null);
                                                affected++;
                                            }
                                        }
                                    }
                                    final int affectedFinal = affected;
                                    final int intervalsFinal = intervals;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Applied " + intervalsFinal + " party step tick(s) (" + affectedFinal + " applications across shadow Pokémon)."),
                                            true
                                    );
                                    return 1;
                                })))
                .then(Commands.literal("depthdump").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Use client command /sh_depthdump in chat (client only)."), false);
                    return 1;
                }))
                .then(Commands.literal("battledump").executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("Battle debug dump not available in this build."));
                    return 0;
                }))
                // Convenience: manage NPC tags for Shadow injector
                .then(Commands.literal("npc")
                        .then(Commands.literal("tag")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .then(Commands.argument("tag", StringArgumentType.string())
                                                        .executes(ctx -> {
                                                            var entities = EntityArgument.getEntities(ctx, "targets");
                                                            String tag = StringArgumentType.getString(ctx, "tag");
                                                            int applied = 0;
                                                            for (Entity e : entities) {
                                                                if (e instanceof NPCEntity npc) {
                                                                    if (npc.addTag(tag)) applied++;
                                                                }
                                                            }
                                                            final int appliedFinal = applied;
                                                            final String tagFinal = tag;
                                                            ctx.getSource().sendSuccess(() -> Component.literal("Added tag '" + tagFinal + "' to " + appliedFinal + " NPC(s)."), true);
                                                            return appliedFinal;
                                                        }))
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .then(Commands.argument("tag", StringArgumentType.string())
                                                        .executes(ctx -> {
                                                            var entities = EntityArgument.getEntities(ctx, "targets");
                                                            String tag = StringArgumentType.getString(ctx, "tag");
                                                            int removed = 0;
                                                            for (Entity e : entities) {
                                                                if (e instanceof NPCEntity npc) {
                                                                    if (npc.removeTag(tag)) removed++;
                                                                }
                                                            }
                                                            final int removedFinal = removed;
                                                            final String tagFinal = tag;
                                                            ctx.getSource().sendSuccess(() -> Component.literal("Removed tag '" + tagFinal + "' from " + removedFinal + " NPC(s)."), true);
                                                            return removedFinal;
                                                        }))
                                        )
                                )
                        )
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            Entity e = EntityArgument.getEntity(ctx, "target");
                                            if (!(e instanceof PokemonEntity pe)) {
                                                ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity"));
                                                return 0;
                                            }
                                            Pokemon pk = pe.getPokemon();
                                            boolean val = BoolArgumentType.getBool(ctx, "value");
                                            ShadowService.setShadow(pk, pe, val);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Shadow set to " + val), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("corr")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            Entity e = EntityArgument.getEntity(ctx, "target");
                                            if (!(e instanceof PokemonEntity pe)) {
                                                ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity"));
                                                return 0;
                                            }
                                            Pokemon pk = pe.getPokemon();
                                            int percent = IntegerArgumentType.getInteger(ctx, "value");
                                            // Convert percent (0..100) to species-absolute value
                                            int max = HeartGaugeConfig.getMax(pk);
                                            int absolute = Math.max(0, Math.min(100, percent));
                                            absolute = Math.round((absolute / 100.0f) * max);
                                            ShadowService.setHeartGauge(pk, pe, absolute);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Corruption meter set to " + percent + "%"), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("setPurified")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (!(e instanceof PokemonEntity pe)) {
                                        ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity"));
                                        return 0;
                                    }
                                    Pokemon pk = pe.getPokemon();
                                    ShadowService.fullyPurify(pk, pe);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Set to fully purified (0)"), true);
                                    return 1;
                                })))
                .then(Commands.literal("setCorrupted")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (!(e instanceof PokemonEntity pe)) {
                                        ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity"));
                                        return 0;
                                    }
                                    Pokemon pk = pe.getPokemon();
                                    ShadowService.fullyCorrupt(pk, pe);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Set to fully corrupted (100)"), true);
                                    return 1;
                                })))
                .then(Commands.literal("purify")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (!(e instanceof PokemonEntity pe)) {
                                        ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity"));
                                        return 0;
                                    }
                                    Pokemon pk = pe.getPokemon();
                                    ShadowService.fullyPurify(pk, pe);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Purified: shadow=false, meter=0"), true);
                                    return 1;
                                })))
                .then(Commands.literal("corrupt")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (!(e instanceof PokemonEntity pe)) {
                                        ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity"));
                                        return 0;
                                    }
                                    Pokemon pk = pe.getPokemon();
                                    ShadowService.fullyCorrupt(pk, pe);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Corrupted: shadow=true, meter=100"), true);
                                    return 1;
                                })))
        );
    }
}
