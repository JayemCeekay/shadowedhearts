package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Snag Machine settings under [snag] in the shared common.toml spec.
 */
public final class SnagConfig implements ISnagConfig {
    private static final Data DATA = new Data();

    static void build(ModConfigSpec.Builder builder) {
        DATA.build(builder);
    }

    @Override
    public int toggleCooldownTicks() {
        if (!isLoaded()) return ISnagConfig.super.toggleCooldownTicks();
        return DATA.toggleCooldownTicks.get();
    }

    @Override
    public boolean deferDeliveryUntilVictory() {
        return isLoaded() ? DATA.deferDeliveryUntilVictory.get() : ISnagConfig.super.deferDeliveryUntilVictory();
    }

    @Override
    public boolean failStackingBonus() {
        if (!isLoaded()) return ISnagConfig.super.failStackingBonus();
        return DATA.snaggingPity.failStackingBonus.get();
    }

    @Override
    public double failBonusPerAttempt() {
        if (!isLoaded()) return ISnagConfig.super.failBonusPerAttempt();
        return DATA.snaggingPity.failBonusPerAttempt.get();
    }

    @Override
    public double maxFailBonus() {
        if (!isLoaded()) return ISnagConfig.super.maxFailBonus();
        return DATA.snaggingPity.maxFailBonus.get();
    }

    @Override
    public ModConfigSpec getSpec() {
        return ModConfig.SPEC;
    }

    private static final class Data {
        public ModConfigSpec.IntValue toggleCooldownTicks;
        public ModConfigSpec.BooleanValue deferDeliveryUntilVictory;
        public final SnaggingPityConfig snaggingPity = new SnaggingPityConfig();

        private void build(ModConfigSpec.Builder builder) {
            deferDeliveryUntilVictory = builder
                    .comment("Hold newly snagged NPC Pokemon outside the party/PC until the capturing player's side wins that battle. Losing, fleeing or stopping the battle grants no queued Pokemon. The policy is fixed when each snag is attempted.")
                    .translation("shadowedhearts.configuration.snag.deferDeliveryUntilVictory")
                    .define("deferDeliveryUntilVictory", false);
            toggleCooldownTicks = builder
                    .comment("Cooldown in ticks (20 ticks = 1 second) between toggling the Snag Machine on or off.")
                    .translation("shadowedhearts.configuration.snag.toggle_cooldown_ticks")
                    .defineInRange("toggle_cooldown_ticks", 20, 0, 1200);

            builder.translation("shadowedhearts.configuration.snag.snagging_pity").push("snagging_pity");
            snaggingPity.build(builder);
            builder.pop();
        }
    }

    public static final class SnaggingPityConfig {
        public ModConfigSpec.BooleanValue failStackingBonus;
        public ModConfigSpec.DoubleValue failBonusPerAttempt;
        public ModConfigSpec.DoubleValue maxFailBonus;

        private void build(ModConfigSpec.Builder builder) {
            failStackingBonus = builder
                    .comment("If true, each failed snag attempt increases the success chance of the next attempt.")
                    .translation("shadowedhearts.configuration.snag.snagging_pity.failStackingBonus")
                    .define("failStackingBonus", true);

            failBonusPerAttempt = builder
                    .comment("The bonus added to the catch rate for each failed snag attempt.")
                    .translation("shadowedhearts.configuration.snag.snagging_pity.failBonusPerAttempt")
                    .defineInRange("failBonusPerAttempt", 0.05, 0.0, 1.0);

            maxFailBonus = builder
                    .comment("The maximum bonus that can be accumulated from failed snag attempts.")
                    .translation("shadowedhearts.configuration.snag.snagging_pity.maxFailBonus")
                    .defineInRange("maxFailBonus", 0.25, 0.0, 1.0);
        }
    }

    @Override
    public void load() {
        Shadowedhearts.LOGGER.info("SnagConfig loaded...");
    }

    @Override
    public boolean isLoaded() {
        return ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded();
    }
}
