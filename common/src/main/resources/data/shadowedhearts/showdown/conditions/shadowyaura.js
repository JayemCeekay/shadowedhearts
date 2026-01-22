({
  name: "Shadowy Aura",
  effectType: "Weather",
  duration: 5,
  onFieldStart(field, source, effect) {
    this.add("-weather", "Shadowy Aura");
  },
  onFieldResidualOrder: 1,
  onFieldResidual() {
    this.add("-weather", "Shadowy Aura", "[upkeep]");
    if (this.field.isWeather("shadowyaura")) this.eachEvent("Weather");
  },
  onWeather(target) {
    if (target.set.isShadow) return;
    this.damage(target.baseMaxhp / 16);
  },
  onEffectiveness(typeMod, target, type, move) {
    if (!move || move.type !== "shadow") return;
    const isShadowTarget = !!(target?.set?.isShadow);
    return isShadowTarget ? -1 : 1;
  },
  onModifyType(move) {
    if (move?.id === "weatherball") move.type = "???";
  },
  onBasePower(basePower, attacker, defender, move) {
    if (move?.id === "weatherball") {
      return this.chainModify(2);
    }
    if (move?.id === "solarbeam") {
      return this.chainModify(0.5);
    }
    if (move?.type === "shadow") {
      return this.chainModify(1.5);
    }
  },
  onTryHeal(damage, target, source, effect) {
    if (!effect) return;
    const id = effect.id;
    if (id === "moonlight" || id === "synthesis" || id === "morningsun") {
      return Math.floor(target.baseMaxhp / 4);
    }
  },
  onDamagingHit(damage, target, source, move) {
    if (!target?.hp) return;
    if (move?.id === "weatherball" && move.type === "???" && target.hasAbility?.("colorchange")) {
      if (!target.setType("Normal")) return;
      this.add("-start", target, "typechange", "Normal", "[from] ability: Color Change");
    }
  },
  onFieldEnd() {
    this.add("-weather", "none");
  }
})