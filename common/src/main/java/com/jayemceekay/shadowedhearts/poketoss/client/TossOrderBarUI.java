package com.jayemceekay.shadowedhearts.poketoss.client;

import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Bottom-bar Toss Order UI (separated from the radial wheel).
 * Tick-driven carousel simulation with inertia and snapping, render-only interpolation.
 * Uses TossOrderCommon for labels and action dispatch.
 */
public final class TossOrderBarUI {
    private TossOrderBarUI() {}

    // Wheel state
    private static boolean wheelActive = false;
    private static boolean prevWheelActive = false;
    private static boolean wasLeftDown = false;
    private static boolean wasRightDown = false;
    private static boolean suppressUntilMouseUp = false;

    // Alternate bottom-bar menu model
    private static int barCategoryIndex = 0;
    private static int barOrderIndex = 0; // index within the current category's orders
    private static boolean barChoosingCategory = true;
    // Seamless switching flags
    private static boolean justSwitchedToOrders = false;
    private static boolean justSwitchedToCategory = false;

    // Carousel (tick-driven) state
    private static float catShiftPrev = 0f, catShiftCurr = 0f, catVel = 0f;
    private static float ordShiftPrev = 0f, ordShiftCurr = 0f, ordVel = 0f;

    // Tuning (PD spring + input)
    private static final float WHEEL_SLOTS_PER_NOTCH = 1.5f; // slots per wheel notch impulse (to velocity)
    private static final float FREQ_HZ = 0.15f;                // natural frequency
    private static final float ZETA    = 0.95f;                // damping ratio (1 = critical)
    private static final float MAX_SPEED = 6.0f;              // slots/second clamp
    private static final float SNAP_POS_EPS = 1e-3f;
    private static final float SNAP_VEL_EPS = 1e-3f;
    private static final float VISIBLE_RADIUS = 2.75f;  // slots radius for cosine window (shows at least 3 items)
    private static final float CLICK_BIAS = 0.15f;
    private static final float VEL_BIAS_THRESHOLD = 0.05f;

    private static TossOrderCommon.WheelAction pendingAction = TossOrderCommon.WheelAction.NONE;

    public static boolean isActive() { return wheelActive; }

    private static int wrap(int idx, int size) {
        if (size <= 0) return 0;
        int m = idx % size;
        return m < 0 ? m + size : m;
    }


    // Scroll: convert wheel delta to velocity impulses for the active carousel
    public static void onScrollDelta(double vertical) {
        if (!wheelActive) return;
        float impulse = (float) vertical * WHEEL_SLOTS_PER_NOTCH; // slots/sec impulse; dt scales in integrator
        if (barChoosingCategory) catVel += impulse; else ordVel += impulse;
    }

    /** Tick/update: handle key toggling, close/open, recenter mouse, dispatch actions, and advance carousel sim. */
    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Toggle the wheel on key press
        boolean pressed = ModKeybinds.consumeOrderWheelPress();
        if (pressed) {
            if (WhistleSelectionClient.isHoldingWhistle()) {
                wheelActive = !wheelActive;
                if (wheelActive) {
                    // Reset bar state and recenter cursor
                    barChoosingCategory = true;
                    barCategoryIndex = 0;
                    barOrderIndex = 0;
                    catShiftPrev = catShiftCurr = 0f; catVel = 0f;
                    ordShiftPrev = ordShiftCurr = 0f; ordVel = 0f;
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

        // When the wheel closes, execute any pending action and reset state
        if (!wheelActive && prevWheelActive) {
            if (pendingAction != TossOrderCommon.WheelAction.NONE) {
                TossOrderCommon.dispatch(pendingAction);
                pendingAction = TossOrderCommon.WheelAction.NONE;
            }
            suppressUntilMouseUp = false;
        }

        // === Tick-driven simulation for the bottom-bar carousel ===
        if (wheelActive) {
            // 1) Preserve previous shifts for interpolation
            catShiftPrev = catShiftCurr;
            ordShiftPrev = ordShiftCurr;

            // Fixed dt (MC tick ~20 Hz)
            float dt = 1.0f / 20.0f;

            int transfers = 0;
            // 2) Advance simulation for whichever bar is active (critically-damped PD spring)
            if (barChoosingCategory) {
                int size = TossOrderCommon.CATEGORY_LABELS.length;
                // Normalize phase to (-0.5 .. +0.5] by transferring whole slots to index
                while (catShiftCurr > 0.5f)  { barCategoryIndex = wrap(barCategoryIndex + 1, size); catShiftCurr -= 1f; transfers++; }
                while (catShiftCurr <= -0.5f){ barCategoryIndex = wrap(barCategoryIndex - 1, size); catShiftCurr += 1f; transfers--; }
                catShiftPrev -= transfers;
                // PD spring to center (target 0)
                final float omega = (float)(2.0 * Math.PI * FREQ_HZ);
                final float k = omega * omega;
                final float c = 2.0f * ZETA * omega;
                float acc = -k * catShiftCurr - c * catVel;
                catVel += acc * dt;
                catVel = Mth.clamp(catVel, -MAX_SPEED, MAX_SPEED);
                catShiftCurr += catVel * dt;
                if (Math.abs(catShiftCurr) < SNAP_POS_EPS && Math.abs(catVel) < SNAP_VEL_EPS) { catShiftCurr = 0f; catVel = 0f; }
            } else {
                // Determine orders set/size
                String[] orders = switch (wrap(barCategoryIndex, TossOrderCommon.CATEGORY_LABELS.length)) {
                    case 0 -> TossOrderCommon.COMBAT_LABELS;
                    case 1 -> TossOrderCommon.POSITION_LABELS;
                    case 2 -> TossOrderCommon.UTILITY_LABELS;
                    case 3 -> TossOrderCommon.GATHERING_LABELS;
                    default -> TossOrderCommon.CANCEL_LABELS;
                };
                int sizeO = orders.length;
                transfers = 0;
                while (ordShiftCurr > 0.5f)  { barOrderIndex = wrap(barOrderIndex + 1, sizeO); ordShiftCurr -= 1f; transfers++; }
                while (ordShiftCurr <= -0.5f){ barOrderIndex = wrap(barOrderIndex - 1, sizeO); ordShiftCurr += 1f; transfers--; }
                ordShiftPrev -= transfers;
                final float omega = (float)(2.0 * Math.PI * FREQ_HZ);
                final float k = omega * omega;
                final float c = 2.0f * ZETA * omega;
                float acc = -k * ordShiftCurr - c * ordVel;
                ordVel += acc * dt;
                ordVel = Mth.clamp(ordVel, -MAX_SPEED, MAX_SPEED);
                ordShiftCurr += ordVel * dt;
                if (Math.abs(ordShiftCurr) < SNAP_POS_EPS && Math.abs(ordVel) < SNAP_VEL_EPS) { ordShiftCurr = 0f; ordVel = 0f; }
            }
        }

        prevWheelActive = wheelActive;
    }

    /** 2D HUD overlay rendering and interaction for the bottom bar UI. */
    public static void onHudRender(GuiGraphics gfx, float partialTick) {
        if (!wheelActive) { wasLeftDown = false; wasRightDown = false; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) { wasLeftDown = false; wasRightDown = false; return; }
        if (mc.screen != null) { wasLeftDown = false; wasRightDown = false; return; }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int baseDim = Math.min(w, h);

        // Convert absolute mouse position to GUI-scaled coordinates
        double mx = mc.mouseHandler.xpos() * (double) w / (double) mc.getWindow().getWidth();
        double my = mc.mouseHandler.ypos() * (double) h / (double) mc.getWindow().getHeight();
        int mouseX = (int) Math.round(mx);
        //int mouseY = (int) Math.round(my);

        // Dim background
        gfx.fill(0, 0, w, h, 0x66000000);

        int white = 0xFFFFFFFF;
        int base = 0xCC1E1E1E;
        int hi = 0xFFE0FFE0;
        int selBorder = 0xFF44EE44;

        // Scale to player's hotbar and window size
        final int hotbarW = 182;
        final int hotbarH = 22;
        float barWinScale = Math.max(0.85f, Math.min(1.75f, (float) Math.min(w, h) / 240f));
        int totalBarW = Math.max(160, Math.round(hotbarW * barWinScale));
        int scaledHotbarH = Math.max(18, Math.round(hotbarH * barWinScale));
        int gap = Math.max(Math.round(2 * barWinScale), (int) Math.round(totalBarW * 0.02))*3;
        int minItemW = Math.max(48, Math.round(64 * barWinScale * 0.9f));
        int itemW = Math.max(minItemW, (totalBarW - 2 * gap) / 3);
        int itemH = Math.max(Math.round(20 * barWinScale), scaledHotbarH);
        int stepX = itemW + gap;
        int midX = w / 2;

        // Sit just above the hotbar
        int catY1 = h - (scaledHotbarH + Math.max(3, gap));
        int catY0 = catY1 - itemH;

        var font = mc.font;

        // Interpolated shifts for the carousels
        if (justSwitchedToOrders) { ordShiftPrev = ordShiftCurr; justSwitchedToOrders = false; }
        if (justSwitchedToCategory) { catShiftPrev = catShiftCurr; justSwitchedToCategory = false; }
        float pt = Mth.clamp(partialTick, 0f, 1f);
        float catDisplayShift = Mth.lerp(pt, catShiftPrev, catShiftCurr);
        float ordDisplayShift = Mth.lerp(pt, ordShiftPrev, ordShiftCurr);

        // Category bar
        float catShift = catDisplayShift;
        float baseCenterX = midX - catShift * stepX;
        for (int s = -3; s <= 3; s++) {
            int newIdx = wrap(barCategoryIndex + s, TossOrderCommon.CATEGORY_LABELS.length);

            float centerXf = baseCenterX + s * stepX;
            float d = Math.abs(s - catShift);
            float weight = (d >= VISIBLE_RADIUS) ? 0f : 0.5f * (float)(1.0 + Math.cos(Math.PI * (d / VISIBLE_RADIUS)));
            float scale = Mth.lerp(weight, 0.80f, 1.20f);
            int alpha = Math.round(255 * weight);

            float wSf = itemW * scale;
            float hSf = itemH * scale;
            int wS = Math.max(0, Math.round(wSf));
            int hS = Math.max(0, Math.round(hSf));
            int centerX = Math.round(centerXf);
            int x0 = centerX - wS / 2;
            int y0 = catY0 + (itemH - hS) / 2;
            int x1 = x0 + wS;
            int y1 = y0 + hS;

            boolean primary = s == 0;
            int fillColor = (primary ? hi : base) & 0x00FFFFFF | ((alpha & 0xFF) << 24);
            if (alpha != 0) gfx.fill(x0, y0, x1, y1, fillColor);
            if (primary && alpha != 0) {
                gfx.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, selBorder);
            }
            int textColor = ((alpha & 0xFF) << 24) | 0xFFFFFF;
            if (alpha != 0 && wS > 0 && hS > 0) {
                var pose = gfx.pose();
                pose.pushPose();
                float textScale = scale / 1.25f;
                float tx = (x0 + x1) / 2f;
                float ty = y0 + hS / 2f;
                pose.translate(tx, ty, 0f);
                pose.scale(textScale, textScale, 1f);
                gfx.drawCenteredString(font, Component.literal(TossOrderCommon.CATEGORY_LABELS[newIdx]), 0, -font.lineHeight / 2, textColor);
                pose.popPose();
            }
        }

        // Orders bar (only if category is confirmed)
        if (!barChoosingCategory) {
            int ordersY1 = catY0 - Math.max(3, gap);
            int ordersY0 = ordersY1 - itemH;

            String[] orders = switch (wrap(barCategoryIndex, TossOrderCommon.CATEGORY_LABELS.length)) {
                case 0 -> TossOrderCommon.COMBAT_LABELS;
                case 1 -> TossOrderCommon.POSITION_LABELS;
                case 2 -> TossOrderCommon.UTILITY_LABELS;
                case 3 -> TossOrderCommon.GATHERING_LABELS;
                default -> TossOrderCommon.CANCEL_LABELS; // 4
            };

            float ordShift = ordDisplayShift;
            float baseCenterX2 = midX - ordShift * stepX;
            for (int s = -3; s <= 3; s++) {
                int newIdx = wrap(barOrderIndex + s, orders.length);

                float centerXf = baseCenterX2 + s * stepX;
                float d = Math.abs(s - ordShift);
                float weight = (d >= VISIBLE_RADIUS) ? 0f : 0.5f * (float)(1.0 + Math.cos(Math.PI * (d / VISIBLE_RADIUS)));
                float scale = Mth.lerp(weight, 0.80f, 1.20f);
                int alpha = Math.round(255 * weight);

                float wSf = itemW * scale;
                float hSf = itemH * scale;
                int wS = Math.max(0, Math.round(wSf));
                int hS = Math.max(0, Math.round(hSf));
                int centerX = Math.round(centerXf);
                int x0 = centerX - wS / 2;
                int y0 = ordersY0 + (itemH - hS) / 2;
                int x1 = x0 + wS;
                int y1 = y0 + hS;

                boolean primary = s == 0;
                int fillColor = (primary ? hi : base) & 0x00FFFFFF | ((alpha & 0xFF) << 24);
                if (alpha != 0) gfx.fill(x0, y0, x1, y1, fillColor);
                if (primary && alpha != 0) {
                    gfx.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, selBorder);
                }
                int textColor = ((alpha & 0xFF) << 24) | 0xFFFFFF;
                if (alpha != 0 && wS > 0 && hS > 0) {
                    var pose = gfx.pose();
                    pose.pushPose();
                    float textScale = scale;
                    float tx = (x0 + x1) / 2f;
                    float ty = y0 + hS / 2f;
                    pose.translate(tx, ty, 0f);
                    pose.scale(textScale, textScale, 1f);
                    gfx.drawCenteredString(font, Component.literal(orders[newIdx]), 0, -font.lineHeight / 2, textColor);
                    pose.popPose();
                }
            }
        }

        // Simple cursor for consistency (in bar mode we don't draw the center crosshair)
        int curSize = Math.max(6, Math.min(12, (int)Math.round(baseDim * 0.0125)));
        int half = curSize / 2;
        gfx.fill(mouseX - half - 1, h - half - 1, mouseX + half + 1, h - half + 1, 0x00000000);

        boolean leftDown = mc.mouseHandler.isLeftPressed();
        boolean rightDown = mc.options.keyUse.isDown();
        if (suppressUntilMouseUp) {
            if (!leftDown && !rightDown) suppressUntilMouseUp = false;
        } else if (!barChoosingCategory && rightDown && !wasRightDown) {
            // Cancel order selection and go back to choosing a category
            barChoosingCategory = true;
            // Reset tick state so returning later starts clean
            catShiftPrev = catShiftCurr = 0f; catVel = 0f;
            ordShiftPrev = ordShiftCurr = 0f; ordVel = 0f;
            barOrderIndex = 0;
            justSwitchedToCategory = true;
            suppressUntilMouseUp = true;
        } else if (leftDown && !wasLeftDown) {
            if (barChoosingCategory) {
                // Snap category selection with a slight bias toward current velocity direction
                {
                    int size = TossOrderCommon.CATEGORY_LABELS.length;
                    float tSel = catShiftCurr;
                    float vSel = catVel;
                    float sgn = Math.abs(vSel) > VEL_BIAS_THRESHOLD ? Math.signum(vSel) : 0f;
                    int off = Math.round(tSel + CLICK_BIAS * sgn);
                    if (off != 0) {
                        barCategoryIndex = wrap(barCategoryIndex + off, size);
                    }
                    // lock category carousel at center
                    catShiftPrev = catShiftCurr = 0f;
                    catVel = 0f;
                }
                barChoosingCategory = false;
                barOrderIndex = 0;
                // reset orders tick state
                ordShiftPrev = ordShiftCurr = 0f; ordVel = 0f;
                justSwitchedToOrders = true;
                suppressUntilMouseUp = true; // avoid double-fire on same click
            } else {
                // Confirm order
                int cat = wrap(barCategoryIndex, TossOrderCommon.CATEGORY_LABELS.length);
                String[] orders = switch (cat) {
                    case 0 -> TossOrderCommon.COMBAT_LABELS;
                    case 1 -> TossOrderCommon.POSITION_LABELS;
                    case 2 -> TossOrderCommon.UTILITY_LABELS;
                    case 3 -> TossOrderCommon.GATHERING_LABELS;
                    default -> TossOrderCommon.CANCEL_LABELS; // 4
                };
                // Snap order selection with a slight bias toward current velocity direction
                {
                    int sizeO = orders.length;
                    float tSel = ordShiftCurr;
                    float vSel = ordVel;
                    float sgn = Math.abs(vSel) > VEL_BIAS_THRESHOLD ? Math.signum(vSel) : 0f;
                    int off = Math.round(tSel + CLICK_BIAS * sgn);
                    if (off != 0) {
                        barOrderIndex = wrap(barOrderIndex + off, sizeO);
                    }
                    // lock orders carousel at center
                    ordShiftPrev = ordShiftCurr = 0f;
                    ordVel = 0f;
                }
                String chosen = orders[wrap(barOrderIndex, orders.length)];
                TossOrderCommon.WheelAction action = TossOrderCommon.WheelAction.NONE;
                if (cat == 0) { // Combat
                    if ("Attack Target".equals(chosen)) action = TossOrderCommon.WheelAction.COMBAT_ENGAGE;
                    else if ("Guard Target".equals(chosen)) action = TossOrderCommon.WheelAction.COMBAT_GUARD;
                } else if (cat == 1) { // Position
                    if ("Move To".equals(chosen)) action = TossOrderCommon.WheelAction.POSITION_MOVE_TO;
                    else if ("Hold Position".equals(chosen)) action = TossOrderCommon.WheelAction.POSITION_HOLD;
                } else if (cat == 2) { // Utility
                    if ("Regroup to Me".equals(chosen)) action = TossOrderCommon.WheelAction.UTILITY_REGROUP_TO_ME;
                } else if (cat == 3) { // Context
                    if ("Hold At Me".equals(chosen)) action = TossOrderCommon.WheelAction.CONTEXT_HOLD_AT_ME;
                } else if (cat == 4) { // Cancel
                    if ("Cancel All".equals(chosen)) action = TossOrderCommon.WheelAction.CANCEL_ALL;
                }
                if (action != TossOrderCommon.WheelAction.NONE) {
                    pendingAction = action;
                }
                wheelActive = false;
                suppressUntilMouseUp = true;
            }
        }
        wasLeftDown = leftDown;
        wasRightDown = rightDown;
    }
}
