package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import net.minecraft.world.item.Item;

public class MegaShowdownBridgeImpl implements MegaShowdownBridge {
    @Override
    public Item createShadowiumZ(Item.Properties properties) {
        return new ShadowiumZ(properties);
    }
}
