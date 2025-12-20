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
        for (Path showdown : locateShowdownDirs()) {
            try {
                patchTypechart(showdown.resolve("data").resolve("typechart.js"));
            } catch (Exception e) {
                log("Failed to patch typechart.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchDexConditions(showdown.resolve("sim").resolve("dex-conditions.js"));
            } catch (Exception e) {
                log("Failed to patch dex-conditions.js at " + showdown + ": " + e.getMessage());
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
            try {
                patchHyperReverseModes(showdown.resolve("data").resolve("conditions.js"));
            } catch (Exception e) {
                log("Failed to patch Hyper/Reverse modes in conditions.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchHyperInstructionTiming(showdown.resolve("data").resolve("conditions.js"));
            } catch (Exception e) {
                log("Failed to patch Hyper instruction timing in conditions.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchShadowEngine(showdown.resolve("data").resolve("conditions.js"));
            } catch (Exception e) {
                log("Failed to patch Shadow Engine in conditions.js at " + showdown + ": " + e.getMessage());
            }
            try {
                patchBattleAddShadowEngine(showdown.resolve("sim").resolve("battle.js"));
            } catch (Exception e) {
                log("Failed to patch battle.js for Shadow Engine at " + showdown + ": " + e.getMessage());
            }
            try {
                patchFieldAddPseudoWeatherDebug(showdown.resolve("sim").resolve("field.js"));
            } catch (Exception e) {
                log("Failed to patch field.js for Shadow Engine debug at " + showdown + ": " + e.getMessage());
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

    /**
     * Guard the custom '|hyper|' token emissions so they are not sent during pre-start switch-in.
     * Sending a custom token before turn 1 can confuse the interpreter and stall choices.
     * We only change the 'start' emission; 'end' happens during the battle and is safe.
     */
    private static void patchHyperInstructionTiming(Path conditionsPath) throws IOException {
        if (!Files.isRegularFile(conditionsPath)) return;
        String content = Files.readString(conditionsPath, StandardCharsets.UTF_8);

        String startLine = "this.add('hyper', 'start', pokemon.getDetails().split('|')[0]);";
        if (!content.contains(startLine)) {
            // Nothing to do or already upgraded
            return;
        }

        String guarded =
                "if (this.turn && this.turn >= 1) { this.add('hyper', 'start', pokemon.getDetails().split('|')[0]); } " +
                "else { this.add('shdebug', '[SH DEBUG] (guarded) Hyper start suppressed until turn 1 for ' + pokemon.name); }";

        String patched = content.replace(startLine, guarded);
        Files.writeString(conditionsPath, patched, StandardCharsets.UTF_8);
        log("Applied guarded emission for '|hyper start|' in conditions.js: " + conditionsPath);
    }

    private static List<Path> locateShowdownDirs() {
        List<Path> result = new ArrayList<>();
        // Common dev environment paths
        addIfDir(result, Paths.get("fabric", "run", "showdown"));
        addIfDir(result, Paths.get("neoforge", "run", "showdown"));
        // Dedicated/server run variants
        addIfDir(result, Paths.get("fabric", "run", "server", "showdown"));
        addIfDir(result, Paths.get("neoforge", "run", "server", "showdown"));
        // Generic runtime paths
        addIfDir(result, Paths.get("run", "showdown"));
        addIfDir(result, Paths.get("run", "server", "showdown"));
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
        } else {
            // Ensure the block has the key if it was loaded from a resource and doesn't have it
            if (!block.trim().startsWith("shadowyaura:")) {
                block = "  shadowyaura: " + block.trim();
            }
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
            log("dex-conditions.js already preserves 'Field' effectType: " + dexConditionsPath);
            return;
        }

        String needle = "this.effectType = [\"Weather\", \"Status\"].includes(data.effectType) ? data.effectType : \"Condition\";";
        String replacement = "this.effectType = [\"Weather\", \"Status\", \"Field\"].includes(data.effectType) ? data.effectType : \"Condition\";";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(dexConditionsPath, patched, StandardCharsets.UTF_8);
            log("Patched dex-conditions.js to allow 'Field' effectType: " + dexConditionsPath);
            return;
        }

        // Fallback: perform a broader replacement on the whitelist array if constructed slightly differently
        if (content.contains(whitelistPattern)) {
            String patched = content.replace(whitelistPattern, desired);
            Files.writeString(dexConditionsPath, patched, StandardCharsets.UTF_8);
            log("Patched dex-conditions.js (broad replace) to allow 'Field': " + dexConditionsPath);
            return;
        }

        // If we couldn't find the expected code, log and skip to avoid breaking the file.
        log("Could not locate effectType whitelist in dex-conditions.js: " + dexConditionsPath);
    }

    /**
     * Inserts custom volatile statuses for Hyper Mode and Reverse Mode into data/conditions.js.
     * Approximates Colosseum/XD mechanics in a Showdown-friendly way:
     * - hypermode: favors Shadow moves, may refuse non-Shadow moves, boosts Shadow move power.
     * - reversemode: chip damage each turn to the afflicted Shadow Pokémon, may occasionally refuse non-Shadow moves.
     */
    private static void patchHyperReverseModes(Path conditionsPath) throws IOException {
        if (!Files.isRegularFile(conditionsPath)) return;
        String content = Files.readString(conditionsPath, StandardCharsets.UTF_8);

        boolean hasHyper = content.contains("\n  hypermode:");
        boolean hasReverse = content.contains("\n  reversemode:");
        if (hasHyper && hasReverse) {
            log("conditions.js already contains hypermode and reversemode: " + conditionsPath);
            return;
        }

        int endIndex = content.lastIndexOf("};");
        if (endIndex < 0) {
            log("Could not find end of Conditions object in conditions.js for Hyper/Reverse patch: " + conditionsPath);
            return;
        }

        int j = endIndex - 1;
        while (j >= 0 && Character.isWhitespace(content.charAt(j))) j--;
        boolean hasTrailingComma = j >= 0 && content.charAt(j) == ',';

        String hyperBlock =
            "  hypermode: {\n" +
            "    name: \"Hyper Mode\",\n" +
            "    effectType: \"VolatileStatus\",\n" +
            "    // When a Shadow Pokémon enters Hyper Mode, it becomes erratic and favors Shadow moves.\n" +
            "    onStart(pokemon) {\n" +
            "      this.add('-start', pokemon, 'Hyper Mode');\n" +
            "      this.add('-message', '[SH DEBUG] HyperMode onStart for ' + pokemon.name);\n" +
            "      // Notify Cobblemon to persist Hyper Mode outside battle\n" +
            "      // Custom instruction: |hyper|start|<active>\n" +
            "      this.add('hyper', 'start', pokemon.getDetails().split('|')[0]);\n" +
            "    },\n" +
            "    onEnd(pokemon) {\n" +
            "      this.add('-end', pokemon, 'Hyper Mode');\n" +
            "      this.add('-message', '[SH DEBUG] HyperMode onEnd for ' + pokemon.name);\n" +
            "      // Custom instruction: |hyper|end|<active>\n" +
            "      this.add('hyper', 'end', pokemon.getDetails().split('|')[0]);\n" +
            "    },\n" +
            "    // Small chance to end on its own each turn (1/256).\n" +
            "    onResidualOrder: 6,\n" +
            "    onResidual(pokemon) {\n" +
            "      if (this.randomChance(1, 256)) {\n" +
            "        this.add('-message', pokemon.name + ' calmed down.');\n" +
            "        this.add('-message', '[SH DEBUG] HyperMode onResidual ended for ' + pokemon.name);\n" +
            "        pokemon.removeVolatile('hypermode');\n" +
            "      }\n" +
            "    },\n" +
            "    // Always leave Hyper Mode on faint in battle.\n" +
            "    onFaint(pokemon) {\n" +
            "      if (pokemon.volatiles['hypermode']) { this.add('-message', '[SH DEBUG] HyperMode onFaint removing for ' + pokemon.name); pokemon.removeVolatile('hypermode'); }\n" +
            "    },\n" +
            "    // If attempting a non-Shadow move, 50% chance to instead use a Shadow move if available.\n" +
            "    onTryMove(source, target, move) {\n" +
            "      if (!move || move.type === 'Shadow') return;\n" +
            "      this.add('-message', '[SH DEBUG] HyperMode onTryMove non-Shadow detected: ' + move.name + ' by ' + source.name);\n" +
            "      if (this.randomChance(1, 2)) {\n" +
            "        const shadowMoves = source.moveSlots.filter(m => this.dex.moves.get(m.id).type === 'Shadow');\n" +
            "        if (shadowMoves.length) {\n" +
            "          // Use the first Shadow move instead\n" +
            "          const chosen = shadowMoves[0].id;\n" +
            "          this.add('-message', source.name + ' goes into Hyper Mode and uses a Shadow move instead!');\n" +
            "          this.add('-message', '[SH DEBUG] HyperMode onTryMove switching to Shadow move: ' + this.dex.moves.get(chosen).name);\n" +
            "          // Replace chosen move id for this turn\n" +
            "          move.id = chosen; move.name = this.dex.moves.get(chosen).name; move.type = 'Shadow';\n" +
            "          return;\n" +
            "        }\n" +
            "        // Otherwise, pick a Hyper Mode disobedience outcome at random.\n" +
            "        const outcomes = ['otherMove','attackAlly','selfHurt','fakeItem','yellAtTrainer','doNothing','returnBall'];\n" +
            "        const pick = outcomes[this.random(outcomes.length)];\n" +
            "        this.add('-message', '[SH DEBUG] HyperMode onTryMove outcome: ' + pick);\n" +
            "        // Helper: pick another known move if any.\n" +
            "        const otherKnown = source.moveSlots.filter(m => m.id !== move.id);\n" +
            "        switch (pick) {\n" +
            "          case 'otherMove': {\n" +
            "            if (otherKnown.length) {\n" +
            "              const chosen = otherKnown[this.random(otherKnown.length)].id;\n" +
            "              this.add('-message', source.name + ' won\\'t obey and uses another move!');\n" +
            "              this.add('-message', '[SH DEBUG] HyperMode onTryMove switching to other known move: ' + this.dex.moves.get(chosen).name);\n" +
            "              move.id = chosen; move.name = this.dex.moves.get(chosen).name;\n" +
            "              return;\n" +
            "            }\n" +
            "            // fallthrough to doNothing if no other move\n" +
            "          }\n" +
            "          case 'attackAlly': {\n" +
            "            const ally = source.side.active[1] && !source.side.active[1].fainted ? source.side.active[1] : null;\n" +
            "            if (ally) {\n" +
            "              this.add('-message', source.name + ' turns on its ally in Hyper Mode!');\n" +
            "              this.add('-message', '[SH DEBUG] HyperMode onTryMove retargeting to ally: ' + ally.name);\n" +
            "              // Retarget the move to ally if possible\n" +
            "              if (target && target !== ally) target = ally;\n" +
            "              return;\n" +
            "            }\n" +
            "            // No ally; fall through to self-hurt\n" +
            "          }\n" +
            "          case 'selfHurt': {\n" +
            "            this.add('-message', source.name + ' is thrashing about in Hyper Mode!');\n" +
            "            this.add('-message', '[SH DEBUG] HyperMode onTryMove self-damage 1/8 max HP');\n" +
            "            this.damage(Math.max(1, Math.floor(source.baseMaxhp / 8)), source, source);\n" +
            "            return false;\n" +
            "          }\n" +
            "          case 'fakeItem': {\n" +
            "            this.add('-message', source.name + ' fumbles with an item... but nothing happens.');\n" +
            "            this.add('-message', '[SH DEBUG] HyperMode onTryMove fakeItem no-op');\n" +
            "            return false;\n" +
            "          }\n" +
            "          case 'yellAtTrainer': {\n" +
            "            this.add('-message', source.name + ' lashes out at its Trainer! (No effect)');\n" +
            "            this.add('-message', '[SH DEBUG] HyperMode onTryMove yellAtTrainer no-op');\n" +
            "            return false;\n" +
            "          }\n" +
            "          case 'returnBall': {\n" +
            "            const sw = source.side.pokemon.some(p => !p.fainted && !p.active);\n" +
            "            this.add('-message', source.name + ' tries to return to its Poké Ball!');\n" +
            "            this.add('-message', '[SH DEBUG] HyperMode onTryMove returnBall switchAvailable=' + sw);\n" +
            "            if (!sw) this.add('-message', 'But it can\\'t be switched out!');\n" +
            "            return false;\n" +
            "          }\n" +
            "          default: {\n" +
            "            this.add('-message', source.name + ' refuses to act!');\n" +
            "            this.add('-message', '[SH DEBUG] HyperMode onTryMove default refuse');\n" +
            "            return false;\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    },\n" +
            "    // Boost power of Shadow-type moves.\n" +
            "    onBasePower(basePower, attacker, defender, move) {\n" +
            "      if (move?.type === 'Shadow') { this.add('-message', '[SH DEBUG] HyperMode onBasePower boost applied for ' + attacker.name + ' using ' + move.name); return this.chainModify(1.5); }\n" +
            "    },\n" +
            "  }\n";

        String reverseBlock =
            "  reversemode: {\n" +
            "    name: \"Reverse Mode\",\n" +
            "    effectType: \"VolatileStatus\",\n" +
            "    // Reverse Mode harms the Shadow Pokémon each turn and increases disobedience.\n" +
            "    onStart(pokemon) {\n" +
            "      this.add('-start', pokemon, 'Reverse Mode');\n" +
            "    },\n" +
            "    onEnd(pokemon) {\n" +
            "      this.add('-end', pokemon, 'Reverse Mode');\n" +
            "    },\n" +
            "    onResidualOrder: 5,\n" +
            "    onResidual(pokemon) {\n" +
            "      this.damage(pokemon.baseMaxhp / 16, pokemon, pokemon);\n" +
            "      this.add('-message', pokemon.name + \" is hurt by Reverse Mode!\");\n" +
            "    },\n" +
            "    // 20% chance to refuse non-Shadow moves.\n" +
            "    onTryMove(source, target, move) {\n" +
            "      if (!move || move.type === 'Shadow') return;\n" +
            "      if (this.randomChance(1, 5)) {\n" +
            "        this.add('-message', source.name + ' is in Reverse Mode and won\\'t obey!');\n" +
            "        return false;\n" +
            "      }\n" +
            "    },\n" +
            "  }\n";

        StringBuilder blocks = new StringBuilder();
        if (!hasHyper) blocks.append(hyperBlock);
        if (!hasReverse) {
            if (blocks.length() > 0) blocks.append(",\n");
            blocks.append(reverseBlock);
        }

        if (blocks.length() == 0) return; // nothing to add

        String insertionPrefix = hasTrailingComma ? "\n" : ",\n";
        String patched = content.substring(0, endIndex) + insertionPrefix + blocks + content.substring(endIndex);
        Files.writeString(conditionsPath, patched, StandardCharsets.UTF_8);
        if (!hasHyper && !hasReverse) {
            log("Inserted hypermode and reversemode into conditions.js: " + conditionsPath);
        } else if (!hasHyper) {
            log("Inserted hypermode into conditions.js: " + conditionsPath);
        } else {
            log("Inserted reversemode into conditions.js: " + conditionsPath);
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

        // Idempotence/upgrade:
        // - If the file already has our newer flags (isHyper/isReverse), skip.
        // - If it only has the older heartGaugeBars patch, proceed to upgrade to add two more fields.
        boolean hasNewFlags = content.contains("set.isHyper") || content.contains("set.isReverse") || content.contains("misc[9]");
        if (hasNewFlags) {
            log("teams.js already patched with isHyper/isReverse: " + teamsPath);
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

    /**
     * Inserts a global pseudo-weather-like condition 'shadowengine' that listens to onTryMove
     * to roll Hyper Mode entry based on Nature + Heart Gauge bars. This keeps logic local to
     * Showdown and avoids editing many engine files.
     */
    private static void patchShadowEngine(Path conditionsPath) throws IOException {
        if (!Files.isRegularFile(conditionsPath)) return;
        String content = Files.readString(conditionsPath, StandardCharsets.UTF_8);

        // Upgrade policy: if shadowengine exists and already has onSwitchIn, onFieldStart, and onFieldRestart handlers, skip.
        // Otherwise, append a newer block so upgrades add any missing hooks.
        boolean hasShadowEngine = content.contains("\n  shadowengine:");
        boolean hasSwitchInHook = content.contains("shadowengine") && content.contains("onSwitchIn(pokemon)");
        boolean hasFieldStartHook = content.contains("shadowengine") && content.contains("onFieldStart(");
        boolean hasFieldRestartHook = content.contains("shadowengine") && content.contains("onFieldRestart(");
        boolean hasShDebugEmits = content.contains("this.add('shdebug'") || content.contains("this.add(\"shdebug\"");
        if (hasShadowEngine && hasSwitchInHook && hasFieldStartHook && hasFieldRestartHook && hasShDebugEmits) {
            log("conditions.js already contains shadowengine with hooks: " + conditionsPath);
            return;
        }

        int endIndex = content.lastIndexOf("};");
        if (endIndex < 0) {
            log("Could not find end of Conditions object in conditions.js for shadowengine: " + conditionsPath);
            return;
        }

        int j = endIndex - 1;
        while (j >= 0 && Character.isWhitespace(content.charAt(j))) j--;
        boolean hasTrailingComma = j >= 0 && content.charAt(j) == ',';

        String block =
            "  shadowengine: {\n" +
            "    name: 'Shadow Engine',\n" +
            "    effectType: 'Field',\n" +
            "    // If the same pseudo-weather is added again (e.g., by subformats),\n" +
            "    // Showdown will call onFieldRestart if present. Provide it so re-adds are harmless.\n" +
            "    onFieldRestart(field, source, effect) {\n" +
            "      this.add('shdebug', '[SH DEBUG] ShadowEngine restart (duplicate add)');\n" +
            "      return true;\n" +
            "    },\n" +
            "    // Debug: confirm Shadow Engine field condition is active when added.\n" +
            "    onFieldStart(field, source, effect) {\n" +
            "      this.add('shdebug', '[SH DEBUG] ShadowEngine active');\n" +
            "    },\n" +
            "    // Apply starting volatiles if provided by team data (from Cobblemon aspects).\n" +
            "    onSwitchIn(pokemon) {\n" +
            "      // If the packed set indicates Hyper/Reverse at start, ensure volatiles are applied.\n" +
            "      if (pokemon?.set?.isHyper && !pokemon.volatiles['hypermode']) {\n" +
            "        this.add('shdebug', '[SH DEBUG] ShadowEngine onSwitchIn applying Hyper Mode to ' + pokemon.name);\n" +
            "        this.add('-message', '[SH DEBUG] ShadowEngine onSwitchIn applying Hyper Mode to ' + pokemon.name);\n" +
            "        pokemon.addVolatile('hypermode');\n" +
            "      }\n" +
            "      if (pokemon?.set?.isReverse && !pokemon.volatiles['reversemode']) {\n" +
            "        this.add('shdebug', '[SH DEBUG] ShadowEngine onSwitchIn applying Reverse Mode to ' + pokemon.name);\n" +
            "        this.add('-message', '[SH DEBUG] ShadowEngine onSwitchIn applying Reverse Mode to ' + pokemon.name);\n" +
            "        pokemon.addVolatile('reversemode');\n" +
            "      }\n" +
            "    },\n" +
            "    // Probability table: index maps bars 5..0 to [0..5]\n" +
            "    natureTable: {\n" +
            "      calm:[30,25,20,15,10,5], lonely:[30,25,20,15,10,5], modest:[30,25,20,15,10,5], timid:[30,25,20,15,10,5],\n" +
            "      bold:[50,40,30,20,10,5], brave:[50,40,30,20,10,5], lax:[50,40,30,20,10,5], quirky:[50,40,30,20,10,5], sassy:[50,40,30,20,10,5],\n" +
            "      hasty:[50,0,40,30,25,12], impish:[50,0,40,30,25,12], naughty:[50,0,40,30,25,12], rash:[50,0,40,30,25,12],\n" +
            "      careful:[0,20,0,15,10,5], docile:[0,20,0,15,10,5], quiet:[0,20,0,15,10,5], serious:[0,20,0,15,10,5],\n" +
            "      gentle:[0,0,50,0,0,0], jolly:[0,0,50,0,0,0], mild:[0,0,50,0,0,0], naive:[0,0,50,0,0,0],\n" +
            "      adamant:[30,0,70,0,50,25], bashful:[30,0,70,0,50,25], hardy:[30,0,70,0,50,25], relaxed:[30,0,70,0,50,25]\n" +
            "    },\n" +
            "    onTryMove(source, target, move) {\n" +
            "      if (!move || move.type === 'Shadow') return;\n" +
            "      if (!source?.set?.isShadow) return;\n" +
            "      if (source.volatiles['hypermode']) return;\n" +
            "      const nat = (source.set.nature || '').toLowerCase();\n" +
            "      let bars = Number(source.set.heartGaugeBars);\n" +
            "      if (!Number.isFinite(bars)) bars = 5;\n" +
            "      if (bars < 0) bars = 0; if (bars > 5) bars = 5;\n" +
            "      const table = this.field.getPseudoWeather('shadowengine').natureTable;\n" +
            "      const row = table[nat];\n" +
            "      const idx = 5 - bars; // bars 5..0 -> idx 0..5\n" +
            "      const pct = row ? row[idx] : [30,25,20,15,10,5][idx];\n" +
            "      if (pct && this.random(100) < pct) {\n" +
            "        source.addVolatile('hypermode');\n" +
            "        this.add('-message', source.name + ' flew into Hyper Mode!');\n" +
            "      }\n" +
            "    },\n" +
            "  }\n";

        String insertionPrefix = hasTrailingComma ? "\n" : ",\n";
        String patched = content.substring(0, endIndex) + insertionPrefix + block + content.substring(endIndex);
        Files.writeString(conditionsPath, patched, StandardCharsets.UTF_8);
        log("Inserted shadowengine into conditions.js: " + conditionsPath);
    }

    /** Adds field.addPseudoWeather('shadowengine') to battle constructor so the engine runs. */
    private static void patchBattleAddShadowEngine(Path battlePath) throws IOException {
        if (!Files.isRegularFile(battlePath)) return;
        String content = Files.readString(battlePath, StandardCharsets.UTF_8);
        if (content.contains("addPseudoWeather('shadowengine')") || content.contains("addPseudoWeather(\"shadowengine\")")) {
            log("battle.js already enables shadowengine: " + battlePath);
            return;
        }
        String anchor = "this.add(\"gametype\", this.gameType);";
        int idx = content.indexOf(anchor);
        if (idx < 0) { log("Could not find gametype anchor in battle.js: " + battlePath); return; }
        int insertPos = idx + anchor.length();
        String injected = "\n    this.field.addPseudoWeather('shadowengine');\n    this.add('shdebug', '[SH DEBUG] ShadowEngine enabled at battle start');";
        String patched = content.substring(0, insertPos) + injected + content.substring(insertPos);
        Files.writeString(battlePath, patched, StandardCharsets.UTF_8);
        log("Enabled shadowengine at battle start in battle.js: " + battlePath);
    }

    /**
     * Adds extra debug lines in sim/field.js addPseudoWeather to surface success/failure when adding
     * the 'shadowengine' pseudo-weather. This helps diagnose cases where FieldStart returns false.
     *
     * The patch is idempotent and only touches the specific addPseudoWeather return block.
     */
    private static void patchFieldAddPseudoWeatherDebug(Path fieldPath) throws IOException {
        if (!Files.isRegularFile(fieldPath)) return;
        String content = Files.readString(fieldPath, StandardCharsets.UTF_8);

        // Skip if our debug is already present
        if (content.contains("ShadowEngine addPseudoWeather success") || content.contains("ShadowEngine FieldStart failed")) {
            log("field.js already contains ShadowEngine addPseudoWeather debug: " + fieldPath);
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
                "      if (status.id === 'shadowengine') this.battle.add('shdebug', '[SH DEBUG] ShadowEngine FieldStart failed');\n" +
                "      return false;\n" +
                "    }\n" +
                "    if (status.id === 'shadowengine') this.battle.add('shdebug', '[SH DEBUG] ShadowEngine addPseudoWeather success');\n" +
                "    this.battle.runEvent(\"PseudoWeatherChange\", source, source, status);\n" +
                "    return true;";

        if (content.contains(needle)) {
            String patched = content.replace(needle, replacement);
            Files.writeString(fieldPath, patched, StandardCharsets.UTF_8);
            log("Patched field.js addPseudoWeather with ShadowEngine debug: " + fieldPath);
        } else {
            log("Could not locate addPseudoWeather return block in field.js (no debug injected): " + fieldPath);
        }
    }
}
