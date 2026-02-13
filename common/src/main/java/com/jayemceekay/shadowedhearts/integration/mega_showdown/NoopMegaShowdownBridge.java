package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.world.item.Item;

public class NoopMegaShowdownBridge implements MegaShowdownBridge {
    @Override
    public Item createShadowiumZ(Item.Properties properties) {
        return new Item(properties);
    }

    @Override
    public void revertMegaShowdownAspects(Pokemon pokemon) {
    }
}
