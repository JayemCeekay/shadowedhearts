package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
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
        public ModConfigSpec.BooleanValue auraScannerEnabled;

        private void build(ModConfigSpec.Builder builder) {
            enableShadowAura = builder
                    .comment("Master toggle for client-side Shadow aura rendering.")
                    .define("enableShadowAura", true);

            auraScannerEnabled = builder
                    .comment("Whether the Aura Scanner HUD is enabled.")
                    .define("auraScannerEnabled", true);
        }
    }

    @Override
    public boolean enableShadowAura() {
        return DATA.enableShadowAura.get();
    }

    @Override
    public boolean auraScannerEnabled() {
        return DATA.auraScannerEnabled.get();
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    @Override
    public void load() {
        loaded = true;
        Shadowedhearts.LOGGER.info("ClientConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
