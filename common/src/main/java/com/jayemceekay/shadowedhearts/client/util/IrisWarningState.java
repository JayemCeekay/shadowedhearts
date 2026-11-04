package com.jayemceekay.shadowedhearts.client.util;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IrisWarningState {
    private final Path file;
    private boolean dismissedForSession;

    IrisWarningState(Path file) {
        this.file = file;
    }

    private static final class Holder {
        private static final IrisWarningState INSTANCE = new IrisWarningState(
                Platform.getConfigFolder().resolve("shadowedhearts-iris-warning.txt"));
    }

    public static IrisWarningState getInstance() {
        return Holder.INSTANCE;
    }

    public void dismissForSession() {
        dismissedForSession = true;
    }

    public boolean skipWarning() {
        if (dismissedForSession) return true;
        try {
            if (!Files.exists(file)) {
                return false;
            }
            return Files.readString(file).trim().equalsIgnoreCase("skip=true");
        } catch (IOException ignored) {
            return false;
        }
    }

    public void setSkipWarning(boolean skip) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "skip=" + skip);
        dismissedForSession = skip;
    }
}
