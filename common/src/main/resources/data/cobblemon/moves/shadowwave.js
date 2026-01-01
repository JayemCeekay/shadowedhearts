({
    accuracy: 100,
    basePower: 50,
    category: "Special",
    desc: "Shadowy aura waves are loosed to inflict damage on both opposing Pokémon.",
    shortDesc: "50 BP Shadow attack that hits both foes.",
    name: "Shadow Wave",
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
