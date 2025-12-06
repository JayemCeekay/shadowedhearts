({
    accuracy: 100,
    basePower: 70,
    category: "Special",
    desc: "A shadowy aura in the ground launches spikes to damage both opposing Pokémon.",
    shortDesc: "70 BP Shadow attack that hits both foes.",
    name: "Shadow Rave",
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
        if (!target || !move || move.type !== "Shadow") return;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
    },
    target: "allAdjacentFoes",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})
