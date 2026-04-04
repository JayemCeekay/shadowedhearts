package com.jayemceekay.shadowedhearts.config;

public final class ShadowedHeartsConfigs {
    private static volatile ShadowedHeartsConfigs INSTANCE;

    private final IClientConfig clientConfig;
    private final IShadowConfig shadowConfig;
    private final ISnagConfig snagConfig;

    private ShadowedHeartsConfigs(
            IClientConfig clientConfig,
            IShadowConfig shadowConfig,
            ISnagConfig snagConfig) {
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
        if (INSTANCE == null) {
            synchronized (ShadowedHeartsConfigs.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ShadowedHeartsConfigs(
                            new ClientConfig(), new ModConfig(), new SnagConfig());
                }
            }
        }
        return INSTANCE;
    }
}
