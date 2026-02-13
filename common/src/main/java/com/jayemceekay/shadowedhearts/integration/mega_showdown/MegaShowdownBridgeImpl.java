package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.utils.AspectUtils;
import net.minecraft.world.item.Item;

public class MegaShowdownBridgeImpl implements MegaShowdownBridge {
    @Override
    public Item createShadowiumZ(Item.Properties properties) {
        return new ShadowiumZ(properties);
    }

    @Override
    public void revertMegaShowdownAspects(Pokemon pokemon) {
        AspectUtils.revertPokemonsIfRequired(pokemon, true);
    }

}
