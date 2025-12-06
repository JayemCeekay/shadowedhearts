({
    accuracy: 100,
        basePower: 0,
    category: "Status",
    desc: "A shadowy aura sharply cuts the foe's evasiveness.",
    shortDesc: "Lowers foes' evasion by 2 stages.",
    name: "Shadow Mist",
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

    boosts: {
        evasion: -2
    },

    target: "allAdjacentFoes",
        type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})