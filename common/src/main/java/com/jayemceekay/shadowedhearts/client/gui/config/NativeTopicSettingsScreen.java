package com.jayemceekay.shadowedhearts.client.gui.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationListScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** The loader's own controls and undo history for one selected set of config values. */
public final class NativeTopicSettingsScreen extends ConfigurationSectionScreen {
    private final BooleanSupplier canEdit;

    private NativeTopicSettingsScreen(Context context, Component title, BooleanSupplier canEdit) {
        super(context, title);
        this.canEdit = canEdit;
    }

    public static Screen create(Screen parent, ModConfigSpec spec, Component title,
                                List<ModConfigSpec.ConfigValue<?>> values, BooleanSupplier canEdit) {
        if (!canEdit.getAsBoolean() || !spec.isLoaded()) return parent;
        var registered = ModConfigs.getModConfigs(Shadowedhearts.MOD_ID).stream()
                .filter(config -> config.getSpec() == spec).findFirst().orElse(null);
        if (registered == null || registered.getLoadedConfig() == null) return parent;
        var source = registered.getLoadedConfig();

        // These are references to the real ConfigValues. Only the menu's row keys are flattened:
        // e.g. the append and replace trainer lists both have a leaf named "trainers".
        Config entries = Config.of(LinkedHashMap::new, InMemoryFormat.withUniversalSupport());
        Map<String, Object> definitions = new LinkedHashMap<>();
        for (var value : values) {
            String rowKey = String.join("/", value.getPath());
            entries.set(List.of(rowKey), value);
            definitions.put(rowKey, value.getSpec());
        }
        Context context = new Context(registered.getModId(), parent, registered, spec, entries.entrySet(),
                definitions, List.of(), (section, key, row) -> row);
        return new NativeTopicSettingsScreen(context, title,
                () -> canEdit.getAsBoolean() && registered.getLoadedConfig() == source);
    }

    @Override
    protected <T> Element createList(String key, ModConfigSpec.ListValueSpec spec, ModConfigSpec.ConfigValue<List<T>> value) {
        // The native button opens its cached child. Retain the list's native add/reorder/edit/undo
        // behavior while guarding the one operation that writes its working copy back to the spec.
        sectionCache.computeIfAbsent(key, ignored -> new ConfigurationListScreen<T>(Context.list(context, this), key,
                getTitle().copy().append(" › ").append(getTranslationComponent(key)), spec, value) {
            @Override public void tick() { if (!leaveIfStale()) super.tick(); }
            @Override public void onClose() { if (!leaveIfStale()) super.onClose(); }
        });
        return super.createList(key, spec, value);
    }

    private boolean leaveIfStale() {
        if (canEdit.getAsBoolean()) return false;
        Minecraft.getInstance().setScreen(context.parent());
        return true;
    }

    @Override public void tick() { if (!leaveIfStale()) super.tick(); }
    @Override public void onClose() { if (!leaveIfStale()) super.onClose(); }

    // Scalar controls update their live ConfigValues during input; check before dispatch as well as on close.
    @Override public boolean mouseClicked(double x, double y, int button) {
        return leaveIfStale() || super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return leaveIfStale() || super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return leaveIfStale() || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return leaveIfStale() || super.mouseScrolled(x, y, dx, dy);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        return leaveIfStale() || super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        return leaveIfStale() || super.charTyped(character, modifiers);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The native reset button reads the spec on every frame.
        if (!leaveIfStale()) super.render(graphics, mouseX, mouseY, partialTick);
    }
}
