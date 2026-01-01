({
    accuracy: 100,
        basePower: 75,
    category: "Special",
    name: "Shadow Chill",
    desc: "A shadowy ice attack that may freeze.",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 0, snatch: 0},
    onEffectiveness(typeMod, target, type, move) {
        if (!target || move.type !== "Shadow") return;
        const targetTypes = target.getTypes();
        if (type !== targetTypes[0]) return 0;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
},
    secondary: {
        chance: 10,
        status: "frz",
        kingsrock: true
    },
    target: "normal",
        type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})