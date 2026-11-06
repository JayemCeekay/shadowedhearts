package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.client.render.block.UmbrafallCoreRenderer;
import com.jayemceekay.shadowedhearts.client.render.block.UmbrafallCoreSeepIrisRenderer;
import com.jayemceekay.shadowedhearts.client.render.iris.IrisCompat;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;

public final class UmbrafallCoreClient {
    private UmbrafallCoreClient() { }

    public static void registerRenderer() {
        BlockEntityRendererRegistry.register(ModBlockEntities.UMBRAFALL_CORE.get(), UmbrafallCoreRenderer::new);
        ClientTickEvent.CLIENT_POST.register(client -> {
            var iris = IrisCompat.IRIS_HANDLER;
            if (client.level == null || iris == null || !iris.isShaderPackInUse()) {
                UmbrafallCoreSeepIrisRenderer.clear();
            }
        });
    }
}
