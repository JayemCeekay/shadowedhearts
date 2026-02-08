({
    accuracy: 80,
        basePower: 0,
    category: "Status",
    desc: "Targets both opposing Pokémon and prevents them from switching out as long as the user remains in battle. Can still be forced out by phazing moves; Baton Pass passes the effect to the replacement.",
    shortDesc: "Traps both foes while the user stays in; Baton Pass passes trap.",

    name: "Shadow Hold",
    pp: 100,
    priority: 0,

    flags: {
    protect: 1,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0,
        contact: 0,
},


    target: "allAdjacentFoes",
    type: "Shadow",
    zMove: { boost: { accuracy: 1 } },
    contestType: "Tough",
    isNonstandard: "Custom",

    onHit(target, source, move) {
    if (!target || !target.isActive || target.fainted) return;
    if (target.volatiles["shadowhold"]) return;
    target.addVolatile("shadowhold", source, move);
},

    condition: {
        onStart(pokemon, source) {
            this.effectState.source = source;
            this.add("-start", pokemon, "Shadow Hold", "[of] " + source.name);
        },

        onTrapPokemon(pokemon) {
            const source = this.effectState.source;
            if (!source || !source.isActive || source.fainted) {
                pokemon.removeVolatile("shadowhold");
                return;
            }
            if (pokemon.side === source.side) return;
            pokemon.trapped = true;
            pokemon.maybeTrapped = true;
        },

        onEnd(pokemon) {
            this.add("-end", pokemon, "Shadow Hold");
        },
    },
})