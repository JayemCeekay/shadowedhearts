package com.jayemceekay.shadowedhearts.config;

import java.util.function.Supplier;

public final class ShadowedHeartsConfigs {
    private static Supplier<ShadowedHeartsConfigs> instanceSupplier = () -> {
        var defaultInstance = new ShadowedHeartsConfigs(
            new ClientConfig(), new ModConfig(), new SnagConfig());

        ShadowedHeartsConfigs.instanceSupplier = () -> defaultInstance;
        return defaultInstance;
    };

    private final IClientConfig clientConfig;
    private final IShadowConfig shadowConfig;
    private final ISnagConfig snagConfig;

    private ShadowedHeartsConfigs(
        IClientConfig clientConfig,
        IShadowConfig shadowConfig,
        ISnagConfig snagConfig)
    {
        this.clientConfig = clientConfig;
        this.shadowConfig = shadowConfig;
        this.snagConfig = snagConfig;
    }

    public IClientConfig getClientConfig() {
        return this.clientConfig;
    }

    public IShadowConfig getShadowConfig() {
        return this.shadowConfig;
    }

    public ISnagConfig getSnagConfig() {
        return this.snagConfig;
    }


    public static ShadowedHeartsConfigs getInstance() {
        return ShadowedHeartsConfigs.instanceSupplier.get();
    }
}
