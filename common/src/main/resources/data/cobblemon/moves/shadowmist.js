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


    boosts: {
        evasion: -2
    },

    target: "allAdjacentFoes",
    type: "Shadow",
    zMove: { boost: { spa: 1 } },
    contestType: "Tough",
    isNonstandard: "Custom"
})