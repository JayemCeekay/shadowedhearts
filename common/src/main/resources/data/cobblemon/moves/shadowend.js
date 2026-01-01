({
    accuracy: 60,
        basePower: 120,
    category: "Physical",
    desc: "A shadowy aura ram attack that also rebounds on the user.",
    name: "Shadow End",
    pp: 100,
    priority: 0,
    flags: { contact: 1, protect: 1, mirror: 0, metronome: 0, reflectable: 0, snatch: 0 },
    onEffectiveness(typeMod, target, type, move) {
        if (!target || move.type !== "Shadow") return;
        const targetTypes = target.getTypes();
        if (type !== targetTypes[0]) return 0;
        const isShadowTarget = !!(target.set && target.set.isShadow);
        return isShadowTarget ? -1 : 1;
},
    onAfterMoveSecondarySelf(pokemon, target, move) {
        if(!pokemon.hp) return;
        const hpToLose = this.clampIntRange(
            this.trunc(pokemon.hp/2),
            1
        );

        if(hpToLose > 0) {
            this.damage(hpToLose, pokemon, pokemon, move);
        }
},
    secondary: {
        kingsrock: true
    },
    target: "normal",
        type: "Shadow",
    contestType: "Tough",
    isNonstandard: "Custom"
})