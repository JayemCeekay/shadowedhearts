package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;

public class ScentCooldownProperty implements CustomPokemonProperty {
    private final long lastUseTime;

    public ScentCooldownProperty(long lastUseTime) {
        this.lastUseTime = lastUseTime;
    }

    public long getLastUseTime() {
        return lastUseTime;
    }

    @Override
    public String asString() {
        return "sh_scent_cooldown=" + lastUseTime;
    }

    @Override
    public void apply(Pokemon pokemon) {
        ShadowAspectUtil.setScentCooldown(pokemon, lastUseTime);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof ScentCooldownProperty && ((ScentCooldownProperty) p).lastUseTime == this.lastUseTime);
    }
}
