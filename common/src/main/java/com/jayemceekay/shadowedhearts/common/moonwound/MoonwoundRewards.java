package com.jayemceekay.shadowedhearts.common.moonwound;

import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Personal event materials, resolved from the server's current datapack loot tables. */
public final class MoonwoundRewards {
    private MoonwoundRewards() { }

    static List<ResourceKey<LootTable>> tablesFor(UmbrafallTier tier, MoonwoundEvent.Outcome outcome,
                                                 boolean overloadSucceeded) {
        Objects.requireNonNull(tier, "tier");
        String path = switch (outcome) {
            case HARVEST -> "harvest";
            case SEAL -> "seal";
            case OVERLOAD -> overloadSucceeded ? "overload" : "overload_failure";
        };
        boolean successful = outcome != MoonwoundEvent.Outcome.OVERLOAD || overloadSucceeded;
        if (tier != UmbrafallTier.RESONANT || !successful) return List.of(table(path));
        return List.of(table(path), table(outcome == MoonwoundEvent.Outcome.OVERLOAD
                ? "resonant_overload_bonus" : "resonant_bonus"));
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("shadowedhearts", "moonwound/" + path));
    }

    /** Roll everything before recording the claim, so a missing table never consumes a player's reward. */
    public static List<ItemStack> roll(ServerLevel level, BlockPos core, UmbrafallTier tier,
                                       MoonwoundEvent event, ServerPlayer player) {
        if (event.phase() != MoonwoundEvent.Phase.COMPLETE) {
            throw new IllegalArgumentException("Cannot reward an unfinished Moonwound");
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(core))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        var registry = level.getServer().reloadableRegistries().get().registryOrThrow(Registries.LOOT_TABLE);
        List<ItemStack> rewards = new ArrayList<>();
        for (ResourceKey<LootTable> key : tablesFor(tier, event.outcome(), event.overloadSucceeded())) {
            LootTable loot = registry.getHolder(key).orElseThrow(() ->
                    new IllegalStateException("Missing Moonwound reward loot table: " + key.location())).value();
            validate(key, loot, level.getServer().reloadableRegistries().lookup());
            rewards.addAll(loot.getRandomItems(params, seed(event.eventId(), player.getUUID(), key)));
        }
        return rewards;
    }

    /** Nested categories must resolve too; vanilla otherwise logs a missing reference and yields no items. */
    static void validate(ResourceKey<LootTable> key, LootTable loot, HolderGetter.Provider resolver) {
        var problems = new ProblemReporter.Collector();
        loot.validate(new ValidationContext(problems, LootContextParamSets.GIFT, resolver)
                .enterElement("{" + key.location() + "}", key));
        if (!problems.get().isEmpty()) {
            throw new IllegalStateException("Invalid Moonwound reward loot table " + key.location()
                    + ": " + problems.getReport().orElse("Unknown loot validation error"));
        }
    }

    /** Stable per event, recipient and table, including retries before a claim can be delivered. */
    static long seed(UUID event, UUID player, ResourceKey<LootTable> table) {
        long seed = UUID.nameUUIDFromBytes((event + "/" + player + "/" + table.location())
                .getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits();
        return seed == LootTable.RANDOMIZE_SEED ? 1 : seed;
    }
}
