package com.jayemceekay.shadowedhearts.client.util;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IrisWarningUtil {
    private IrisWarningUtil() {}

    public static boolean shouldShowWarning() {
        if (!Platform.isModLoaded("iris")) {
            return false;
        }

        Path configDir = Platform.getConfigFolder();
        Path irisConfig = configDir.resolve("iris.properties");

        if (!Files.exists(irisConfig)) {
            // Iris is installed but its config file is missing.
            // Show warning by default.
            return true;
        }

        try {
            List<String> lines = Files.readAllLines(irisConfig);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equals("allowUnknownShaders=true")) {
                    return false;
                }
            }
        } catch (IOException ignored) {
            // On read failure, fail open or fail safe depending on your preference.
            return true;
        }

        return true;
    }
}
