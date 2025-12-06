({
    accuracy: 100,
        basePower: 75,
    category: "Physical",
    desc: "A shattering ram attack with a shadowy aura.",
    name: "Shadow Break",
    pp: 100,
    priority: 0,
    flags: { contact: 1, protect: 1, mirror: 0, metronome: 0, snatch: 0 },
    onEffectiveness(typeMod, target, type, move) {
    if (!target || !move || move.type !== "Shadow") return;
    const isShadowTarget = !!(target.set && target.set.isShadow);
    return isShadowTarget ? -1 : 1;
},
    secondary: {
        kingsrock: true
    },
    target: "normal",
        type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})