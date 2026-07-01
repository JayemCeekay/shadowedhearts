package com.jayemceekay.shadowedhearts.world.gen;

import java.util.List;

public class ImpactLocationSelector {

    public static boolean isBiomeAllowed(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, List<? extends String> whitelist, List<? extends String> blacklist) {
        if (!whitelist.isEmpty()) {
            boolean whitelisted = false;
            for (String entry : whitelist) {
                if (matches(biome, entry)) {
                    whitelisted = true;
                    break;
                }
            }
            if (!whitelisted) return false;
        }

        for (String entry : blacklist) {
            if (matches(biome, entry)) {
                return false;
            }
        }

        return true;
    }

    private static boolean matches(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, String entry) {
        if (entry.startsWith("#")) {
            net.minecraft.resources.ResourceLocation tagId = net.minecraft.resources.ResourceLocation.tryParse(entry.substring(1));
            if (tagId != null) {
                return biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, tagId));
            }
        } else {
            return biome.unwrapKey().map(key -> key.location().toString().equals(entry)).orElse(false);
        }
        return false;
    }
}
