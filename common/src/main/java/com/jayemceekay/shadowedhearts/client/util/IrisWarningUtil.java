package com.jayemceekay.shadowedhearts.client.util;

import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class IrisWarningUtil {
    private static final String ALLOW_UNKNOWN_SHADERS = "allowUnknownShaders";

    private IrisWarningUtil() {}

    public static boolean shouldShowWarning() {
        return shouldShowWarning(Platform.isModLoaded("iris"), IrisWarningState.getInstance(),
                Platform.getConfigFolder().resolve("iris.properties"));
    }

    static boolean shouldShowWarning(boolean irisInstalled, IrisWarningState state, Path irisConfig) {
        if (!irisInstalled || state.skipWarning()) return false;

        try {
            return !Files.exists(irisConfig) || !unknownShadersAllowed(Files.readString(irisConfig, StandardCharsets.ISO_8859_1));
        } catch (IOException ignored) {
            // A failed read must still leave the user able to dismiss the warning.
            return true;
        }
    }

    public static void enableUnknownShaders() throws IOException {
        enableUnknownShaders(Platform.getConfigFolder().resolve("iris.properties"));
    }

    static void enableUnknownShaders(Path irisConfig) throws IOException {
        String original = Files.exists(irisConfig) ? Files.readString(irisConfig, StandardCharsets.ISO_8859_1) : "";
        if (unknownShadersAllowed(original)) return;
        String updated = withUnknownShadersEnabled(original);
        Path target = irisConfig.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "iris.properties-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.ISO_8859_1);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean unknownShadersAllowed(String contents) throws IOException {
        // Iris uses Properties.load(InputStream) and an exact, case-sensitive value check.
        return "true".equals(properties(contents).getProperty(ALLOW_UNKNOWN_SHADERS));
    }

    /** Replace the complete logical property, retaining comments and unrelated settings. */
    private static String withUnknownShadersEnabled(String original) throws IOException {
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = original.split("\r\n|\n|\r", -1);
        List<String> updated = new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lines.length; i++) {
            int start = i;
            String entry = lines[i];
            String trimmed = entry.stripLeading();
            if (!trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                while (continues(lines[i]) && i + 1 < lines.length) {
                    entry += "\n" + lines[++i];
                }
            }
            if (properties(entry).containsKey(ALLOW_UNKNOWN_SHADERS)) {
                if (!replaced) updated.add(ALLOW_UNKNOWN_SHADERS + "=true");
                replaced = true;
            } else {
                updated.addAll(Arrays.asList(lines).subList(start, i + 1));
            }
        }
        if (!replaced) {
            // A blank line also terminates an unrelated trailing continuation at EOF.
            updated.add("");
            updated.add(ALLOW_UNKNOWN_SHADERS + "=true");
            updated.add("");
        }
        return String.join(newline, updated);
    }

    private static boolean continues(String line) {
        int slashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) slashes++;
        return slashes % 2 != 0;
    }

    private static Properties properties(String contents) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(contents));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Iris properties; no settings were changed", exception);
        }
        return properties;
    }
}
