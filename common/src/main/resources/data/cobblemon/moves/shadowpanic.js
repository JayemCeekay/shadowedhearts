({
    accuracy: 60,
    basePower: 0,
    category: "Status",
    desc: "A shadowy aura emanates to cause confusion in all opposing Pokémon.",
    shortDesc: "Confuses all adjacent foes.",
    name: "Shadow Panic",
    pp: 100,
    priority: 0,
    flags: {
        contact: 0,
        protect: 1,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0,
        sound: 1
    },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || !move || move.type !== "Shadow") return;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
    },
    target: "allAdjacentFoes",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom",
    onHit(target, source, move) {
        if (!target || target.fainted) return;
        target.addVolatile("confusion", source, move);
    }
})
