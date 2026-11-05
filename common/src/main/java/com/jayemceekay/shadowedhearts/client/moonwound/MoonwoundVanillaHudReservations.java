package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHudRect;
import com.jayemceekay.shadowedhearts.mixin.client.MoonwoundBossHealthOverlayAccessor;
import com.jayemceekay.shadowedhearts.mixin.client.MoonwoundChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.scores.DisplaySlot;

import java.util.List;

/** Vanilla HUD space that a Moonwound objective infobox must leave clear. */
public final class MoonwoundVanillaHudReservations {
    private MoonwoundVanillaHudReservations() { }

    public static void add(Minecraft minecraft, int width, int height, List<AuraReaderHudRect> occupied) {
        if (width <= 0 || height <= 0) return;

        addClipped(occupied, width, height, width / 2 - 12, height / 2 - 12,
                width / 2 + 12, height / 2 + 12);
        addClipped(occupied, width, height, 0, height - 60, width, height);
        // Keep a stable lane for incoming toasts/effects instead of moving the card for each icon.
        addClipped(occupied, width, height, width - 168, 0, width, 64);
        addBossBars(minecraft, width, height, occupied);
        addChat(minecraft, width, height, occupied);

        if (minecraft.level != null) {
            var scoreboard = minecraft.level.getScoreboard();
            var team = minecraft.player == null ? null : minecraft.player.getTeam();
            var teamSlot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
            var objective = teamSlot == null ? null : scoreboard.getDisplayObjective(teamSlot);
            if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective != null) {
                addClipped(occupied, width, height, width * 2 / 3, height / 3, width, height - 60);
            }
        }
    }

    private static void addBossBars(Minecraft minecraft, int width, int height, List<AuraReaderHudRect> occupied) {
        var events = ((MoonwoundBossHealthOverlayAccessor) minecraft.gui.getBossOverlay()).shadowedhearts$getBossEvents();
        int y = 12;
        for (var event : events.values()) {
            int nameWidth = minecraft.font.width(event.getName());
            int nameLeft = width / 2 - nameWidth / 2;
            addClipped(occupied, width, height, Math.min(width / 2 - 91, nameLeft), y - 9,
                    Math.max(width / 2 + 91, nameLeft + nameWidth + 1), y + 5);
            // Match BossHealthOverlay's 182x5 bar, title, row spacing and height limit.
            y += 19;
            if (y >= height / 3) break;
        }
    }

    private static void addChat(Minecraft minecraft, int width, int height, List<AuraReaderHudRect> occupied) {
        if (minecraft.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return;
        var chat = minecraft.gui.getChat();
        var accessor = (MoonwoundChatComponentAccessor) chat;
        var messages = accessor.shadowedhearts$getTrimmedMessages();
        if (messages.isEmpty()) return;

        // ChatComponent scales first, then translates 4 pixels; its background begins at x=-4.
        float scale = (float) chat.getScale();
        if (scale <= 0) return;
        int chatWidth = Mth.ceil(chat.getWidth() / scale);
        int bottom = Mth.floor((height - 40) / scale);
        int lineHeight = (int) (9.0 * (minecraft.options.chatLineSpacing().get() + 1.0));
        double opacity = minecraft.options.chatOpacity().get() * 0.8999999761581421 + 0.10000000149011612;
        int firstLine = Math.max(0, accessor.shadowedhearts$getChatScrollbarPos());
        int guiTicks = minecraft.gui.getGuiTicks();
        int lineLimit = Math.min(chat.getLinesPerPage(), messages.size() - firstLine);
        int firstVisibleRow = -1;
        int lastVisibleRow = -1;
        for (int row = 0; row < lineLimit; row++) {
            var message = messages.get(firstLine + row);
            if (message == null) continue;
            int age = guiTicks - message.addedTime();
            if (age >= 200) continue;
            double fade = Mth.clamp((1.0 - age / 200.0) * 10.0, 0.0, 1.0);
            // Vanilla skips both text and background once the text alpha is at most three.
            if ((int) (255.0 * fade * fade * opacity) <= 3) continue;
            if (firstVisibleRow < 0) firstVisibleRow = row;
            lastVisibleRow = row;
        }
        if (firstVisibleRow >= 0) {
            // One bounds rectangle keeps placement work independent of the number of visible lines.
            addClipped(occupied, width, height, 0,
                    Mth.floor((bottom - (lastVisibleRow + 1) * lineHeight) * scale),
                    Mth.ceil((chatWidth + 12) * scale),
                    Mth.ceil((bottom - firstVisibleRow * lineHeight + 2) * scale));
        }
    }

    private static void addClipped(List<AuraReaderHudRect> occupied, int width, int height,
                                   int left, int top, int right, int bottom) {
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(width, right);
        bottom = Math.min(height, bottom);
        if (right > left && bottom > top) occupied.add(new AuraReaderHudRect(left, top, right, bottom));
    }
}
