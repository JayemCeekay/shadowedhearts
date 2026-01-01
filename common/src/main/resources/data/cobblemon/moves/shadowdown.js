({
    accuracy: 100,
        basePower: 0,
    category: "Status",
    desc: "A shadowy aura sharply cuts the foe's Defense.",
    name: "Shadow Down",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 0, reflectable: 0, snatch: 0 },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || move.type !== "Shadow") return;
        const targetTypes = target.getTypes();
        if (type !== targetTypes[0]) return 0;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
},
    boosts: {
        def: -2
    },
    target: "foeSide",
        type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})