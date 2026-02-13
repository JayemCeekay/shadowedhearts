package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.world.item.Item;

public interface MegaShowdownBridge {
    Item createShadowiumZ(Item.Properties properties);

    void revertMegaShowdownAspects(Pokemon pokemon);
}
