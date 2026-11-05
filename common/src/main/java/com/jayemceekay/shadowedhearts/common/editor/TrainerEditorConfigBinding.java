package com.jayemceekay.shadowedhearts.common.editor;

import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The loaders supply the actual loaded SERVER config path, including per-world/global selection. */
public final class TrainerEditorConfigBinding {
    private static Path path;

    private TrainerEditorConfigBinding() {}

    public static synchronized void bind(Path loadedPath) {
        path = loadedPath == null ? null : loadedPath.toAbsolutePath().normalize();
    }

    public static synchronized void clear() { path = null; }

    static synchronized String fingerprint() {
        if (path == null) return "unbound";
        try { return path + ":" + TrainerEditorTags.revision(Files.readString(path)); }
        catch (IOException ex) { return path + ":unreadable"; }
    }

    static synchronized void save(Map<ModConfigSpec.ConfigValue<List<? extends String>>, List<String>> changes) {
        save(changes, null);
    }

    /** Preflight for coordinated child definitions; does not publish config values or touch the file. */
    static synchronized void validateSave(Map<ModConfigSpec.ConfigValue<List<? extends String>>, List<String>> changes, String expectedFingerprint) {
        if (path == null) throw TrainerEditorMessageException.state(message("error.the_loaded_server_config_path_is_unavailable_reopen_this_world_before_editing_rct_rules"));
        if (expectedFingerprint != null && !expectedFingerprint.equals(fingerprint())) {
            throw TrainerEditorMessageException.state(message("error.the_server_config_changed_during_editing_refresh_before_saving"));
        }
        try {
            Map<List<String>, List<String>> paths = new LinkedHashMap<>();
            changes.forEach((value, replacement) -> paths.put(value.getPath(), List.copyOf(replacement)));
            updateToml(Files.readAllBytes(path), paths);
        } catch (IOException | RuntimeException ex) {
            throw TrainerEditorMessageException.state(message("prepare_config_failed" , TrainerEditorMessageException.component(ex)), ex);
        }
    }

    static synchronized void save(Map<ModConfigSpec.ConfigValue<List<? extends String>>, List<String>> changes, String expectedFingerprint) {
        if (path == null) throw TrainerEditorMessageException.state(message("error.the_loaded_server_config_path_is_unavailable_reopen_this_world_before_editing_rct_rules"));
        if (expectedFingerprint != null && !expectedFingerprint.equals(fingerprint())) {
            throw TrainerEditorMessageException.state(message("error.the_server_config_changed_during_editing_refresh_before_saving"));
        }
        Map<ModConfigSpec.ConfigValue<List<? extends String>>, List<? extends String>> previous = new LinkedHashMap<>();
        changes.forEach((value, replacement) -> previous.put(value, List.copyOf(value.get())));
        byte[] before;
        try {
            before = Files.readAllBytes(path);
            Map<List<String>, List<String>> paths = new LinkedHashMap<>();
            changes.forEach((value, replacement) -> paths.put(value.getPath(), List.copyOf(replacement)));
            byte[] after = updateToml(before, paths);
            if (!java.util.Arrays.equals(before, Files.readAllBytes(path))) {
                throw TrainerEditorMessageException.state(message("error.the_server_config_changed_during_this_save_refresh_and_try_again"));
            }
            // Do not claim success from an asynchronous native save: complete the disk write first.
            atomicWrite(path, after);
            try {
                changes.forEach((value, replacement) -> value.set(List.copyOf(replacement)));
            } catch (RuntimeException publicationFailure) {
                previous.forEach((value, original) -> {
                    try { value.set(original); } catch (RuntimeException rollbackFailure) { publicationFailure.addSuppressed(rollbackFailure); }
                });
                try { atomicWrite(path, before); } catch (IOException rollbackFailure) { publicationFailure.addSuppressed(rollbackFailure); }
                throw publicationFailure;
            }
        } catch (IOException | RuntimeException ex) {
            throw TrainerEditorMessageException.state(message("save_config_failed" , TrainerEditorMessageException.component(ex)), ex);
        }
    }

    static byte[] updateToml(byte[] original, Map<List<String>, List<String>> replacements) {
        CommentedConfig copy = new TomlParser().parse(new StringReader(new String(original, StandardCharsets.UTF_8)));
        replacements.forEach(copy::set);
        StringWriter output = new StringWriter();
        new TomlWriter().write(copy, output);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void atomicWrite(Path target, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".trainer-editor-", ".tmp");
        try {
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            try { Files.deleteIfExists(temporary); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }
}
