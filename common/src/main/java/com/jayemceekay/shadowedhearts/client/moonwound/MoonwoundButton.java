package com.jayemceekay.shadowedhearts.client.moonwound;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Cobblemon's resizable NPC button skin with native input, focus and narration behavior. */
final class MoonwoundButton extends Button {
    static final int HEIGHT = 16;
    private static final ResourceLocation BASE = texture("button_base");
    private static final ResourceLocation BORDER = texture("button_border");
    private static final ResourceLocation DISABLED_BASE = texture("button_base_disabled");
    private static final ResourceLocation DISABLED_BORDER = texture("button_border_disabled");
    private final float renderScale;
    private final float renderX, renderY;
    private final int logicalWidth;
    private Component narrationLabel;

    MoonwoundButton(float x, float y, int width, float scale, Component label, Component narration, OnPress onPress) {
        super(Math.round(x), Math.round(y), Math.max(1, Math.round(x + width * scale) - Math.round(x)),
                Math.max(1, Math.round(y + HEIGHT * scale) - Math.round(y)),
                label.copy().withStyle(style -> style.withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE())
                        .withBold(true)), onPress, DEFAULT_NARRATION);
        renderScale = scale;
        renderX = x;
        renderY = y;
        logicalWidth = width;
        narrationLabel = narration;
    }

    void setNarrationLabel(Component label) { narrationLabel = label; }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.button", narrationLabel);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/npc/" + name + ".png");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation base = active ? BASE : DISABLED_BASE;
        ResourceLocation border = active ? BORDER : DISABLED_BORDER;
        int v = active && isHoveredOrFocused() ? HEIGHT : 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        try {
            // Match the panel's fractional transform; round only native input/focus/tooltip bounds.
            graphics.pose().translate(renderX, renderY, 0);
            graphics.pose().scale(renderScale, renderScale, 1F);
            graphics.blit(border, 0, 0, 0F, v, 2, HEIGHT, 3, HEIGHT * 2);
            graphics.blit(base, 2, 0, logicalWidth - 4, HEIGHT, 0F, v, 1, HEIGHT, 1, HEIGHT * 2);
            graphics.blit(border, logicalWidth - 2, 0, 1F, v, 2, HEIGHT, 3, HEIGHT * 2);
            var font = Minecraft.getInstance().font;
            Component label = getMessage();
            if (font.width(label) > logicalWidth - 8) {
                String shortened = font.substrByWidth(label, Math.max(0, logicalWidth - 8
                        - font.width(Component.literal("...").setStyle(label.getStyle())))).getString();
                label = Component.literal(shortened + "...").setStyle(label.getStyle());
            }
            // Native scrolling labels use an unscaled scissor; bounded text keeps scaled hitboxes aligned.
            graphics.drawString(font, label, (logicalWidth - font.width(label)) / 2, 4,
                    active ? 0xFFFFFFFF : 0xFFB1B8BA, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F));
    }
}
