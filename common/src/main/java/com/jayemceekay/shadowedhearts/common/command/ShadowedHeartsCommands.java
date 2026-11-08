package com.jayemceekay.shadowedhearts.common.command;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowProgressionBuffers;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.command.argument.PokemonPropertiesArgumentType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeEvents;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowStateInitialization;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.network.aura.AuraBroadcastQueue;
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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

import java.util.*;

public class ShadowedHeartsCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shadowedhearts")
                .requires(src -> src.hasPermission(2));

        root.then(com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSites.command());
        root.then(com.jayemceekay.shadowedhearts.config.preset.RulesPresetCommands.command());
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(VoidShadowCommands.command());
        registerDebugSubcommands(debug);
        root.then(debug);
        registerSubcommands(root);

        var registeredRoot = d.register(root);
        d.register(Commands.literal("sh").requires(src -> src.hasPermission(2)).redirect(registeredRoot));
    }

    private static void registerDebugSubcommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("steps")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player;
                                    try {
                                        player = ctx.getSource().getPlayerOrException();
                                    } catch (Exception ex) {
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.player_required"));
                                        return 0;
                                    }

                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    var reg = player.registryAccess();
                                    PurificationChamberStore store = Cobblemon.INSTANCE.getStorage().getCustomStore(PurificationChamberStore.class, player.getUUID(), reg);
                                    if (store == null) {
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.chamber.unavailable"));
                                        return 0;
                                    }
                                    store.advanceSteps(count);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.command.chamber.advanced", count), true);
                                    return 1;
                                })))
                // Debug: simulate overworld walking steps that affect party Pokémon
                // Usage: /sh debug partysteps <count>
                .then(Commands.literal("partysteps")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player;
                                    try {
                                        player = ctx.getSource().getPlayerOrException();
                                    } catch (Exception ex) {
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.player_required"));
                                        return 0;
                                    }

                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    int intervals = Math.max(0, count / 256);
                                    if (intervals <= 0) {
                                        ctx.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.command.party.no_ticks", count), false);
                                        return 1;
                                    }

                                    var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                    // Take a defensive snapshot to avoid ConcurrentModificationException if any listeners
                                    // mutate the party while we iterate (e.g., aspect application syncing storage)
                                    List<Pokemon> snapshot = new ArrayList<>();
                                    for (Pokemon mon : party) snapshot.add(mon);

                                    int affected = 0;
                                    Set<Pokemon> affectedMons = new HashSet<>();
                                    for (int i = 0; i < intervals; i++) {
                                        for (Pokemon mon : snapshot) {
                                            if (ShadowAspectUtil.hasShadowAspect(mon)) {
                                                HeartGaugeEvents.onPartyStep(mon, null, false);
                                                affectedMons.add(mon);
                                                affected++;
                                            }
                                        }
                                    }

                                    for (Pokemon mon : affectedMons) {
                                        ShadowService.syncAll(mon);
                                    }
                                    final int affectedFinal = affected;
                                    final int intervalsFinal = intervals;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("message.shadowedhearts.command.party.advanced", intervalsFinal, affectedFinal),
                                            true
                                    );
                                    return 1;
                                })));
    }

    private static void registerSubcommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(NPCShadowCommands.npc())
                .then(NPCShadowCommands.shadowPool())
                .then(shadowify())
                .then(Commands.literal("purify")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "target");
                                    if (e instanceof PokemonEntity pe) {
                                        Pokemon pk = pe.getPokemon();
                                        ShadowService.fullyPurify(pk, pe);
                                        ctx.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.command.purify.entity", pk.getSpecies().getTranslatedName()), true);
                                        return 1;
                                    } else if (e instanceof ServerPlayer player) {
                                        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                        int count = 0;
                                        for (Pokemon pk : party) {
                                            ShadowService.fullyPurify(pk, null);
                                            count++;
                                        }
                                        final int countFinalPurify = count;
                                        ctx.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.command.purify.party", countFinalPurify, player.getDisplayName()), true);
                                        return count;
                                    }
                                    ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.pokemon_or_player_required"));
                                    return 0;
                                })
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            Entity e = EntityArgument.getEntity(ctx, "target");
                                            if (!(e instanceof ServerPlayer player)) {
                                                ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.slot_requires_player"));
                                                return 0;
                                            }
                                            int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
                                            Pokemon pk = party.get(slot);
                                            if (pk == null) {
                                                ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.slot_empty", slot + 1));
                                                return 0;
                                            }
                                            ShadowService.fullyPurify(pk, null);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.command.purify.party_member", pk.getSpecies().getTranslatedName(), player.getDisplayName()), true);
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
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.spawn.invalid_position"));
                                        return 0;
                                    }
                                    var properties = PokemonPropertiesArgumentType.Companion.getPokemonProperties(ctx, "properties");
                                    if (!properties.hasSpecies()) {
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.spawn.missing_species"));
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
                                            ShadowStateInitialization.ensureRequiredShadowAspects(pokemon);
                                            ShadowMoveUtil.assignNewEncounterMoves(pokemon);
                                            AuraBroadcastQueue.queueBroadcast(pokemonEntity, 2.5f, 200);

                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.shadowedhearts.command.spawn.success", pokemon.getSpecies().getTranslatedName()), true);
                                            return 1;
                                        }
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.spawn.unavailable"));
                                        return 0;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.spawn.failed", com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.component(e)));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("inspect")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                .executes(ctx -> {
                                    UUID uuid = UuidArgument.getUuid(ctx, "uuid");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    Entity entity = level.getEntity(uuid);

                                    if (entity == null) {
                                        ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.inspect.entity_missing", uuid.toString()));
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
                                                ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.inspect.slot_empty", slot + 1, player.getDisplayName()));
                                                return 0;
                                            }

                                            inspectPokemon(ctx.getSource(), pokemon);
                                            return 1;
                                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shadowify() {
        return Commands.literal("shadowify")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("entity")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> shadowifyEntity(ctx, null))
                                .then(Commands.literal("gauge")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> shadowifyEntity(ctx, IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("party")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> shadowifyParty(ctx, null))
                                .then(Commands.literal("gauge")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> shadowifyParty(ctx, IntegerArgumentType.getInteger(ctx, "value")))))))
                .then(Commands.literal("slot")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> shadowifySlot(ctx, null))
                                        .then(Commands.literal("gauge")
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> shadowifySlot(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))));
    }

    private static int shadowifyEntity(CommandContext<CommandSourceStack> ctx, Integer gauge) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "target");
        if (!(target instanceof PokemonEntity entity)) {
            ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.pokemon_required"));
            return 0;
        }
        Pokemon pokemon = entity.getPokemon();
        corrupt(pokemon, entity, gauge);
        ctx.getSource().sendSuccess(() -> gauge == null
                ? Component.translatable("message.shadowedhearts.command.shadow.entity", pokemon.getSpecies().getTranslatedName())
                : Component.translatable("message.shadowedhearts.command.shadow.entity_gauge", pokemon.getSpecies().getTranslatedName(), ShadowHeartGauge.getHeartGaugeMeter(pokemon)), true);
        return 1;
    }

    private static int shadowifyParty(CommandContext<CommandSourceStack> ctx, Integer gauge) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        // Corruption synchronizes the store, so iterate a snapshot as the step command does.
        List<Pokemon> snapshot = new ArrayList<>();
        for (Pokemon pokemon : party) snapshot.add(pokemon);
        for (Pokemon pokemon : snapshot) corrupt(pokemon, null, gauge);
        ctx.getSource().sendSuccess(() -> gauge == null
                ? Component.translatable("message.shadowedhearts.command.shadow.party", snapshot.size(), player.getDisplayName())
                : Component.translatable("message.shadowedhearts.command.shadow.party_gauge", snapshot.size(), player.getDisplayName(), gauge), true);
        return snapshot.size();
    }

    private static int shadowifySlot(CommandContext<CommandSourceStack> ctx, Integer gauge) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.slot_empty", slot));
            return 0;
        }
        corrupt(pokemon, null, gauge);
        ctx.getSource().sendSuccess(() -> gauge == null
                ? Component.translatable("message.shadowedhearts.command.shadow.party_member", pokemon.getSpecies().getTranslatedName(), player.getDisplayName())
                : Component.translatable("message.shadowedhearts.command.shadow.party_member_gauge", pokemon.getSpecies().getTranslatedName(), player.getDisplayName(), ShadowHeartGauge.getHeartGaugeMeter(pokemon)), true);
        return 1;
    }

    /** An omitted gauge means full corruption; explicit values use the species-specific clamp. */
    private static void corrupt(Pokemon pokemon, PokemonEntity entity, Integer gauge) {
        if (gauge == null) ShadowService.fullyCorrupt(pokemon, entity);
        else ShadowService.corrupt(pokemon, entity, gauge);
    }

    private static void inspectEntity(CommandSourceStack source, Entity entity) {
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.entity", entity.getName()), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.uuid", entity.getUUID().toString()), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.type", entity.getType().getDescription()), false);

        if (entity instanceof PokemonEntity pe) {
            Pokemon pk = pe.getPokemon();
            outputPokemonData(source, pk);
        }

        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.nbt"), false);
        source.sendSuccess(() -> Component.literal(nbt.toString()), false);
    }

    private static void inspectPokemon(CommandSourceStack source, Pokemon pokemon) {
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.pokemon", pokemon.getDisplayName(false)), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.uuid", pokemon.getUuid().toString()), false);

        outputPokemonData(source, pokemon);

        CompoundTag nbt = pokemon.saveToNBT(source.registryAccess(), new CompoundTag());
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.nbt"), false);
        source.sendSuccess(() -> Component.literal(nbt.toString()), false);
    }

    private static void outputPokemonData(CommandSourceStack source, Pokemon pk) {
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.data"), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.species", pk.getSpecies().getTranslatedName()), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.aspects", pk.getAspects().toString()), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.gauge", ShadowHeartGauge.getHeartGaugePercent(pk), ShadowHeartGauge.getHeartGaugeMeter(pk), HeartGaugeConfig.getMax(pk)), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.experience", ShadowProgressionBuffers.getBufferedExp(pk)), false);
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.command.inspect.evs", Arrays.toString(ShadowProgressionBuffers.getBufferedEvs(pk))), false);
    }
}
