package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
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
    public boolean auraReaderRechargeOnVictory() {
        return DATA.auraReaderRechargeOnVictory.get();
    }

    @Override
    public boolean auraReaderRechargeInPvp() {
        return DATA.auraReaderRechargeInPvp.get();
    }

    @Override
    public int auraReaderRechargeBase() {
        return DATA.auraReaderRechargeBase.get();
    }

    @Override
    public double auraReaderRechargePerLevel() {
        return DATA.auraReaderRechargePerLevel.get();
    }

    @Override
    public int auraReaderRechargePerNpc() {
        return DATA.auraReaderRechargePerNpc.get();
    }

    @Override
    public int auraReaderRechargeMin() {
        return DATA.auraReaderRechargeMin.get();
    }

    @Override
    public int auraReaderRechargeMax() {
        return DATA.auraReaderRechargeMax.get();
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

        public ModConfigSpec.BooleanValue auraReaderRechargeOnVictory;
        public ModConfigSpec.BooleanValue auraReaderRechargeInPvp;
        public ModConfigSpec.IntValue auraReaderRechargeBase;
        public ModConfigSpec.DoubleValue auraReaderRechargePerLevel;
        public ModConfigSpec.IntValue auraReaderRechargePerNpc;
        public ModConfigSpec.IntValue auraReaderRechargeMin;
        public ModConfigSpec.IntValue auraReaderRechargeMax;

        private void build(ModConfigSpec.Builder builder) {
            energyPerAttempt = builder
                    .comment("The amount of energy consumed by the Snag Machine for each snag attempt.")
                    .defineInRange("energy_per_attempt", 50, 0, 1000);
            
            toggleCooldownTicks = builder
                    .comment("Cooldown in ticks (20 ticks = 1 second) between toggling the Snag Machine on or off.")
                    .defineInRange("toggle_cooldown_ticks", 20, 0, 1200);
            

            builder.push("recharge");
            rechargeOnVictory = builder
                    .comment("If true, the Snag Machine recharges energy when the player wins a battle.")
                    .define("on_victory", true);
            
            rechargeInPvp = builder
                    .comment("If true, the Snag Machine recharges energy when the player wins a PvP battle.")
                    .define("in_pvp", false);
            
            rechargeBase = builder
                    .comment("The base amount of energy recharged on victory.")
                    .defineInRange("base", 10, 0, 1000);
            
            rechargePerLevel = builder
                    .comment("Additional energy recharged per level of the defeated Pokémon.")
                    .defineInRange("per_level", 0.25, 0.0, 100.0);
            
            rechargePerNpc = builder
                    .comment("Additional energy recharged per Pokémon in the NPC's party.")
                    .defineInRange("per_npc", 3, 0, 100);
            
            rechargeMin = builder
                    .comment("The minimum energy recharged on victory.")
                    .defineInRange("min", 5, 0, 1000);
            
            rechargeMax = builder
                    .comment("The maximum energy recharged on victory.")
                    .defineInRange("max", 15, 0, 1000);
            builder.pop();
            

            builder.push("aura_reader_recharge");
            auraReaderRechargeOnVictory = builder
                    .comment("If true, the Aura Reader recharges energy when the player wins a battle.")
                    .define("on_victory", true);
            
            auraReaderRechargeInPvp = builder
                    .comment("If true, the Aura Reader recharges energy when the player wins a PvP battle.")
                    .define("in_pvp", false);
            
            auraReaderRechargeBase = builder
                    .comment("The base amount of energy recharged for the Aura Reader on victory.")
                    .defineInRange("base", 200, 0, 12000);
            
            auraReaderRechargePerLevel = builder
                    .comment("Additional energy recharged for the Aura Reader per level of the defeated Pokémon.")
                    .defineInRange("per_level", 5.0, 0.0, 1000.0);
            
            auraReaderRechargePerNpc = builder
                    .comment("Additional energy recharged for the Aura Reader per Pokémon in the NPC's party.")
                    .defineInRange("per_npc", 60, 0, 12000);
            
            auraReaderRechargeMin = builder
                    .comment("The minimum energy recharged for the Aura Reader on victory.")
                    .defineInRange("min", 100, 0, 12000);
            
            auraReaderRechargeMax = builder
                    .comment("The maximum energy recharged for the Aura Reader on victory.")
                    .defineInRange("max", 3000, 0, 12000);
            builder.pop();
        }
    }

    @Override
    public void load() {
        loaded = true;
        Shadowedhearts.LOGGER.info("SnagConfig loaded...");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
