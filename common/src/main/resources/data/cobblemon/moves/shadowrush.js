({
    accuracy: 100,
    basePower: 55,
    onModifyMove(move) {
        if (this.dex.data.Scripts.shadowedhearts?.Config?.shadowBattleRules?.shadowRushVariant === 'COLOSSEUM') {
            move.basePower = 90;
            move.shadowedheartsColosseumRush = true;
        }
    },
    onAfterMoveSecondarySelf(source, target, move) {
        if (move.shadowedheartsColosseumRush && move.totalDamage > 0) {
            /* Fixed maximum-HP recoil, deliberately not the native Rock Head-blocked recoil effect. */
            this.damage(Math.max(1, Math.floor(source.baseMaxhp / 16)), source, source, move);
        }
    },
    category: "Physical",
    desc: "A Pokémon executes a tackle while exuding a shadowy aura.",
    name: "Shadow Rush",
    pp: 100,
    priority: 0,
    flags: {
        contact: 1,
        protect: 1,
        mirror: 0,
        metronome: 0,
        reflectable: 0,
        snatch: 0
    },
    target: "normal",
    type: "Shadow",
    zMove: {basePower: 100},
    maxMove: {basePower: 110},
    contestType: "Tough",
    isNonstandard: "Custom"
})
