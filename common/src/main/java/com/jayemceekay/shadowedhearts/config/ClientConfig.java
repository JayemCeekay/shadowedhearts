package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only config for visual toggles using ModConfigSpec.
 */
public final class ClientConfig implements IClientConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        SPEC = builder.build();
    }

    private static final class Data {
        public ModConfigSpec.BooleanValue enableShadowAura;
        public ModConfigSpec.BooleanValue useXdAura;

        private void build(ModConfigSpec.Builder builder) {
            enableShadowAura = builder
                    .comment("Master toggle for client-side Shadow aura rendering.")
                    .define("enableShadowAura", true);

            useXdAura = builder
                    .comment("Whether to use the XD-style (filament) aura instead of the Colosseum-style (fog) aura.")
                    .define("useXdAura", false);
        }
    }

    @Override
    public boolean enableShadowAura() {
        return DATA.enableShadowAura.get();
    }

    @Override
    public boolean useXdAura() {
        return DATA.useXdAura.get();
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    @Override
    public void load() {
        loaded = true;
        System.out.println("[ShadowedHearts] ClientConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
