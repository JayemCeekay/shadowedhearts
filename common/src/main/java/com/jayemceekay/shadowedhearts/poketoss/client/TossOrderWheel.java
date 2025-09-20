package com.jayemceekay.shadowedhearts.poketoss.client;

import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Extracted tactical order tween menu (radial wheel) logic.
 * Handles wheel state, input, rendering, and dispatching actions.
 */
public final class TossOrderWheel {
    private TossOrderWheel() {}

    // Wheel state
    private static boolean wheelActive = false;
    private static boolean prevWheelActive = false;
    private static boolean wasLeftDown = false;
    private static boolean suppressUntilMouseUp = false;

    // Submenus and tweens
    private static boolean combatSubOpen = false;
    private static float combatTween = 0.0f;
    private static boolean posSubOpen = false;
    private static float posTween = 0.0f;
    private static boolean utilSubOpen = false;
    private static float utilTween = 0.0f;
    private static boolean ctxSubOpen = false;
    private static float ctxTween = 0.0f;

    // Scrollable submenu model
    private static final String[] COMBAT_LABELS = new String[] { "Attack", "Guard", "Disengage" };
    private static int combatIndex = 0;

    private static final String[] POSITION_LABELS = new String[] { "Move To", "Hold Position" };
    private static int posIndex = 0;

    private static final String[] UTILITY_LABELS = new String[] { "Regroup to Me" };
    private static int utilIndex = 0;

    private static final String[] CONTEXT_LABELS = new String[] { "Hold At Me" };
    private static int ctxIndex = 0;

    private static int wrap(int idx, int size) {
        if (size <= 0) return 0;
        int m = idx % size;
        return m < 0 ? m + size : m;
    }

    private enum WheelAction {
        NONE,
        COMBAT_ATTACK,
        COMBAT_GUARD,
        POSITION_MOVE_TO,
        POSITION_HOLD,
        UTILITY_REGROUP_TO_ME,
        CONTEXT_HOLD_AT_ME,
        CANCEL_ALL
    }

    private static WheelAction pendingAction = WheelAction.NONE;

    public static boolean isActive() { return wheelActive; }

    /** Tick/update: handle key toggling, close/open, recenter mouse, and dispatch pending actions on close. */
    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Toggle the wheel on key press
        boolean pressed = ModKeybinds.consumeOrderWheelPress();
        if (pressed) {
            if (WhistleSelectionClient.isHoldingWhistle()) {
                wheelActive = !wheelActive;
                if (wheelActive) {
                    // Recenter cursor to the middle of the window
                    try {
                        int winW = mc.getWindow().getWidth();
                        int winH = mc.getWindow().getHeight();
                        double cx = winW / 2.0;
                        double cy = winH / 2.0;
                        long handle = mc.getWindow().getWindow();
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(handle, cx, cy);
                        ((com.jayemceekay.shadowedhearts.mixin.MouseHandlerAccessor)(Object)mc.mouseHandler).shadowedhearts$setXpos(cx);
                        ((com.jayemceekay.shadowedhearts.mixin.MouseHandlerAccessor)(Object)mc.mouseHandler).shadowedhearts$setYpos(cy);
                    } catch (Throwable ignored) {}
                }
            } else {
                wheelActive = false;
            }
        }

        // Auto-close if not holding the whistle or a screen is open
        if (wheelActive && !WhistleSelectionClient.isHoldingWhistle()) wheelActive = false;
        if (wheelActive && mc.screen != null) wheelActive = false;

        // When the wheel opens, suppress any current left-click press to avoid spurious toggles
        if (wheelActive && !prevWheelActive) {
            suppressUntilMouseUp = true;
        }

        // When the wheel closes, execute any pending action and reset submenu state
        if (!wheelActive && prevWheelActive) {
            if (pendingAction != WheelAction.NONE) {
                switch (pendingAction) {
                    case COMBAT_ATTACK -> TargetSelectionClient.beginAttack();
                    case COMBAT_GUARD -> TargetSelectionClient.begin(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.GUARD_TARGET);
                    case POSITION_MOVE_TO -> PositionSelectionClient.begin(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.MOVE_TO);
                    case POSITION_HOLD -> PositionSelectionClient.begin(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.HOLD_POSITION);
                    case UTILITY_REGROUP_TO_ME -> WhistleSelectionClient.sendPosOrderAtPlayer(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.MOVE_TO, 2.0f, false);
                    case CONTEXT_HOLD_AT_ME -> WhistleSelectionClient.sendPosOrderAtPlayer(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.HOLD_POSITION, 2.5f, true);
                    case CANCEL_ALL -> WhistleSelectionClient.sendCancelOrdersToServer();
                    default -> {}
                }
                pendingAction = WheelAction.NONE;
            }
            // Reset submenus/tweens
            combatSubOpen = false; combatTween = 0f;
            posSubOpen = false; posTween = 0f;
            utilSubOpen = false; utilTween = 0f;
            ctxSubOpen = false; ctxTween = 0f;
            suppressUntilMouseUp = false;
        }
        prevWheelActive = wheelActive;
    }

    /** 2D HUD overlay rendering and interaction for the wheel. */
    public static void onHudRender(GuiGraphics gfx, float partialTick) {
        if (!wheelActive) { wasLeftDown = false; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) { wasLeftDown = false; return; }
        if (mc.screen != null) { wasLeftDown = false; return; }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int baseDim = Math.min(w, h);
        int cx = w / 2;
        int cy = h / 2;

        // Convert absolute mouse position to GUI-scaled coordinates
        double mx = mc.mouseHandler.xpos() * (double) w / (double) mc.getWindow().getWidth();
        double my = mc.mouseHandler.ypos() * (double) h / (double) mc.getWindow().getHeight();
        int mouseX = (int) Math.round(mx);
        int mouseY = (int) Math.round(my);

        // Dim background
        gfx.fill(0, 0, w, h, 0x66000000);

        // Layout constants
        int centerSize = Math.max(48, (int)Math.round(baseDim * 0.0833));
        int gap = Math.max(8, (int)Math.round(baseDim * 0.0203));
        int catHScaled = Math.max(24, (int)Math.round(baseDim * 0.0390));
        int catWScaled = Math.max(140, (int)Math.round(baseDim * 0.2040));
        int maxCatW = Math.max(50, (w - centerSize - 2 * gap) / 2);
        int maxCatH = Math.max(20, (h - centerSize - 2 * gap) / 2);
        int catW = Math.min(catWScaled, maxCatW);
        int catH = Math.min(catHScaled, maxCatH);

        // Category rects
        int cX0 = cx - centerSize / 2, cY0 = cy - centerSize / 2, cX1 = cX0 + centerSize, cY1 = cY0 + centerSize;
        int topX0 = cx - catW / 2, topY0 = cy - (centerSize / 2 + gap + catH), topX1 = topX0 + catW, topY1 = topY0 + catH;
        int leftX0 = cx - (centerSize / 2 + gap + catW), leftY0 = cy - catH / 2, leftX1 = leftX0 + catW, leftY1 = leftY0 + catH;
        int rightX0 = cx + (centerSize / 2 + gap), rightY0 = cy - catH / 2, rightX1 = rightX0 + catW, rightY1 = rightY0 + catH;
        int bottomX0 = cx - catW / 2, bottomY0 = cy + (centerSize / 2 + gap), bottomX1 = bottomX0 + catW, bottomY1 = bottomY0 + catH;

        boolean hovCenter = mouseX >= cX0 && mouseX <= cX1 && mouseY >= cY0 && mouseY <= cY1;
        boolean hovTop = mouseX >= topX0 && mouseX <= topX1 && mouseY >= topY0 && mouseY <= topY1;
        boolean hovLeft = mouseX >= leftX0 && mouseX <= leftX1 && mouseY >= leftY0 && mouseY <= leftY1;
        boolean hovRight = mouseX >= rightX0 && mouseX <= rightX1 && mouseY >= rightY0 && mouseY <= rightY1;
        boolean hovBottom = mouseX >= bottomX0 && mouseX <= bottomX1 && mouseY >= bottomY0 && mouseY <= bottomY1;

        int base = 0xAA1E1E1E;
        int hi = 0xFFC8FFC8; // light green highlight
        int white = 0xFFFFFFFF;

        // Draw categories
        gfx.fill(cX0, cY0, cX1, cY1, hovCenter ? hi : 0xFFAA4444);
        gfx.fill(topX0, topY0, topX1, topY1, hovTop ? hi : base);
        gfx.fill(leftX0, leftY0, leftX1, leftY1, hovLeft ? hi : base);
        gfx.fill(rightX0, rightY0, rightX1, rightY1, hovRight ? hi : base);
        gfx.fill(bottomX0, bottomY0, bottomX1, bottomY1, hovBottom ? hi : base);

        var font = mc.font;
        var tCancel = Component.literal("Cancel");
        var tCombat = Component.literal("Combat");
        var tPos = Component.literal("Position");
        var tUtil = Component.literal("Utility");
        var tCtx = Component.literal("Context");
        gfx.drawString(font, tCancel, cx - font.width(tCancel) / 2, cy - 4, white, false);
        gfx.drawString(font, tCombat, cx - font.width(tCombat) / 2, topY0 + 12, white, false);
        gfx.drawString(font, tPos, leftX0 + catW / 2 - font.width(tPos) / 2, leftY0 + 12, white, false);
        gfx.drawString(font, tUtil, rightX0 + catW / 2 - font.width(tUtil) / 2, rightY0 + 12, white, false);
        gfx.drawString(font, tCtx, bottomX0 + catW / 2 - font.width(tCtx) / 2, bottomY0 + 12, white, false);

        // Tween the combat submenu
        float target = combatSubOpen && hovTop ? 1.0f : (combatSubOpen ? 0.9f : 0.0f);
        if (combatSubOpen) target = 1.0f;
        if (!combatSubOpen && !hovTop) target = 0.0f;
        combatTween += (target - combatTween) * 0.35f;

        int subCount = 3;
        int spacing = Math.max(6, (int)Math.round(baseDim * 0.0093));
        int minSubW = Math.max(50, (int)Math.round(baseDim * 0.0650));
        int maxSubWAllowed = Math.max(20, (w - 8 - spacing * (subCount - 1)) / subCount);
        int baseSubW = Math.max(minSubW, (catW - 2 * spacing) / subCount);
        int subW = (int) (Math.min(baseSubW, maxSubWAllowed) * Math.max(0.5f, combatTween));
        int baseSubH = Math.max(22, (int)Math.round(baseDim * 0.0296));
        int subH = (int) (baseSubH * Math.max(0.5f, combatTween));
        int maxAbove = Math.max(4, topY0 - gap - 4);
        subH = Math.min(subH, maxAbove);
        int subY = topY0 - gap - subH;
        int firstX = cx - (subW * subCount + spacing * (subCount - 1)) / 2;

        WheelAction hoveredAction = WheelAction.NONE;

        // Consume mouse wheel for scrolling within the hovered/open submenu
        int wheelSteps = 0;
        try {
            double ay = ((com.jayemceekay.shadowedhearts.mixin.MouseHandlerAccessor)(Object)mc.mouseHandler).shadowedhearts$getAccumulatedScrollY();
            int s = (int) ay;
            if (s != 0) {
                wheelSteps = s;
                ((com.jayemceekay.shadowedhearts.mixin.MouseHandlerAccessor)(Object)mc.mouseHandler).shadowedhearts$setAccumulatedScrollY(ay - s);
            }
        } catch (Throwable ignored) {}

        // Combat submenu (horizontal with wrap-around)
        int combatAreaX0 = firstX;
        int combatAreaX1 = firstX + subW * subCount + spacing * (subCount - 1);
        int combatAreaY0 = subY;
        int combatAreaY1 = subY + subH;
        if (combatTween > 0.05f) {
            if (wheelSteps != 0 && combatSubOpen && mouseX >= combatAreaX0 && mouseX <= combatAreaX1 && mouseY >= combatAreaY0 && mouseY <= combatAreaY1) {
                combatIndex = wrap(combatIndex - wheelSteps, COMBAT_LABELS.length);
            }
            int x = firstX;
            for (int i = 0; i < subCount; i++) {
                int idx = wrap(combatIndex + i, COMBAT_LABELS.length);
                int rX0 = x, rY0 = subY, rX1 = x + subW, rY1 = subY + subH; x += subW + spacing;
                boolean hov = mouseX >= rX0 && mouseX <= rX1 && mouseY >= rY0 && mouseY <= rY1;
                gfx.fill(rX0, rY0, rX1, rY1, hov ? 0xFF66AAFF : 0xFF2A2A2A);
                String label = COMBAT_LABELS[idx];
                gfx.drawCenteredString(font, Component.literal(label), (rX0 + rX1) / 2, rY0 + 10, white);
                if (hov) {
                    if ("Attack".equals(label)) hoveredAction = WheelAction.COMBAT_ATTACK;
                    else if ("Guard".equals(label)) hoveredAction = WheelAction.COMBAT_GUARD;
                    else hoveredAction = WheelAction.NONE; // Disengage not wired yet
                }
            }
        }

        // Position submenu (left, vertical wrap-around)
        float posTarget = posSubOpen && hovLeft ? 1.0f : (posSubOpen ? 0.9f : 0.0f);
        if (posSubOpen) posTarget = 1.0f;
        if (!posSubOpen && !hovLeft) posTarget = 0.0f;
        posTween += (posTarget - posTween) * 0.35f;
        if (posTween > 0.05f) {
            int minPSW = Math.max(70, (int)Math.round(baseDim * 0.0740));
            int basePSW = Math.max(minPSW, catW / 2);
            int pSubW = (int)(basePSW * Math.max(0.5f, posTween));
            int basePSH = Math.max(22, (int)Math.round(baseDim * 0.0259));
            int pSubH = (int)(basePSH * Math.max(0.5f, posTween));
            int allowedPSubW = Math.max(4, leftX0 - gap - 4);
            pSubW = Math.min(pSubW, allowedPSubW);
            int maxPH = Math.max(4, Math.min(leftY0 - 4, h - (leftY0 + 6) - 4));
            pSubH = Math.min(pSubH, maxPH);

            int pX0 = leftX0 - gap - pSubW;
            int pY0a = Math.max(4, leftY0 - pSubH - 6);
            int pY0b = Math.min(h - 4 - pSubH, leftY0 + 6);
            int pX1 = pX0 + pSubW;
            int pY1a = pY0a + pSubH;
            int pY1b = pY0b + pSubH;

            if (wheelSteps != 0 && posSubOpen && mouseX >= pX0 && mouseX <= pX1 && mouseY >= Math.min(pY0a, pY0b) && mouseY <= Math.max(pY1a, pY1b)) {
                posIndex = wrap(posIndex - wheelSteps, POSITION_LABELS.length);
            }

            int idxA = wrap(posIndex, POSITION_LABELS.length);
            boolean hovA = mouseX >= pX0 && mouseX <= pX1 && mouseY >= pY0a && mouseY <= pY1a;
            gfx.fill(pX0, pY0a, pX1, pY1a, hovA ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, Component.literal(POSITION_LABELS[idxA]), (pX0 + pX1) / 2, pY0a + 8, white);
            if (hovA) {
                if ("Move To".equals(POSITION_LABELS[idxA])) hoveredAction = WheelAction.POSITION_MOVE_TO;
                else if ("Hold Position".equals(POSITION_LABELS[idxA])) hoveredAction = WheelAction.POSITION_HOLD;
            }

            int idxB = wrap(posIndex + 1, POSITION_LABELS.length);
            boolean hovB = mouseX >= pX0 && mouseX <= pX1 && mouseY >= pY0b && mouseY <= pY1b;
            gfx.fill(pX0, pY0b, pX1, pY1b, hovB ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, Component.literal(POSITION_LABELS[idxB]), (pX0 + pX1) / 2, pY0b + 8, white);
            if (hovB) {
                if ("Move To".equals(POSITION_LABELS[idxB])) hoveredAction = WheelAction.POSITION_MOVE_TO;
                else if ("Hold Position".equals(POSITION_LABELS[idxB])) hoveredAction = WheelAction.POSITION_HOLD;
            }
        }

        // Utility submenu (right)
        float utilTarget = utilSubOpen && hovRight ? 1.0f : (utilSubOpen ? 0.9f : 0.0f);
        if (utilSubOpen) utilTarget = 1.0f;
        if (!utilSubOpen && !hovRight) utilTarget = 0.0f;
        utilTween += (utilTarget - utilTween) * 0.35f;
        if (utilTween > 0.05f) {
            int minUSW = Math.max(90, (int)Math.round(baseDim * 0.1020));
            int baseUSW = Math.max(minUSW, catW / 2);
            int uX0 = rightX1 + gap;
            int uMaxW = Math.max(4, w - 4 - uX0);
            int uSubW = (int)(Math.min(baseUSW, uMaxW) * Math.max(0.5f, utilTween));
            int baseUSH = Math.max(22, (int)Math.round(baseDim * 0.0259));
            int uSubH = (int)(baseUSH * Math.max(0.5f, utilTween));
            int uY0 = rightY0 + (catH - uSubH) / 2;
            int uX1 = uX0 + uSubW;
            int uY1 = uY0 + uSubH;

            if (wheelSteps != 0 && utilSubOpen && mouseX >= uX0 && mouseX <= uX1 && mouseY >= uY0 && mouseY <= uY1) {
                utilIndex = wrap(utilIndex - wheelSteps, UTILITY_LABELS.length);
            }

            boolean hov = mouseX >= uX0 && mouseX <= uX1 && mouseY >= uY0 && mouseY <= uY1;
            gfx.fill(uX0, uY0, uX1, uY1, hov ? 0xFF66AAFF : 0xFF2A2A2A);
            String label = UTILITY_LABELS[wrap(utilIndex, UTILITY_LABELS.length)];
            gfx.drawCenteredString(font, Component.literal(label), (uX0 + uX1) / 2, uY0 + 8, white);
            if (hov && "Regroup to Me".equals(label)) hoveredAction = WheelAction.UTILITY_REGROUP_TO_ME;
        }

        // Context submenu (bottom)
        float ctxTarget = ctxSubOpen && hovBottom ? 1.0f : (ctxSubOpen ? 0.9f : 0.0f);
        if (ctxSubOpen) ctxTarget = 1.0f;
        if (!ctxSubOpen && !hovBottom) ctxTarget = 0.0f;
        ctxTween += (ctxTarget - ctxTween) * 0.35f;
        if (ctxTween > 0.05f) {
            int minCSW = Math.max(90, (int)Math.round(baseDim * 0.1020));
            int baseCSW = Math.max(minCSW, catW / 2);
            int cSubW = (int)(Math.min(baseCSW, catW - 8) * Math.max(0.5f, ctxTween));
            int baseCSH = Math.max(22, (int)Math.round(baseDim * 0.0259));
            int cSubH = (int)(baseCSH * Math.max(0.5f, ctxTween));
            int ctxY0 = bottomY1 + gap;
            cSubH = Math.min(cSubH, Math.max(12, h - 4 - ctxY0));
            int ctxX0 = bottomX0 + (catW - cSubW) / 2;
            int ctxX1 = ctxX0 + cSubW;
            int ctxY1 = ctxY0 + cSubH;

            if (wheelSteps != 0 && ctxSubOpen && mouseX >= ctxX0 && mouseX <= ctxX1 && mouseY >= ctxY0 && mouseY <= ctxY1) {
                ctxIndex = wrap(ctxIndex - wheelSteps, CONTEXT_LABELS.length);
            }

            boolean hov = mouseX >= ctxX0 && mouseX <= ctxX1 && mouseY >= ctxY0 && mouseY <= ctxY1;
            gfx.fill(ctxX0, ctxY0, ctxX1, ctxY1, hov ? 0xFF66AAFF : 0xFF2A2A2A);
            String label = CONTEXT_LABELS[wrap(ctxIndex, CONTEXT_LABELS.length)];
            gfx.drawCenteredString(font, Component.literal(label), (ctxX0 + ctxX1) / 2, ctxY0 + 8, white);
            if (hov && "Hold At Me".equals(label)) hoveredAction = WheelAction.CONTEXT_HOLD_AT_ME;
        }

        // Simple custom cursor
        int curSize = Math.max(6, Math.min(12, (int)Math.round(baseDim * 0.0125)));
        int half = curSize / 2;
        int bx0 = mouseX - half - 1;
        int by0 = mouseY - half - 1;
        int bx1 = mouseX + half + 1;
        int by1 = mouseY + half + 1;
        gfx.fill(bx0, by0, bx1, by1, 0xFF000000); // border
        gfx.fill(mouseX - half, mouseY - half, mouseX + half, mouseY + half, 0xFFFFFFFF); // fill

        boolean leftDown = mc.mouseHandler.isLeftPressed();
        if (leftDown) {
            boolean anyOpenNow = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
            if (anyOpenNow) {
                if (hovTop && !combatSubOpen) {
                    combatSubOpen = true; posSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                    combatTween = Math.max(combatTween, 0.2f);
                } else if (hovLeft && !posSubOpen) {
                    posSubOpen = true; combatSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                    posTween = Math.max(posTween, 0.2f);
                } else if (hovRight && !utilSubOpen) {
                    utilSubOpen = true; combatSubOpen = false; posSubOpen = false; ctxSubOpen = false;
                    utilTween = Math.max(utilTween, 0.2f);
                } else if (hovBottom && !ctxSubOpen) {
                    ctxSubOpen = true; combatSubOpen = false; posSubOpen = false; utilSubOpen = false;
                    ctxTween = Math.max(ctxTween, 0.2f);
                }
            }
        }

        if (suppressUntilMouseUp) {
            if (!leftDown) suppressUntilMouseUp = false;
        } else if (leftDown && !wasLeftDown) {
            boolean submenuReady = (combatSubOpen && combatTween > 0.8f) || (posSubOpen && posTween > 0.8f) || (utilSubOpen && utilTween > 0.8f) || (ctxSubOpen && ctxTween > 0.8f);
            if (submenuReady && hoveredAction != WheelAction.NONE) {
                pendingAction = hoveredAction;
                suppressUntilMouseUp = true;
                wheelActive = false;
            } else if (hovTop) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !combatSubOpen) {
                    combatSubOpen = true; posSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                    combatTween = Math.max(combatTween, 0.2f);
                } else {
                    combatSubOpen = !combatSubOpen; posSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                    if (combatSubOpen) combatTween = Math.max(combatTween, 0.2f);
                }
            } else if (hovLeft) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !posSubOpen) {
                    posSubOpen = true; combatSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                    posTween = Math.max(posTween, 0.2f);
                } else {
                    posSubOpen = !posSubOpen; combatSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                    if (posSubOpen) posTween = Math.max(posTween, 0.2f);
                }
            } else if (hovRight) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !utilSubOpen) {
                    utilSubOpen = true; combatSubOpen = false; posSubOpen = false; ctxSubOpen = false;
                    utilTween = Math.max(utilTween, 0.2f);
                } else {
                    utilSubOpen = !utilSubOpen; combatSubOpen = false; posSubOpen = false; ctxSubOpen = false;
                    if (utilSubOpen) utilTween = Math.max(utilTween, 0.2f);
                }
            } else if (hovBottom) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !ctxSubOpen) {
                    ctxSubOpen = true; combatSubOpen = false; posSubOpen = false; utilSubOpen = false;
                    ctxTween = Math.max(ctxTween, 0.2f);
                } else {
                    ctxSubOpen = !ctxSubOpen; combatSubOpen = false; posSubOpen = false; utilSubOpen = false;
                    if (ctxSubOpen) ctxTween = Math.max(ctxTween, 0.2f);
                }
            } else if (hovCenter) {
                pendingAction = WheelAction.CANCEL_ALL;
                suppressUntilMouseUp = true;
                wheelActive = false;
            }
        }
        wasLeftDown = leftDown;
    }
}
