package com.jayemceekay.shadowedhearts.showdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.jayemceekay.shadowedhearts.showdown.LiteralShowdownPatch.Status.*;

/** Applies both halves of the team format together, preserving code outside their misc-field blocks. */
final class ShowdownTeamPatches {
    private ShowdownTeamPatches() {}

    record Step(String id, LiteralShowdownPatch.Result result) {}
    record Result(LiteralShowdownPatch.Status status, String content, List<Step> steps) {}

    static final String PACK_STOCK = """
                  if (set.pokeball || set.hpType || set.gigantamax || set.dynamaxLevel !== void 0 && set.dynamaxLevel !== 10 || set.teraType) {
                    buf += "," + this.packName(set.pokeball || "");
                    buf += "," + (set.hpType || "");
                    buf += "," + (set.gigantamax ? "G" : "");
                    buf += "," + (set.dynamaxLevel !== void 0 && set.dynamaxLevel !== 10 ? set.dynamaxLevel : "");
                    buf += "," + (set.teraType || "");
                  }
                }
                return buf;
              }
            """.stripTrailing();

    static final String PACK_SHADOW = PACK_STOCK
            .replace(" || set.teraType) {", " || set.teraType || set.isShadow) {")
            .replace("        buf += \",\" + (set.teraType || \"\");",
                    "        buf += \",\" + (set.teraType || \"\");\n"
                            + "        buf += \",\" + (set.isShadow ? \"true\" : \"false\");");

    static final String PACK_HEART_GAUGE = PACK_SHADOW
            .replace(" || set.isShadow) {", " || set.isShadow || set.heartGaugeBars !== void 0) {")
            .replace("        buf += \",\" + (set.isShadow ? \"true\" : \"false\");",
                    "        buf += \",\" + (set.isShadow ? \"true\" : \"false\");\n"
                            + "        buf += \",\" + (set.heartGaugeBars !== void 0 ? set.heartGaugeBars : \"\");");

    static final String PACK_MODES = PACK_HEART_GAUGE
            .replace(" || set.heartGaugeBars !== void 0) {",
                    " || set.heartGaugeBars !== void 0 || set.isHyper || set.isReverse) {")
            .replace("        buf += \",\" + (set.heartGaugeBars !== void 0 ? set.heartGaugeBars : \"\");",
                    "        buf += \",\" + (set.heartGaugeBars !== void 0 ? set.heartGaugeBars : \"\");\n"
                            + "        buf += \",\" + (set.isHyper ? 'true' : 'false');\n"
                            + "        buf += \",\" + (set.isReverse ? 'true' : 'false');");

    static final String PACK_EXACT_GAUGE = PACK_MODES
            .replace(" || set.isReverse) {", " || set.isReverse || set.heartGaugeFraction !== void 0) {")
            .replace("        buf += \",\" + (set.isReverse ? 'true' : 'false');",
                    "        buf += \",\" + (set.isReverse ? 'true' : 'false');\n"
                            + "        buf += \",\" + (set.heartGaugeFraction !== void 0 ? set.heartGaugeFraction : \"\");");

    static final String PACK_COMPLETE = PACK_EXACT_GAUGE
            .replace(" || set.heartGaugeFraction !== void 0) {", " || set.heartGaugeFraction !== void 0 || set.playerControlled) {")
            .replace("        buf += \",\" + (set.heartGaugeFraction !== void 0 ? set.heartGaugeFraction : \"\");",
                    "        buf += \",\" + (set.heartGaugeFraction !== void 0 ? set.heartGaugeFraction : \"\");\n"
                            + "        buf += \",\" + (set.playerControlled ? 'true' : 'false');");

    static final String UNPACK_STOCK = """
                  i = j + 1;
                  j = buf.indexOf("]", i);
                  let misc;
                  if (j < 0) {
                    if (i < buf.length)
                      misc = buf.substring(i).split(",", 6);
                  } else {
                    if (i !== j)
                      misc = buf.substring(i, j).split(",", 6);
                  }
                  if (misc) {
                    set.happiness = misc[0] ? Number(misc[0]) : 255;
                    set.pokeball = this.unpackName(misc[1] || "", import_dex.Dex.items);
                    set.hpType = misc[2] || "";
                    set.gigantamax = !!misc[3];
                    set.dynamaxLevel = misc[4] ? Number(misc[4]) : 10;
                    set.teraType = misc[5];
                  }
                  if (j < 0)
                    break;
                  i = j + 1;
                }
                return team;
              }
            """.stripTrailing();

    // These are the literal blocks emitted by previous Shadowed Hearts patch versions.
    static final String UNPACK_SHADOW = """
                  i = j + 1;
                    // happiness + extended misc
                    j = buf.indexOf(']', i);
                    let misc;
                    if (j < 0) {
                        if (i < buf.length) misc = buf.substring(i).split(',', 7);
                    } else {
                        if (i !== j) misc = buf.substring(i, j).split(',', 7);
                    }
                    if (misc) {
                        set.happiness = (misc[0] ? Number(misc[0]) : 255);
                        set.pokeball = this.unpackName(misc[1] || '', import_dex.Dex.items);
                        set.hpType = misc[2] || '';
                        set.gigantamax = !!misc[3];
                        set.dynamaxLevel = (misc[4] ? Number(misc[4]) : 10);
                        set.teraType = misc[5];

                        // 7th token: Shadow flag
                        const shadowTok = misc[6];
                        set.isShadow = shadowTok === 'true';
                    }
                  if (j < 0)
                    break;
                  i = j + 1;
                }
                return team;
              }
            """.stripTrailing();

    static final String UNPACK_HEART_GAUGE = UNPACK_SHADOW.replace("split(',', 7)", "split(',', 8)")
            .replace("            set.isShadow = shadowTok === 'true';",
                    "            set.isShadow = shadowTok === 'true';\n"
                            + "            // 8th token: Heart Gauge bars (0..5)\n"
                            + "            if (misc.length > 7 && misc[7] !== undefined && misc[7] !== '') {\n"
                            + "                set.heartGaugeBars = Number(misc[7]);\n"
                            + "            }");

    static final String UNPACK_MODES = UNPACK_HEART_GAUGE.replace("split(',', 8)", "split(',', 10)")
            .replace("                set.heartGaugeBars = Number(misc[7]);\n            }",
                    "                set.heartGaugeBars = Number(misc[7]);\n            }\n"
                            + "            // 9th token: Start in Hyper Mode flag\n"
                            + "            if (misc.length > 8) {\n"
                            + "                set.isHyper = misc[8] === 'true';\n"
                            + "            }\n"
                            + "            // 10th token: Start in Reverse Mode flag\n"
                            + "            if (misc.length > 9) {\n"
                            + "                set.isReverse = misc[9] === 'true';\n"
                            + "            }");

    static final String UNPACK_EXACT_GAUGE = UNPACK_MODES.replace("split(',', 10)", "split(',', 11)")
            .replace("                set.isReverse = misc[9] === 'true';\n            }",
                    "                set.isReverse = misc[9] === 'true';\n            }\n"
                            + "            // 11th token: exact Heart Gauge fraction (0..1)\n"
                            + "            if (misc.length > 10 && misc[10] !== undefined && misc[10] !== '') {\n"
                            + "                const fraction = Number(misc[10]);\n"
                            + "                if (Number.isFinite(fraction)) set.heartGaugeFraction = Math.max(0, Math.min(1, fraction));\n"
                            + "            }");

    static final String UNPACK_COMPLETE = UNPACK_EXACT_GAUGE.replace("split(',', 11)", "split(',', 12)")
            .replace("                if (Number.isFinite(fraction)) set.heartGaugeFraction = Math.max(0, Math.min(1, fraction));\n            }",
                    "                if (Number.isFinite(fraction)) set.heartGaugeFraction = Math.max(0, Math.min(1, fraction));\n            }\n"
                            + "            // 12th token: controlled by a player actor, not an NPC or wild opponent\n"
                            + "            set.playerControlled = misc[11] === 'true';");

    private static final List<Part> PARTS = List.of(
            new Part("teams.pack", "  pack(team) {",
                    List.of(PACK_STOCK, PACK_SHADOW, PACK_HEART_GAUGE, PACK_MODES, PACK_EXACT_GAUGE), PACK_COMPLETE),
            new Part("teams.unpack", "  unpack(buf) {",
                    List.of(UNPACK_STOCK, UNPACK_SHADOW, UNPACK_HEART_GAUGE, UNPACK_MODES, UNPACK_EXACT_GAUGE), UNPACK_COMPLETE));

    static Result apply(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return new Result(MISSING_FILE, "", List.of());
        Result result = apply(Files.readString(path, StandardCharsets.UTF_8));
        if (result.status() == APPLIED) Files.writeString(path, result.content(), StandardCharsets.UTF_8);
        return result;
    }

    static Result apply(String content) {
        String candidate = content;
        List<Step> steps = new ArrayList<>();
        for (Part part : PARTS) {
            var result = part.apply(candidate);
            steps.add(new Step(part.id(), result));
            if (result.status() != APPLIED && result.status() != ALREADY_APPLIED) {
                return new Result(result.status(), content, List.copyOf(steps));
            }
            candidate = result.content();
        }
        // Verify both complete blocks on the combined result before committing either edit.
        for (Part part : PARTS) {
            if (part.apply(candidate).status() != ALREADY_APPLIED) {
                return new Result(VERIFICATION_FAILED, content, List.copyOf(steps));
            }
        }
        return new Result(candidate.equals(content) ? ALREADY_APPLIED : APPLIED, candidate, List.copyOf(steps));
    }

    private record Part(String id, String header, List<String> originals, String replacement) {
        LiteralShowdownPatch.Result apply(String content) {
            var declaration = Pattern.compile("(?m)^" + Pattern.quote(header) + "(?=\\r?$)").matcher(content);
            long targets = declaration.results().count();
            if (targets != 1) return result(targets > 1 ? AMBIGUOUS : NOT_FOUND, content, targets, 0, 0);
            declaration.reset();
            declaration.find();
            int start = declaration.start();
            var end = Pattern.compile("(?m)^  }(?=\\r?$)").matcher(content);
            if (!end.find(declaration.end())) return result(NOT_FOUND, content, targets, 0, 0);
            String method = content.substring(start, end.end());
            List<LiteralShowdownPatch.Result> variants = originals.stream()
                    .map(original -> new LiteralShowdownPatch(id, header, original, replacement).apply(method)).toList();
            long before = variants.stream().mapToLong(LiteralShowdownPatch.Result::originals).sum();
            long after = variants.getFirst().replacements();
            if (before + after > 1) return result(AMBIGUOUS, content, targets, before, after);
            if (after == 1) return result(ALREADY_APPLIED, content, targets, before, after);
            if (before != 1) return result(NOT_FOUND, content, targets, before, after);
            var changed = variants.stream().filter(value -> value.status() == APPLIED).findFirst();
            if (changed.isEmpty()) return result(VERIFICATION_FAILED, content, targets, before, after);
            String patched = content.substring(0, start) + changed.get().content() + content.substring(end.end());
            return result(APPLIED, patched, targets, before, after);
        }

        private LiteralShowdownPatch.Result result(LiteralShowdownPatch.Status status, String content,
                                                   long targets, long before, long after) {
            return new LiteralShowdownPatch.Result(status, content, targets, before, after);
        }
    }
}
