package com.jayemceekay.shadowedhearts.client.gui.config;

import java.util.List;

/** Menu topics for common.toml. The file's section names remain independent of its presentation. */
final class GameplaySettingsLayout {
    static final List<String> ORDER = List.of("gameplay_spawns", "gameplay_aura", "gameplay_snagging",
            "gameplay_capture", "gameplay_battles", "gameplay_modes", "gameplay_moves", "gameplay_gauge",
            "gameplay_growth", "gameplay_restrictions", "gameplay_purification", "gameplay_infuser",
            "gameplay_items", "gameplay_trainers", "gameplay_other");

    private GameplaySettingsLayout() {}

    static String group(List<String> path) {
        String setting = path.getLast();
        return switch (path.getFirst()) {
            case "shadowSpawn", "contextualSpawn" -> "gameplay_spawns";
            case "shadowAuraStyle", "auraScanner" -> "gameplay_aura";
            case "snag", "snagMachines" -> "gameplay_snagging";
            case "umbraBall", "darkBall" -> "gameplay_capture";
            case "shadowStatChanges" -> setting.startsWith("catchRate") ? "gameplay_capture" : "gameplay_growth";
            case "goDamageModifier", "shadowGimmickRestrictions" -> "gameplay_battles";
            case "hyperMode", "reverseMode", "callButton" -> "gameplay_modes";
            case "shadowBattleRules" -> switch (setting) {
                case "shadowRushVariant", "unlimitedShadowPP" -> "gameplay_moves";
                default -> "gameplay_modes";
            };
            case "shadowMoves", "moveLifecycle" -> "gameplay_moves";
            case "orreProgression" -> "gameplay_gauge";
            case "heartGauge" -> switch (setting) {
                case "maxOverrides" -> "gameplay_gauge";
                case "bufferExperience", "bufferEVs", "blockFriendshipChanges" -> "gameplay_growth";
                default -> "gameplay_restrictions";
            };
            case "gameplayTuning" -> switch (path.get(1)) {
                case "features" -> setting.equals("snaggingEnabled") ? "gameplay_snagging" : "gameplay_purification";
                case "progression" -> "gameplay_gauge";
                default -> "gameplay_other";
            };
            case "progressionEconomy" -> switch (setting) {
                case "careTable" -> "gameplay_gauge";
                case "scentsClearModes" -> "gameplay_purification";
                default -> "gameplay_growth";
            };
            case "purificationChamber", "relicStone", "scent" -> "gameplay_purification";
            case "infuser" -> "gameplay_infuser";
            case "compatibilityItems" -> switch (setting) {
                case "darkBallCorruptionEnabled" -> "gameplay_capture";
                case "expandedScentItemsEnabled" -> "gameplay_purification";
                case "shadowTMsEnabled", "shadowTMAllowedMoves" -> "gameplay_moves";
                default -> "gameplay_items";
            };
            case "modIntegrations" -> path.get(1).equals("rctmod") ? "gameplay_trainers" : "gameplay_other";
            default -> "gameplay_other";
        };
    }
}
