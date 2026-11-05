package com.jayemceekay.shadowedhearts.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public interface MoonwoundBossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> shadowedhearts$getBossEvents();
}
