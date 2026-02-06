package com.jayemceekay.shadowedhearts.server;

import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetworkingUtils;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class AuraBroadcastQueue {
    private static final List<BroadcastTask> QUEUE = new ArrayList<>();

    public static void init() {
        TickEvent.SERVER_POST.register(server -> {
            synchronized (QUEUE) {
                if (!QUEUE.isEmpty()) {
                    List<BroadcastTask> toRemove = new ArrayList<>();
                    for (BroadcastTask task : QUEUE) {
                        task.delay--;
                        if (task.delay <= 0) {
                            if (task.entity != null && !task.entity.isRemoved()) {
                                ShadowedHeartsNetworkingUtils.broadcastAuraStartToTracking(task.entity, task.heightMultiplier, task.sustainOverride);
                            }
                            toRemove.add(task);
                        }
                    }
                    QUEUE.removeAll(toRemove);
                }
            }
        });
    }

    public static void queueBroadcast(Entity entity, float heightMultiplier, int sustainOverride) {
        queueBroadcast(entity, heightMultiplier, sustainOverride, 0);
    }

    public static void queueBroadcast(Entity entity, float heightMultiplier, int sustainOverride, int delay) {
        synchronized (QUEUE) {
            QUEUE.add(new BroadcastTask(entity, heightMultiplier, sustainOverride, delay));
        }
    }

    private static class BroadcastTask {
        Entity entity;
        float heightMultiplier;
        int sustainOverride;
        int delay;

        BroadcastTask(Entity entity, float heightMultiplier, int sustainOverride, int delay) {
            this.entity = entity;
            this.heightMultiplier = heightMultiplier;
            this.sustainOverride = sustainOverride;
            this.delay = delay;
        }
    }
}
