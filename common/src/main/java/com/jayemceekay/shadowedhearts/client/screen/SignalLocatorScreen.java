package com.jayemceekay.shadowedhearts.client.screen;

import com.jayemceekay.shadowedhearts.blocks.menu.SignalLocatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Basic placeholder screen for the Signal Locator. Uses default background and simple labels.
 */
public class SignalLocatorScreen extends AbstractContainerScreen<SignalLocatorMenu> {
    public SignalLocatorScreen(SignalLocatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Draw a simple dark background panel
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gfx.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xAA101018);
        // Accent line
        gfx.fill(x, y + 14, x + this.imageWidth, y + 15, 0xFF6A5ACD);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gfx.drawString(this.font, this.title, x + 8, y + 6, 0xFFE0E0E0, false);
        gfx.drawString(this.font, this.playerInventoryTitle, x + 8, y + this.imageHeight - 94, 0xFF9AA0A6, false);
    }
}
