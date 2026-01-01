({
    accuracy: 100,
    basePower: 75,
    category: "Special",
    desc: "A shadowy thunder attack that may paralyze.",
    name: "Shadow Bolt",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 1, snatch: 0 },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || move.type !== "Shadow") return;
        const targetTypes = target.getTypes();
        if (type !== targetTypes[0]) return 0;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
},
    secondary: {
        chance: 10,
        status: 'par',
        kingsrock: true
},
    target: "normal",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})