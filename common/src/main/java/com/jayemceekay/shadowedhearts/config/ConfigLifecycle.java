package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorConfigBinding;
import com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService;
import com.jayemceekay.shadowedhearts.config.preset.RulesPresetService;
import net.neoforged.fml.config.IConfigSpec;

import java.nio.file.Path;
import java.util.function.Supplier;

/** Shared config behavior; each loader owns event subscriptions and registration timing. */
public final class ConfigLifecycle {
    private ConfigLifecycle() {}

    /** Applies both initial loads and reloads of the authoritative gameplay configuration. */
    public static void loadGameplay(IConfigSpec config, Supplier<Path> source) {
        ConfigSnapshotService.loaded(config, source);
        if (config == ModConfig.SPEC) {
            bindGameplaySource(source);
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            Shadowedhearts.injectShowdownConfig();
        } else if (config == ServerConfig.SPEC) {
            ServerConfig.INSTANCE.load();
        }
    }

    public static void unloadGameplay(IConfigSpec config) {
        ConfigSnapshotService.unloaded(config);
        if (config == ModConfig.SPEC) {
            TrainerEditorConfigBinding.bind(null);
            RulesPresetService.bind(null);
            HeartGaugeConfig.reset();
        }
    }

    /** Called only by client entrypoints, after the loader has supplied its config values. */
    public static void loadClient(IConfigSpec config, Supplier<Path> source) {
        if (config == ClientConfig.SPEC) {
            ConfigSnapshotService.loaded(config, source);
            ShadowedHeartsConfigs.getInstance().getClientConfig().load();
        }
    }

    private static void bindGameplaySource(Supplier<Path> source) {
        try {
            TrainerEditorConfigBinding.bind(source.get());
            RulesPresetService.bind(source.get());
            HeartGaugeConfig.setLegacyFallbackAllowed(true);
        } catch (IllegalStateException nonFileConfig) {
            // Remote server config snapshots have no local backing file.
            TrainerEditorConfigBinding.bind(null);
            RulesPresetService.bind(null);
            HeartGaugeConfig.setLegacyFallbackAllowed(false);
        }
    }
}
