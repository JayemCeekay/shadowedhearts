// Exported string snippet to be inserted into Showdown's data/conditions.js Conditions object
// The content between backticks is pasted as-is by the ShadowedHearts runtime patcher.
module.exports = `
  shadowyaura: {
    name: "Shadowy Aura",
    effectType: "Weather",
    duration: 5,
    onFieldStart(field, source, effect) {
      this.add("-weather", "Shadowy Aura");
    },
    // Showdown weathers call Weather each residual tick via onFieldResidual
    onFieldResidualOrder: 1,
    onFieldResidual() {
      this.add("-weather", "Shadowy Aura", "[upkeep]");
      // After-turn flavor line for Shadowy Aura
      if (this.field.isWeather("shadowyaura")) this.eachEvent("Weather");
    },
    // End-of-turn damage to all non-Shadow Pokémon (1/16 max HP)
    onWeather(target) {
      if (target.set.isShadow) return;
      this.damage(target.baseMaxhp / 16);
    },
    // Shadow move effectiveness tweak: super-effective vs non-Shadow, resisted vs Shadow
    onEffectiveness(typeMod, target, type, move) {
      if (!move || move.type !== "Shadow") return;
      const isShadowTarget = !!(target?.set?.isShadow);
      return isShadowTarget ? -1 : 1;
    },
    // Weather Ball becomes typeless under Shadowy Aura
    onModifyType(move) {
      if (move?.id === "weatherball") move.type = "???";
    },
    // Power tweaks while Shadowy Aura is active
    onBasePower(basePower, attacker, defender, move) {
      // Double Weather Ball's power while typeless
      if (move?.id === "weatherball") {
        return this.chainModify(2);
      }
      // Halve Solar Beam's power
      if (move?.id === "solarbeam") {
        return this.chainModify(0.5);
      }
      // Boost Shadow-type moves while the weather is active
      // Optional: uncomment to have Utility Umbrella negate this boost vs/used by holder
      // if (attacker.hasItem("utilityumbrella") || defender.hasItem("utilityumbrella")) return;
      if (move?.type === "Shadow") {
        return this.chainModify(1.5);
      }
    },
    // Healing tweaks: Moonlight/Synthesis/Morning Sun heal 1/4 max HP
    onTryHeal(damage, target, source, effect) {
      if (!effect) return;
      const id = effect.id;
      if (id === "moonlight" || id === "synthesis" || id === "morningsun") {
        return Math.floor(target.baseMaxhp / 4);
      }
    },
    // Special case: Color Change + typeless Weather Ball makes target Normal type
    onDamagingHit(damage, target, source, move) {
      if (!target?.hp) return;
      if (move?.id === "weatherball" && move.type === "???" && target.hasAbility?.("colorchange")) {
        if (!target.setType("Normal")) return;
        this.add("-start", target, "typechange", "Normal", "[from] ability: Color Change");
      }
    },
    onFieldEnd() {
      this.add("-weather", "none");
      this.add("-message", "The shadowy aura faded away!");
    }
  }
`;
