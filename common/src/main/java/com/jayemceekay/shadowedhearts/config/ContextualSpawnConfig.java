package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.common.shadow.ContextualSpawnRules;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Additional common.toml rules used only by automatic wild Shadow conversion. */
public final class ContextualSpawnConfig {
    public static final ContextualSpawnConfig INSTANCE = new ContextualSpawnConfig();
    private ModConfigSpec.ConfigValue<List<? extends String>> dimensionAllowlist;
    private ModConfigSpec.ConfigValue<List<? extends String>> dimensionDenylist;
    private ModConfigSpec.ConfigValue<List<? extends String>> biomeAllowlist;
    private ModConfigSpec.ConfigValue<List<? extends String>> biomeDenylist;
    private ModConfigSpec.ConfigValue<List<? extends String>> speciesChanceOverrides;
    private ModConfigSpec.IntValue umbrafallHorizontalRadius;
    private ModConfigSpec.IntValue umbrafallVerticalRadius;
    private ModConfigSpec ownerSpec;

    ContextualSpawnConfig() { }

    public void bind(ModConfigSpec spec) { ownerSpec = spec; }
    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }

    public void build(ModConfigSpec.Builder builder) {
        builder.comment("Automatic wild Shadow conversion only. Explicitly authored Shadow Pokemon remain valid. Species blacklist and dimension/biome denylists always win; empty allowlists allow every location.")
                .translation("shadowedhearts.configuration.contextualSpawn").push("contextualSpawn");
        dimensionAllowlist = builder.comment("Dimension IDs allowed for automatic conversion, for example minecraft:overworld. Empty allows every dimension. Dimension tags are not supported.")
                .translation("shadowedhearts.configuration.contextualSpawn.dimensionAllowlist")
                .defineListAllowEmpty("dimensionAllowlist", List.of(), () -> "minecraft:overworld", value -> ContextualSpawnRules.validIdentifier(value, false));
        dimensionDenylist = builder.comment("Dimension IDs excluded from automatic conversion, even when also allowed.")
                .translation("shadowedhearts.configuration.contextualSpawn.dimensionDenylist")
                .defineListAllowEmpty("dimensionDenylist", List.of(), () -> "minecraft:the_end", value -> ContextualSpawnRules.validIdentifier(value, false));
        biomeAllowlist = builder.comment("Biome IDs or #biome_tags allowed for automatic conversion. Empty allows every biome.")
                .translation("shadowedhearts.configuration.contextualSpawn.biomeAllowlist")
                .defineListAllowEmpty("biomeAllowlist", List.of(), () -> "#minecraft:is_forest", value -> ContextualSpawnRules.validIdentifier(value, true));
        biomeDenylist = builder.comment("Biome IDs or #biome_tags excluded from automatic conversion, even when also allowed.")
                .translation("shadowedhearts.configuration.contextualSpawn.biomeDenylist")
                .defineListAllowEmpty("biomeDenylist", List.of(), () -> "minecraft:plains", value -> ContextualSpawnRules.validIdentifier(value, true));
        speciesChanceOverrides = builder.comment("Ordered species-or-#species_tag=percent rules, for example cobblemon:eevee=2.5 or #shadowedhearts:legendaries=0. The first matching rule replaces the global percentage, then the Umbrafall multiplier applies. Values are always percentages (0-100), with an optional % suffix. The existing species blacklist still wins.")
                .translation("shadowedhearts.configuration.contextualSpawn.speciesChanceOverrides")
                .defineListAllowEmpty("speciesChanceOverrides", List.of(), () -> "cobblemon:eevee=2.5", ContextualSpawnRules::validChanceOverride);
        umbrafallHorizontalRadius = builder.comment("Horizontal range in blocks for the nearby active Umbrafall spawn multiplier. Zero disables the multiplier.")
                .translation("shadowedhearts.configuration.contextualSpawn.umbrafallHorizontalRadius")
                .defineInRange("umbrafallHorizontalRadius", 48, 0, 256);
        umbrafallVerticalRadius = builder.comment("Vertical range in blocks for the nearby active Umbrafall spawn multiplier. Zero disables the multiplier.")
                .translation("shadowedhearts.configuration.contextualSpawn.umbrafallVerticalRadius")
                .defineInRange("umbrafallVerticalRadius", 48, 0, 256);
        builder.pop();
    }

    public List<? extends String> dimensionAllowlist() { return loaded() ? dimensionAllowlist.get() : List.of(); }
    public List<? extends String> dimensionDenylist() { return loaded() ? dimensionDenylist.get() : List.of(); }
    public List<? extends String> biomeAllowlist() { return loaded() ? biomeAllowlist.get() : List.of(); }
    public List<? extends String> biomeDenylist() { return loaded() ? biomeDenylist.get() : List.of(); }
    public List<? extends String> speciesChanceOverrides() { return loaded() ? speciesChanceOverrides.get() : List.of(); }
    public int umbrafallHorizontalRadius() { return loaded() ? umbrafallHorizontalRadius.get() : 48; }
    public int umbrafallVerticalRadius() { return loaded() ? umbrafallVerticalRadius.get() : 48; }
}
