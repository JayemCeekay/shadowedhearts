package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleFeedback;
import com.github.yajatkaul.mega_showdown.components.MegaShowdownDataComponents;
import com.github.yajatkaul.mega_showdown.item.custom.z.ElementalZCrystal;
import com.github.yajatkaul.mega_showdown.utils.RegistryLocator;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.registry.ModCreativeTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

public class ShadowiumZ extends ElementalZCrystal {
    public ShadowiumZ(Item.Properties properties) {
        super(new Item.Properties().component(MegaShowdownDataComponents.REGISTRY_TYPE_COMPONENT.get(), RegistryLocator.Z_CRYSTAL_ITEM)
                .component(MegaShowdownDataComponents.RESOURCE_LOCATION_COMPONENT.get(), ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "shadowium_z")).arch$tab(ModCreativeTabs.SHADOWED_HEARTS_TAB), List.of("Arceus"), true, ElementalTypes.get("Shadow"));
    }

    @Override
    public void apply(Pokemon pokemon) {
        ItemRuleSettings rules = ItemRuleSettings.forPokemon(pokemon);
        if (rules != null && rules.effectEnabled("shadowiumZ")) super.apply(pokemon);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        if (!ItemRuleFeedback.appendDisabled(stack, tooltip)) super.appendHoverText(stack, context, tooltip, flag);
    }
}
