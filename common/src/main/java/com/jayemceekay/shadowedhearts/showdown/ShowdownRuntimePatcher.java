package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.relocations.graalvm.polyglot.Context;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies small text patches to the runtime-extracted Pokemon Showdown JS files.
 * <p>
 * Goals:
 * - Insert a custom "shadow" type into data/typechart.js.
 * - Extend sim/battle.js capture(pokemon) to emit a replacement request after capture.
 * <p>
 * This runs at mod init and targets common dev/prod locations. The patch is idempotent.
 */
public final class ShowdownRuntimePatcher {
    private ShowdownRuntimePatcher() {
    }

    public static void applyPatches() {
        Shadowedhearts.LOGGER.info("Applying Showdown runtime patches...");
        for (Path showdown : locateShowdownDirs()) {
            ensureModFilesExist(showdown);
            try {
                patchDexConditions(showdown.resolve("sim").resolve("dex-conditions.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch dex-conditions.js in Showdown directory: " + showdown);
            }
            try {
                patchBattleCapture(showdown.resolve("sim").resolve("battle.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch battle.js in Showdown directory: " + showdown);
            }
            try {
                patchTeams(showdown.resolve("sim").resolve("teams.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch teams.js in Showdown directory: " + showdown);
            }
            try {
                patchBattleAddShadowEngine(showdown.resolve("sim").resolve("battle.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch battle.js in Showdown directory: " + showdown);
            }
            try {
                patchFieldAddPseudoWeatherDebug(showdown.resolve("sim").resolve("field.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch field.js in Showdown directory: " + showdown);
            }
            try {
                patchSideAddCallChoice(showdown.resolve("sim").resolve("side.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch side.js in Showdown directory: " + showdown);
            }
            try {
                patchBattleQueueAddCallOrder(showdown.resolve("sim").resolve("battle-queue.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch battle-queue.js in Showdown directory: " + showdown);
            }
            try {
                patchBattleAddCallAction(showdown.resolve("sim").resolve("battle.js"));
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to patch battle.js in Showdown directory: " + showdown);
            }
        }
    }

    public static class DynamicInjector {
        public static void prepareContext(Context context) {
            try {
                if (context == null) {
                    Shadowedhearts.LOGGER.info("Failed to access Showdown context object, skipping preparation.");
                    return;
                }

                var bindings = context.getBindings("js");

                if (!Platform.isModLoaded("mega_showdown")) {
                    context.eval("js", "if (typeof items === 'undefined') items = require('./data/mods/cobblemon/items');\n" +
                            "if (typeof conditions === 'undefined') conditions = require('./data/mods/cobblemon/conditions');\n" +
                            "if (typeof typechart === 'undefined') typechart = require('./data/mods/cobblemon/typechart');\n" +
                            "if (typeof scripts === 'undefined') scripts = require('./data/mods/cobblemon/scripts');\n" +
                            "\n" +
                            "if (typeof receiveConditionData === 'undefined') {\n" +
                            "    receiveConditionData = function(conditionId, conditionData) {\n" +
                            "        const target = conditions.Conditions || conditions;\n" +
                            "        target[conditionId] = eval(`(${conditionData})`);\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (typeof receiveTypeChartData === 'undefined') {\n" +
                            "    receiveTypeChartData = function(typeChartId, typeChartData) {\n" +
                            "        const target = typechart.TypeChart || typechart;\n" +
                            "        target[typeChartId] = eval(`(${typeChartData})`);\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (typeof receiveScriptData === 'undefined') {\n" +
                            "    receiveScriptData = function(scriptId, scriptData) {\n" +
                            "        const newFunctions = eval(`(${scriptData})`);\n" +
                            "        const target = scripts.Scripts || scripts;\n" +
                            "        if (!target[scriptId]) target[scriptId] = {};\n" +
                            "        Object.assign(target[scriptId], newFunctions);\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (typeof receiveHeldItemData === 'undefined') {\n" +
                            "    receiveHeldItemData = function(itemId, itemData) {\n" +
                            "        const target = items.Items || items;\n" +
                            "        target[itemId] = eval(`(${itemData})`);\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (typeof receiveMoveData === 'undefined') {\n" +
                            "    receiveMoveData = function(moveId, moveData) {\n" +
                            "        const target = moves.Moves || moves;\n" +
                            "        target[moveId] = eval(`(${moveData})`);\n" +
                            "    };\n" +
                            "}\n" +
                            "\n" +
                            "if (typeof receiveNewTypeData === 'undefined') {\n" +
                            "    receiveNewTypeData = function(type, max, z) {\n" +
                            "        battleActions.MAX_MOVES[type] = max;\n" +
                            "        battleActions.Z_MOVES[type] = z;\n" +
                            "    };\n" +
                            "}");
                }

                if (bindings.hasMember("receiveConditionData")) {
                    Object receiveConditionDataFn = bindings.getMember("receiveConditionData");
                    injectCondition(receiveConditionDataFn, "shadowyaura", "/data/shadowedhearts/showdown/conditions/shadowyaura.js");
                    injectCondition(receiveConditionDataFn, "hypermode", "/data/shadowedhearts/showdown/conditions/hypermode.js");
                    injectCondition(receiveConditionDataFn, "reversemode", "/data/shadowedhearts/showdown/conditions/reversemode.js");
                    injectCondition(receiveConditionDataFn, "shadowengine", "/data/shadowedhearts/showdown/conditions/shadowengine.js");
                    Shadowedhearts.LOGGER.info("Injected custom conditions into Showdown...");
                } else {
                    Shadowedhearts.LOGGER.info("Failed to find receiveConditionData function, skipping injection.");
                }

                if (bindings.hasMember("receiveTypeChartData")) {
                    Object receiveTypeChartDataFn = bindings.getMember("receiveTypeChartData");
                    injectTypeChart(receiveTypeChartDataFn, "shadow", "/data/shadowedhearts/showdown/typecharts/shadow.js");
                    Shadowedhearts.LOGGER.info("Injected custom typechart into Showdown...");
                } else {
                    Shadowedhearts.LOGGER.info("Failed to find receiveTypeChartData function, skipping injection.");
                }

                if (bindings.hasMember("receiveMoveData")) {
                    Object receiveMoveDataFn = bindings.getMember("receiveMoveData");
                    injectMove(receiveMoveDataFn, "maxshadow", "/data/shadowedhearts/showdown/moves/maxshadow.js");
                    injectMove(receiveMoveDataFn, "shadowysupernova", "/data/shadowedhearts/showdown/moves/shadowysupernova.js");
                    Shadowedhearts.LOGGER.info("Injected custom gimmick moves into Showdown...");
                }

                if (bindings.hasMember("receiveHeldItemData")) {
                    Object receiveHeldItemDataFn = bindings.getMember("receiveHeldItemData");
                    injectHeldItem(receiveHeldItemDataFn, "shadowiumz", "/data/shadowedhearts/showdown/held_items/shadowiumz.js");
                    Shadowedhearts.LOGGER.info("Injected custom items into Showdown...");
                }

                if (bindings.hasMember("receiveNewTypeData")) {
                    Object receiveNewTypeDataFn = bindings.getMember("receiveNewTypeData");
                    executeFn(receiveNewTypeDataFn, "Shadow", "Max Shadow", "Shadowy Supernova");
                    Shadowedhearts.LOGGER.info("Mapped Shadow type to custom gimmick moves...");
                }
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to prepare Showdown context object: " + e);
                e.printStackTrace();
            }
        }

        public static void injectConfig(Context context) {
            try {
                if (context == null) {
                    Shadowedhearts.LOGGER.info("Failed to access Showdown context object, skipping config injection.");
                    return;
                }

                var bindings = context.getBindings("js");

                if (bindings.hasMember("receiveScriptData")) {
                    Object receiveScriptDataFn = bindings.getMember("receiveScriptData");
                    injectScript(receiveScriptDataFn, "shadowedhearts", "/data/shadowedhearts/showdown/scripts/shadowedhearts.js");
                    Shadowedhearts.LOGGER.info("Injected custom scripts and config into Showdown.");
                } else {
                    Shadowedhearts.LOGGER.info("Failed to find receiveScriptData function, skipping config injection.");
                }

                if (bindings.hasMember("receiveHeldItemData")) {
                    Object receiveHeldItemDataFn = bindings.getMember("receiveHeldItemData");
                    // If we had any custom items to inject, we'd do it here.
                    // injectHeldItem(receiveHeldItemDataFn, "itemid", "/path/to/item.js");
                }

            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to inject config into Showdown context object: " + e);
                e.printStackTrace();
            }
        }

        public static void inject(Context context) {
            prepareContext(context);
            if (ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) {
                injectConfig(context);
            }
        }

        private static void injectScript(Object fn, String id, String resourcePath) {
            String js = readResourceText(resourcePath);
            if (js != null) {
                js = js.replaceAll("//.*", "");
                js = extractExportedTemplate(js);

                if (id.equals("shadowedhearts")) {
                    IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
                    String configJs = String.format(
                            "\"Config\": { " +
                                    "\"callButton\": { \"accuracyBoost\": %b, \"removeSleep\": %b }, " +
                                    "\"hyperMode\": { \"enabled\": %b}, " +
                                    "\"reverseMode\": { \"enabled\": %b}, " +
                                    "\"GOdamageModifier\": { \"enabled\": %b}, " +
                                    "\"shadowMoves\": { \"superEffectiveEnabled\": %b } " +
                                    "},",
                            cfg.callButtonAccuracyBoost(),
                            cfg.callButtonRemoveSleep(),
                            cfg.hyperModeEnabled(),
                            cfg.reverseModeEnabled(),
                            cfg.goDamageModifierEnabled(),
                            cfg.superEffectiveShadowMovesEnabled()
                    );
                    js = js.replaceFirst("\\{", "{" + configJs);
                }

                js = js.replace("\n", " ").replace("\r", "");
                executeFn(fn, id, js);
            } else {
                Shadowedhearts.LOGGER.info("Failed to find script resource: " + resourcePath);
            }
        }

        private static void injectCondition(Object fn, String id, String resourcePath) {
            String js = readResourceText(resourcePath);
            if (js != null) {
                js = js.replaceAll("//.*", "");
                js = extractExportedTemplate(js);
                js = js.replace("\n", " ").replace("\r", "");
                executeFn(fn, id, js);
            } else {
                Shadowedhearts.LOGGER.info("Failed to find condition resource: " + resourcePath);
            }
        }

        private static void injectTypeChart(Object fn, String id, String resourcePath) {
            String js = readResourceText(resourcePath);
            if (js != null) {
                js = js.replaceAll("//.*", "");
                js = js.replace("\n", " ").replace("\r", "");
                executeFn(fn, id, js);
            } else {
                Shadowedhearts.LOGGER.info("Failed to find typechart resource: " + resourcePath);
            }
        }

        private static void injectMove(Object fn, String id, String resourcePath) {
            String js = readResourceText(resourcePath);
            if (js != null) {
                js = js.replaceAll("//.*", "");
                js = js.replace("\n", " ").replace("\r", "");
                executeFn(fn, id, js);
            } else {
                Shadowedhearts.LOGGER.info("Failed to find move resource: " + resourcePath);
            }
        }

        private static void injectHeldItem(Object fn, String id, String resourcePath) {
            String js = readResourceText(resourcePath);
            if (js != null) {
                js = js.replaceAll("//.*", "");
                js = js.replace("\n", " ").replace("\r", "");
                executeFn(fn, id, js);
            } else {
                Shadowedhearts.LOGGER.info("Failed to find held item resource: " + resourcePath);
            }
        }

        private static void executeFn(Object fn, Object... args) {
            try {
                var executeMethod = fn.getClass().getMethod("execute", Object[].class);
                executeMethod.invoke(fn, (Object) args);
            } catch (Exception e) {
                Shadowedhearts.LOGGER.info("Failed to execute Showdown injection function for args: " + java.util.Arrays.toString(args));
            }
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
            if (Files.isDirectory(p)) list.add(p.toRealPath());
        } catch (IOException ignored) {
            Shadowedhearts.LOGGER.info("Failed to resolve directory: " + p);
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


    /**
     * Ensure DexConditions preserves the 'Field' effectType for pseudo-weather conditions.
     * By default, upstream code only allows ["Weather", "Status"]. We add "Field" so
     * our injected shadowengine condition retains effectType: 'Field'.
     */
    private static void patchDexConditions(Path dexConditionsPath) throws IOException {
        if (!Files.isRegularFile(dexConditionsPath)) return;
        String content = Files.readString(dexConditionsPath, StandardCharsets.UTF_8);

        // Guard: if 'Field' is already in the whitelist, skip.
        // Look for the constructor assignment line pattern in compiled JS.
        String whitelistPattern = "[\"Weather\", \"Status\"]";
        String desired = "[\"Weather\", \"Status\", \"Field\"]";

        if (content.contains("\"Field\"")) {
           // Shadowedhearts.LOGGER.info("Field already in dex-conditions.js whitelist, skipping patch");
            return;
        }

        String needle = "this.effectType = [\"Weather\", \"Status\"].includes(data.effectType) ? data.effectType : \"Condition\";";
        String replacement = "this.effectType = [\"Weather\", \"Status\", \"Field\"].includes(data.effectType) ? data.effectType : \"Condition\";";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(dexConditionsPath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Patched dex-conditions.js with Field effectType whitelist");
            return;
        }

        // Fallback: perform a broader replacement on the whitelist array if constructed slightly differently
        if (content.contains(whitelistPattern)) {
            String patched = content.replace(whitelistPattern, desired);
            Files.writeString(dexConditionsPath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Patched dex-conditions.js with Field effectType whitelist");
            return;
        }
    }


    private static String readResourceText(String resourcePath) {
        try {
            var in = ShowdownRuntimePatcher.class.getResourceAsStream(resourcePath);
            if (in == null)
                throw new IOException("Resource not found: " + resourcePath);
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Shadowedhearts.LOGGER.info("Failed to read resource: " + resourcePath, e);
            return null;
        }
    }

    /**
     * Allows JS snippet files to export a template string (module.exports = `...`).
     * If the provided content appears to be a Node-style module with backticks,
     * this extracts the inner template; otherwise returns the original string.
     */
    private static String extractExportedTemplate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int firstTick = s.indexOf('`');
        int lastTick = s.lastIndexOf('`');
        if ((s.startsWith("module.exports") || s.contains("module.exports")) && firstTick >= 0 && lastTick > firstTick) {
            return s.substring(firstTick + 1, lastTick);
        }
        return raw;
    }


    private static void patchBattleCapture(Path battlePath) throws IOException {
        if (!Files.isRegularFile(battlePath)) return;
        String content = Files.readString(battlePath, StandardCharsets.UTF_8);

        if (content.contains("pokemon.side.emitRequest(req)") || content.contains("// Build and emit a new request")) {
            Shadowedhearts.LOGGER.info("Battle already patched for request emission, skipping patch");
            return;
        }

        final String needle =
                "      if (this.checkWin())\n" +
                        "        return true;\n" +
                        "    }\n" +
                        "    return false;\n" +
                        "  }";

        final String replacement =
                "      if (this.checkWin())\n" +
                        "        return true;\n" +
                        "      this.checkFainted();\n\n" +
                        "      // Build and emit a new request so the victim side can choose a replacement\n" +
                        "      const activeData = pokemon.side.active.map(pk => pk?.getMoveRequestData());\n" +
                        "      const req = { active: activeData, side: pokemon.side.getRequestData() };\n" +
                        "      if (pokemon.side.allySide) {\n" +
                        "          req.ally = pokemon.side.allySide.getRequestData(true);\n" +
                        "      }\n" +
                        "      pokemon.side.emitRequest(req);\n" +
                        "      pokemon.side.clearChoice();\n" +
                        "    }\n" +
                        "    return false;\n" +
                        "  }";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(battlePath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Patched battle.js to emit capture request");
        }
    }

    private static void patchTeams(Path teamsPath) throws IOException {
        if (!Files.isRegularFile(teamsPath)) return;
        String content = Files.readString(teamsPath, StandardCharsets.UTF_8);

        // Idempotence/upgrade:
        // - If the file already has our newer flags (isHyper/isReverse), skip.
        // - If it only has the older heartGaugeBars patch, proceed to upgrade to add two more fields.
        boolean hasNewFlags = content.contains("set.isHyper") || content.contains("set.isReverse") || content.contains("misc[9]");
        if (hasNewFlags) {
            Shadowedhearts.LOGGER.info("Teams already patched for new flags, skipping patch");
            return;
        }

        int packStart = content.indexOf("  pack(team) {");
        if (packStart < 0) {
            Shadowedhearts.LOGGER.info("Teams already patched for pack function, skipping patch");
            return;
        }
        int unpackStart = content.indexOf("\n  unpack(buf) {", packStart);
        if (unpackStart < 0) {
            Shadowedhearts.LOGGER.info("Teams already patched for unpack function, skipping patch");
            return;
        }
        int afterUnpack = content.indexOf("\n  /**", unpackStart);
        if (afterUnpack < 0) {
            afterUnpack = content.indexOf("\n  packName(", unpackStart);
        }
        if (afterUnpack < 0) {
            afterUnpack = content.indexOf("\n  static packName(", unpackStart);
        }
        if (afterUnpack < 0) {
            return;
        }

        final String newBlock =
                "  pack(team) {\n" +
                        "    if (!team)\n" +
                        "      return \"\";\n" +
                        "    function getIv(ivs, s) {\n" +
                        "      return ivs[s] === 31 || ivs[s] === void 0 ? \"\" : ivs[s].toString();\n" +
                        "    }\n" +
                        "    let buf = \"\";\n" +
                        "    for (const set of team) {\n" +
                        "      if (buf)\n" +
                        "        buf += \"]\";\n" +
                        "      buf += set.name || set.species;\n" +
                        "      const id = this.packName(set.species || set.name);\n" +
                        "      buf += \"|\" + (this.packName(set.name || set.species) === id ? \"\" : id);\n" +
                        "      buf += \"|\" + this.packName(set.item);\n" +
                        "      buf += \"|\" + this.packName(set.ability);\n" +
                        "      buf += \"|\" + set.moves.map(this.packName).join(\",\");\n" +
                        "      buf += \"|\" + (set.nature || \"\");\n" +
                        "      let evs = \"|\";\n" +
                        "      if (set.evs) {\n" +
                        "        evs = \"|\" + (set.evs[\"hp\"] || \"\") + \",\" + (set.evs[\"atk\"] || \"\") + \",\" + (set.evs[\"def\"] || \"\") + \",\" + (set.evs[\"spa\"] || \"\") + \",\" + (set.evs[\"spd\"] || \"\") + \",\" + (set.evs[\"spe\"] || \"\");\n" +
                        "      }\n" +
                        "      if (evs === \"|,,,,,\") {\n" +
                        "        buf += \"|\";\n" +
                        "      } else {\n" +
                        "        buf += evs;\n" +
                        "      }\n" +
                        "      if (set.gender) {\n" +
                        "        buf += \"|\" + set.gender;\n" +
                        "      } else {\n" +
                        "        buf += \"|\";\n" +
                        "      }\n" +
                        "      let ivs = \"|\";\n" +
                        "      if (set.ivs) {\n" +
                        "        ivs = \"|\" + getIv(set.ivs, \"hp\") + \",\" + getIv(set.ivs, \"atk\") + \",\" + getIv(set.ivs, \"def\") + \",\" + getIv(set.ivs, \"spa\") + \",\" + getIv(set.ivs, \"spd\") + \",\" + getIv(set.ivs, \"spe\");\n" +
                        "      }\n" +
                        "      if (ivs === \"|,,,,,\") {\n" +
                        "        buf += \"|\";\n" +
                        "      } else {\n" +
                        "        buf += ivs;\n" +
                        "      }\n" +
                        "      if (set.shiny) {\n" +
                        "        buf += \"|S\";\n" +
                        "      } else {\n" +
                        "        buf += \"|\";\n" +
                        "      }\n" +
                        "      if (set.level && set.level !== 100) {\n" +
                        "        buf += \"|\" + set.level;\n" +
                        "      } else {\n" +
                        "        buf += \"|\";\n" +
                        "      }\n" +
                        "      if (set.happiness !== void 0 && set.happiness !== 255) {\n" +
                        "        buf += \"|\" + set.happiness;\n" +
                        "      } else {\n" +
                        "        buf += \"|\";\n" +
                        "      }\n" +
                        "      if (set.pokeball || set.hpType || set.gigantamax || set.dynamaxLevel !== void 0 && set.dynamaxLevel !== 10 || set.teraType || set.isShadow || set.heartGaugeBars !== void 0 || set.isHyper || set.isReverse) {\n" +
                        "        buf += \",\" + this.packName(set.pokeball || \"\");\n" +
                        "        buf += \",\" + (set.hpType || \"\");\n" +
                        "        buf += \",\" + (set.gigantamax ? \"G\" : \"\");\n" +
                        "        buf += \",\" + (set.dynamaxLevel !== void 0 && set.dynamaxLevel !== 10 ? set.dynamaxLevel : \"\");\n" +
                        "        buf += \",\" + (set.teraType || \"\");\n" +
                        "        buf += \",\" + (set.isShadow ? \"true\" : \"false\");\n" +
                        "        buf += \",\" + (set.heartGaugeBars !== void 0 ? set.heartGaugeBars : \"\");\n" +
                        "        buf += \",\" + (set.isHyper ? 'true' : 'false');\n" +
                        "        buf += \",\" + (set.isReverse ? 'true' : 'false');\n" +
                        "      }\n" +
                        "    }\n" +
                        "    return buf;\n" +
                        "  }\n" +
                        "  unpack(buf) {\n" +
                        "    if (!buf)\n" +
                        "      return null;\n" +
                        "    if (typeof buf !== \"string\")\n" +
                        "      return buf;\n" +
                        "    if (buf.startsWith(\"[\") && buf.endsWith(\"]\")) {\n" +
                        "      try {\n" +
                        "        buf = this.pack(JSON.parse(buf));\n" +
                        "      } catch {\n" +
                        "        return null;\n" +
                        "      }\n" +
                        "    }\n" +
                        "    const team = [];\n" +
                        "    let i = 0;\n" +
                        "    let j = 0;\n" +
                        "    for (let count = 0; count < 24; count++) {\n" +
                        "      const set = {};\n" +
                        "      team.push(set);\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.name = buf.substring(i, j);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.species = this.unpackName(buf.substring(i, j), import_dex.Dex.species) || set.name;\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.uuid = buf.substring(i, j);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.currentHealth = parseInt(buf.substring(i, j));\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.status = buf.substring(i, j);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.statusDuration = parseInt(buf.substring(i, j));\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.item = this.unpackName(buf.substring(i, j), import_dex.Dex.items);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      const ability = buf.substring(i, j);\n" +
                        "      set.ability = this.unpackName(ability, import_dex.Dex.abilities);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.moves = buf.substring(i, j).split(\",\", 24).map((name) => this.unpackName(name, import_dex.Dex.moves));\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.movesInfo = buf.substring(i, j).split(\",\", 24).map((moveData) => {\n" +
                        "        const moveInfo = {};\n" +
                        "        let data = moveData.split(\"/\");\n" +
                        "        moveInfo.pp = parseInt(data[0]);\n" +
                        "        moveInfo.maxPp = parseInt(data[1]);\n" +
                        "        return moveInfo;\n" +
                        "      });\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      set.nature = this.unpackName(buf.substring(i, j), import_dex.Dex.natures);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      if (j !== i) {\n" +
                        "        const evs = buf.substring(i, j).split(\",\", 6);\n" +
                        "        set.evs = {\n" +
                        "          hp: Number(evs[0]) || 0,\n" +
                        "          atk: Number(evs[1]) || 0,\n" +
                        "          def: Number(evs[2]) || 0,\n" +
                        "          spa: Number(evs[3]) || 0,\n" +
                        "          spd: Number(evs[4]) || 0,\n" +
                        "          spe: Number(evs[5]) || 0\n" +
                        "        };\n" +
                        "      }\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      if (i !== j)\n" +
                        "        set.gender = buf.substring(i, j);\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      if (j !== i) {\n" +
                        "        const ivs = buf.substring(i, j).split(\",\", 6);\n" +
                        "        set.ivs = {\n" +
                        "          hp: ivs[0] === \"\" ? 31 : Number(ivs[0]) || 0,\n" +
                        "          atk: ivs[1] === \"\" ? 31 : Number(ivs[1]) || 0,\n" +
                        "          def: ivs[2] === \"\" ? 31 : Number(ivs[2]) || 0,\n" +
                        "          spa: ivs[3] === \"\" ? 31 : Number(ivs[3]) || 0,\n" +
                        "          spd: ivs[4] === \"\" ? 31 : Number(ivs[4]) || 0,\n" +
                        "          spe: ivs[5] === \"\" ? 31 : Number(ivs[5]) || 0\n" +
                        "        };\n" +
                        "      }\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      if (i !== j)\n" +
                        "        set.shiny = true;\n" +
                        "      i = j + 1;\n" +
                        "      j = buf.indexOf(\"|\", i);\n" +
                        "      if (j < 0)\n" +
                        "        return null;\n" +
                        "      if (i !== j)\n" +
                        "        set.level = parseInt(buf.substring(i, j));\n" +
                        "      i = j + 1;\n" +
                        "        // happiness + extended misc\n" +
                        "        j = buf.indexOf(']', i);\n" +
                        "        let misc;\n" +
                        "        if (j < 0) {\n" +
                        "            if (i < buf.length) misc = buf.substring(i).split(',', 10);\n" +
                        "        } else {\n" +
                        "            if (i !== j) misc = buf.substring(i, j).split(',', 10);\n" +
                        "        }\n" +
                        "        if (misc) {\n" +
                        "            set.happiness = (misc[0] ? Number(misc[0]) : 255);\n" +
                        "            set.pokeball = this.unpackName(misc[1] || '', import_dex.Dex.items);\n" +
                        "            set.hpType = misc[2] || '';\n" +
                        "            set.gigantamax = !!misc[3];\n" +
                        "            set.dynamaxLevel = (misc[4] ? Number(misc[4]) : 10);\n" +
                        "            set.teraType = misc[5];\n" +
                        "\n" +
                        "            // 7th token: Shadow flag\n" +
                        "            const shadowTok = misc[6];\n" +
                        "            set.isShadow = shadowTok === 'true';\n" +
                        "            // 8th token: Heart Gauge bars (0..5)\n" +
                        "            if (misc.length > 7 && misc[7] !== undefined && misc[7] !== '') {\n" +
                        "                set.heartGaugeBars = Number(misc[7]);\n" +
                        "            }\n" +
                        "            // 9th token: Start in Hyper Mode flag\n" +
                        "            if (misc.length > 8) {\n" +
                        "                set.isHyper = misc[8] === 'true';\n" +
                        "            }\n" +
                        "            // 10th token: Start in Reverse Mode flag\n" +
                        "            if (misc.length > 9) {\n" +
                        "                set.isReverse = misc[9] === 'true';\n" +
                        "            }\n" +
                        "        }\n" +
                        "      if (j < 0)\n" +
                        "        break;\n" +
                        "      i = j + 1;\n" +
                        "    }\n" +
                        "    return team;\n" +
                        "  }\n";

        String patched = content.substring(0, packStart) + newBlock + content.substring(afterUnpack);
        Files.writeString(teamsPath, patched, StandardCharsets.UTF_8);
        Shadowedhearts.LOGGER.info("Patched pack() in " + teamsPath.toAbsolutePath());
    }

    private static void patchCustomFormats(Path customFormatsPath) throws IOException {
        // Ensure the file exists; if not, scaffold a minimal formats file
        if (!Files.isRegularFile(customFormatsPath)) {
            try {
                Files.createDirectories(customFormatsPath.getParent());
            } catch (IOException ignored) {
            }
            String scaffold = "exports.Formats = [\n];\n";
            Files.writeString(customFormatsPath, scaffold, StandardCharsets.UTF_8);
        }

        String content = Files.readString(customFormatsPath, StandardCharsets.UTF_8);

        // Idempotence check
        if (content.contains("name: \"[Gen 9] Micro\"") || content.contains("name: '[Gen 9] Micro'")) {
            return;
        }

        final String microBlock =
                "\n" +
                        ",{\n" +
                        "    section: \"Shadowed Hearts\",\n" +
                        "},\n" +
                        "{\n" +
                        "    name: \"[Gen 9] Micro\",\n" +
                        "    desc: \"Internal one-action micro battle for overworld interactions; not for human play.\",\n" +
                        "    mod: 'micro',\n" +
                        "    gameType: 'singles',\n" +
                        "    searchShow: false,\n" +
                        "    challengeShow: false,\n" +
                        "    tournamentShow: false,\n" +
                        "    rated: false,\n" +
                        "    ruleset: [\n" +
                        "        'Obtainable', 'Species Clause', 'HP Percentage Mod', 'Cancel Mod', 'Illusion Level Mod', 'Endless Battle Clause',\n" +
                        "        'Picked Team Size = 1', 'Max Team Size = 1', 'Min Team Size = 1',\n" +
                        "    ],\n" +
                        "    banlist: [\n" +
                        "        // Hazards and delayed-turn moves\n" +
                        "        'Stealth Rock', 'Spikes', 'Toxic Spikes', 'Sticky Web',\n" +
                        "        'Future Sight', 'Doom Desire', 'Perish Song',\n" +
                        "        // Pivots and passers (avoid switch flow)\n" +
                        "        'Baton Pass', 'Parting Shot',\n" +
                        "        // Two-turn/charge/invuln moves\n" +
                        "        'Sky Drop', 'Dive', 'Dig', 'Bounce', 'Fly', 'Phantom Force', 'Shadow Force', 'Solar Beam', 'Solar Blade', 'Skull Bash', 'Freeze Shock', 'Ice Burn', 'Razor Wind', 'Geomancy',\n" +
                        "    ],\n" +
                        "    onBegin() {\n" +
                        "        // State should be injected by the micro-battle runner; nothing to do here.\n" +
                        "    },\n" +
                        "    onResidual() {\n" +
                        "        // Prevent format-level residual effects; core statuses/weather may still apply if present.\n" +
                        "    },\n" +
                        "},\n";

        int endIndex = content.lastIndexOf("];");
        String patched;
        if (endIndex >= 0) {
            patched = content.substring(0, endIndex) + microBlock + content.substring(endIndex);
        } else {
            // No obvious array terminator; reconstruct a minimal formats file
            patched = "exports.Formats = [\n" + microBlock + "];\n";
        }

        Files.writeString(customFormatsPath, patched, StandardCharsets.UTF_8);
        Shadowedhearts.LOGGER.info("Custom formats patch applied successfully");
    }

    private static void patchMicroScripts(Path scriptsPath) throws IOException {
        // Ensure the directory exists and create or update the micro mod scripts
        String marker1 = "tiebreak()";
        String marker2 = "addSideCondition(";
        if (Files.isRegularFile(scriptsPath)) {
            String existing = Files.readString(scriptsPath, StandardCharsets.UTF_8);
            if (existing.contains("exports.Scripts") && existing.contains(marker1) && existing.contains(marker2)) {
                return;
            }
        } else {
            try {
                Files.createDirectories(scriptsPath.getParent());
            } catch (IOException ignored) {
            }
        }

        String js = "exports.Scripts = {\n" +
                "\tgen: 9,\n" +
                "\tinherit: 'gen9',\n\n" +
                "\t// Keep battles as quiet/minimal as possible; logs are handled by the runner.\n" +
                "\tbattle: {\n" +
                "\t\t// Suppress tiebreaks and other special-casing – micro battles should never reach them\n" +
                "\t\ttiebreak() {\n" +
                "\t\t\t// no-op\n" +
                "\t\t},\n" +
                "\t},\n\n" +
                "\tside: {\n" +
                "\t\t// Block adding common hazard/side condition effects; return false to indicate failure\n" +
                "\t\taddSideCondition(status, source = null, sourceEffect = null) {\n" +
                "\t\t\t// Fallback to parent implementation for non-hazard conditions\n" +
                "\t\t\tconst hazards = [\n" +
                "\t\t\t\t'stealthrock', 'spikes', 'toxicspikes', 'stickyweb',\n" +
                "\t\t\t\t// G-Max residual hazards\n" +
                "\t\t\t\t'gmaxsteelsurge', 'gmaxcannonade', 'gmaxvinelash', 'gmaxvolcalith', 'gmaxwildfire',\n" +
                "\t\t\t];\n" +
                "\t\t\tconst id = this.battle.toID((status && status.id) || status);\n" +
                "\t\t\tif (hazards.includes(id)) return false;\n" +
                "\t\t\treturn this.__proto__.addSideCondition.call(this, status, source, sourceEffect);\n" +
                "\t\t},\n" +
                "\t},\n\n" +
                "\t// Disable global residual tick if possible by preventing the format residual from doing anything.\n" +
                "\t// Many residual effects (weather/status) still occur via their own hooks; for micro, the\n" +
                "\t// controlling format will opt not to include residual turns.\n" +
                "};\n";

        Files.writeString(scriptsPath, js, StandardCharsets.UTF_8);
        Shadowedhearts.LOGGER.info("Field pseudo weather patch applied successfully");
    }

    private static void patchBattleAddShadowEngine(Path battlePath) throws IOException {
        if (!Files.isRegularFile(battlePath)) return;
        String content = Files.readString(battlePath, StandardCharsets.UTF_8);
        if (content.contains("addPseudoWeather('shadowengine')") || content.contains("addPseudoWeather(\"shadowengine\")")) {
            Shadowedhearts.LOGGER.info("Battle already patched for shadowengine pseudo weather, skipping patch");
            return;
        }
        String anchor = "this.add(\"gametype\", this.gameType);";
        int idx = content.indexOf(anchor);
        if (idx < 0) {
            Shadowedhearts.LOGGER.info("Battle patch failed to find anchor, skipping patch");
            return;
        }
        int insertPos = idx + anchor.length();
        String injected = "\n    this.field.addPseudoWeather('shadowengine');";
        String patched = content.substring(0, insertPos) + injected + content.substring(insertPos);
        Files.writeString(battlePath, patched, StandardCharsets.UTF_8);
        Shadowedhearts.LOGGER.info("Battle patched for shadowengine pseudo weather");
    }

    /**
     * Adds extra lines in sim/field.js addPseudoWeather for 'shadowengine'.
     * <p>
     * The patch is idempotent and only touches the specific addPseudoWeather return block.
     */
    private static void patchFieldAddPseudoWeatherDebug(Path fieldPath) throws IOException {
        if (!Files.isRegularFile(fieldPath)) return;
        String content = Files.readString(fieldPath, StandardCharsets.UTF_8);

        // Skip if our modification is already present
        if (content.contains("status.id === 'shadowengine'")) {
            Shadowedhearts.LOGGER.info("Field already patched for shadowengine pseudo weather, skipping patch");
            return;
        }

        // Target the compiled JS for addPseudoWeather() block shown in PS sim/field.js
        String needle =
                "if (!this.battle.singleEvent(\"FieldStart\", status, state, this, source, sourceEffect)) {\n" +
                        "      delete this.pseudoWeather[status.id];\n" +
                        "      return false;\n" +
                        "    }\n" +
                        "    this.battle.runEvent(\"PseudoWeatherChange\", source, source, status);\n" +
                        "    return true;";

        String replacement =
                "if (!this.battle.singleEvent(\"FieldStart\", status, state, this, source, sourceEffect)) {\n" +
                        "      delete this.pseudoWeather[status.id];\n" +
                        "      return false;\n" +
                        "    }\n" +
                        "    this.battle.runEvent(\"PseudoWeatherChange\", source, source, status);\n" +
                        "    return true;";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(fieldPath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Field pseudo weather patch applied successfully");
        }
    }

    private static void patchSideAddCallChoice(Path sidePath) throws IOException {
        if (!Files.isRegularFile(sidePath)) return;
        String content = Files.readString(sidePath, StandardCharsets.UTF_8);

        if (content.contains("case \"call\":")) {
            Shadowedhearts.LOGGER.info("Side already patched for call choice, skipping patch");
            return;
        }

        String needle = "switch (choiceType) {";
        String replacement = "switch (choiceType) {\n" +
                "        case \"call\":\n" +
                "          const index = this.getChoiceIndex();\n" +
                "          if (index >= this.active.length) return this.emitChoiceError(\"Can't call: All Pokemon have already acted\");\n" +
                "          this.choice.actions.push({choice: \"call\", pokemon: this.active[index]});\n" +
                "          break;";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(sidePath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Side call choice patch applied successfully");
        }
    }

    private static void patchBattleQueueAddCallOrder(Path queuePath) throws IOException {
        if (!Files.isRegularFile(queuePath)) return;
        String content = Files.readString(queuePath, StandardCharsets.UTF_8);

        if (content.contains("call: 200")) {
            Shadowedhearts.LOGGER.info("Battle queue already patched for call order, skipping patch");
            return;
        }

        String needle = "residual: 300";
        String replacement = "residual: 300,\n        call: 200";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(queuePath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Battle queue call order patch applied successfully");
        }
    }

    private static void patchBattleAddCallAction(Path battlePath) throws IOException {
        if (!Files.isRegularFile(battlePath)) return;
        String content = Files.readString(battlePath, StandardCharsets.UTF_8);

        if (content.contains("case \"call\":")) {
            Shadowedhearts.LOGGER.info("Battle already patched for call action, skipping patch");
            return;
        }

        String needle = "case \"move\":";
        String replacement = "case \"call\":\n" +
                "        (this.dex.data.Scripts.shadowedhearts?.call || this.scripts.call).call(this, action.pokemon);\n" +
                "        break;\n" +
                "      case \"move\":";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(battlePath, patched, StandardCharsets.UTF_8);
            Shadowedhearts.LOGGER.info("Battle call action patch applied successfully");
        }
    }
}
