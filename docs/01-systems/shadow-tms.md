# Shadow moves and Shadow TMs

This guide covers ShadowedHearts 2.0.0-alpha with Cobblemon 1.8.4. The examples use the mod defaults unless they name a preset or datapack.

## From corruption to purification

A newly encountered or corrupted Shadow Pokémon normally starts with **one damaging Shadow move**. If it already has a Shadow move equipped, that move counts. If it still needs moves, the game tries the bench before rolling new ones. A server can set the random count or range with `shadowMoves.replaceCount`, or choose Shadow Rush with `onlyShadowRush`. When a Pokémon needs its first new Shadow move, the roll comes from damaging moves. Additional rolls can include status moves and avoid duplicates. [Starting move rules](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/ShadowMoveUtil.java)

Shadow moves are usable from a full Heart Gauge. For a player commanding a Shadow Pokémon, ordinary moves become visible and usable as the gauge opens. The default thresholds use the displayed percentage **remaining**.

| Heart Gauge remaining | Ordinary moves available |
|---|---|
| Above 80% | None normally. An authored set with no active Shadow move can use its first ordinary move. |
| 41–80% | Up to one |
| 21–40% | Up to two |
| 0–20% | Up to three |

At the third milestone, a Pokémon with several active Shadow moves can recover ordinary attacks in some of those slots. It keeps at least one Shadow move. Ordinary-eligible moves, individually retained moves, and moves marked for purification retention are protected from this automatic replacement. A server can also keep every assigned Shadow move until the ceremony. [Heart Gauge unlocks](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/restrictions/ShadowUnlockPolicy.java) · [Move restoration](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/ShadowService.java)

**Purification** removes Shadow-only moves from the active set and bench, including moves taught by disc, and restores ordinary moves where possible. A datapack can give a species ordinary-state access to a move or let an individual keep a move it already knows. By default, Shadow Pokémon bank earned EXP until purification, so levels gained from that payout use the purified Pokémon's eligibility. The server also controls manual move editing and ordinary TM teaching while Shadow. [Purification and EXP release](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/ShadowService.java)

## Discovering, crafting, and using a Shadow TM

1. **Discover the recipe.** Cobblemon can unlock recipes from active or accessible moves on Pokémon you own. A snagged Pokémon carrying Shadow Bolt can unlock the Shadow Bolt recipe. You can also learn the recipe by inserting an existing disc into a Data Monitor. The discovery belongs to the player and survives the Pokémon's purification.
2. **Burn the disc.** Select the discovered move at a Cobblemon TM Machine. Every recipe takes one **Blank TM**, the Shadow Gems in the table below, and its other ingredients. A Shadow Gem takes one eligible Cobblemon type Gem and one Shadow Shard. Nine Shadow Dust make one Condensed Shadow. [TM recipes](../../common/src/main/resources/data/shadowedhearts/tms) · [Shadow Gem recipe](../../common/src/main/resources/data/shadowedhearts/recipe/shadow_gem.json)
3. **Choose a Pokémon.** Use the finished disc on your own sent-out party Pokémon, or teach a party or PC Pokémon through the TM Machine. Finish any battle or trade first.
4. **Choose the slot.** On a Shadow Pokémon, select an **existing active Shadow move**. If it only knows Shadow Rush, teaching Shadow Bolt gives it Shadow Bolt in that same slot. If Rush had 7 PP left and one PP Up, Bolt inherits 7 PP and one PP Up, capped at its own maximum. A zero-PP slot stays at zero. Success consumes one disc unless you are in Creative or Cobblemon's `infiniteTmUses` is enabled. Cancelling or being refused costs nothing. [Disc teaching rules](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/tm/ShadowTMTeaching.java)

**Who can learn it?** With no species profile, any Shadow Pokémon with an active Shadow slot can use any server-enabled standard Shadow disc it does not already have active. A species profile narrows the choices to its unlocked level entries and its listed TM moves. An eligible outgoing move is remembered on a profiled Pokémon. The default unprofiled disc swap drops the outgoing move. If an ordinary Pokémon has explicit ordinary-state eligibility for a TM move, teaching fills a free slot or puts it on the bench when all four slots are occupied. A move already accessible through its level-up list is already learned. [Species and bench eligibility](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/ShadowLearnsetMoves.java)

**Shadow Scales** offer a random alternative. Use one on your own sent-out Shadow Pokémon and choose a slot. A Shadow slot rerolls among eligible Shadow moves absent from the active set. An unlocked ordinary slot draws a different ordinary move from the bench. A species profile supplies the Shadow pool from its unlocked level moves and TM entries. The Scale has its own server switch. To make one, infuse a Deep Sea Scale in the Umbra Infuser with 8 charge for 300 ticks. [Scale rules](../../common/src/main/java/com/jayemceekay/shadowedhearts/content/items/ShadowScale.java) · [Infusion recipe](../../common/src/main/resources/data/shadowedhearts/recipe/infusion/shadow_scale.json)

## Move and recipe reference

All 18 standard moves are **Shadow type** and have **100 base PP**. Under default rules, damaging Shadow attacks normally hit an ordinary target for **2×** and a Shadow target for **½×**. The matchup applies once even against a dual-type defender. A held Null Charm can neutralize the weakness. Shadow Fire, Bolt, and Chill all use this same matchup. Their secondary effects differ. Max Shadow and Shadow Supernova are available through battle gimmicks and have no native discs. [Move definitions](../../common/src/main/resources/data/cobblemon/moves) · [Battle effectiveness](../../common/src/main/resources/data/shadowedhearts/showdown/conditions/shadowengine.js)

Every row also costs **one Blank TM**. The power and effects shown use the mod defaults. Accuracy is 100% before battle modifiers unless the effect column gives another value.

| Shadow move | Category | Power | Effect | Shadow Gems | Other TM ingredients |
|---|---|---:|---|---:|---|
| Blitz | Physical | 40 | Single-target tackle | 2 | 1 Muscle Feather |
| Rush | Physical | 55 | Single-target tackle with no recoil under default rules | 3 | 2 Muscle Feathers |
| Break | Physical | 75 | Single-target attack | 4 | 1 Black Belt |
| Blast | Physical | 80 | Elevated critical-hit chance | 5 | 1 Sharp Beak |
| End | Physical | 120 | 60% accuracy. On a hit the user loses half its current HP, rounded down, minimum 1. | 6 | 4 Jaboca Berries and 1 Condensed Shadow |
| Wave | Special | 50 | Hits all adjacent foes | 3 | 2 Genius Feathers |
| Rave | Special | 70 | Hits all adjacent foes | 5 | 1 Soft Sand |
| Storm | Special | 95 | Hits all adjacent foes | 6 | 1 Float Stone and 1 Condensed Shadow |
| Bolt | Special | 75 | 10% chance to paralyze | 5 | 1 Magnet |
| Chill | Special | 75 | 10% chance to freeze | 5 | 1 Never-Melt Ice |
| Fire | Special | 75 | 10% chance to burn | 5 | 1 Cobblemon Charcoal Stick |
| Down | Status | — | Lowers adjacent foes' Defense by two stages | 3 | 4 Qualot Berries |
| Hold | Status | — | 80% accuracy. Traps adjacent foes while the user remains active. | 4 | 1 Binding Band |
| Mist | Status | — | Lowers adjacent foes' evasion by two stages | 2 | 1 Wide Lens |
| Panic | Status | — | 60% accuracy. Confuses adjacent foes. | 3 | 4 Figy Berries |
| Shed | Status | — | Clears Reflect, Light Screen, and Safeguard on the opposing side | 4 | 1 Light Clay |
| Half | Status | — | Deals half of each active battler's current HP, rounded down, including the user's. Protect applies individually. The user recharges next turn. | 5 | 1 Razor Fang and 1 Condensed Shadow |
| Sky | Status | — | Sets Shadowy Aura for five turns. Shadow attacks gain 50% power. Exposed non-Shadow battlers lose 1/16 of maximum HP each turn. Dig and Dive avoid the weather damage. | 6 | 1 Spell Tag and 1 Condensed Shadow |

## Species and story encounters

A species profile lives at `data/<species namespace>/shadow_learnsets/<species path>.json`. Fresh Shadow spawns and newly corrupted Pokémon take the most recent eligible level entries up to `starting_shadow_slots` (0–4, default 4). At the same level, later entries in the file win the last slots. On a later level gain, newly eligible moves can fill empty slots. A full active set leaves the new level move available in the move picker, subject to the server's move-editing rules.

The optional Houndour pack demonstrates three different permissions. [Houndour JSON](../../datapacks/shadowedhearts-shadow-learnsets/data/cobblemon/shadow_learnsets/houndour.json) · [Authoring guide](../../datapacks/shadowedhearts-shadow-learnsets/README.md)

| Move | What the example allows |
|---|---|
| Shadow Rush and Shadow Hold | Rush unlocks at level 1 and Hold at 16. A level 20 Shadow Houndour starts with both. Both are Shadow-only. |
| Shadow Fire | Unlocks at level 28 with `requires_shadow: false`. An ordinary level 28+ Houndour can learn it too. A level 30 Shadow Houndour starts with Hold and Fire and keeps Fire on purification. |
| Shadow Break | Listed under `tm` with `retain_on_purification: true`. A Shadow Houndour that knows Break at purification, active or benched, keeps it as an individual reward. Another ordinary Houndour receives no such entitlement. |

Install the example in the world's `datapacks` folder, run `/reload`, then try `/spawnpokemon houndour level=30 sh_shadow`. For an exact story opponent, try `/spawnpokemon houndour level=30 sh_shadow sh_shadow_moves=shadowrush,bite`. That Houndour starts with exactly Rush and Bite in that order. A snag preserves the encounter's moves. Reloading profile edits updates eligibility and future assignments while existing active sets stay in place. Restart when adding a new species or move definition to Cobblemon itself.

## Server settings and presets

On a fresh corruption, starting move priority runs from **GO Frustration**, to an **exact encounter set**, to a **species profile**, to **preferred Lost Lore form moves** if enabled, and finally to the **server's random or Rush-only assignment**. The profile governs the species' level and TM rules. The encounter property specifies only that individual's starting roster. [Assignment order](../../common/src/main/java/com/jayemceekay/shadowedhearts/common/shadow/ShadowMoveUtil.java)

| Rules template | Starting moves without a species or encounter override | Battle and purification behavior |
|---|---|---|
| Mod defaults | One random damaging Shadow move on a fresh ordinary moveset | Normal PP. Shadow matchup enabled. Extra slots can return to ordinary moves as the heart opens. |
| Colosseum | Shadow Rush | 90 power and 1/16 maximum-HP recoil on a damaging hit. Special Shadow matchup off. Unlimited Shadow PP. Assigned Shadow moves stay until purification. |
| XD | Two to four random Shadow moves | Special Shadow matchup on. Unlimited Shadow PP. Assigned Shadow moves stay until purification. |
| GO | Frustration takes precedence over species and encounter sets | Purification grants Return. The server can open an event window for removing Frustration. |

Applying Colosseum, XD, or GO disables Shadow TM teaching and Shadow Scales. These presets write editable settings. Operators can enable discs with `compatibilityItems.shadowTMsEnabled`, choose the allowed standard moves with `compatibilityItems.shadowTMAllowedMoves`, and toggle Scales with `compatibilityItems.effects.shadowScale`. An empty TM allowlist permits no disc moves. Unlimited Shadow PP applies to a **Shadow Pokémon** using a Shadow move. An ordinary Pokémon that learns one through a pack still spends PP normally. [Preset definitions](../../common/src/main/java/com/jayemceekay/shadowedhearts/config/preset/RulesPresetCatalog.java) · [Item settings](../../common/src/main/java/com/jayemceekay/shadowedhearts/config/PresetCompatibilityConfig.java)

## Native integration and appearance

TM IDs use `shadowedhearts:<move-id>`, such as `shadowedhearts:shadowrush`. The item remains `cobblemon:technical_machine` with its native move component. The registry supplies creative-tab entries and synchronizes recipe definitions with clients.

The shared [Shadow disc model](../../common/src/main/resources/assets/cobblemon/models/item/tm/shadow_tm.json) uses the supplied [Shadow TM inventory sprite](../../common/src/main/resources/assets/cobblemon/textures/item/tms/shadow_tm.png) and inherits Cobblemon's TM overlay. The [shelf texture](../../common/src/main/resources/assets/cobblemon/textures/block/disc_shelf/shadow_tm.png) remains an unmodified copy of Cobblemon 1.8.2's Ghost TM shelf artwork, supplied at the native renderer's expected Shadow path. The Data Monitor retains Cobblemon's fallback screen appearance for the custom type.

## Original TM integration checks (October 2026)

Validation on October 28, 2026 passed 137 focused common checks: two resource tests covering all 18 approved recipes and assets, plus 135 Cobblemon 1.8.2 mixin compatibility checks.

All 30 native Fabric GameTests passed: 13 Shadow TM tests, nine existing Heart Gauge restriction tests and eight Shadow Scale regression tests. The Shadow TM suite burns every real recipe, teaches every move, exercises party and PC targets, preserves zero/current PP and PP Ups, checks creative/infinite use, rejects forged and stale selections, round-trips saved Pokemon, and verifies progression and purification removal.

Fabric and NeoForge builds passed. Validation used separate build output and Gradle state directories because another build was active in this checkout. Live client appearance, multiplayer interaction and survival balance require playtesting.
