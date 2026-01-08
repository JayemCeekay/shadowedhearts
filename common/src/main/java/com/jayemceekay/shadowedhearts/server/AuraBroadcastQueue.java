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
                    for (BroadcastTask task : QUEUE) {
                        if (task.entity != null && !task.entity.isRemoved()) {
                            ShadowedHeartsNetworkingUtils.broadcastAuraStartToTracking(task.entity, task.heightMultiplier, task.sustainOverride);
                        }
                    }
                    QUEUE.clear();
                }
            }
        });
    }

    public static void queueBroadcast(Entity entity, float heightMultiplier, int sustainOverride) {
        synchronized (QUEUE) {
            QUEUE.add(new BroadcastTask(entity, heightMultiplier, sustainOverride));
        }
    }

    private record BroadcastTask(Entity entity, float heightMultiplier, int sustainOverride) {}
}
