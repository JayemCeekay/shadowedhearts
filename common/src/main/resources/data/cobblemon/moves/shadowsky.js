({
    accuracy: 100,
    basePower: 0,
    category: "Status",
    desc: "Darkness hurts all but Shadow Pokémon for 5 turns.",
    name: "Shadow Sky",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 0, snatch: 0, reflectable: 0 },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || !move || move.type !== "Shadow") return;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
    },
    weather: "shadowyaura",
    target: "all",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom",
})