package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.network.trainer.TrainerEditorRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client-only transport boundary; late replies cannot reopen a dismissed editor. */
public final class TrainerEditorClient {
    private static int nextRequestId;
    private static TrainerEditorScreen active;
    private TrainerEditorClient() {}

    public static void open(Screen parent, String targetUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            active = new TrainerEditorScreen(parent, targetUuid == null ? "" : targetUuid);
            minecraft.setScreen(active);
        });
    }

    static int send(TrainerEditorScreen screen, JsonObject request) {
        int requestId = ++nextRequestId;
        request.addProperty("requestId", requestId);
        String json = request.toString();
        if (json.length() > 8192) throw new IllegalArgumentException(tr("screen.shadowedhearts.trainer_editor.this_complete_setup_is_too_large_for_one_save_reduce_its_inline_pool_entries_or"));
        if (Minecraft.getInstance().getConnection() == null) throw new IllegalStateException(tr("screen.shadowedhearts.trainer_editor.join_a_world_or_server_to_edit_trainer_settings"));
        if (active != screen) throw new IllegalStateException(tr("screen.shadowedhearts.trainer_editor.this_editor_session_is_no_longer_active"));
        new TrainerEditorRequestPacket(json).sendToServer();
        return requestId;
    }

    public static void receive(String json) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            TrainerEditorScreen screen = active;
            if (screen == null || !screen.isOpenAt(minecraft.screen)) return;
            try {
                JsonObject response = JsonParser.parseString(json).getAsJsonObject();
                screen.receive(response);
            } catch (RuntimeException invalid) {
                screen.showError(tr("screen.shadowedhearts.trainer_editor.could_not_read_the_server_s_editor_response"));
            }
        });
    }

    static void closed(TrainerEditorScreen screen) {
        if (active == screen) active = null;
    }
}
