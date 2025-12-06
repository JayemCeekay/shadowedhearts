package com.jayemceekay.shadowedhearts;
// /shadow set <pokemonEntity> <true|false>
// /shadow corr <pokemonEntity> <0..100>

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.runs.MIssionCommands.MissionCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;

public class ShadowCommands {
    /**
     * Register the /shadow command tree.
     */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("shadow").requires(src -> src.hasPermission(2))
                .then(Commands.literal("depthdump").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Use client command /sh_depthdump in chat (client only)."), false);
                    return 1;
                }))
                .then(Commands.literal("battledump").executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("Battle debug dump not available in this build."));
                    return 0;
                }))
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
                                            int v = IntegerArgumentType.getInteger(ctx, "value");
                                            ShadowService.setHeartGauge(pk, pe, v);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Corruption meter set to " + v), true);
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
        d.register(Commands.literal("shadowmission").requires(src -> src.hasPermission(2))
                .then(Commands.literal("tp")
                        .then(Commands.argument("runId", LongArgumentType.longArg(1))
                                .executes(ctx -> MissionCommands.tpToMissions(ctx, LongArgumentType.getLong(ctx, "runId")))))
                .then(Commands.literal("gen")
                        .then(Commands.literal("place")
                                .then(Commands.argument("runId", LongArgumentType.longArg(1))
                                        .then(Commands.argument("structureId", ResourceLocationArgument.id())
                                                .executes(ctx -> MissionCommands.genPlaceStructure(
                                                        ctx,
                                                        LongArgumentType.getLong(ctx, "runId"),
                                                        ResourceLocationArgument.getId(ctx, "structureId"),
                                                        Rotation.NONE
                                                ))
                                                .then(Commands.argument("rotation", StringArgumentType.word())
                                                        .executes(ctx -> MissionCommands.genPlaceStructure(
                                                                ctx,
                                                                LongArgumentType.getLong(ctx, "runId"),
                                                                ResourceLocationArgument.getId(ctx, "structureId"),
                                                                MissionCommands.parseRotation(StringArgumentType.getString(ctx, "rotation"))
                                                        ))))))
                        .then(Commands.literal("demo")
                                .then(Commands.argument("runId", LongArgumentType.longArg(1))
                                        .executes(ctx -> MissionCommands.genDemo(ctx, LongArgumentType.getLong(ctx, "runId"))))))
                .then(Commands.literal("give_fragment")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> MissionCommands.giveFragment(
                                                ctx,
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "value")
                                        )))))
                .then(Commands.literal("give_signal")
                        .then(Commands.argument("theme", StringArgumentType.word())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                                        .executes(ctx -> MissionCommands.giveSignal(
                                                ctx,
                                                StringArgumentType.getString(ctx, "theme"),
                                                IntegerArgumentType.getInteger(ctx, "tier"),
                                                null
                                        ))
                                        .then(Commands.argument("affixes", StringArgumentType.greedyString())
                                                .executes(ctx -> MissionCommands.giveSignal(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "theme"),
                                                        IntegerArgumentType.getInteger(ctx, "tier"),
                                                        StringArgumentType.getString(ctx, "affixes")
                                                ))))))
                .then(Commands.literal("start")
                        .executes(ctx -> MissionCommands.startRun(ctx, false))
                        .then(Commands.literal("demo").executes(ctx -> MissionCommands.startRun(ctx, true))))
                .then(Commands.literal("seed")
                        .then(Commands.argument("runId", LongArgumentType.longArg(1))
                                .executes(ctx -> MissionCommands.seedStub(ctx, LongArgumentType.getLong(ctx, "runId")))))
                .then(Commands.literal("abort")
                        .then(Commands.argument("runId", LongArgumentType.longArg(1))
                                .executes(ctx -> MissionCommands.abortStub(ctx, LongArgumentType.getLong(ctx, "runId")))))
        );
        d.register(Commands.literal("poketoss")
                .then(Commands.literal("order")
                        .then(Commands.literal("follow")
                                .executes(ctx -> PokeTossCommands.follow(ctx, null))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> PokeTossCommands.follow(ctx, EntityArgument.getEntity(ctx, "target")))))
                        .then(Commands.literal("regroup")
                                .executes(ctx -> PokeTossCommands.regroup(ctx, null))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> PokeTossCommands.regroup(ctx, EntityArgument.getEntity(ctx, "target"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> PokeTossCommands.clear(ctx, EntityArgument.getEntity(ctx, "target")))))
        );

    }
}
