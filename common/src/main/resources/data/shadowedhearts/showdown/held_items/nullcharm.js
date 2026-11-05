({
    name: "Null Charm",
    spritename: "nullcharm",
    // Apply the ward after Shadow Engine, weather and ability effectiveness rules.
    // Native item events handle Klutz, Embargo and Magic Room suppression.
    onEffectivenessPriority: -1,
    onEffectiveness(typeMod, target, type, move) {
        if (this.dex.data.Scripts.shadowedhearts?.Config?.nullCharm?.wardEnabled === false) return;
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.shadowMoves || {superEffectiveEnabled: true};
        if (!config.superEffectiveEnabled) return;
        if (move?.type === "Shadow" && typeMod > 0) return 0;
    },
    // GO mode is handled in Shadow Engine before its damage factors are combined,
    // so the attacker's exact bonus is removed without reciprocal-rounding drift.
    onSourceModifyDamage(damage, source, target, move) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config;
        if (config?.nullCharm?.damageGuardEnabled === false) return;
        const shadowMoves = config?.shadowMoves || {superEffectiveEnabled: true};
        if (shadowMoves.superEffectiveEnabled || config?.GOdamageModifier?.enabled) return;
        if (source !== target && move?.type === "Shadow" && move.category !== "Status") {
            return this.chainModify(0.8);
        }
    },
    num: -1008,
    gen: 9,
    isNonstandard: "Custom",
})
