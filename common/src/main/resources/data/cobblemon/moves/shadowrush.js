({
    accuracy: 100,
    basePower: 55,
    category: "Physical",
    desc: "A Pokémon executes a tackle while exuding a shadowy aura.",
    name: "Shadow Rush",
    pp: 100,
    priority: 0,
    flags: {
        contact: 1,
        protect: 1,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0
    },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || !move || move.type !== "Shadow") return;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
    },
    target: "normal",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})
