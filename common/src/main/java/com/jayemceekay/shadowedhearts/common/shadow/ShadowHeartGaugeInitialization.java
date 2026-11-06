package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.network.PokemonStateSync;

import java.util.IdentityHashMap;
import java.util.Map;

/** Delays only default gauge creation while native properties are still choosing species and overrides. */
public final class ShadowHeartGaugeInitialization {
    private static final ThreadLocal<Map<Pokemon, Scope>> SCOPES = new ThreadLocal<>();

    private ShadowHeartGaugeInitialization() { }

    public static void applyProperties(Pokemon pokemon, Runnable apply) {
        Map<Pokemon, Scope> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new IdentityHashMap<>();
            SCOPES.set(scopes);
        }
        Scope scope = scopes.computeIfAbsent(pokemon, ignored -> new Scope());
        scope.depth++;
        try {
            apply.run();
        } finally {
            if (--scope.depth == 0) {
                scopes.remove(pokemon);
                if (scopes.isEmpty()) SCOPES.remove();
            }
        }

        // Reached only after successful application and outside the outermost scope.
        // Do not replay Shadow initialization: that would also reapply IV/move rules.
        if (scope.depth == 0 && scope.defaultRequested && ShadowAspectUtil.hasShadowAspect(pokemon)
                && ShadowStateInitialization.initializeMissingHeartGauge(pokemon)) {
            PokemonStateSync.syncProperties(pokemon);
        }
    }

    static boolean deferDefault(Pokemon pokemon) {
        Map<Pokemon, Scope> scopes = SCOPES.get();
        Scope scope = scopes == null ? null : scopes.get(pokemon);
        if (scope == null) return false;
        scope.defaultRequested = true;
        return true;
    }

    private static final class Scope {
        private int depth;
        private boolean defaultRequested;
    }
}
