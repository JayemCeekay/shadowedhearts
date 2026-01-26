package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.client.integration.accessories.AuraReaderAccessoryRenderer;
import com.jayemceekay.shadowedhearts.client.integration.accessories.SnagMachineAdvancedAccessoryRenderer;
import com.jayemceekay.shadowedhearts.client.integration.accessories.SnagMachinePrototypeAccessoryRenderer;
import com.jayemceekay.shadowedhearts.core.ModItems;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;

public class AccessoriesRendererBridge {
    public static void registerRenderers() {
        AccessoriesRendererRegistry.registerRenderer(ModItems.SNAG_MACHINE_PROTOTYPE.get(), SnagMachinePrototypeAccessoryRenderer::new);
        AccessoriesRendererRegistry.registerRenderer(ModItems.SNAG_MACHINE_ADVANCED.get(), SnagMachineAdvancedAccessoryRenderer::new);
        AccessoriesRendererRegistry.registerRenderer(ModItems.AURA_READER.get(), AuraReaderAccessoryRenderer::new);
    }
}
