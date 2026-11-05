({
    name: "EM Wave Amplifier",
    spritename: "emwaveamplifier",
    onStart(pokemon) {
        // Pickpocket can acquire this during Pursuit, after a switch was chosen.
        this.effectState.acquiredWhileSwitching = pokemon.beingCalledBack
            && !pokemon.forceSwitchFlag && !pokemon.ignoringItem();
    },
    onSwitchOutPriority: 100,
    onSwitchOut(pokemon) {
        const acquiredWhileSwitching = this.effectState.acquiredWhileSwitching;
        delete this.effectState.acquiredWhileSwitching;
        if (acquiredWhileSwitching && pokemon.beingCalledBack && pokemon.hp) {
            pokemon.beingCalledBack = false;
            this.add("-activate", pokemon, "item: EM Wave Amplifier");
            return false;
        }
    },
    onBasePowerPriority: 16,
    onBasePower(basePower, source, target, move) {
        if (move.category !== "Status") {
            return this.chainModify(1.3);
        }
    },
    onDisableMove(pokemon) {
        for (const moveSlot of pokemon.moveSlots) {
            if (this.dex.moves.get(moveSlot.id).category === "Status") {
                pokemon.disableMove(moveSlot.id);
            }
        }
    },
    onBeforeMovePriority: 5,
    onBeforeMove(pokemon, target, move) {
        if (move.category === "Status") {
            this.add("cant", pokemon, "item: EM Wave Amplifier", move);
            return false;
        }
    },
    // Called moves can skip BeforeMove, so enforce the restriction there too.
    onTryMove(pokemon, target, move) {
        if (move.category === "Status") {
            this.add("cant", pokemon, "item: EM Wave Amplifier", move);
            return false;
        }
    },
    onTrapPokemon(pokemon) {
        // This is the holder's own commitment, including Ghost-type holders.
        pokemon.trapped = true;
    },
    onAfterMove(pokemon, target, move) {
        // Resolve after contact/item removal, and preserve forced switches.
        if (move.selfSwitch && pokemon.switchFlag === move.id) {
            pokemon.switchFlag = false;
        }
    },
    num: -1009,
    gen: 9,
    isNonstandard: "Custom",
})
