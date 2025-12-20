package com.jayemceekay.shadowedhearts.client.fabric;

import com.jayemceekay.shadowedhearts.client.integration.accessories.SnagMachineAccessoryRenderer;
import com.jayemceekay.shadowedhearts.core.ModItems;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;

public class AccessoriesRendererBridge {
    public static void registerRenderers() {
        AccessoriesRendererRegistry.registerRenderer(ModItems.SNAG_MACHINE_PROTOTYPE.get(), SnagMachineAccessoryRenderer::new);
        AccessoriesRendererRegistry.registerRenderer(ModItems.SNAG_MACHINE_ADVANCED.get(), SnagMachineAccessoryRenderer::new);
    }
}
