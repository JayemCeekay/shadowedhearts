package com.jayemceekay.shadowedhearts.client.gui.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/** A regular bound option keeps list edits in the same search, reset, and save flow as scalars. */
record StringListController(Option<List<String>> option, ModConfigSpec.ListValueSpec spec, List<String> defaults,
                            Supplier<String> newElement) implements Controller<List<String>> {
    @Override
    public Component formatValue() {
        return Component.translatable("gui.shadowedhearts.config.list.edit", option.pendingValue().size());
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dimension) {
        return new ControllerWidget<>(this, screen, dimension) {
            @Override protected int getHoveredControlWidth() { return getUnhoveredControlWidth(); }

            @Override
            public boolean onMouseClicked(double mouseX, double mouseY, int button) {
                return button == 0 && isMouseOver(mouseX, mouseY) && openEditor();
            }

            @Override
            public boolean onKeyPressed(int key, int scanCode, int modifiers) {
                return focused && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
                        || key == InputConstants.KEY_SPACE) && openEditor();
            }

            private boolean openEditor() {
                if (!isAvailable()) return false;
                playDownSound();
                ((YaclSettingsScreen.FileScreen) screen).openList(control);
                return true;
            }

            @Override
            public void updateNarration(NarrationElementOutput narration) {
                super.updateNarration(narration);
                narration.add(NarratedElementType.USAGE, formatValue());
            }
        };
    }
}
