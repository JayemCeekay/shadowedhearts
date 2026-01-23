package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default boolean enableShadowAura() { return true; }
    default boolean auraScannerEnabled() { return true; }
    default boolean skipIrisWarning() { return false; }
    void setSkipIrisWarning(boolean value);
}
