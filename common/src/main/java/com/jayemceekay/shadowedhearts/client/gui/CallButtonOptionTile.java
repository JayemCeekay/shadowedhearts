package com.jayemceekay.shadowedhearts.client.gui;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.CobblemonRenderable;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.render.RenderHelperKt;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public class CallButtonOptionTile implements CobblemonRenderable, GuiEventListener, NarratableEntry {

    public static final int OPTION_WIDTH = 90;
    public static final int OPTION_HEIGHT = 34;

    private final BattleGUI battleGUI;
    private final int x;
    private final int y;
    private final ResourceLocation resource;
    private final MutableComponent text;
    private final Function0<Unit> onClick;

    private boolean focused = false;

    public CallButtonOptionTile(BattleGUI battleGUI, int x, int y, ResourceLocation resource, MutableComponent text, Function0<Unit> onClick) {
        this.battleGUI = battleGUI;
        this.x = x;
        this.y = y;
        this.resource = resource;
        this.text = text;
        this.onClick = onClick;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        float opacity = (float) CobblemonClient.INSTANCE.getBattleOverlay().getOpacityRatio();
        if (opacity < 0.1f) {
            return;
        }
        GuiUtilsKt.blitk(
                context.pose(),
                resource,
                x * 2,
                y * 2,
                OPTION_HEIGHT,
                OPTION_WIDTH,
                0,
                isHovered(mouseX, mouseY) ? OPTION_HEIGHT : 0,
                OPTION_WIDTH,
                OPTION_HEIGHT * 2,
                0,
                1,
                1,
                1,
                opacity,
                true,
                0.5F
        );

        float scale = 1F;
        RenderHelperKt.drawScaledText(
                context,
                Minecraft.DEFAULT_FONT,
                text,
                x + 18,
                y + 4,
                scale,
                opacity,
                Integer.MAX_VALUE,
                0x00FFFFFF + (((int)(opacity * 255)) << 24),
                false,
                true,
                null,
                null
        );
    }

    public boolean mousePrimaryClicked(double mouseX, double mouseY) {
        if (mouseX < x || mouseY < y || mouseX > x + OPTION_WIDTH || mouseY > y + OPTION_HEIGHT) {
            return false;
        }
        onClick.invoke();
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX > x && mouseY > y && mouseX < x + OPTION_WIDTH && mouseY < y + OPTION_HEIGHT;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, text);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.HOVERED;
    }
}
