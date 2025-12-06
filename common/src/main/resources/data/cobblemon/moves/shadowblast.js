({
    accuracy: 100,
    basePower: 80,
    category: "Physical",
    desc: "A wicked blade of air is formed using a shadowy aura.",
    name: "Shadow Blast",
    pp: 100,
    priority: 0,
    flags: { contact: 0, protect: 1, mirror: 0, metronome: 0, snatch: 0 },
    onEffectiveness(typeMod, target, type, move) {
    if (!target || !move || move.type !== "Shadow") return;
    const isShadowTarget = !!(target.set && target.set.isShadow);
    return isShadowTarget ? -1 : 1;
    },
    onTryMove() {
    this.attrLastMove('[still]');
    },
    onPrepareHit(target, source) {
        this.add("-anim", source, "Aeroblast", target);
    },
    secondary: {
        kingsrock: true
    },
    target: "normal",
    type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})