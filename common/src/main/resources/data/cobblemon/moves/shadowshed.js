({
    accuracy: 100,
    basePower: 0,
    category: "Status",
    desc: "A shadowy aura eliminates Reflect, Light Screen, and Safeguard from the opposing side of the field.",
    shortDesc: "Removes Reflect, Light Screen, and Safeguard from foe's side.",
    name: "Shadow Shed",
    pp: 100,
    priority: 0,
    flags: {
        contact: 0,
        protect: 0,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0
    },
    target: "foeSide",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom",
    onHitSide(side, source, move) {
        side.removeSideCondition("reflect");
        side.removeSideCondition("lightscreen");
        side.removeSideCondition("safeguard");
    }
})
