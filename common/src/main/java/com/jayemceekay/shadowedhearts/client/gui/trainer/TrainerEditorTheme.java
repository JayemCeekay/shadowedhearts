package com.jayemceekay.shadowedhearts.client.gui.trainer;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Presentation tokens and widget rendering live here so the editor can be restyled independently. */
public final class TrainerEditorTheme {
    public static final int BACKGROUND = 0xFFA0ADB1;
    public static final int PANEL = 0xFF89979B;
    public static final int BORDER = 0xFFB7C4C7;
    public static final int INSET = 0xFF68777C;
    public static final int ACCENT = 0xFFDEC8F2;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFE2E7E9;
    public static final int WARNING = 0xFFFFDFC0;
    public static final int ERROR = 0xFFFFCCD1;
    public static final int SELECTED = 0xFFD9F2BD;
    public static final int CONTROL_HEIGHT = 16;
    public static final int ROW_HEIGHT = 20;
    private static final ResourceLocation FRAME = texture("base");
    private static final ResourceLocation BASE = texture("button_base");
    private static final ResourceLocation BUTTON_BORDER = texture("button_border");
    private static final ResourceLocation DISABLED_BASE = texture("button_base_disabled");
    private static final ResourceLocation DISABLED_BORDER = texture("button_border_disabled");
    private TrainerEditorTheme() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/npc/" + name + ".png");
    }

    public static MutableComponent styled(String text) { return styled(Component.literal(text)); }

    public static MutableComponent styled(Component text) {
        return text.copy().withStyle(style -> style.withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE()));
    }

    /** Reuse only the NPC frame's outside edge so its baked panel geometry never stretches into the workspace. */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, BACKGROUND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int corner = Math.min(4, Math.min(width, height) / 2);
        if (corner == 0) return;
        framePart(graphics, x, y, corner, corner, 0, 0, corner, corner);
        framePart(graphics, x + width - corner, y, corner, corner, 360 - corner, 0, corner, corner);
        framePart(graphics, x, y + height - corner, corner, corner, 0, 220 - corner, corner, corner);
        framePart(graphics, x + width - corner, y + height - corner, corner, corner, 360 - corner, 220 - corner, corner, corner);
        framePart(graphics, x + corner, y, width - corner * 2, corner, corner, 0, 360 - corner * 2, corner);
        framePart(graphics, x + corner, y + height - corner, width - corner * 2, corner, corner, 220 - corner, 360 - corner * 2, corner);
        framePart(graphics, x, y + corner, corner, height - corner * 2, 0, corner, corner, 220 - corner * 2);
        framePart(graphics, x + width - corner, y + corner, corner, height - corner * 2, 360 - corner, corner, corner, 220 - corner * 2);
    }

    private static void framePart(GuiGraphics graphics, int x, int y, int width, int height,
                                  int u, int v, int sourceWidth, int sourceHeight) {
        if (width > 0 && height > 0) graphics.blit(FRAME, x, y, width, height, u, v, sourceWidth, sourceHeight, 360, 220);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, INSET);
        graphics.fill(x, y, x + 1, y + height, INSET);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
    }

    public static Button button(String text, int x, int y, int width, int height, Runnable action) {
        return new StyledButton(x, y, width, height, styled(text), ignored -> action.run(), false);
    }

    public static Button listButton(String text, int x, int y, int width, Runnable action) {
        return new StyledButton(x, y, width, CONTROL_HEIGHT, styled(text), ignored -> action.run(), true);
    }

    public static Button rosterButton(String text, String subtitle, int x, int y, int width, boolean selected, Runnable action) {
        return new RosterButton(text, subtitle, x, y, width, selected, action);
    }

    public static EditBox textBox(Font font, int x, int y, int width, int height, Component label) {
        return new TrainerEditorEditBox(font, x, y, width, height, label);
    }

    /** Native cycle logic and narration, with a single Cobblemon surface draw. */
    public static <T> Button cycleButton(CycleButton<T> nativeButton) {
        return new StyledCycleButton<>(nativeButton);
    }

    /** Compatibility for callers that have not yet adopted {@link #cycleButton(CycleButton)}. */
    public static void renderCycleButton(GuiGraphics graphics, CycleButton<?> button) {
        if (button.visible) renderControl(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                button.getMessage(), button.active, button.isHoveredOrFocused(), false);
    }

    private static void renderControl(GuiGraphics graphics, int x, int y, int width, int height, Component message,
                                      boolean active, boolean highlighted, boolean leftAligned) {
        if (width <= 0 || height <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ResourceLocation base = active ? BASE : DISABLED_BASE;
        ResourceLocation border = active ? BUTTON_BORDER : DISABLED_BORDER;
        int v = active && highlighted ? CONTROL_HEIGHT : 0;
        int edge = Math.min(2, width / 2);
        graphics.blit(border, x, y, edge, height, 0, v, edge, CONTROL_HEIGHT, 3, CONTROL_HEIGHT * 2);
        if (width > edge * 2) graphics.blit(base, x + edge, y, width - edge * 2, height, 0, v, 1, CONTROL_HEIGHT, 1, CONTROL_HEIGHT * 2);
        graphics.blit(border, x + width - edge, y, edge, height, 3 - edge, v, edge, CONTROL_HEIGHT, 3, CONTROL_HEIGHT * 2);
        var font = Minecraft.getInstance().font;
        Component text = styled(message);
        int maximum = Math.max(0, width - 8);
        if (font.width(text) > maximum) {
            var ellipsis = styled("...");
            text = styled(font.substrByWidth(text, Math.max(0, maximum - font.width(ellipsis))).getString() + "...");
        }
        int textX = leftAligned ? x + 4 : x + (width - font.width(text)) / 2;
        graphics.enableScissor(x + 2, y + 1, x + Math.max(2, width - 2), y + Math.max(1, height - 1));
        graphics.drawString(font, text, textX, y + (height - 8) / 2, active ? TEXT : MUTED, true);
        graphics.disableScissor();
    }

    private static class StyledButton extends Button {
        private final boolean leftAligned;
        private StyledButton(int x, int y, int width, int height, Component text, OnPress press, boolean leftAligned) {
            super(x, y, width, height, text, press, DEFAULT_NARRATION);
            this.leftAligned = leftAligned;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderControl(graphics, getX(), getY(), getWidth(), getHeight(), getMessage(), active, isHoveredOrFocused(), leftAligned);
        }

        @Override public void playDownSound(SoundManager soundManager) {
            soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F));
        }
    }

    private static final class StyledCycleButton<T> extends StyledButton {
        private final CycleButton<T> delegate;

        private StyledCycleButton(CycleButton<T> delegate) {
            super(delegate.getX(), delegate.getY(), delegate.getWidth(), delegate.getHeight(),
                    styled(delegate.getMessage()), ignored -> {}, false);
            this.delegate = delegate;
        }

        private void syncDelegate() {
            delegate.active = active;
            delegate.visible = visible;
            delegate.setX(getX());
            delegate.setY(getY());
            delegate.setWidth(getWidth());
            delegate.setHeight(getHeight());
            delegate.setFocused(isFocused());
        }

        @Override public void onPress() {
            syncDelegate();
            delegate.onPress();
            setMessage(styled(delegate.getMessage()));
        }

        @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            if (!active || !visible) return false;
            syncDelegate();
            boolean handled = delegate.mouseScrolled(mouseX, mouseY, horizontal, vertical);
            setMessage(styled(delegate.getMessage()));
            return handled;
        }

        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            syncDelegate();
            delegate.updateWidgetNarration(output);
        }
    }

    private static final class RosterButton extends StyledButton {
        private final Component label;
        private final Component subtitle;
        private final boolean selected;

        private RosterButton(String text, String subtitle, int x, int y, int width, boolean selected, Runnable action) {
            super(x, y, width, ROW_HEIGHT, styled(text + (subtitle.isBlank() ? "" : ", " + subtitle)),
                    ignored -> action.run(), true);
            this.label = styled(text);
            this.subtitle = styled(subtitle);
            this.selected = selected;
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), width = getWidth();
            int background = selected ? 0xFF53666C : isHoveredOrFocused() && active ? 0xFF778B91 : PANEL;
            graphics.fill(x, y, x + width, y + getHeight(), background);
            graphics.fill(x, y, x + (selected ? 2 : 1), y + getHeight(), selected ? SELECTED : BORDER);
            if (isHoveredOrFocused() && active) graphics.fill(x + 1, y, x + width, y + 1, BORDER);
            var font = Minecraft.getInstance().font;
            Component shortLabel = styled(font.substrByWidth(label, Math.max(0, width - 9)).getString());
            Component shortSubtitle = styled(font.substrByWidth(subtitle, Math.max(0, width - 9)).getString());
            graphics.drawString(font, shortLabel, x + 5, y + 2, active && selected ? SELECTED : TEXT, true);
            graphics.drawString(font, shortSubtitle, x + 5, y + 11, MUTED, true);
        }

        @Override protected MutableComponent createNarrationMessage() {
            MutableComponent narration = super.createNarrationMessage();
            return selected ? Component.translatable("screen.shadowedhearts.trainer_editor.selected_narration", narration) : narration;
        }
    }
}
