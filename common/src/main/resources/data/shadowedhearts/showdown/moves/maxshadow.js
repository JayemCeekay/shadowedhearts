({
    accuracy: true,
    basePower: 100,
    category: "Physical",
    desc: "A Shadow-type attack that Dynamax Pokémon use. This lowers everyone's Defense and Special Defense by 1 stage, and raises everyone's Attack and Special Attack by 1 stage.",
    name: "Max Shadow",
    pp: 10,
    priority: 0,
    flags: {},
    isMax: true,
    self: {
        onHit(source) {
            if (!source.volatiles['dynamax']) return;
            for (const pokemon of this.getAllActive()) {
                this.boost({def: -1, spd: -1, atk: 1, spa: 1}, pokemon);
            }
        },
    },
    target: "adjacentFoe",
    type: "Shadow",
    contestType: "Cool",
    isNonstandard: "Custom",
})
