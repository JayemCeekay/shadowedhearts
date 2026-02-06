package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default boolean enableShadowAura() { return true; }
    default boolean auraScannerEnabled() { return true; }
    default boolean skipIrisWarning() { return false; }
    void setSkipIrisWarning(boolean value);
    default float auraReaderYOffset() { return -0.15f; }
    ISoundConfig soundConfig();
}
