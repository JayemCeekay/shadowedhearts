({
    accuracy: 100,
        basePower: 75,
    category: "Special",
    desc: "A shadowy fireball attack that may inflict a burn.",
    name: "Shadow Fire",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 0, reflectable: 0, snatch: 0 },
    onEffectiveness(typeMod, target, type, move) {
    if (!target || !move || move.type !== "Shadow") return;
    const isShadowTarget = !!(target.set && target.set.isShadow);
    return isShadowTarget ? -1 : 1;
},
    secondary: {
        chance: 10,
        status: 'brn',
            kingsrock: true
    },
    target: "normal",
        type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})