package com.jayemceekay.shadowedhearts.config;

import java.util.function.Supplier;

public final class ShadowedHeartsConfigs {
    private static Supplier<ShadowedHeartsConfigs> instanceSupplier = () -> {
        var defaultInstance = new ShadowedHeartsConfigs(
            new ClientConfig(), new ModConfig(), new SnagConfig(), new TrainerSpawnConfig());

        ShadowedHeartsConfigs.instanceSupplier = () -> defaultInstance;
        return defaultInstance;
    };

    private final IClientConfig clientConfig;
    private final IShadowConfig shadowConfig;
    private final ISnagConfig snagConfig;
    private final ITrainerSpawnConfig trainerSpawnConfig;

    private ShadowedHeartsConfigs(
        IClientConfig clientConfig,
        IShadowConfig shadowConfig,
        ISnagConfig snagConfig,
        ITrainerSpawnConfig trainerSpawnConfig)
    {
        this.clientConfig = clientConfig;
        this.shadowConfig = shadowConfig;
        this.snagConfig = snagConfig;
        this.trainerSpawnConfig = trainerSpawnConfig;
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

    public ITrainerSpawnConfig getTrainerSpawnConfig() {
        return this.trainerSpawnConfig;
    }

    public static ShadowedHeartsConfigs getInstance() {
        return ShadowedHeartsConfigs.instanceSupplier.get();
    }
}
