({
    accuracy: true,
    basePower: 140,
    category: "Physical",
    desc: "The user creates a powerful blast of shadow energy. This move is used by a Pokémon holding a Shadowium-Z.",
    name: "Shadow Supernova",
    pp: 1,
    priority: 0,
    flags: {contact: 0, protect: 0, mirror: 0, metronome: 0, snatch: 0},
    onHit(target, source, move) {
        const allMons = this.getAllActive();
        for (const mon of allMons) {
            if (!mon || !mon.hp || target == mon) continue;
            const damage = 140;

            if (damage <= 0) continue;

            this.damage(damage, mon, source, move);
        }
    },
    onAfterMoveSecondarySelf(pokemon, target, move) {
        this.faint(pokemon);
    },
    secondary: {
        kingsrock: true
    },
    isZ: "shadowiumz",
    target: "normal",
    type: "Shadow",
    contestType: "Cool",
    isNonstandard: "Custom",
})
