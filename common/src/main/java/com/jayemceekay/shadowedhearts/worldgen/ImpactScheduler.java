package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

public class ImpactScheduler {
    private static final Random RANDOM = new Random();

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(level -> {
            if (level instanceof ServerLevel serverLevel && !serverLevel.players().isEmpty()) {
                IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
                if (config.shadowfallActive()) {
                    if (RANDOM.nextInt(config.impactChanceOneInTicks()) == 0) {
                        attemptImpact(serverLevel);
                    }
                }
            }
        });
    }

    public static void attemptImpact(ServerLevel level) {
        ImpactLocationSelector.selectLocation(level).ifPresent(pos -> {
            CraterGenerator.generateCrater(level, pos);
            IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
            int broadcastRadius = config.meteoroidImpactBroadcastRadius();
            level.players().forEach(player -> {
                if (player.blockPosition().closerThan(pos, broadcastRadius)) {
                    player.displayClientMessage(Component.literal("A shadowfall meteoroid has impacted nearby!"), false);
                }
            });
        });
    }
}
