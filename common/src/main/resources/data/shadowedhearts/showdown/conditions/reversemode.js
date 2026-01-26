({
    name: "Reverse Mode",
    effectType: "VolatileStatus",
    onStart(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode || {enabled: false};
        if (pokemon?.set?.isShadow) {
            this.add('-start', pokemon, 'Reverse Mode');
            if (pokemon.volatiles['hypermode']) {
                pokemon.removeVolatile('hypermode');
            }
            if (this.turn && this.turn >= 1) {
                if (!config.enabled) {
                    delete pokemon.volatiles['reversemode'];
                    return;
                }
                this.add('reverse', 'start', pokemon.getSlot());
            }
        }
    },
    onEnd(pokemon) {
        this.add('-end', pokemon, 'Reverse Mode');
        this.add('reverse', 'end', pokemon.getSlot());
    },
    onResidualOrder: 5,
    onResidual(pokemon) {
        const config = this.dex.data.Scripts.shadowedhearts?.Config?.reverseMode || {enabled: false};
        if (!config.enabled) return;
        this.damage(pokemon.baseMaxhp / 16, pokemon, pokemon);
    }
})
