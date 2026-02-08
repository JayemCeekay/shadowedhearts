({
    accuracy: true,
    basePower: 100,
    category: "Physical",
    desc: "A Shadow-type attack that Dynamax Pokémon use. This lowers everyone's Defense and Special Defense by 2 stages, and raises everyone's Attack and Special Attack by 2 stages.",
    name: "Max Shadow",
    pp: 10,
    priority: 0,
    flags: {},
    isMax: true,
    self: {
        onHit(source) {
            if (!source.volatiles['dynamax']) return;
            for (const pokemon of this.getAllActive()) {
                this.boost({def: -2, spd: -2, atk: 2, spa: 2}, pokemon);
            }
        },
    },
    target: "adjacentFoe",
    type: "Shadow",
    contestType: "Cool",
    isNonstandard: "Custom",
})
