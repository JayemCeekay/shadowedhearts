package com.jayemceekay.shadowedhearts.snag;

import net.minecraft.world.item.Item;

/**
 * Specialized Poké Ball. For now behaves like a normal item placeholder.
 * Snagging logic will be handled via battle capture hook.
 */
public class SnagBallItem extends Item {
    public SnagBallItem(Properties properties) {
        super(properties);
    }
}
