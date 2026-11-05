({
    name: "Riven Orb",
    spritename: "rivenorb",
    onBasePowerPriority: 16,
    onBasePower(basePower, source, target, move) {
        if (source.set?.isShadow && move.type === "Shadow" && move.category !== "Status") {
            return this.chainModify(1.4);
        }
    },
    onAfterMoveSecondarySelf(source, target, move) {
        if (source.set?.isShadow && source !== target && move.type === "Shadow"
            && move.category !== "Status" && move.totalDamage > 0 && !source.forceSwitchFlag) {
            this.damage(source.baseMaxhp / 8, source, source, this.dex.items.get("rivenorb"));
        }
    },
    num: -1003,
    gen: 9,
    isNonstandard: "Custom",
})
