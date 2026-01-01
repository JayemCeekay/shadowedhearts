package com.jayemceekay.shadowedhearts.config;

import com.google.gson.*;
import dev.architectury.platform.Platform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Minimal config system for Shadowed Hearts.
 * Now stored as JSON in config/shadowedhearts-common.json (common) and a separate client file.
 * Common also centralizes ShadowSpawnConfig settings. See 02 §5 Mission Entrance flow.
 */
public final class ModConfig {
    private ModConfig() {}

    private static final String COMMON_FILE = "shadowedhearts-common.json";
    private static final String LEGACY_PROPS_FILE = "shadowedhearts.properties";

    public static final class Data {
        public boolean showdownPatched = false;
        /** If true, use the alternate bottom-bar Toss Order UI instead of the radial wheel. */
        public boolean tossOrderBarUI = true;

        // Shadow spawn block (moved from ShadowSpawnConfig)
        public double shadowSpawnChancePercent = 0.78125; // default 1/256
        public final Set<String> shadowSpawnBlacklist = new HashSet<>(Set.of("#shadowedhearts:legendaries", "#shadowedhearts:mythical"));

        // Hyper Mode config
        public final HyperModeConfig hyperMode = new HyperModeConfig();
        // Reverse Mode config
        public final ReverseModeConfig reverseMode = new ReverseModeConfig();
        // Call Button config
        public final CallButtonConfig callButton = new CallButtonConfig();

        // Shadow Moves config
        public final ShadowMovesConfig shadowMoves = new ShadowMovesConfig();

        // Mod Integrations
        public final RCTIntegrationConfig rctIntegration = new RCTIntegrationConfig();
    }

    private static final Data DATA = new Data();

    public static Data get() {
        return DATA;
    }

    public static double getShadowSpawnChancePercent() { return DATA.shadowSpawnChancePercent; }
    public static Set<String> getShadowSpawnBlacklist() { return Collections.unmodifiableSet(DATA.shadowSpawnBlacklist); }

    public static void load() {
        Path configDir = Platform.getConfigFolder();
        Path jsonFile = configDir.resolve(COMMON_FILE);

        // One-time migration from legacy properties if present and JSON missing
        if (!Files.isRegularFile(jsonFile)) {
            Path legacy = configDir.resolve(LEGACY_PROPS_FILE);
            if (Files.isRegularFile(legacy)) {
                // Load legacy .properties into DATA then persist JSON
                var props = new Properties();
                try (InputStream in = Files.newInputStream(legacy)) {
                    props.load(in);
                    DATA.showdownPatched = Boolean.parseBoolean(props.getProperty("showdownPatched", Boolean.toString(DATA.showdownPatched)));
                    DATA.tossOrderBarUI = Boolean.parseBoolean(props.getProperty("tossOrderBarUI", Boolean.toString(DATA.tossOrderBarUI)));
                } catch (IOException ignored) {}
                save();
            }
        }

        if (!Files.isRegularFile(jsonFile)) {
            save();
            return; // keep defaults if nothing exists, but save them
        }

        try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el != null && el.isJsonObject()) {
                JsonObject root = el.getAsJsonObject();
                DATA.showdownPatched = optBool(root, "showdownPatched", DATA.showdownPatched);
                DATA.tossOrderBarUI = optBool(root, "tossOrderBarUI", DATA.tossOrderBarUI);

                if (root.has("hyperMode") && root.get("hyperMode").isJsonObject()) {
                    JsonObject hm = root.getAsJsonObject("hyperMode");
                    DATA.hyperMode.enabled = optBool(hm, "enabled", DATA.hyperMode.enabled);
                    DATA.hyperMode.debugCalmDown = optBool(hm, "debugCalmDown", DATA.hyperMode.debugCalmDown);
                    DATA.hyperMode.debugHyperModeAction = hm.has("debugHyperModeAction") ? hm.get("debugHyperModeAction").getAsString() : DATA.hyperMode.debugHyperModeAction;
                 //   DATA.hyperMode.baseChanceModifier = optDouble(hm, "baseChanceModifier", DATA.hyperMode.baseChanceModifier);
                }

                if (root.has("reverseMode") && root.get("reverseMode").isJsonObject()) {
                    JsonObject rm = root.getAsJsonObject("reverseMode");
                    DATA.reverseMode.enabled = optBool(rm, "enabled", DATA.reverseMode.enabled);
                    DATA.reverseMode.debugReverseModeFailure = optBool(rm, "debugReverseModeFailure", DATA.reverseMode.debugReverseModeFailure);
                //    DATA.reverseMode.baseChanceModifier = optDouble(rm, "baseChanceModifier", DATA.reverseMode.baseChanceModifier);
                }

                if (root.has("callButton") && root.get("callButton").isJsonObject()) {
                    JsonObject cb = root.getAsJsonObject("callButton");
                    DATA.callButton.reducesHeartGauge = optBool(cb, "reducesHeartGauge", DATA.callButton.reducesHeartGauge);
                    DATA.callButton.accuracyBoost = optBool(cb, "accuracyBoost", DATA.callButton.accuracyBoost);
                    DATA.callButton.removeSleep = optBool(cb, "removeSleep", DATA.callButton.removeSleep);
                }

                if (root.has("shadowMoves") && root.get("shadowMoves").isJsonObject()) {
                    JsonObject sm = root.getAsJsonObject("shadowMoves");
                    DATA.shadowMoves.replaceCount = optInt(sm, "replaceCount", DATA.shadowMoves.replaceCount);
                }

                if (root.has("shadowSpawn") && root.get("shadowSpawn").isJsonObject()) {
                    JsonObject ss = root.getAsJsonObject("shadowSpawn");
                    double p = optDouble(ss, "chancePercent", DATA.shadowSpawnChancePercent);
                    if (p < 0) p = 0; if (p > 100) p = 100;
                    DATA.shadowSpawnChancePercent = p;
                    DATA.shadowSpawnBlacklist.clear();
                    if (ss.has("blacklist") && ss.get("blacklist").isJsonArray()) {
                        for (JsonElement bl : ss.getAsJsonArray("blacklist")) {
                            try {
                                String s = bl.getAsString();
                                if (s != null && !s.isBlank()) DATA.shadowSpawnBlacklist.add(s.toLowerCase(Locale.ROOT).trim());
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // Mod Integrations → RCTMod
                if (root.has("modIntegrations") && root.get("modIntegrations").isJsonObject()) {
                    JsonObject mi = root.getAsJsonObject("modIntegrations");
                    // Accept keys: "rctmod", "Radical Cobblemon Trainers" (user-friendly)
                    JsonObject rct = null;
                    if (mi.has("rctmod") && mi.get("rctmod").isJsonObject()) rct = mi.getAsJsonObject("rctmod");
                    else if (mi.has("Radical Cobblemon Trainers") && mi.get("Radical Cobblemon Trainers").isJsonObject()) rct = mi.getAsJsonObject("Radical Cobblemon Trainers");
                    if (rct != null) {
                        readRCTIntegration(rct, DATA.rctIntegration);
                    }
                }
            }
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
        save();
    }

    public static void save() {
        Path configDir = Platform.getConfigFolder();
        Path jsonFile = configDir.resolve(COMMON_FILE);
        try {
            if (!Files.isDirectory(configDir)) Files.createDirectories(configDir);
            JsonObject root = new JsonObject();
            root.addProperty("showdownPatched", DATA.showdownPatched);
            root.addProperty("tossOrderBarUI", DATA.tossOrderBarUI);

            JsonObject hm = new JsonObject();
            hm.addProperty("enabled", DATA.hyperMode.enabled);
            hm.addProperty("debugCalmDown", DATA.hyperMode.debugCalmDown);
            hm.addProperty("debugHyperModeAction", DATA.hyperMode.debugHyperModeAction);
           // hm.addProperty("baseChanceModifier", DATA.hyperMode.baseChanceModifier);
            root.add("hyperMode", hm);

            JsonObject rm = new JsonObject();
            rm.addProperty("enabled", DATA.reverseMode.enabled);
            rm.addProperty("debugReverseModeFailure", DATA.reverseMode.debugReverseModeFailure);
            //rm.addProperty("baseChanceModifier", DATA.reverseMode.baseChanceModifier);
            root.add("reverseMode", rm);

            JsonObject cb = new JsonObject();
            cb.addProperty("reducesHeartGauge", DATA.callButton.reducesHeartGauge);
            cb.addProperty("accuracyBoost", DATA.callButton.accuracyBoost);
            cb.addProperty("removeSleep", DATA.callButton.removeSleep);
            root.add("callButton", cb);

            JsonObject sm = new JsonObject();
            sm.addProperty("replaceCount", DATA.shadowMoves.replaceCount);
            root.add("shadowMoves", sm);

            JsonObject ss = new JsonObject();
            ss.addProperty("chancePercent", DATA.shadowSpawnChancePercent);
            JsonArray arr = new JsonArray();
            for (String s : DATA.shadowSpawnBlacklist) arr.add(s);
            ss.add("blacklist", arr);
            root.add("shadowSpawn", ss);

            // Persist minimal RCT integration block (only if enabled to avoid noise)
            JsonObject mi = new JsonObject();
            JsonObject rct = new JsonObject();
            rct.addProperty("enabled", DATA.rctIntegration.enabled);
            rct.add("append", writeRCTSection(DATA.rctIntegration.append));
            rct.add("convert", writeRCTSection(DATA.rctIntegration.convert));
            rct.add("replace", writeRCTSection(DATA.rctIntegration.replace));
            mi.add("rctmod", rct);
            root.add("modIntegrations", mi);

            try (BufferedWriter w = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
    }

    private static boolean optBool(JsonObject o, String key, boolean def) {
        try { return o.has(key) ? o.get(key).getAsBoolean() : def; } catch (Exception e) { return def; }
    }
    private static int optInt(JsonObject o, String key, int def) {
        try { return o.has(key) ? o.get(key).getAsInt() : def; } catch (Exception e) { return def; }
    }

    private static double optDouble(JsonObject o, String key, double def) {
        try { return o.has(key) ? o.get(key).getAsDouble() : def; } catch (Exception e) { return def; }
    }

    public static final class HyperModeConfig {
        public boolean enabled = true;
        public boolean debugCalmDown = false;
        public String debugHyperModeAction = "";
        //public double baseChanceModifier = 1.0;
    }

    public static final class ReverseModeConfig {
        public boolean enabled = true;
        public boolean debugReverseModeFailure = false;
        //public double baseChanceModifier = 1.0;
    }

    public static final class CallButtonConfig {
        public boolean reducesHeartGauge = true;
        public boolean accuracyBoost = true;
        public boolean removeSleep = true;
    }

    public static final class ShadowMovesConfig {
        public int replaceCount = 1;
    }

    // ===== RCT Integration config types and (de)serialization =====
    public static final class RCTIntegrationConfig {
        public boolean enabled = false;
        public final RCTSection append = new RCTSection("append");
        public final RCTSection convert = new RCTSection("convert");
        public final RCTSection replace = new RCTSection("replace");
    }

    public static final class RCTTrainerConfig {
        public String id = ""; // exact trainer id (implementation-defined from RCT)
        public final List<String> tags = new ArrayList<>(); // NPCShadowInjector tags to add
        public String preset = ""; // optional: Shadow aspect preset key
    }

    public static final class RCTSection {
        public final String name; // for debugging/logging only
        public final List<String> trainerTypes = new ArrayList<>();
        public final Map<String, String> typePresets = new HashMap<>(); // type -> presetId
        public final List<String> trainerBlacklist = new ArrayList<>();
        public final List<RCTTrainerConfig> trainers = new ArrayList<>();
        RCTSection(String n) {
            this.name = n;
            if ("replace".equals(n)) {
                trainerTypes.add("team_rocket");
                typePresets.put("team_rocket", "shadowedhearts/team_rocket");
                RCTTrainerConfig teamRocket = new RCTTrainerConfig();
                teamRocket.id = "team_rocket";
                teamRocket.preset = "shadowedhearts/team_rocket";
                trainers.add(teamRocket);
            }
        }
    }

    private static void readRCTIntegration(JsonObject obj, RCTIntegrationConfig out) {
        out.enabled = optBool(obj, "enabled", out.enabled);
        if (obj.has("append") && obj.get("append").isJsonObject()) readRCTSection(obj.getAsJsonObject("append"), out.append);
        if (obj.has("convert") && obj.get("convert").isJsonObject()) readRCTSection(obj.getAsJsonObject("convert"), out.convert);
        if (obj.has("replace") && obj.get("replace").isJsonObject()) readRCTSection(obj.getAsJsonObject("replace"), out.replace);
    }

    private static void readRCTSection(JsonObject obj, RCTSection out) {
        out.trainerTypes.clear();
        out.typePresets.clear();
        out.trainerBlacklist.clear();
        out.trainers.clear();
        // Allow either keys: trainer_types / trainerTypes (be lenient)
        readStringArrayInto(obj, out.trainerTypes, obj.has("trainer_types") ? "trainer_types" : "trainerTypes");
        
        if (obj.has("type_presets") && obj.get("type_presets").isJsonObject()) {
            JsonObject tp = obj.getAsJsonObject("type_presets");
            for (Map.Entry<String, JsonElement> entry : tp.entrySet()) {
                out.typePresets.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            }
        } else if (obj.has("typePresets") && obj.get("typePresets").isJsonObject()) {
            JsonObject tp = obj.getAsJsonObject("typePresets");
            for (Map.Entry<String, JsonElement> entry : tp.entrySet()) {
                out.typePresets.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            }
        }

        readStringArrayInto(obj, out.trainerBlacklist, obj.has("trainer_blacklist") ? "trainer_blacklist" : "trainerBlacklist");
        if (obj.has("trainers") && obj.get("trainers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("trainers")) {
                if (!el.isJsonObject()) continue;
                JsonObject to = el.getAsJsonObject();
                RCTTrainerConfig t = new RCTTrainerConfig();
                try { if (to.has("id")) t.id = to.get("id").getAsString(); } catch (Exception ignored) {}
                readStringArrayInto(to, t.tags, "tags");
                try { if (to.has("preset")) t.preset = to.get("preset").getAsString(); } catch (Exception ignored) {}
                if (t.id != null && !t.id.isBlank()) out.trainers.add(t);
            }
        }
        // Normalize to lowercase for type/blacklist ids
        for (int i = 0; i < out.trainerTypes.size(); i++) out.trainerTypes.set(i, safeLower(out.trainerTypes.get(i)));
        for (int i = 0; i < out.trainerBlacklist.size(); i++) out.trainerBlacklist.set(i, safeLower(out.trainerBlacklist.get(i)));
    }

    private static void readStringArrayInto(JsonObject obj, List<String> out, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        for (JsonElement el : obj.getAsJsonArray(key)) {
            try {
                String s = el.getAsString();
                if (s != null && !s.isBlank()) out.add(s);
            } catch (Exception ignored) {}
        }
    }

    private static String safeLower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }

    private static JsonObject writeRCTSection(RCTSection sec) {
        JsonObject o = new JsonObject();
        JsonArray types = new JsonArray();
        for (String s : sec.trainerTypes) types.add(s);
        o.add("trainer_types", types);
        
        JsonObject typePresets = new JsonObject();
        for (Map.Entry<String, String> entry : sec.typePresets.entrySet()) {
            typePresets.addProperty(entry.getKey(), entry.getValue());
        }
        o.add("type_presets", typePresets);

        JsonArray bl = new JsonArray();
        for (String s : sec.trainerBlacklist) bl.add(s);
        o.add("trainer_blacklist", bl);
        JsonArray trs = new JsonArray();
        for (RCTTrainerConfig t : sec.trainers) {
            JsonObject to = new JsonObject();
            to.addProperty("id", t.id);
            if (!t.preset.isBlank()) to.addProperty("preset", t.preset);
            JsonArray tags = new JsonArray();
            for (String tg : t.tags) tags.add(tg);
            to.add("tags", tags);
            trs.add(to);
        }
        o.add("trainers", trs);
        return o;
    }
}
