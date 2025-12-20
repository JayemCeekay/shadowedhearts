package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.AspectsUpdatePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.Nullable;


/**
 * Service façade for synchronizing shadow state between stored Cobblemon Pokemon and live entities.
 */
public final class ShadowService {

    /**
     * Make a Pokemon (and its live entity if present) shadow/non-shadow.
     * @param pokemon stored Pokemon object
     * @param live currently loaded entity for that Pokemon (nullable)
     * @param shadow true to set shadow, false to clear
     */
    public static void setShadow(Pokemon pokemon, @Nullable PokemonEntity live, boolean shadow) {
        PokemonAspectUtil.setShadowAspect(pokemon, shadow);
        // Ensure required supporting aspects exist when shadowed
        PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
        if (live != null) {
            ShadowPokemonData.set(live, shadow, PokemonAspectUtil.getHeartGauge(pokemon));
        }
        // Proactively sync aspect changes to observing players (party/PC/chamber UI) and mark store dirty.
        // This ensures client UIs (e.g., Summary screen) update immediately without requiring a PC swap.
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            pokemon.onChange(new AspectsUpdatePacket(supplier, pokemon.getAspects()));
        } catch (Throwable t) {
            // Fallback: at least mark store changed so persistence occurs even if packet construction fails
            pokemon.onChange(null);
        }
        // If live == null but is in world somewhere, optionally locate it to sync (handled by store observers above).
    }

    /** Set heart gauge absolute meter [0..speciesMax from config]. */
    public static void setHeartGauge(Pokemon pokemon, @Nullable PokemonEntity live, int meter) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int clamped = Math.max(0, Math.min(max, meter));
        PokemonAspectUtil.setHeartGaugeValue(pokemon, clamped);
        // Ensure required supporting aspects exist when shadowed (no-op if not shadowed)
        PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
        if (live != null) ShadowPokemonData.set(live, ShadowPokemonData.isShadow(live), PokemonAspectUtil.getHeartGauge(pokemon));
        // Proactively sync aspect changes to observing players and mark store dirty so client UI updates live.
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            pokemon.onChange(new AspectsUpdatePacket(supplier, pokemon.getAspects()));
        } catch (Throwable t) {
            pokemon.onChange(null);
        }
    }

    /** Convenience: fully purified (0) and not shadow. */
    public static void fullyPurify(Pokemon pokemon, @Nullable PokemonEntity live) {
        setShadow(pokemon, live, false);
        setHeartGauge(pokemon, live, 0);
    }

    /** Convenience: fully corrupted (speciesMax) and shadow. */
    public static void fullyCorrupt(Pokemon pokemon, @Nullable PokemonEntity live) {
        setShadow(pokemon, live, true);
        setHeartGauge(pokemon, live, HeartGaugeConfig.getMax(pokemon));
    }
}
