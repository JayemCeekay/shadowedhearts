package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowFriendshipProgression;

/** Signed pending friendship change; serialized alongside the persistent data fallback. */
public final class FriendshipBufferProperty implements CustomPokemonProperty {
    private final int value;
    public FriendshipBufferProperty(int value) { this.value = value; }
    public int getValue() { return value; }
    @Override public String asString() { return "sh_friendship_buf=" + value; }
    @Override public void apply(Pokemon pokemon) { ShadowFriendshipProgression.setBuffer(pokemon, value); }
    @Override public boolean matches(Pokemon pokemon) { return ShadowFriendshipProgression.buffer(pokemon) == value; }
}
