# Core Mechanics — Shadow vs Purity (Canonical)

Purpose: Define the Shadow ↔ Purity mechanics for Pokémon, including corruption meter behavior, effects, visuals, data sync, and integration hooks. This file consolidates and supersedes overlapping drafts.

Sources consolidated:
- ShadowedHearts_Overview.md (mechanics, thresholds, effects)
- implementation.md (aspect, data sync, shaders, gameplay hooks)

---

## 1) Corruption ↔ Purity Meter

Representation
- Internal: corruption float 0.0–1.0 (for precision and storage)
- UI: display 0–100 scale mapped from the internal value
- Movement: battles, items, faction events, and environments move corruption up or down

Server-Configurable Thresholds (Default: OFF)
- When enabled, apply these out-of-combat restrictions at bands (display values):
  - 0% and above → cannot gain xp
  - 25% → no trading
  - 75 → Heavily Corrupted: cannot gain XP
  - 100 → Fully Corrupted (Shadow): full restrictions
- When disabled (default), restrictions affect only combat/behavior/visuals while allowing normal breed/trade/XP
- Corrupted pokemon should not be able to switch out movesets or learn new moves
- Every 25% removal of the corruption meter results in unlocking 
- The more corrupted, the higher the chance to disobey/act unpredictably with a 50/50 at 100% corruption
- Shadow pokemon have a unique ability that cannot be changed

Shadow Persistence
- Once a Pokémon becomes Shadow, it remains Shadow until it undergoes a purification ritual
- Having corruption at 0 is a prerequisite for purification, but does not clear Shadow on its own
- Store a boolean `shadow_state` flag alongside `corruption`

Corruption Sources and Sinks
- Increase: Shadow-aligned battles, Shadow artifacts, Corruption Storms, Shadow dungeon miasma
- Decrease: Purification items/rituals, Purity events, Purity Pools, dungeon rewards

---

## 2) Effects of State

Shadow Pokémon Effects (scale by corruption level)
- + Attack / Sp. Atk (e.g., +15% at high corruption)
- – Defense / Sp. Def (e.g., –10% at high corruption)
- Increased critical hit rate
- Loyalty variance: 5–15% chance to disobey/act unpredictably
- Visual cues: shader aura, darker palette tint, glowing eyes, send-out flare

Purified Pokémon Effects (scale by happiness/purity)
- Always obedient; faster happiness gain
- Resistance to status effects and/or shorter durations
- Visual cues: shimmer/glow, soft particles, lighter palette tint

Tuning
- All values should be data/config driven for easy balancing

---

## 3) Visual Identity

Shader Aura
- Location: assets/shadowedhearts/shaders/
- Additive rim/fresnel pass with scrolling noise texture
- Uniforms: AuraColor, Intensity, FresnelPower, NoiseScale, Scroll, GameTime
- Intensity scales with corruption; optional particles on relevant events

Palette and Particles
- Subtle base tint for Shadow; light tint for Purified
- Optional particles on send-out, crits, and purification ticks

---

## 4) Data Model & Sync

Aspect & Flags
- Aspect key: `shadowedhearts:shadow`
- Fields: `corruption` (0.0–1.0), `shadow_state` (boolean)

Entity Sync
- On entity spawn → mirror from Pokémon
- On despawn/capture → commit back to Pokémon
- Client render reads entity data only (fast path)

Persistence
- Persist across captures, trades, PC storage, and world reloads

---

## 5) Gameplay Hooks

Acquisition
- Wild spawn chance to carry Shadow aspect (configurable)
- Story encounters and dungeon rewards may apply Shadow or Purity influences

Items & Interactions
- Items to toggle/apply Shadow, reduce corruption, or perform purification
- Purification ritual/action consumes required items/conditions and clears `shadow_state` at corruption 0

Factions & Events
- Shadow Corruption Surges vs Purification Festivals provide world-scale modifiers
- Reputation tracks and rewards tie into player actions

---

## 6) Configuration & Balancing

- Server config for corruption rate multipliers and threshold-toggle (default OFF)
- Datapack hooks for status scaling and event multipliers
- Advancements guide players through corruption/purification gameplay

---

## 7) Notes for Implementation

- Store internally as floats/flags; format display as 0–100 with localized labels
- Expose a single source of truth utility for set/get and conversions (aspect+entity sync)
- Keep visual intensity strictly client-side, driven by synced entity data
