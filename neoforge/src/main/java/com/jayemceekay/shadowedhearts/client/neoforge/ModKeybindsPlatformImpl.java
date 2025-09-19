package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeybindsPlatformImpl {
    private static final List<KeyMapping> PENDING = new ArrayList<>();

    public static void register(KeyMapping mapping) {
        // Defer until the proper registration event
        PENDING.add(mapping);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping km : PENDING) {
            event.register(km);
        }
        PENDING.clear();
    }
}
