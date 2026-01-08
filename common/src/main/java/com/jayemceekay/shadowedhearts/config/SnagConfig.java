package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Snag Machine configuration using ModConfigSpec.
 */
public final class SnagConfig implements ISnagConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        SPEC = builder.build();
    }

    @Override
    public int energyPerAttempt() {
        return DATA.energyPerAttempt.get();
    }

    @Override
    public int toggleCooldownTicks() {
        return DATA.toggleCooldownTicks.get();
    }

    @Override
    public boolean rechargeOnVictory() {
        return DATA.rechargeOnVictory.get();
    }

    @Override
    public boolean rechargeInPvp() {
        return DATA.rechargeInPvp.get();
    }

    @Override
    public int rechargeBase() {
        return DATA.rechargeBase.get();
    }

    @Override
    public double rechargePerLevel() {
        return DATA.rechargePerLevel.get();
    }

    @Override
    public int rechargePerNpc() {
        return DATA.rechargePerNpc.get();
    }

    @Override
    public int rechargeMin() {
        return DATA.rechargeMin.get();
    }

    @Override
    public int rechargeMax() {
        return DATA.rechargeMax.get();
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    private static final class Data {
        public ModConfigSpec.IntValue energyPerAttempt;
        public ModConfigSpec.IntValue toggleCooldownTicks;

        public ModConfigSpec.BooleanValue rechargeOnVictory;
        public ModConfigSpec.BooleanValue rechargeInPvp;
        public ModConfigSpec.IntValue rechargeBase;
        public ModConfigSpec.DoubleValue rechargePerLevel;
        public ModConfigSpec.IntValue rechargePerNpc;
        public ModConfigSpec.IntValue rechargeMin;
        public ModConfigSpec.IntValue rechargeMax;

        private void build(ModConfigSpec.Builder builder) {
            energyPerAttempt = builder.defineInRange("energy_per_attempt", 50, 0, 1000);
            toggleCooldownTicks = builder.defineInRange("toggle_cooldown_ticks", 20, 0, 1200);

            builder.push("recharge");
            rechargeOnVictory = builder.define("on_victory", true);
            rechargeInPvp = builder.define("in_pvp", false);
            rechargeBase = builder.defineInRange("base", 10, 0, 1000);
            rechargePerLevel = builder.defineInRange("per_level", 0.25, 0.0, 100.0);
            rechargePerNpc = builder.defineInRange("per_npc", 3, 0, 100);
            rechargeMin = builder.defineInRange("min", 5, 0, 1000);
            rechargeMax = builder.defineInRange("max", 15, 0, 1000);
            builder.pop();
        }
    }

    @Override
    public void load() {
        loaded = true;
        System.out.println("[ShadowedHearts] SnagConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
