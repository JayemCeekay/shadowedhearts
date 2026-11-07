package com.jayemceekay.shadowedhearts.client.gui.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService;
import dev.isxander.yacl3.api.Binding;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.BooleanSupplier;

/** YACL applies bindings one at a time. Stage those values until the entire edit can be validated. */
final class SpecEditSession {
    final ModConfigSpec spec;
    private final BooleanSupplier canEdit;
    private final List<Draft<?>> drafts = new ArrayList<>();

    SpecEditSession(ModConfigSpec spec, BooleanSupplier canEdit) {
        this.spec = spec;
        this.canEdit = canEdit;
    }

    <T> Binding<T> bind(ModConfigSpec.ConfigValue<T> value) {
        Draft<T> draft = new Draft<>(value);
        drafts.add(draft);
        return draft;
    }

    void save() {
        List<Draft<?>> changes = drafts.stream().filter(Draft::changed).toList();
        if (changes.isEmpty()) return;
        int written = 0;
        ConfigSnapshotService.ScreenEditFile file = null;
        try {
            requireAccess();
            for (var draft : changes) draft.validate();
            file = ConfigSnapshotService.beginScreenEdit(spec);
            requireAccess();
            for (var draft : changes) {
                draft.write();
                written++;
            }
            requireAccess();
            file.save();
            changes.forEach(Draft::accept);
        } catch (RuntimeException failure) {
            try {
                if (file != null && canEdit.getAsBoolean() && file.isBound()) {
                    for (int i = 0; i < written; i++) changes.get(i).restore();
                    file.saveRollback();
                }
            } catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            // The widgets still own the pending values. Reset only their bindings so Save can be retried.
            changes.forEach(Draft::discardStaged);
            if (failure instanceof SaveRejected rejected) throw rejected;
            if (failure instanceof ConcurrentModificationException) {
                throw new SaveRejected("changed_elsewhere", Component.literal(failure.getMessage()));
            }
            Shadowedhearts.LOGGER.error("Could not save settings", failure);
            throw new SaveRejected("save_failed", Component.translatable("gui.shadowedhearts.config.save_failed"));
        }
    }

    private void requireAccess() {
        if (!canEdit.getAsBoolean())
            throw new SaveRejected("save_failed", Component.translatable("message.shadowedhearts.snapshots.unavailable"));
    }

    static final class SaveRejected extends RuntimeException {
        final Component title;
        final Component detail;

        SaveRejected(String key, Component detail) {
            super(key);
            this.title = Component.translatable("gui.shadowedhearts.config." + key);
            this.detail = detail;
        }
    }

    private static final class Draft<T> implements Binding<T> {
        private final ModConfigSpec.ConfigValue<T> value;
        private T original;
        private T staged;

        Draft(ModConfigSpec.ConfigValue<T> value) {
            this.value = value;
            this.original = value.get();
            this.staged = original;
        }

        @Override public T defaultValue() { return value.getDefault(); }
        @Override public T getValue() { return staged; }
        @Override public void setValue(T proposed) { staged = proposed; }

        boolean changed() { return !staged.equals(original); }
        void write() { value.set(staged); }
        void restore() { if (staged.equals(value.get())) value.set(original); }
        void accept() { original = staged; }
        void discardStaged() { staged = original; }

        void validate() {
            var validator = value.getSpec();
            String path = String.join(".", value.getPath());
            if (validator instanceof ModConfigSpec.ListValueSpec listSpec && staged instanceof List<?> items) {
                for (int i = 0; i < items.size(); i++) {
                    if (!listSpec.testElement(items.get(i))) throw invalid(path + "[" + (i + 1) + "]");
                }
                if (!listSpec.getSizeRange().test(items.size())) throw invalid(path);
            }
            if (!validator.test(staged)) throw invalid(path);
            if (!original.equals(value.get())) {
                throw new SaveRejected("changed_elsewhere", Component.literal(path));
            }
        }

        private SaveRejected invalid(String path) {
            String key = value.getSpec().getTranslationKey();
            Component detail = key == null ? Component.literal(path) :
                    Component.translatable(key).append("\n").append(path).append("\n")
                            .append(Component.translatableWithFallback(key + ".tooltip", value.getSpec().getComment()));
            return new SaveRejected("invalid_value", detail);
        }
    }
}
