package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.command.argument.PokemonPropertiesArgumentType;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.heart.HeartGaugeEvents;
import com.jayemceekay.shadowedhearts.server.AuraBroadcastQueue;
import com.jayemceekay.shadowedhearts.server.WildShadowSpawnListener;
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore;
import com.jayemceekay.shadowedhearts.worldgen.CraterGenerator;
import com.jayemceekay.shadowedhearts.worldgen.ImpactScheduler;
import com.jayemceekay.shadowedhearts.worldgen.PlayerActivityHeatmap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ShadowCommands {

    public static void registerSubcommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("steps")
                        .requires(src -> src.hasPermission(2))
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
                        .requires(src -> src.hasPermission(2))
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
                // Convenience: manage NPC tags for Shadow injector
                .then(Commands.literal("npc")
                        .requires(src -> src.hasPermission(2))
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
                                                                    if (npc.addTag(tag))
                                                                        applied++;
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
                                                                    if (npc.removeTag(tag))
                                                                        removed++;
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
                .then(Commands.literal("shadowify")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (e instanceof PokemonEntity pe) {
                                        Pokemon pk = pe.getPokemon();
                                        ShadowService.fullyCorrupt(pk, pe);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + pk.getSpecies().getName()), true);
                                        return 1;
                                    } else if (e instanceof ServerPlayer player) {
                                        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                        int count = 0;
                                        for (Pokemon pk : party) {
                                            ShadowService.fullyCorrupt(pk, null);
                                            count++;
                                        }
                                        final int countFinal = count;
                                        ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + countFinal + " Pokémon in " + player.getScoreboardName() + "'s party"), true);
                                        return count;
                                    }
                                    ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity or a player"));
                                    return 0;
                                })
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            Entity e = EntityArgument.getEntity(ctx, "target");
                                            int val = IntegerArgumentType.getInteger(ctx, "slot");
                                            if (e instanceof PokemonEntity pe) {
                                                Pokemon pk = pe.getPokemon();
                                                ShadowService.corrupt(pk, pe, val);
                                                ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + pk.getSpecies().getName() + " with heart gauge " + val), true);
                                                return 1;
                                            } else if (e instanceof ServerPlayer player) {
                                                int slot = val - 1;
                                                var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                                Pokemon pk = party.get(slot);
                                                if (pk == null) {
                                                    ctx.getSource().sendFailure(Component.literal("No Pokemon in slot " + (slot + 1)));
                                                    return 0;
                                                }
                                                ShadowService.fullyCorrupt(pk, null);
                                                ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + pk.getSpecies().getName() + " in " + player.getScoreboardName() + "'s party"), true);
                                                return 1;
                                            }
                                            return 0;
                                        })
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                                    if (!(e instanceof ServerPlayer player)) {
                                                        ctx.getSource().sendFailure(Component.literal("Slot argument only applicable when targeting a player"));
                                                        return 0;
                                                    }
                                                    int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                                    var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                                    Pokemon pk = party.get(slot);
                                                    if (pk == null) {
                                                        ctx.getSource().sendFailure(Component.literal("No Pokemon in slot " + (slot + 1)));
                                                        return 0;
                                                    }
                                                    ShadowService.corrupt(pk, null, value);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + pk.getSpecies().getName() + " in " + player.getScoreboardName() + "'s party with heart gauge " + value), true);
                                                    return 1;
                                                })))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Entity e = EntityArgument.getEntity(ctx, "target");
                                            int value = IntegerArgumentType.getInteger(ctx, "value");
                                            if (e instanceof PokemonEntity pe) {
                                                Pokemon pk = pe.getPokemon();
                                                ShadowService.corrupt(pk, pe, value);
                                                ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + pk.getSpecies().getName() + " with heart gauge " + value), true);
                                                return 1;
                                            } else if (e instanceof ServerPlayer player) {
                                                var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                                int count = 0;
                                                for (Pokemon pk : party) {
                                                    ShadowService.corrupt(pk, null, value);
                                                    count++;
                                                }
                                                final int countFinal = count;
                                                final int valFinal = value;
                                                ctx.getSource().sendSuccess(() -> Component.literal("Shadowified " + countFinal + " Pokémon in " + player.getScoreboardName() + "'s party with heart gauge " + valFinal), true);
                                                return count;
                                            }
                                            ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity or a player"));
                                            return 0;
                                        }))
                        ))
                .then(Commands.literal("purify")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (e instanceof PokemonEntity pe) {
                                        Pokemon pk = pe.getPokemon();
                                        ShadowService.fullyPurify(pk, pe);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Purified " + pk.getSpecies().getName()), true);
                                        return 1;
                                    } else if (e instanceof ServerPlayer player) {
                                        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                        int count = 0;
                                        for (Pokemon pk : party) {
                                            ShadowService.fullyPurify(pk, null);
                                            count++;
                                        }
                                        final int countFinalPurify = count;
                                        ctx.getSource().sendSuccess(() -> Component.literal("Purified " + countFinalPurify + " Pokémon in " + player.getScoreboardName() + "'s party"), true);
                                        return count;
                                    }
                                    ctx.getSource().sendFailure(Component.literal("Target must be a Pokemon entity or a player"));
                                    return 0;
                                })
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            Entity e = EntityArgument.getEntity(ctx, "target");
                                            if (!(e instanceof ServerPlayer player)) {
                                                ctx.getSource().sendFailure(Component.literal("Slot argument only applicable when targeting a player"));
                                                return 0;
                                            }
                                            int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                            Pokemon pk = party.get(slot);
                                            if (pk == null) {
                                                ctx.getSource().sendFailure(Component.literal("No Pokemon in slot " + (slot + 1)));
                                                return 0;
                                            }
                                            ShadowService.fullyPurify(pk, null);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Purified " + pk.getSpecies().getName() + " in " + player.getScoreboardName() + "'s party"), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("spawn")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("properties", PokemonPropertiesArgumentType.Companion.properties())
                                .executes(ctx -> {
                                    var pos = ctx.getSource().getPosition();
                                    var world = ctx.getSource().getLevel();
                                    var blockPos = net.minecraft.core.BlockPos.containing(pos);
                                    if (!Level.isInSpawnableBounds(blockPos)) {
                                        ctx.getSource().sendFailure(Component.literal("Invalid position"));
                                        return 0;
                                    }
                                    var properties = PokemonPropertiesArgumentType.Companion.getPokemonProperties(ctx, "properties");
                                    if (!properties.hasSpecies()) {
                                        ctx.getSource().sendFailure(Component.literal("No species specified"));
                                        return 0;
                                    }
                                    try {
                                        PokemonEntity pokemonEntity = properties.createEntity(world, null);
                                        pokemonEntity.moveTo(pos.x, pos.y, pos.z, pokemonEntity.getYRot(), pokemonEntity.getXRot());
                                        pokemonEntity.getEntityData().set(PokemonEntity.Companion.getSPAWN_DIRECTION(), pokemonEntity.getRandom().nextFloat() * 360F);
                                        pokemonEntity.finalizeSpawn(world, world.getCurrentDifficultyAt(blockPos), MobSpawnType.COMMAND, null);
                                        if (world.addFreshEntity(pokemonEntity)) {
                                            Pokemon pokemon = pokemonEntity.getPokemon();
                                            ShadowService.setShadow(pokemon, pokemonEntity, true);
                                            ShadowService.setHeartGauge(pokemon, pokemonEntity, HeartGaugeConfig.getMax(pokemon));
                                            PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
                                            WildShadowSpawnListener.assignShadowMoves(pokemon);
                                            AuraBroadcastQueue.queueBroadcast(pokemonEntity, 2.5f, 200);

                                            ctx.getSource().sendSuccess(() -> Component.literal("Spawned Shadow " + pokemon.getSpecies().getName()), true);
                                            return 1;
                                        }
                                        ctx.getSource().sendFailure(Component.literal("Unable to spawn at the given position"));
                                        return 0;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        ctx.getSource().sendFailure(Component.literal("Failed to spawn: " + e.getMessage()));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("impact")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("force")
                                .executes(ctx -> {
                                    ImpactScheduler.attemptImpact(ctx.getSource().getLevel());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Forced an impact attempt."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("at")
                                .executes(ctx -> {
                                    CraterGenerator.generateCrater(ctx.getSource().getLevel(), net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Generated crater at current position."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("heatmap")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    double activity = PlayerActivityHeatmap.getActivity(ctx.getSource().getLevel(), player.chunkPosition());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Current chunk activity: " + activity), false);
                                    return 1;
                                })))
                .then(Commands.literal("inspect")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                .executes(ctx -> {
                                    UUID uuid = UuidArgument.getUuid(ctx, "uuid");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    Entity entity = level.getEntity(uuid);

                                    if (entity == null) {
                                        ctx.getSource().sendFailure(Component.literal("No entity found with UUID: " + uuid));
                                        return 0;
                                    }

                                    inspectEntity(ctx.getSource(), entity);
                                    return 1;
                                }))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
                                            int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                            Pokemon pokemon = party.get(slot);

                                            if (pokemon == null) {
                                                ctx.getSource().sendFailure(Component.literal("No Pokemon found in slot " + (slot + 1) + " of " + player.getScoreboardName() + "'s party."));
                                                return 0;
                                            }

                                            inspectPokemon(ctx.getSource(), pokemon);
                                            return 1;
                                        }))));
    }

    private static void inspectEntity(CommandSourceStack source, Entity entity) {
        source.sendSuccess(() -> Component.literal("--- Entity Inspection: " + entity.getName().getString() + " ---"), false);
        source.sendSuccess(() -> Component.literal("UUID: " + entity.getUUID()), false);
        source.sendSuccess(() -> Component.literal("Type: " + entity.getType().getDescription().getString()), false);

        if (entity instanceof PokemonEntity pe) {
            Pokemon pk = pe.getPokemon();
            outputPokemonData(source, pk);
        }

        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        source.sendSuccess(() -> Component.literal("--- NBT Data ---"), false);
        source.sendSuccess(() -> Component.literal(nbt.toString()), false);
    }

    private static void inspectPokemon(CommandSourceStack source, Pokemon pokemon) {
        source.sendSuccess(() -> Component.literal("--- Pokemon Inspection: " + pokemon.getDisplayName(false).getString() + " ---"), false);
        source.sendSuccess(() -> Component.literal("UUID: " + pokemon.getUuid()), false);

        outputPokemonData(source, pokemon);

        CompoundTag nbt = pokemon.saveToNBT(source.registryAccess(), new CompoundTag());
        source.sendSuccess(() -> Component.literal("--- NBT Data ---"), false);
        source.sendSuccess(() -> Component.literal(nbt.toString()), false);
    }

    private static void outputPokemonData(CommandSourceStack source, Pokemon pk) {
        source.sendSuccess(() -> Component.literal("--- Pokemon Data ---"), false);
        source.sendSuccess(() -> Component.literal("Species: " + pk.getSpecies().getName()), false);
        source.sendSuccess(() -> Component.literal("Aspects: " + pk.getAspects()), false);
        source.sendSuccess(() -> Component.literal("Heart Gauge: " + PokemonAspectUtil.getHeartGaugePercent(pk) + "% (" + PokemonAspectUtil.getHeartGaugeMeter(pk) + "/" + HeartGaugeConfig.getMax(pk) + ")"), false);
        source.sendSuccess(() -> Component.literal("Buffered EXP: " + PokemonAspectUtil.getBufferedExp(pk)), false);
        source.sendSuccess(() -> Component.literal("Buffered EVs: " + Arrays.toString(PokemonAspectUtil.getBufferedEvs(pk))), false);
    }
}
