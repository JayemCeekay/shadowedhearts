((item, id, enabled) => {
    // Retained volatile callbacks can outlive a config reload and cache refresh.
    // The live check keeps them inert too, while ordinary native item transfers remain usable.
    const wrap = object => {
        for (const key of Object.keys(object)) {
            if (key.startsWith("on") && typeof object[key] === "function") {
                const callback = object[key];
                object[key] = function(...args) {
                    if (this.dex.data.Scripts.shadowedhearts?.Config?.itemEffects?.[id] !== false) {
                        return callback.apply(this, args);
                    }
                };
            } else if (key === "condition" && object[key]) {
                wrap(object[key]);
            }
        }
    };
    wrap(item);
    if (enabled) return item;
    // Metadata such as isChoice, isGem, zMove, boosts and onTakeItem also enables effects.
    return {name: item.name, spritename: item.spritename, num: item.num,
        gen: item.gen, isNonstandard: item.isNonstandard};
})
