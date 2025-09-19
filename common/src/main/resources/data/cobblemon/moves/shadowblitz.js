{
    accuracy: 100,
    basePower: 40,
    category: "Physical",
    name: "Shadow Blitz",
    pp: 10,
    priority: 0,
    flags: { protect: 1, mirror: 1, metronome: 1 },
    onEffectiveness(typeMod, target, type, move) {
    if (!target || !move || move.type !== "Shadow") return;
    const isShadowTarget = !!(target.set && target.set.isShadow);
    return isShadowTarget ? -1 : 1;
},
    target: "normal",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
}