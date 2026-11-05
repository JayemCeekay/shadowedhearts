package com.jayemceekay.shadowedhearts.common.command;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.jayemceekay.shadowedhearts.AspectHolder;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowProfile;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowProfiles;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowTargets;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages;
import com.jayemceekay.shadowedhearts.data.ShadowAspectPresets;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import com.jayemceekay.shadowedhearts.network.trainer.OpenTrainerEditorPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** Operator authoring; inspection and validation never construct or roll a battle party. */
public final class NPCShadowCommands {
    private NPCShadowCommands() {}

    private static final String PRESET_PREFIX = "sh_shadow_presets/";
    private static final SuggestionProvider<CommandSourceStack> PRESETS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ShadowAspectPresets.ids(ctx.getSource().getServer()), builder);
    private static final SuggestionProvider<CommandSourceStack> POOLS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(ShadowPools.ids(ctx.getSource().getServer()), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> npc() {
        var command = Commands.literal("npc").requires(source -> source.hasPermission(2))
                .then(edit())
                .then(Commands.literal("tag")
                        .then(Commands.literal("add").then(targets().then(Commands.argument("tag", StringArgumentType.string())
                                .executes(safe(ctx -> changeTag(ctx, true))))))
                        .then(Commands.literal("remove").then(targets().then(Commands.argument("tag", StringArgumentType.string())
                                .executes(safe(ctx -> changeTag(ctx, false)))))))
                .then(presets())
                .then(Commands.literal("inspect").then(targets().executes(safe(ctx -> report(ctx, true)))))
                .then(Commands.literal("validate").then(targets().executes(safe(ctx -> report(ctx, false)))))
                .then(Commands.literal("reset").then(targets().executes(safe(NPCShadowCommands::reset))))
                .then(set());
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> edit() {
        return Commands.literal("edit")
                .executes(safe(ctx -> {
                    new OpenTrainerEditorPacket("").sendToPlayer(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.argument("target", EntityArgument.entity()).executes(safe(ctx -> {
                    Entity entity = EntityArgument.getEntity(ctx, "target");
                    if (!NPCShadowTargets.isSupported(entity)) {
                        throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.select_target"));
                    }
                    new OpenTrainerEditorPacket(entity.getUUID().toString()).sendToPlayer(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> set() {
        var fields = targets();
        fields.then(booleanField("enabled", "sh_shadow_party", "sh_shadow_party_false"));
        var mode = Commands.literal("mode");
        for (String value : List.of("convert", "append", "replace")) {
            mode.then(Commands.literal(value).executes(safe(ctx -> setField(ctx, "mode", "sh_shadow_mode_" + value))));
        }
        fields.then(mode);
        fields.then(Commands.literal("count").then(Commands.argument("value", IntegerArgumentType.integer(0, 6))
                .executes(safe(ctx -> setField(ctx, "count", "sh_shadow_n" + IntegerArgumentType.getInteger(ctx, "value"))))));
        fields.then(Commands.literal("pool")
                .then(Commands.literal("none").executes(safe(ctx -> setField(ctx, "pool", "sh_pool_none"))))
                .then(poolId().executes(safe(ctx -> {
                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "poolId");
                    requirePool(ctx.getSource().getServer(), id);
                    return setField(ctx, "pool", "sh_pool/" + id.getNamespace() + "/" + id.getPath());
                }))));
        fields.then(Commands.literal("level")
                .then(Commands.literal("match").executes(safe(ctx -> setField(ctx, "level", "sh_lvl_match"))))
                .then(Commands.literal("fixed").then(Commands.argument("value", IntegerArgumentType.integer(1))
                        .executes(safe(ctx -> setField(ctx, "level", "sh_lvl_fixed_" + IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("plus").then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(safe(ctx -> setField(ctx, "level", "sh_lvl_plus_" + IntegerArgumentType.getInteger(ctx, "value")))))));
        fields.then(Commands.literal("chance")
                .then(Commands.literal("default").executes(safe(ctx -> setField(ctx, "chance", "sh_convert_chance_default"))))
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                        .executes(safe(ctx -> setField(ctx, "chance", "sh_convert_chance_" + IntegerArgumentType.getInteger(ctx, "value"))))));
        fields.then(booleanField("unique", "sh_unique", "sh_unique_false"));
        fields.then(booleanField("enforce-evo-min", "sh_lvl_enforce_evo_min", "sh_lvl_enforce_evo_min_false"));
        return Commands.literal("set").then(fields);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> booleanField(String field, String yes, String no) {
        return Commands.literal(field).then(Commands.argument("value", BoolArgumentType.bool())
                .executes(safe(ctx -> setField(ctx, field, BoolArgumentType.getBool(ctx, "value") ? yes : no))));
    }

    private static int setField(CommandContext<CommandSourceStack> ctx, String field, String tag) throws CommandSyntaxException {
        var entities = supportedTargets(ctx);
        validateTag(ctx.getSource().getServer(), tag);
        for (Entity entity : entities) {
            // Check capacity before removing existing settings, avoiding a partial update at Minecraft's tag limit.
            if (entity.getTags().size() >= 1024 && entity.getTags().stream().noneMatch(value -> matchesField(field, value))) {
                throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.tag_capacity", entity.getUUID().toString()));
            }
        }
        for (Entity entity : entities) {
            removeSettings(entity, value -> matchesField(field, value));
            if (!entity.addTag(tag)) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.store_failed", entity.getUUID().toString()));
        }
        success(ctx, Component.translatable("message.shadowedhearts.command.npc.set", field, entities.size()));
        return entities.size();
    }

    static boolean matchesField(String field, String tag) {
        return switch (field) {
            case "enabled" -> tag.equals("sh_shadow_party") || tag.equals("sh_shadow_party_false");
            case "mode" -> tag.startsWith("sh_shadow_mode_");
            case "count" -> tag.startsWith("sh_shadow_n");
            case "pool" -> tag.startsWith("sh_pool/") || tag.equals("sh_pool_none");
            case "level" -> tag.equals("sh_lvl_match") || tag.startsWith("sh_lvl_fixed_") || tag.startsWith("sh_lvl_plus_");
            case "chance" -> tag.startsWith("sh_convert_chance_");
            case "unique" -> tag.equals("sh_unique") || tag.equals("sh_unique_false");
            case "enforce-evo-min" -> tag.equals("sh_lvl_enforce_evo_min") || tag.equals("sh_lvl_enforce_evo_min_false");
            default -> false;
        };
    }

    private static int changeTag(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        String tag = StringArgumentType.getString(ctx, "tag");
        var entities = supportedTargets(ctx);
        if (add) validateTag(ctx.getSource().getServer(), tag);
        int changed = 0;
        for (Entity entity : entities) {
            if (add ? entity.addTag(tag) : entity.removeTag(tag)) changed++;
        }
        if (changed == 0) throw new MessageException(add
                ? Component.translatable("message.shadowedhearts.command.npc.tags_unchanged")
                : Component.translatable("message.shadowedhearts.command.npc.tags_missing"));
        success(ctx, (add ? Component.translatable("message.shadowedhearts.command.npc.tag_added", tag, changed) : Component.translatable("message.shadowedhearts.command.npc.tag_removed", tag, changed)));
        return changed;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var entities = supportedTargets(ctx);
        int removed = 0;
        for (Entity entity : entities) removed += removeSettings(entity, NPCShadowProfiles::isShadowTag);
        success(ctx, Component.translatable("message.shadowedhearts.command.npc.reset", removed, entities.size()));
        return entities.size();
    }

    private static int removeSettings(Entity entity, Predicate<String> remove) {
        int count = 0;
        for (String tag : new HashSet<>(entity.getTags())) {
            if (remove.test(tag) && entity.removeTag(tag)) count++;
        }
        if (entity instanceof AspectHolder holder) {
            var aspects = new HashSet<>(holder.shadowedhearts$getAspects());
            int before = aspects.size();
            aspects.removeIf(remove);
            if (aspects.size() != before) {
                holder.shadowedhearts$setAspects(aspects);
                count += before - aspects.size();
            }
        }
        return count;
    }

    private static int report(CommandContext<CommandSourceStack> ctx, boolean includeDetails) throws CommandSyntaxException {
        int valid = 0;
        for (Entity entity : supportedTargets(ctx)) {
            var result = NPCShadowProfiles.resolve(entity);
            output(ctx, Component.translatable("message.shadowedhearts.command.npc.report", entity.getName(), entity.getUUID().toString(), NPCShadowTargets.labelText(entity), result.description()));
            output(ctx, NPCShadowTargets.helpText(entity));
            var profile = result.profile();
            output(ctx, Component.translatable("message.shadowedhearts.command.npc.profile",
                    TrainerEditorMessages.value(Boolean.toString(profile.enabled())),
                    TrainerEditorMessages.value(profile.mode().name().toLowerCase(java.util.Locale.ROOT)), profile.count(),
                    TrainerEditorMessages.value(profile.levelMode().name().toLowerCase(java.util.Locale.ROOT)), profile.levelValue(),
                    profile.poolId() == null ? TrainerEditorMessages.value("none") : Component.literal(profile.poolId()),
                    profile.convertChance() < 0 ? Component.translatable("options.default") : Component.literal(profile.convertChance() + "%"),
                    TrainerEditorMessages.value(Boolean.toString(profile.unique())),
                    TrainerEditorMessages.value(Boolean.toString(profile.enforceMinEvolutionLevel()))));
            if (includeDetails) {
                output(ctx, Component.translatable("message.shadowedhearts.command.npc.tags", sorted(entity.getTags())));
                if (entity instanceof AspectHolder holder) output(ctx, Component.translatable("message.shadowedhearts.command.npc.aspects", sorted(holder.shadowedhearts$getAspects())));
                result.sources().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(entry -> output(ctx, Component.translatable("message.shadowedhearts.command.npc.setting_source",
                                TrainerEditorMessages.field(entry.getKey()), TrainerEditorMessages.source(entry.getValue()))));
            }
            for (Component warning : result.warnings()) output(ctx, Component.translatable("message.shadowedhearts.command.npc.warning", warning));
            for (Component error : result.errors()) ctx.getSource().sendFailure(Component.translatable("message.shadowedhearts.command.npc.invalid", error));
            if (result.valid()) valid++;
        }
        return valid;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> presets() {
        return Commands.literal("preset")
                .then(Commands.literal("define").then(presetId().then(Commands.argument("aspects", StringArgumentType.greedyString())
                        .executes(safe(ctx -> writePreset(ctx, "define"))))))
                .then(Commands.literal("add").then(presetId().then(Commands.argument("aspects", StringArgumentType.greedyString())
                        .executes(safe(ctx -> writePreset(ctx, "add"))))))
                .then(Commands.literal("remove").then(presetId().then(Commands.argument("aspects", StringArgumentType.greedyString())
                        .executes(safe(ctx -> writePreset(ctx, "remove"))))))
                .then(Commands.literal("delete").then(presetId().executes(safe(ctx -> {
                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "presetId");
                    ShadowAspectPresets.deletePreset(ctx.getSource().getServer(), id);
                    success(ctx, Component.translatable("message.shadowedhearts.command.npc.preset_deleted", id.toString()));
                    return 1;
                }))))
                .then(Commands.literal("list").executes(safe(ctx -> {
                    var ids = ShadowAspectPresets.ids(ctx.getSource().getServer());
                    output(ctx, Component.translatable("message.shadowedhearts.command.npc.presets", sortedIds(ids)));
                    return ids.size();
                })))
                .then(Commands.literal("show").then(presetId().executes(safe(ctx -> {
                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "presetId");
                    var aspects = requirePreset(ctx.getSource().getServer(), id);
                    output(ctx, Component.translatable("message.shadowedhearts.command.npc.preset", id.toString(), String.join(", ", aspects)));
                    return 1;
                }))))
                .then(Commands.literal("apply").then(targets().then(presetId().executes(safe(ctx -> {
                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "presetId");
                    var aspects = requirePreset(ctx.getSource().getServer(), id);
                    for (String tag : aspects) validateTag(ctx.getSource().getServer(), tag);
                    validatePresetDefinition(ctx.getSource().getServer(), id, aspects);
                    var entities = supportedTargets(ctx);
                    String tag = PRESET_PREFIX + id.getNamespace() + "/" + id.getPath();
                    int changed = 0;
                    for (Entity entity : entities) if (entity.addTag(tag)) changed++;
                    if (changed == 0) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.preset_unchanged", id.toString()));
                    success(ctx, Component.translatable("message.shadowedhearts.command.npc.preset_applied", id.toString(), changed));
                    return changed;
                })))));
    }

    private static int writePreset(CommandContext<CommandSourceStack> ctx, String operation) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "presetId");
        var server = ctx.getSource().getServer();
        List<String> requested = Arrays.stream(StringArgumentType.getString(ctx, "aspects").trim().split("\\s+"))
                .filter(value -> !value.isBlank()).distinct().toList();
        if (requested.isEmpty()) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.aspects_required"));
        List<String> aspects = operation.equals("define") ? new ArrayList<>() : new ArrayList<>(requirePreset(server, id));
        if (operation.equals("remove")) {
            if (!aspects.removeAll(requested)) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.aspects_missing", id.toString()));
        } else {
            for (String tag : requested) {
                validateTag(server, tag);
                if (!aspects.contains(tag)) aspects.add(tag);
            }
            if (!operation.equals("define") && aspects.equals(ShadowAspectPresets.get(server, id))) {
                throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.aspects_unchanged", id.toString()));
            }
        }
        if (aspects.isEmpty()) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.preset_empty"));
        validatePresetDefinition(server, id, aspects);
        ShadowAspectPresets.savePreset(server, id, aspects);
        success(ctx, Component.translatable("message.shadowedhearts.command.npc.preset_saved", id.toString(), aspects.size()));
        return 1;
    }

    private static void validatePresetDefinition(MinecraftServer server, ResourceLocation id, List<String> aspects) {
        var resolved = NPCShadowProfile.resolve(List.of(new NPCShadowProfile.Layer("Preset " + id, aspects)),
                referenced -> referenced.equals(id.toString()) ? aspects : ShadowAspectPresets.get(server, ResourceLocation.parse(referenced)));
        if (!resolved.valid()) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.preset_invalid", TrainerEditorMessages.join(resolved.errors())));
        if (resolved.profile().poolId() != null) requirePool(server, ResourceLocation.parse(resolved.profile().poolId()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> shadowPool() {
        return Commands.literal("shadowpool").requires(source -> source.hasPermission(2))
                .then(Commands.literal("define").then(poolId().then(Commands.argument("entries", StringArgumentType.greedyString())
                        .executes(safe(ctx -> {
                            ResourceLocation id = ResourceLocationArgument.getId(ctx, "poolId");
                            List<ShadowPools.WeightedEntry> entries = new ArrayList<>();
                            for (PoolEntryInput entry : parsePoolEntries(StringArgumentType.getString(ctx, "entries"))) {
                                PokemonProperties properties = ShadowPools.parseProperties(entry.properties());
                                entries.add(new ShadowPools.WeightedEntry(properties, entry.weight()));
                            }
                            ShadowPools.savePool(ctx.getSource().getServer(), id, entries);
                            success(ctx, Component.translatable("message.shadowedhearts.command.npc.pool_saved", id.toString(), entries.size()));
                            return 1;
                        })))))
                .then(Commands.literal("delete").then(poolId().executes(safe(ctx -> {
                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "poolId");
                    ShadowPools.deletePool(ctx.getSource().getServer(), id);
                    success(ctx, Component.translatable("message.shadowedhearts.command.npc.pool_deleted", id.toString()));
                    return 1;
                }))))
                .then(Commands.literal("list").executes(safe(ctx -> {
                    var ids = ShadowPools.ids(ctx.getSource().getServer());
                    output(ctx, Component.translatable("message.shadowedhearts.command.npc.pools", sortedIds(ids)));
                    return ids.size();
                })))
                .then(Commands.literal("show").then(poolId().executes(safe(ctx -> {
                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "poolId");
                    output(ctx, Component.translatable("message.shadowedhearts.command.npc.pool", id.toString()));
                    for (var entry : requirePool(ctx.getSource().getServer(), id)) {
                        output(ctx, Component.translatable("message.shadowedhearts.command.npc.pool_entry", entry.props.asString(" "), entry.weight));
                    }
                    return 1;
                }))));
    }

    record PoolEntryInput(String properties, int weight) {}

    static List<PoolEntryInput> parsePoolEntries(String text) {
        List<PoolEntryInput> result = new ArrayList<>();
        boolean semicolonSeparated = text.contains(";");
        String[] parts = text.split(semicolonSeparated ? ";" : ",", -1);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            // A trailing semicolon also selects this format for a single entry containing native move commas.
            if (semicolonSeparated && index == parts.length - 1 && part.isBlank() && !result.isEmpty()) continue;
            String[] values = part.trim().split("\\|", -1);
            if (values.length > 2 || values[0].isBlank()) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.pool_entry_invalid", part));
            int weight = 1;
            if (values.length == 2) {
                try {
                    weight = Integer.parseInt(values[1].trim());
                } catch (NumberFormatException exception) {
                    throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.pool_weight_invalid", part));
                }
            }
            if (weight <= 0) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.pool_weight_positive", part));
            result.add(new PoolEntryInput(values[0].trim(), weight));
        }
        return List.copyOf(result);
    }

    private static void validateTag(MinecraftServer server, String tag) {
        if (tag == null || tag.isBlank()) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.tag_required"));
        // Preserve the historical arbitrary entity-tag path and non-shadow preset contents.
        if (!NPCShadowProfiles.isShadowTag(tag)) return;
        var error = NPCShadowProfiles.validateTag(tag);
        if (error != null) throw new com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException(error);
        if (tag.startsWith(PRESET_PREFIX)) {
            String id = NPCShadowProfiles.canonicalId(tag.substring(PRESET_PREFIX.length()));
            requirePreset(server, ResourceLocation.parse(id));
        } else if (tag.startsWith("sh_pool/")) {
            String id = NPCShadowProfiles.canonicalId(tag.substring("sh_pool/".length()));
            requirePool(server, ResourceLocation.parse(id));
        }
    }

    private static List<String> requirePreset(MinecraftServer server, ResourceLocation id) {
        var aspects = ShadowAspectPresets.get(server, id);
        if (aspects.isEmpty()) throw new MessageException(TrainerEditorMessages.join(ShadowAspectPresets.diagnostics(server, id)));
        return aspects;
    }

    private static List<ShadowPools.WeightedEntry> requirePool(MinecraftServer server, ResourceLocation id) {
        var entries = ShadowPools.get(server, id);
        if (entries.isEmpty()) throw new MessageException(TrainerEditorMessages.join(ShadowPools.diagnostics(server, id)));
        return entries;
    }

    private static List<Entity> supportedTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var selected = EntityArgument.getEntities(ctx, "targets");
        List<Entity> entities = new ArrayList<>(selected.stream().filter(NPCShadowTargets::isSupported).toList());
        if (entities.isEmpty()) throw new MessageException(Component.translatable("message.shadowedhearts.command.npc.no_targets"));
        if (entities.size() != selected.size()) output(ctx, Component.translatable("message.shadowedhearts.command.npc.skipped", selected.size() - entities.size()));
        return entities;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector> targets() {
        return Commands.argument("targets", EntityArgument.entities());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> presetId() {
        return Commands.argument("presetId", ResourceLocationArgument.id()).suggests(PRESETS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> poolId() {
        return Commands.argument("poolId", ResourceLocationArgument.id()).suggests(POOLS);
    }

    private static Command<CommandSourceStack> safe(Command<CommandSourceStack> command) {
        return ctx -> {
            try {
                return command.run(ctx);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                ctx.getSource().sendFailure(exception instanceof MessageException localized ? localized.component :
                        Component.translatable("message.shadowedhearts.command.npc.failed", com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.component(exception)));
                return 0;
            }
        };
    }

    /** Keep the component intact until it reaches the command source's client. */
    private static final class MessageException extends IllegalArgumentException {
        private final Component component;
        private MessageException(Component component) {
            super(component.getString());
            this.component = component;
        }
    }

    private static Component sorted(Collection<String> values) {
        return values.isEmpty() ? TrainerEditorMessages.value("none")
                : Component.literal(values.stream().sorted().collect(java.util.stream.Collectors.joining(", ")));
    }

    private static Component sortedIds(Collection<ResourceLocation> values) {
        return sorted(values.stream().map(ResourceLocation::toString).toList());
    }

    private static void output(CommandContext<CommandSourceStack> ctx, Component text) {
        ctx.getSource().sendSuccess(() -> text, false);
    }

    private static void success(CommandContext<CommandSourceStack> ctx, Component text) {
        ctx.getSource().sendSuccess(() -> text, true);
    }
}
