package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderModel;
import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachineAdvancedModel;
import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachinePrototypeModel;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;

public class ShadowedHeartsClient {
    public static void init() {
        EntityModelLayerRegistry.register(
            AuraReaderModel.LAYER_LOCATION,
            AuraReaderModel::createBodyLayer
        );
        EntityModelLayerRegistry.register(
            SnagMachineAdvancedModel.LAYER_LOCATION,
            SnagMachineAdvancedModel::createBodyLayer
        );
        EntityModelLayerRegistry.register(
            SnagMachinePrototypeModel.LAYER_LOCATION,
            SnagMachinePrototypeModel::createBodyLayer
        );
    }
}
