package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/** Server-synchronized Infuser policy, registered as part of common.toml. */
public final class InfuserConfig {
    public static final InfuserConfig INSTANCE = new InfuserConfig();
    public static final int MAX_RESERVOIR_CAPACITY = 1024;
    public static final int MAX_EXPANSIONS = 8;
    private ModConfigSpec spec;
    private ModConfigSpec.BooleanValue pokemonEnabled, itemsEnabled;
    private ModConfigSpec.IntValue baseCost, costPerLevel, rareCost, baseTicks, ticksPerLevel,
            reservoirCapacity, maxExpansions;
    private ModConfigSpec.DoubleValue ticksPerGaugePoint;
    private ModConfigSpec.ConfigValue<List<? extends String>> speciesWhitelist, speciesBlacklist;

    public void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.infuser").push("infuser");
        pokemonEnabled = builder.comment("Allow new Pokemon deposits and conversion. Stored Pokemon can always be retrieved; disabled jobs stop without spending charge.")
                .translation("shadowedhearts.configuration.infuser.pokemonEnabled").define("pokemonEnabled", true);
        itemsEnabled = builder.comment("Allow item deposits and processing. Existing inputs and outputs remain retrievable.")
                .translation("shadowedhearts.configuration.infuser.itemsEnabled").define("itemsEnabled", true);
        speciesWhitelist = builder.comment("Allowed Pokemon species IDs or #species tags. Empty allows all otherwise eligible species. Blacklist wins.")
                .translation("shadowedhearts.configuration.infuser.speciesWhitelist")
                .defineListAllowEmpty("speciesWhitelist", List.of(), () -> "cobblemon:bulbasaur", InfuserConfig::validSelector);
        speciesBlacklist = builder.comment("Pokemon species IDs or #species tags excluded from infusion. Does not prevent retrieval.")
                .translation("shadowedhearts.configuration.infuser.speciesBlacklist")
                .defineListAllowEmpty("speciesBlacklist", List.of(), () -> "#shadowedhearts:legendaries", InfuserConfig::validSelector);
        baseCost = integer(builder, "baseCost", "Base Pokemon conversion charge. Final cost is at least 1 UE.", 32, 0, 9216);
        costPerLevel = integer(builder, "costPerLevel", "Additional charge per level, using level 1-100.", 2, 0, 9216);
        rareCost = integer(builder, "legendaryMythicalExtraCost", "Extra charge for Legendary or Mythical Pokemon.", 128, 0, 9216);
        baseTicks = integer(builder, "baseDurationTicks", "Base Pokemon conversion duration. Final duration is limited to 1-72000 ticks. Existing started jobs retain their saved duration.", 900, 0, 72000);
        ticksPerLevel = integer(builder, "durationTicksPerLevel", "Additional conversion ticks per level (20 ticks per second).", 12, 0, 72000);
        ticksPerGaugePoint = builder.comment("Additional conversion ticks per maximum Heart Gauge point, rounded up. Default 0.05 is 2.5 seconds per 1000 points.")
                .translation("shadowedhearts.configuration.infuser.durationTicksPerGaugePoint")
                .defineInRange("durationTicksPerGaugePoint", 0.05, 0.0, 100.0);
        reservoirCapacity = integer(builder, "reservoirCapacity", "Charge capacity of each reservoir. Lowering this retains surplus saved energy but limits the energy usable at once; surplus drains before new fuel fits.", 64, 1, MAX_RESERVOIR_CAPACITY);
        maxExpansions = integer(builder, "maxExpansions", "Maximum connected tanks. Disconnected tanks retain their stored energy.", 8, 0, MAX_EXPANSIONS);
        builder.pop();
    }

    private static ModConfigSpec.IntValue integer(ModConfigSpec.Builder b, String key, String comment, int value, int min, int max) {
        return b.comment(comment).translation("shadowedhearts.configuration.infuser." + key).defineInRange(key, value, min, max);
    }

    public void bind(ModConfigSpec spec) { this.spec = spec; }
    private boolean loaded() { return spec != null && spec.isLoaded(); }
    public boolean pokemonEnabled() { return !loaded() || pokemonEnabled.get(); }
    public boolean itemsEnabled() { return !loaded() || itemsEnabled.get(); }
    public List<? extends String> speciesWhitelist() { return loaded() ? speciesWhitelist.get() : List.of(); }
    public List<? extends String> speciesBlacklist() { return loaded() ? speciesBlacklist.get() : List.of(); }
    public int reservoirCapacity() { return loaded() ? reservoirCapacity.get() : 64; }
    public int maxExpansions() { return loaded() ? maxExpansions.get() : 8; }
    public int networkCapacity() { return reservoirCapacity() * (maxExpansions() + 1); }
    public PokemonSettings pokemonSettings() {
        return loaded() ? new PokemonSettings(baseCost.get(), costPerLevel.get(), rareCost.get(),
                baseTicks.get(), ticksPerLevel.get(), ticksPerGaugePoint.get()) : PokemonSettings.DEFAULT;
    }

    public static boolean validSelector(Object value) {
        if (!(value instanceof String raw)) return false;
        String selector = raw.startsWith("#") ? raw.substring(1) : raw;
        return selector.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    }

    public record PokemonSettings(int baseCost, int costPerLevel, int rareCost,
                                  int baseTicks, int ticksPerLevel, double ticksPerGaugePoint) {
        public static final PokemonSettings DEFAULT = new PokemonSettings(32, 2, 128, 900, 12, 0.05);
        public int cost(int level, boolean rare) {
            long result = (long) baseCost + (long) Math.clamp(level, 1, 100) * costPerLevel + (rare ? rareCost : 0);
            // May deliberately exceed network capacity, making this Pokemon too expensive to convert.
            return (int) Math.clamp(result, 1L, Integer.MAX_VALUE);
        }
        public int duration(int level, int gauge) {
            double coefficient = Double.isFinite(ticksPerGaugePoint) ? Math.max(0, ticksPerGaugePoint) : 0.05;
            double result = baseTicks + (long) Math.clamp(level, 1, 100) * ticksPerLevel
                    + Math.ceil(Math.max(1L, gauge) * coefficient);
            return (int) Math.clamp(result, 1, 72000);
        }
    }
}
