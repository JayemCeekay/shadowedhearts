# Essence & Affix Progression (Canonical)

Purpose: Define the Essence currency loop and how players unlock and apply dungeon Affixes to customize Mission runs. Consolidates Essence_Affix_Progression_System.md and aligns with canonical systems.

Related:
- 02_Signal_Missions_System.md — where affixes influence generator, trainers, hazards, rewards
- 03_Mission_Objectives_Threat_Director.md — scaling knobs and director hooks that affixes may modify

---

## 1) Overview
- Players collect Type Essences from missions (rooms, trainers, bosses) and themed overworld activities
- Essences are invested at the Research Station to unlock affixes and upgrade dungeon generation options
- Acts as a tech tree-style progression that personalizes future runs

Essence Sources
- NPC trainer drops, shadow Pokémon, boss chests
- Interactable nodes in missions (e.g., crystals, spires)
- Rare Fusion Essences from bosses for apex affixes

Usage
- Spend at the Research Station to unlock affixes and increase station level
- Higher station levels unlock more affix slots and categories

---

## 2) Research Station Levels
- Level 0: Baseline — no affixes
- Level 1 (100 total essence invested): Unlock 1 affix slot
- Level 2 (300 total): Unlock dual-type affixes; 2 affix slots
- Level 3 (600 total): Unlock tri-type affixes
- Level 4 (1000 total): Unlock Fusion affixes; 3 affix slots

Notes
- Values are balancing targets; make data-driven where possible
- Station level should be account-wide (per-player) and persisted

---

## 3) Affix Progression Tree

Tier 1 — Basic Affixes (Single-Type Unlocks)
Requires: 20 of a single type essence
- Fire → Burning Corridors (fire traps, heat damage)
- Water → Flooded Rooms (slowed movement, water puzzles)
- Grass → Overgrowth (vines/roots block paths)
- Electric → Power Surge (lightning/status hazards)
- Rock → Crumbling Walls (hidden passages, cave-ins)
- Normal → Trainer Gauntlet (extra NPC trainers spawn)

Tier 2 — Intermediate (Dual-Type Combos)
Requires: 50 of two different type essences
- Fire + Dark → Infernal Shadows (shadow Pokémon with fiery auras)
- Water + Electric → Storm Surge (flooded chambers with lightning)
- Grass + Poison → Toxic Overgrowth (poison vines, poisoned wild Pokémon)
- Psychic + Ghost → Mind’s Haunt (hallucinations, ambush battles)
- Steel + Rock → Fortified Chambers (armored minibosses, locked loot)

Tier 3 — Advanced (Tri-Type Clusters)
Requires: 100 of three different types
- Dragon + Dark + Ghost → Abyssal Rift (random shadow legendary minibosses)
- Fire + Flying + Electric → Voltaic Skies (storm hazards, aerial ambushes)
- Ice + Water + Psychic → Frozen Depths (slippery ice, psychic enemies)
- Ground + Steel + Fighting → Trial of Endurance (wave-based rooms, shorter timer)

Tier 4 — Apex (Fusion Essences)
Requires: Fusion Essences (rare boss drops)
- Shadow Essence → Corruption Depths (shadow-overrun dungeon, high loot multiplier, high danger)
- Pure Essence → Sanctified Path (friendly NPC assists, reduced difficulty, lower loot)
- Chaos Essence → Entropy Spiral (randomized affixes each floor, high variance)

---

## 4) Application in Runs
- Selected affixes influence DungeonGenerator (room weights, hazards), TrainerSystem (rosters/levels), RaidBossSystem (phases/modifiers), and Loot Tables
- Limit concurrent affixes by Research Station level and per-run caps
- UI/UX: selecting unlocked affixes during signal synthesis or before mission start (e.g., Entrance screen)

---

## 5) Data & Integration
- Affixes in `data/shadowedhearts/affixes/*.json` with tags, weights, scaling, and knobs
- Research Station progression persisted per player (capability/advancement ledger)
- Hooks in AffixEngine to apply effects during generation and runtime

Example Affix JSON (sketch)
```json
{
  "id": "shadowedhearts:burning_corridors",
  "tier": 1,
  "requires": {"essence": {"fire": 20}},
  "effects": {
    "hazards": {"fire_traps": {"weight": 2, "damage": 1.0}},
    "theme_bias": {"heat": 1},
    "loot": {"weights": {"fire": 1.2}}
  }
}
```

---

## 6) Balancing & Telemetry
- Track run difficulty vs completion/timeout rates per affix set
- Adjust weights and slot caps as needed via datapack
- Consider pity systems (e.g., bonus essences after streak of failures)

---

## 7) UI & Persistence
- Research Station Screen: tree view, unlock costs, preview of effects
- Show active affixes and their tooltips in the Mission HUD
- Persist essence balances and unlocks; support server resets via export/import
