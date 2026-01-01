({
    accuracy: 100,
    basePower: 95,
    category: "Special",
    desc: "A shadowy aura is used to whip up a vicious tornado that damages both opposing Pokémon.",
    shortDesc: "95 BP Shadow attack that hits both foes.",
    name: "Shadow Storm",
    pp: 100,
    priority: 0,
    flags: {
        contact: 0,
        protect: 1,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0
    },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || move.type !== "Shadow") return;
        const targetTypes = target.getTypes();
        if (type !== targetTypes[0]) return 0;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
    },
    target: "allAdjacentFoes",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})
