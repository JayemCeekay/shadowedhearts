package com.jayemceekay.shadowedhearts.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Minimal config system for Shadowed Hearts.
 * Stores a small set of key/value pairs in config/shadowedhearts.properties
 * across all loaders (Fabric/NeoForge) using the current run directory.
 */
public final class ModConfig {
    private ModConfig() {}

    private static final String FILE_NAME = "shadowedhearts.properties";
    private static final String KEY_SHOWDOWN_PATCHED = "showdownPatched";
    private static final String KEY_TOSS_ORDER_BAR_UI = "tossOrderBarUI";

    public static final class Data {
        public boolean showdownPatched = false;
        /** If true, use the alternate bottom-bar Toss Order UI instead of the radial wheel. */
        public boolean tossOrderBarUI = true;
    }

    private static final Data DATA = new Data();

    public static Data get() {
        return DATA;
    }

    public static void load() {
        Path configDir = Paths.get("config");
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return; // defaults
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            DATA.showdownPatched = Boolean.parseBoolean(props.getProperty(KEY_SHOWDOWN_PATCHED, Boolean.toString(DATA.showdownPatched)));
            DATA.tossOrderBarUI = Boolean.parseBoolean(props.getProperty(KEY_TOSS_ORDER_BAR_UI, Boolean.toString(DATA.tossOrderBarUI)));
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        Path configDir = Paths.get("config");
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.isDirectory(configDir)) {
                Files.createDirectories(configDir);
            }
            Properties props = new Properties();
            props.setProperty(KEY_SHOWDOWN_PATCHED, Boolean.toString(DATA.showdownPatched));
            props.setProperty(KEY_TOSS_ORDER_BAR_UI, Boolean.toString(DATA.tossOrderBarUI));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Shadowed Hearts configuration");
            }
        } catch (IOException ignored) {
        }
    }
}
