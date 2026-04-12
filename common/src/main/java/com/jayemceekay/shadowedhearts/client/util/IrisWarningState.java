package com.jayemceekay.shadowedhearts.client.util;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IrisWarningState {
    private static final Path FILE =
            Platform.getConfigFolder().resolve("shadowedhearts-iris-warning.txt");

    private IrisWarningState() {}

    public static boolean skipWarning() {
        try {
            if (!Files.exists(FILE)) {
                return false;
            }
            return Files.readString(FILE).trim().equalsIgnoreCase("skip=true");
        } catch (IOException ignored) {
            return false;
        }
    }

    public static void setSkipWarning(boolean skip) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "skip=" + skip);
        } catch (IOException ignored) {
        }
    }
}
