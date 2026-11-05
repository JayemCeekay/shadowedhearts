package com.jayemceekay.shadowedhearts.showdown;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowBattleRulesConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies small text patches to the runtime-extracted Pokemon Showdown JS files.
 * <p>
 * Runs after simulator extraction and other configured unbundler patches, before context startup.
 * Capture edits require a unique, complete supported source block and never rewrite an
 * already-verified patch. Dynamic data injection is handled separately by {@link DynamicInjector}.
 */
public final class ShowdownRuntimePatcher {
    private ShowdownRuntimePatcher() {
    }

    record FilePatch(LiteralShowdownPatch patch, String file) {}

    /** Shared by runtime startup and release verification against freshly extracted simulators. */
    static List<FilePatch> patches() {
        return List.of(
                new FilePatch(ShowdownBattlePatches.DEX_FIELD, "dex-conditions.js"),
                new FilePatch(ShowdownCapturePatches.CAPTURE, "battle.js"),
                new FilePatch(ShowdownBattlePatches.HEART_GAUGE_UPDATE, "battle-stream.js"),
                new FilePatch(ShowdownBattlePatches.SCENT_CALM, "battle-stream.js"),
                new FilePatch(ShowdownBattlePatches.SHADOW_ENGINE, "battle.js"),
                new FilePatch(ShowdownBattlePatches.CALL_CHOICE, "side.js"),
                new FilePatch(ShowdownBattlePatches.CALL_ORDER, "battle-queue.js"),
                new FilePatch(ShowdownBattlePatches.CALL_ACTION, "battle.js"),
                new FilePatch(ShowdownBattlePatches.HYPER_ACTION, "battle-actions.js"),
                new FilePatch(ShowdownShadowBattlePatches.COLOSSEUM_WONDER_GUARD, "battle.js"),
                new FilePatch(ShowdownShadowBattlePatches.DEDUCT_PP, "pokemon.js"),
                new FilePatch(ShowdownShadowBattlePatches.MOVE_REQUEST, "pokemon.js"),
                new FilePatch(ShowdownShadowBattlePatches.REQUEST_PP, "pokemon.js"),
                new FilePatch(ShowdownShadowBattlePatches.UPDATE_PP, "pokemon.js"),
                new FilePatch(ShowdownShadowBattlePatches.MAX_PP, "pokemon.js"),
                new FilePatch(ShowdownShadowBattlePatches.Z_PP, "battle-actions.js"),
                new FilePatch(ShowdownShadowBattlePatches.Z_REQUEST_PP, "battle-actions.js"),
                new FilePatch(ShowdownShadowBattlePatches.CRIT_TABLE, "battle-actions.js"),
                new FilePatch(ShowdownShadowBattlePatches.CRIT_ROLL, "battle-actions.js"),
                new FilePatch(ShowdownCapturePatches.POSSIBLE_SWITCHES, "battle.js"),
                new FilePatch(ShowdownCapturePatches.CAPTURED_FLAG, "pokemon.js"));
    }

    public static void applyPatches() {
        Shadowedhearts.LOGGER.info("Applying Showdown runtime patches...");
        List<Path> directories = locateShowdownDirs();
        if (directories.isEmpty()) Shadowedhearts.LOGGER.error("No extracted Showdown runtime directory found");
        for (Path showdown : directories) {
            ensureModFilesExist(showdown);
            Path sim = showdown.resolve("sim");
            applyTeamPatches(sim.resolve("teams.js"));
            for (FilePatch filePatch : patches()) {
                applyLiteralPatch(filePatch.patch(), sim.resolve(filePatch.file()));
            }
        }
    }
    public static class DynamicInjector {
        public static void prepareContext(Object context) {
            final ShowdownContextAccess access;
            final boolean mega;
            try {
                access = contextAccess(context);
                mega = Platform.isModLoaded("mega_showdown");
                // Fill missing APIs and install cache refresh without replacing another provider's APIs.
                access.eval("js", ShowdownInjectionSupport.fallbackScript());
            } catch (Exception e) {
                Shadowedhearts.LOGGER.error("Failed to prepare Showdown injection APIs", e);
                return;
            }

            injectResource(access, "receiveConditionData", "shadowyaura", "conditions/shadowyaura.js", null);
            injectResource(access, "receiveConditionData", "hypermode", "conditions/hypermode.js", null);
            injectResource(access, "receiveConditionData", "reversemode", "conditions/reversemode.js", null);
            injectResource(access, "receiveConditionData", "shadowengine", "conditions/shadowengine.js", null);
            injectResource(access, "receiveTypeChartData", "shadow", "typecharts/shadow.js", null);
            injectResource(access, "receiveMoveData", "maxshadow", "moves/maxshadow.js", null);
            injectResource(access, "receiveMoveData", "shadowsupernova", "moves/shadowsupernova.js", null);
            injectHeldItems(access);
            if (mega) {
                try {
                    executeFunction(access, "receiveNewTypeData", "Shadow", "Max Shadow", "Shadow Supernova");
                    Shadowedhearts.LOGGER.info("Mapped Shadow type to Mega Showdown gimmick moves");
                } catch (Exception e) {
                    Shadowedhearts.LOGGER.error("Failed Showdown injection receiveNewTypeData/Shadow", e);
                }
            } else {
                Shadowedhearts.LOGGER.info("Skipped Mega Showdown gimmick mapping on stock Showdown");
            }
        }

        private static void injectHeldItems(ShowdownContextAccess access) {
            injectResource(access, "receiveHeldItemData", "shadowiumz", "held_items/shadowiumz.js", null);
        }

        public static void injectConfig(Object context) {
            try {
                IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
                String config = String.format(
                        "\"Config\": { " +
                                "\"modeRules\": " + ShadowBattleRulesConfig.INSTANCE.modeRulesJson() + ", " +
                                "\"callButton\": { \"accuracyBoost\": %b, \"removeSleep\": %b }, " +
                                "\"hyperMode\": { \"enabled\": %b}, " +
                                "\"reverseMode\": { \"enabled\": %b}, " +
                                "\"GOdamageModifier\": { \"enabled\": %b, \"scaleWithHeartGauge\": %b}, " +
                                "\"shadowMoves\": { \"superEffectiveEnabled\": %b }, " +
                                "\"shadowBattleRules\": { \"shadowRushVariant\": \"%s\", \"unlimitedShadowPP\": %b, \"hyperShadowRushCrit\": %b } },",
                        cfg.callButtonAccuracyBoost(), cfg.callButtonRemoveSleep(),
                        cfg.hyperModeEnabled(), cfg.reverseModeEnabled(), cfg.goDamageModifierEnabled(),
                        cfg.goDamageModifierScaleWithHeartGauge(),
                        cfg.superEffectiveShadowMovesEnabled(),
                        ShadowBattleRulesConfig.INSTANCE.shadowRushVariant().name(),
                        ShadowBattleRulesConfig.INSTANCE.unlimitedShadowPP(),
                        ShadowBattleRulesConfig.INSTANCE.hyperShadowRushCrit());
                injectResource(contextAccess(context), "receiveScriptData", "shadowedhearts",
                        "scripts/shadowedhearts.js", config);
            } catch (Exception e) {
                Shadowedhearts.LOGGER.error("Failed to prepare Showdown config injection", e);
            }
        }

        public static void inject(Object context) {
            prepareContext(context);
            if (ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) {
                injectConfig(context);
            }
        }

        private static ShowdownContextAccess contextAccess(Object context) {
            if (context == null) throw new IllegalArgumentException("Showdown context is unavailable");
            return new ShowdownContextAccess(context);
        }

        private static void injectResource(ShowdownContextAccess access, String functionName, String id,
                                           String relativePath, String config) {
            String resourcePath = "/data/shadowedhearts/showdown/" + relativePath;
            try {
                String expression = ShowdownInjectionSupport.resourceExpression(resourcePath);
                if (config != null) {
                    int objectStart = expression.indexOf('{');
                    if (objectStart < 0) throw new IOException("Script has no configuration object: " + resourcePath);
                    expression = expression.substring(0, objectStart + 1) + config + expression.substring(objectStart + 1);
                }
                executeFunction(access, functionName, id, expression + "\n");
                executeFunction(access, "shadowedheartsRefreshData", functionName, id);
                Shadowedhearts.LOGGER.info("Injected Showdown {}/{} from {}", functionName, id, resourcePath);
            } catch (Exception e) {
                Shadowedhearts.LOGGER.error("Failed Showdown injection {}/{} from {}", functionName, id, resourcePath, e);
            }
        }

        private static void executeFunction(ShowdownContextAccess access, String name, Object... arguments)
                throws ReflectiveOperationException {
            var bindings = access.getBindings("js");
            if (!bindings.hasMember(name)) throw new NoSuchMethodException("Missing Showdown function: " + name);
            ShowdownInjectionSupport.execute(bindings.getMember(name), arguments);
        }
    }
    private static List<Path> locateShowdownDirs() {
        List<Path> result = new ArrayList<>();
        addIfDir(result, Platform.getGameFolder().resolve("showdown"));
        addIfDir(result, Paths.get("showdown"));
        return result;
    }

    private static void addIfDir(List<Path> list, Path p) {
        try {
            if (Files.isDirectory(p)) {
                Path resolved = p.toRealPath();
                if (!list.contains(resolved)) list.add(resolved);
            }
        } catch (IOException e) {
            Shadowedhearts.LOGGER.error("Failed to resolve Showdown directory: " + p, e);
        }
    }

    private static void ensureModFilesExist(Path showdownDir) {
        Path modDataDir = showdownDir.resolve("data").resolve("mods").resolve("cobblemon");
        try {
            Files.createDirectories(modDataDir);
            yoink("/assets/shadowedhearts/showdown/mods/conditions.js", modDataDir.resolve("conditions.js"));
            yoink("/assets/shadowedhearts/showdown/mods/typechart.js", modDataDir.resolve("typechart.js"));
            yoink("/assets/shadowedhearts/showdown/mods/items.js", modDataDir.resolve("items.js"));
            yoink("/assets/shadowedhearts/showdown/mods/scripts.js", modDataDir.resolve("scripts.js"));
        } catch (IOException e) {
            Shadowedhearts.LOGGER.error("Failed to ensure mod files exist in " + modDataDir, e);
        }
    }

    private static void yoink(String resourcePath, Path targetPath) {
        if (Files.exists(targetPath)) return;

        try (var in = ShowdownRuntimePatcher.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                Shadowedhearts.LOGGER.error("Resource not found: " + resourcePath);
                return;
            }
            Files.copy(in, targetPath);
            Shadowedhearts.LOGGER.info("Extracted " + resourcePath + " to " + targetPath);
        } catch (IOException e) {
            Shadowedhearts.LOGGER.error("Failed to extract " + resourcePath + " to " + targetPath, e);
        }
    }


    private static void applyLiteralPatch(LiteralShowdownPatch patch, Path path) {
        try {
            reportPatch(patch.id(), path, patch.apply(path));
        } catch (Exception e) {
            Shadowedhearts.LOGGER.error("Failed Showdown patch {} in {}", patch.id(), path.toAbsolutePath(), e);
        }
    }

    private static void applyTeamPatches(Path path) {
        try {
            var result = ShowdownTeamPatches.apply(path);
            if (result.status() == LiteralShowdownPatch.Status.APPLIED
                    || result.status() == LiteralShowdownPatch.Status.ALREADY_APPLIED) {
                Shadowedhearts.LOGGER.info("Showdown patch teams: {} ({})", result.status(), path.toAbsolutePath());
            } else {
                Shadowedhearts.LOGGER.error("Showdown patch teams: {} ({}); no changes written",
                        result.status(), path.toAbsolutePath());
                for (var step : result.steps()) {
                    var check = step.result();
                    // Earlier steps may have a valid proposed edit; none is committed if the group fails.
                    Shadowedhearts.LOGGER.error("Team patch check {}: {} (targets={}, original blocks={}, patched blocks={})",
                            step.id(), check.status(), check.targets(), check.originals(), check.replacements());
                }
            }
        } catch (Exception e) {
            Shadowedhearts.LOGGER.error("Failed Showdown patch teams in {}", path.toAbsolutePath(), e);
        }
    }

    private static void reportPatch(String id, Path path, LiteralShowdownPatch.Result result) {
        if (result.status() == LiteralShowdownPatch.Status.APPLIED
                || result.status() == LiteralShowdownPatch.Status.ALREADY_APPLIED) {
            Shadowedhearts.LOGGER.info("Showdown patch {}: {} ({})", id, result.status(), path.toAbsolutePath());
        } else {
            Shadowedhearts.LOGGER.error("Showdown patch {}: {} ({}; targets={}, original blocks={}, patched blocks={})",
                    id, result.status(), path.toAbsolutePath(),
                    result.targets(), result.originals(), result.replacements());
        }
    }
}
