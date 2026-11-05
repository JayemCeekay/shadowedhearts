package com.jayemceekay.shadowedhearts.config;

import java.nio.file.Path;

/** Keeps the loader's actual config/template locations for the pre-server-load migration. */
public final class ConfigMigrationBootstrap {
    private static volatile Locations locations;

    private ConfigMigrationBootstrap() {}

    public static void initialize(Path configDirectory, Path defaultConfigDirectory) {
        ConfigFileMigration.migrateGlobal(configDirectory, defaultConfigDirectory);
        locations = new Locations(configDirectory, defaultConfigDirectory);
    }

    public static void beforeServerStart(Path worldServerConfigDirectory) {
        Locations current = locations;
        if (current == null) {
            throw new IllegalStateException("Shadowed Hearts config migration was not initialized");
        }
        ConfigFileMigration.migrateWorld(
                current.configDirectory(), current.defaultConfigDirectory(), worldServerConfigDirectory);
    }

    private record Locations(Path configDirectory, Path defaultConfigDirectory) {}
}
