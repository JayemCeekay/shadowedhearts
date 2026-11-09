package com.jayemceekay.shadowedhearts.client.craterrescue;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.craterrescue.CraterRescueOfferPacket;
import com.jayemceekay.shadowedhearts.network.craterrescue.CraterRescueResponsePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** A focused choice. World key mappings never receive a rescue shortcut press. */
public final class CraterRescuePrompt extends Screen {
    private static CraterRescuePrompt pending;
    private final CraterRescueOfferPacket offer;
    private final ClientLevel level;
    private final Component cost;
    private final InputConstants.Key welcomeKey;
    private final InputConstants.Key releaseKey;
    private MultiLineLabel heading;
    private MultiLineLabel costLabel;
    private int left, top, panelWidth, panelHeight;
    private boolean answered;

    private CraterRescuePrompt(CraterRescueOfferPacket offer, ClientLevel level) {
        super(Component.translatable("gui.shadowedhearts.crater_rescue.offer", offer.getPokemonName()));
        this.offer = offer;
        this.level = level;
        this.cost = Component.translatable("gui.shadowedhearts.crater_rescue.cost", offer.getBall().getHoverName());
        welcomeKey = effectiveKey(ModKeybinds.RESCUE_WELCOME, GLFW.GLFW_KEY_Y);
        releaseKey = effectiveKey(ModKeybinds.RESCUE_RELEASE, GLFW.GLFW_KEY_N);
    }

    public static void receive(Minecraft client, CraterRescueOfferPacket offer) {
        if (offer.getBall().isEmpty()) {
            if (pending != null && pending.offer.getSiteId().equals(offer.getSiteId())) pending.answered = true;
            return;
        }
        if (pending != null && !pending.answered && pending.level == client.level
                && pending.offer.getSiteId().equals(offer.getSiteId())
                && ItemStack.isSameItemSameComponents(pending.offer.getBall(), offer.getBall())) return;
        pending = new CraterRescuePrompt(offer, client.level);
    }

    public static void onClientTick(Minecraft client) {
        if (client.screen instanceof CraterRescuePrompt old && old != pending) {
            // An inventory change can replace the offer while the previous answer is still held.
            if (old.level == client.level && old.choiceHeld(client)) return;
            client.setScreen(null);
        }
        if (pending == null) return;
        if (client.level != pending.level || client.player == null || pending.answered) {
            // Keep repeats of the answer key inside this screen until the player lets go.
            if (client.screen == pending && client.player != null && client.level == pending.level
                    && pending.choiceHeld(client)) return;
            if (client.screen == pending) client.setScreen(null);
            pending = null;
        } else if (CobblemonClient.INSTANCE.getBattle() != null) {
            if (client.screen == pending) client.setScreen(null);
        } else if (client.screen == null && client.player.isAlive() && !client.player.isSpectator()
                && client.getOverlay() == null && !pending.choiceHeld(client)) {
            client.setScreen(pending);
        }
    }

    private static InputConstants.Key effectiveKey(KeyMapping override, int defaultKey) {
        return override == null || override.isUnbound()
                ? InputConstants.Type.KEYSYM.getOrCreate(defaultKey) : InputConstants.getKey(override.saveString());
    }

    private boolean choiceHeld(Minecraft client) {
        long window = client.getWindow().getWindow();
        return held(window, welcomeKey) || held(window, releaseKey)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_ENTER)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_KP_ENTER)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
                || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private static boolean held(long window, InputConstants.Key key) {
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    @Override
    protected void init() {
        panelWidth = Math.min(292, width - 12);
        left = (width - panelWidth) / 2;
        heading = MultiLineLabel.create(font, title, panelWidth - 24);
        costLabel = MultiLineLabel.create(font, cost, panelWidth - 24);
        panelHeight = 69 + (heading.getLineCount() + costLabel.getLineCount()) * font.lineHeight;
        top = (height - panelHeight) / 2;
        int buttonWidth = (panelWidth - 24) / 2;
        int buttonY = top + panelHeight - 29;
        addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.crater_rescue.welcome", welcomeKey.getDisplayName()),
                button -> answer(true)).bounds(left + 9, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.shadowedhearts.crater_rescue.release", releaseKey.getDisplayName()),
                button -> answer(false)).bounds(left + panelWidth - 9 - buttonWidth, buttonY, buttonWidth, 20).build());
    }

    private void answer(boolean welcome) {
        if (answered || pending != this) return;
        answered = true;
        ShadowedHeartsNetwork.sendToServer(new CraterRescueResponsePacket(offer.getSiteId(), welcome,
                BuiltInRegistries.ITEM.getKey(offer.getBall().getItem())));
    }

    private boolean shortcut(InputConstants.Key pressed) {
        boolean welcome = pressed.equals(welcomeKey);
        boolean release = pressed.equals(releaseKey);
        if (!welcome && !release) return false;
        // An ambiguous custom binding can still be answered by clicking either button.
        if (welcome != release) answer(welcome);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return shortcut(InputConstants.getKey(keyCode, scanCode)) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button) || shortcut(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(cost); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float tick) { }
    @Override public void renderBlurredBackground(float tick) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0202229);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF8C7DA2);
        heading.renderCentered(graphics, width / 2, top + 9);
        int iconY = top + 13 + heading.getLineCount() * font.lineHeight;
        graphics.renderItem(offer.getBall(), width / 2 - 8, iconY);
        costLabel.renderCentered(graphics, width / 2, iconY + 19);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
