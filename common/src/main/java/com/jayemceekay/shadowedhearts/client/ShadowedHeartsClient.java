package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderModel;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;

public class ShadowedHeartsClient {
    public static void init() {
        EntityModelLayerRegistry.register(
            AuraReaderModel.LAYER_LOCATION,
            AuraReaderModel::createBodyLayer
        );
    }
}
