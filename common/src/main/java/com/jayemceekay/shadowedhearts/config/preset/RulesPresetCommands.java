package com.jayemceekay.shadowedhearts.config.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class RulesPresetCommands {
    private RulesPresetCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        var root = Commands.literal("rules").requires(source -> source.hasPermission(2));
        var snapshots = Commands.literal("snapshots");
        for (String scope : new String[]{"gameplay", "world"}) {
            var scoped = Commands.literal(scope);
            scoped.then(Commands.literal("list").executes(context -> snapshot(context.getSource(), scope, "list", "")));
            scoped.then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(context -> snapshot(context.getSource(), scope, "create", StringArgumentType.getString(context, "name")))));
            for (String action : new String[]{"preview", "restore", "delete"}) {
                scoped.then(Commands.literal(action).then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> snapshot(context.getSource(), scope, action, StringArgumentType.getString(context, "id")))));
            }
            snapshots.then(scoped);
        }
        root.then(snapshots);
        root.then(Commands.literal("list").executes(context -> {
            for (var preset : RulesPresetCatalog.all()) {
                context.getSource().sendSuccess(() -> Component.literal(preset.id() + ": ")
                        .append(Component.translatable(preset.titleKey()))
                        .append(" — ").append(Component.translatable(preset.descriptionKey())), false);
            }
            return RulesPresetCatalog.all().size();
        }));
        for (String action : new String[]{"preview", "apply"}) {
            root.then(Commands.literal(action).then(Commands.argument("preset", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            RulesPresetCatalog.all().stream().map(RulesPresetCatalog.Preset::id), builder))
                    .executes(context -> execute(context.getSource(), action,
                            StringArgumentType.getString(context, "preset")))));
        }
        return root;
    }

    private static int snapshot(CommandSourceStack source, String scope, String action, String value) {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", 0);
        request.addProperty("presetId", scope);
        request.addProperty("action", "snapshot_" + (action.equals("restore") ? "preview" : action));
        request.addProperty(action.equals("create") ? "name" : "id", value);
        JsonObject result = JsonParser.parseString(RulesPresetService.handleCommand(source, request.toString())).getAsJsonObject();
        if (action.equals("restore") && result.get("status").getAsString().equals("preview")) {
            request.addProperty("action", "snapshot_restore");
            request.add("revision", result.get("revision"));
            result = JsonParser.parseString(RulesPresetService.handleCommand(source, request.toString())).getAsJsonObject();
        }
        if (result.get("status").getAsString().equals("error")) {
            source.sendFailure(Component.translatable(result.get("messageKey").getAsString())); return 0;
        }
        JsonObject response = result;
        source.sendSuccess(() -> Component.translatable(response.get("messageKey").getAsString()), false);
        if (result.has("snapshots")) for (var entry : result.getAsJsonArray("snapshots")) {
            JsonObject row = entry.getAsJsonObject();
            source.sendSuccess(() -> Component.literal(row.get("id").getAsString() + "  " + row.get("name").getAsString()
                    + "  " + java.time.Instant.ofEpochMilli(row.get("createdAt").getAsLong())), false);
        }
        if (result.has("changedCount")) {
            source.sendSuccess(() -> Component.translatable("screen.shadowedhearts.snapshots.count", response.get("changedCount").getAsInt()), false);
            for (var entry : result.getAsJsonArray("rows")) {
                JsonObject row = entry.getAsJsonObject();
                source.sendSuccess(() -> Component.literal(row.get("path").getAsString() + ": " + row.get("current").getAsString()
                        + " -> " + row.get("proposed").getAsString()), false);
            }
        }
        return 1;
    }

    private static int execute(CommandSourceStack source, String action, String presetId) {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", 0);
        request.addProperty("action", "preview");
        request.addProperty("presetId", presetId);
        JsonObject result = JsonParser.parseString(RulesPresetService.handleCommand(source, request.toString())).getAsJsonObject();
        if (action.equals("apply") && result.get("status").getAsString().equals("preview")) {
            // A typed operator command is an explicit application of the current preset.
            request.addProperty("action", "apply");
            request.add("revision", result.get("revision"));
            result = JsonParser.parseString(RulesPresetService.handleCommand(source, request.toString())).getAsJsonObject();
        }
        if (result.get("status").getAsString().equals("error")) {
            source.sendFailure(Component.translatable(result.get("messageKey").getAsString()));
            return 0;
        }
        JsonObject response = result;
        source.sendSuccess(() -> Component.translatable(response.get("messageKey").getAsString()), action.equals("apply"));
        source.sendSuccess(() -> Component.translatable("message.shadowedhearts.rules_presets.source", response.get("source").getAsString()), false);
        if (action.equals("preview")) {
            for (var item : result.getAsJsonArray("rows")) {
                JsonObject row = item.getAsJsonObject();
                source.sendSuccess(() -> Component.literal(row.get("path").getAsString() + ": "
                        + row.get("currentValue").getAsString() + " → " + row.get("proposedValue").getAsString()), false);
            }
            RulesPresetCatalog.find(presetId).ifPresent(preset -> source.sendSuccess(() -> Component.translatable(preset.notesKey()), false));
        }
        return 1;
    }
}
