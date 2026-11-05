({
    name: "Shadow Gem",
    spritename: "shadowgem",
    isGem: true,
    onSourceTryPrimaryHit(target, source, move) {
        if (target === source || move.category === "Status" || move.flags["pledgecombo"]) return;
        if (move.type === "Shadow" && source.useItem()) {
            source.addVolatile("gem");
        }
    },
    num: -1002,
    gen: 5,
    isNonstandard: "Custom",
})
