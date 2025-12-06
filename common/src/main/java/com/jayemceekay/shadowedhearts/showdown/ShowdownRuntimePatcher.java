package com.jayemceekay.shadowedhearts.showdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies small text patches to the runtime-extracted Pokemon Showdown JS files.
 *
 * Goals:
 * - Insert a custom "shadow" type into data/typechart.js.
 * - Extend sim/battle.js capture(pokemon) to emit a replacement request after capture.
 *
 * This runs at mod init and targets common dev/prod locations. The patch is idempotent.
 */
public final class ShowdownRuntimePatcher {
    private ShowdownRuntimePatcher() {}

    public static void applyPatches() {
        System.out.println("Applying Showdown runtime patches...");
        for (Path showdown : locateShowdownDirs()) {
            try {
                patchTypechart(showdown.resolve("data").resolve("typechart.js"));
            } catch (Exception e) {
                log("Failed to patch typechart.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchBattleCapture(showdown.resolve("sim").resolve("battle.js"));
            } catch (Exception e) {
                log("Failed to patch battle.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchTeams(showdown.resolve("sim").resolve("teams.js"));
            } catch (Exception e) {
                log("Failed to patch teams.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchConditions(showdown.resolve("data").resolve("conditions.js"));
            } catch (Exception e) {
                log("Failed to patch conditions.js at " + showdown + ": " + e.getMessage());
            }
            /*try {
                patchMicroScripts(showdown.resolve("data").resolve("mods").resolve("micro").resolve("scripts.js"));
            } catch (Exception e) {
                log("Failed to patch micro scripts.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchCustomFormats(showdown.resolve("config").resolve("custom-formats.js"));
            } catch (Exception e) {
                log("Failed to patch custom-formats.js at " + showdown + ": " + e.getMessage());
            }*/
        }
    }

    private static List<Path> locateShowdownDirs() {
        List<Path> result = new ArrayList<>();
        // Common dev environment paths
        addIfDir(result, Paths.get("fabric", "run", "showdown"));
        addIfDir(result, Paths.get("neoforge", "run", "showdown"));
        // Generic runtime paths
        addIfDir(result, Paths.get("run", "showdown"));
        addIfDir(result, Paths.get("showdown"));
        System.out.println("Found Showdown directories: " + result.size());
        return result;
    }

    private static void addIfDir(List<Path> list, Path p) {
        try {
            if (Files.isDirectory(p)) list.add(p.toRealPath());
        } catch (IOException ignored) {}
    }

    private static void patchConditions(Path conditionsPath) throws IOException {
        if (!Files.isRegularFile(conditionsPath)) return;
        String content = Files.readString(conditionsPath, StandardCharsets.UTF_8);

        // Idempotency: if our condition already exists with weather tick, skip.
        // If an older insertion exists without onWeather, we will append an updated block so the latest wins.
        boolean hasShadowyAura = content.contains("\n  shadowyaura:");
        boolean hasShadowyAuraWeatherTick = content.contains("shadowyaura") && content.contains("onWeather(");
        if (hasShadowyAura && hasShadowyAuraWeatherTick) {
            log("conditions.js already contains shadowyaura weather with chip: " + conditionsPath);
            return;
        }

        // Find end of the exported Conditions object. We insert just before the closing "};"
        int endIndex = content.lastIndexOf("};");
        if (endIndex < 0) {
            log("Could not find end of Conditions object in conditions.js: " + conditionsPath);
            return;
        }

        // Determine if there is already a trailing comma before the close
        int j = endIndex - 1;
        while (j >= 0 && Character.isWhitespace(content.charAt(j))) j--;
        boolean hasTrailingComma = j >= 0 && content.charAt(j) == ',';

        // Load our Shadowy Aura WEATHER block (no leading/trailing comma) from an external JS resource.
        // This makes it easier to edit without rebuilding Java strings.
        String block = readResourceText("/data/shadowedhearts/showdown/conditions/shadowyaura.js");
        block = extractExportedTemplate(block);
        if (block == null || block.isBlank()) {
            // Fallback to the previous inlined default if resource missing
            block =
                "  shadowyaura: {\n" +
                "    name: \"Shadowy Aura\",\n" +
                "    effectType: \"Weather\",\n" +
                "    duration: 5,\n" +
                "    onFieldStart(field, source, effect) {\n" +
                "      this.add(\"-weather\", \"Shadowy Aura\");\n" +
                "    },\n" +
                "    // Showdown weathers call Weather each residual tick via onFieldResidual\n" +
                "    onFieldResidualOrder: 1,\n" +
                "    onFieldResidual() {\n" +
                "      this.add(\"-weather\", \"Shadowy Aura\", \"[upkeep]\");\n" +
                "      // After-turn flavor line for Shadowy Aura\n" +
                "      if (this.field.isWeather(\"shadowyaura\")) this.eachEvent(\"Weather\");\n" +
                "    },\n" +
                "    // End-of-turn damage to all non-Shadow Pokémon (1/16 max HP)\n" +
                "    onWeather(target) {\n" +
                "      if (target.set.isShadow) return;\n" +
                "      this.damage(target.baseMaxhp / 16);\n" +
                "    },\n" +
                "    // Boost Shadow-type moves while the weather is active\n" +
                "    onBasePower(basePower, attacker, defender, move) {\n" +
                "      // Optional: uncomment to have Utility Umbrella negate this boost vs/used by holder\n" +
                "      // if (attacker.hasItem(\"utilityumbrella\") || defender.hasItem(\"utilityumbrella\")) return;\n" +
                "      if (move?.type === \"Shadow\") {\n" +
                "        return this.chainModify(1.5);\n" +
                "      }\n" +
                "    },\n" +
                "    // Shadow move effectiveness tweak: super-effective vs non-Shadow, resisted vs Shadow\n" +
                "    onEffectiveness(typeMod, target, type, move) {\n" +
                "      if (!move || move.type !== \"Shadow\") return;\n" +
                "      const isShadowTarget = !!(target?.set?.isShadow);\n" +
                "      return isShadowTarget ? -1 : 1;\n" +
                "    },\n" +
                "    // Optional Weather Ball handling (only if your mod defines the Shadow type)\n" +
                "    onModifyType(move) {\n" +
                "      if (move?.id === \"weatherball\") move.type = \"Shadow\";\n" +
                "    },\n" +
                "    onFieldEnd() {\n" +
                "      this.add(\"-weather\", \"none\");\n" +
                "      this.add(\"-message\", \"The shadowy aura faded away!\");\n" +
                "    }\n" +
                "  }\n";
        }

        String insertionPrefix = hasTrailingComma ? "\n" : ",\n";
        String patched = content.substring(0, endIndex) + insertionPrefix + block + content.substring(endIndex);
        Files.writeString(conditionsPath, patched, StandardCharsets.UTF_8);
        if (hasShadowyAura && !hasShadowyAuraWeatherTick) {
            log("Upgraded existing shadowyaura weather to include chip in conditions.js: " + conditionsPath);
        } else {
            log("Inserted shadowyaura weather into conditions.js: " + conditionsPath);
        }
    }

    private static String readResourceText(String resourcePath) {
        try {
            var in = ShowdownRuntimePatcher.class.getResourceAsStream(resourcePath);
            if (in == null) return null;
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
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

    private static void patchTypechart(Path typechartPath) throws IOException {
        System.out.println(typechartPath);
        if (!Files.isRegularFile(typechartPath)) return;
        String content = Files.readString(typechartPath, StandardCharsets.UTF_8);

        if (content.contains("\n  shadow: {")) {
            log("typechart.js already contains shadow type: " + typechartPath);
            return;
        }

        final String insertAfter =
                "  stellar: {\n" +
                "    damageTaken: {\n" +
                "      Bug: 0,\n" +
                "      Dark: 0,\n" +
                "      Dragon: 0,\n" +
                "      Electric: 0,\n" +
                "      Fairy: 0,\n" +
                "      Fighting: 0,\n" +
                "      Fire: 0,\n" +
                "      Flying: 0,\n" +
                "      Ghost: 0,\n" +
                "      Grass: 0,\n" +
                "      Ground: 0,\n" +
                "      Ice: 0,\n" +
                "      Normal: 0,\n" +
                "      Poison: 0,\n" +
                "      Psychic: 0,\n" +
                "      Rock: 0,\n" +
                "      Steel: 0,\n" +
                "      Stellar: 0,\n" +
                "      Water: 0\n" +
                "    }\n" +
                "  },\n";

        final String shadowBlock =
                "  shadow: {\n" +
                "    damageTaken: {\n" +
                "      Bug: 0,\n" +
                "      Dark: 0,\n" +
                "      Dragon: 0,\n" +
                "      Electric: 0,\n" +
                "      Fairy: 0,\n" +
                "      Fighting: 0,\n" +
                "      Fire: 0,\n" +
                "      Flying: 0,\n" +
                "      Ghost: 0,\n" +
                "      Grass: 0,\n" +
                "      Ground: 0,\n" +
                "      Ice: 0,\n" +
                "      Normal: 0,\n" +
                "      Poison: 0,\n" +
                "      Psychic: 0,\n" +
                "      Rock: 0,\n" +
                "      Steel: 0,\n" +
                "      Stellar: 0,\n" +
                "      Water: 0\n" +
                "    },\n" +
                "    isNonstandard: \"Custom\",\n" +
                "    HPivs: {},\n" +
                "    HPdvs: {}\n" +
                "  },\n";

        int stellarIndex = content.indexOf("  stellar: {");
        if (stellarIndex >= 0) {
            int waterIndex = content.indexOf("  water: {", stellarIndex);
            if (waterIndex > stellarIndex) {
                String patched = content.substring(0, waterIndex)
                        + shadowBlock
                        + content.substring(waterIndex);
                Files.writeString(typechartPath, patched, StandardCharsets.UTF_8);
                log("Inserted shadow type into typechart.js: " + typechartPath);
                return;
            }
        }

        // Fallback: insert before end of TypeChart object
        int endIndex = content.lastIndexOf("};");
        if (endIndex > 0) {
            String patched = content.substring(0, endIndex)
                    + shadowBlock
                    + content.substring(endIndex);
            Files.writeString(typechartPath, patched, StandardCharsets.UTF_8);
            log("Appended shadow type at end of TypeChart in typechart.js: " + typechartPath);
        } else {
            log("Could not find insertion point in typechart.js: " + typechartPath);
        }
    }

    private static void patchBattleCapture(Path battlePath) throws IOException {
        if (!Files.isRegularFile(battlePath)) return;
        String content = Files.readString(battlePath, StandardCharsets.UTF_8);

        if (content.contains("pokemon.side.emitRequest(req)") || content.contains("// Build and emit a new request")) {
            log("battle.js capture() already extended: " + battlePath);
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
            log("Patched capture() in battle.js: " + battlePath);
        } else {
            log("Could not find capture() tail to patch in battle.js: " + battlePath);
        }
    }

    private static void patchTeams(Path teamsPath) throws IOException {
        if (!Files.isRegularFile(teamsPath)) return;
        String content = Files.readString(teamsPath, StandardCharsets.UTF_8);

        // Idempotence: if we already added the shadow token handling, skip
        if (content.contains("set.isShadow") || content.contains("misc[6]") || content.contains(", (set.isShadow ? \"true\" : \"false\")")) {
            log("teams.js already patched: " + teamsPath);
            return;
        }

        int packStart = content.indexOf("  pack(team) {");
        if (packStart < 0) { log("Could not find pack(team) in teams.js: " + teamsPath); return; }
        int unpackStart = content.indexOf("\n  unpack(buf) {", packStart);
        if (unpackStart < 0) { log("Could not find unpack(buf) after pack(team) in teams.js: " + teamsPath); return; }
        int afterUnpack = content.indexOf("\n  /**", unpackStart);
        if (afterUnpack < 0) {
            afterUnpack = content.indexOf("\n  packName(", unpackStart);
        }
        if (afterUnpack < 0) { log("Could not find end of unpack(buf) in teams.js: " + teamsPath); return; }

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
                "      if (set.pokeball || set.hpType || set.gigantamax || set.dynamaxLevel !== void 0 && set.dynamaxLevel !== 10 || set.teraType || set.isShadow) {\n" +
                "        buf += \",\" + this.packName(set.pokeball || \"\");\n" +
                "        buf += \",\" + (set.hpType || \"\");\n" +
                "        buf += \",\" + (set.gigantamax ? \"G\" : \"\");\n" +
                "        buf += \",\" + (set.dynamaxLevel !== void 0 && set.dynamaxLevel !== 10 ? set.dynamaxLevel : \"\");\n" +
                "        buf += \",\" + (set.teraType || \"\");\n" +
                "        buf += \",\" + (set.isShadow ? \"true\" : \"false\");\n" +
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
                "            if (i < buf.length) misc = buf.substring(i).split(',', 7);\n" +
                "        } else {\n" +
                "            if (i !== j) misc = buf.substring(i, j).split(',', 7);\n" +
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
                "        }\n" +
                "      if (j < 0)\n" +
                "        break;\n" +
                "      i = j + 1;\n" +
                "    }\n" +
                "    return team;\n" +
                "  }\n";

        String patched = content.substring(0, packStart) + newBlock + content.substring(afterUnpack);
        Files.writeString(teamsPath, patched, StandardCharsets.UTF_8);
        log("Patched pack/unpack in teams.js: " + teamsPath);
    }

    private static void patchCustomFormats(Path customFormatsPath) throws IOException {
        // Ensure the file exists; if not, scaffold a minimal formats file
        if (!Files.isRegularFile(customFormatsPath)) {
            try {
                Files.createDirectories(customFormatsPath.getParent());
            } catch (IOException ignored) {}
            String scaffold = "exports.Formats = [\n];\n";
            Files.writeString(customFormatsPath, scaffold, StandardCharsets.UTF_8);
        }

        String content = Files.readString(customFormatsPath, StandardCharsets.UTF_8);

        // Idempotence check
        if (content.contains("name: \"[Gen 9] Micro\"") || content.contains("name: '[Gen 9] Micro'")) {
            log("custom-formats.js already contains [Gen 9] Micro: " + customFormatsPath);
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
        log("Inserted [Gen 9] Micro into custom-formats.js: " + customFormatsPath);
    }

    private static void patchMicroScripts(Path scriptsPath) throws IOException {
        // Ensure the directory exists and create or update the micro mod scripts
        String marker1 = "tiebreak()";
        String marker2 = "addSideCondition(";
        if (Files.isRegularFile(scriptsPath)) {
            String existing = Files.readString(scriptsPath, StandardCharsets.UTF_8);
            if (existing.contains("exports.Scripts") && existing.contains(marker1) && existing.contains(marker2)) {
                log("micro/scripts.js already present: " + scriptsPath);
                return;
            }
        } else {
            try { Files.createDirectories(scriptsPath.getParent()); } catch (IOException ignored) {}
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
        log("Wrote micro mod scripts.js: " + scriptsPath);
    }

    private static void log(String msg) {
        System.out.println("[ShadowedHearts][ShowdownPatcher] " + msg);
    }
}
