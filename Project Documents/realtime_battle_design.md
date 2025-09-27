# Design Document: Real-Time Battle System for Cobblemon (Showdown Hybrid)

## Overview
This document outlines a design for extending the Cobblemon + Pokémon Showdown battle engine with real-time combat features inspired by *Pokémon Legends: Z‑A*. The approach preserves Showdown as the battle rules arbiter while layering real-time hit detection, windups, cooldowns, and dodge mechanics in front of it.

---

## Core Principle
- **Two Layers, One Truth**
  - **Action Layer (new, real-time):** Runs in Minecraft tick time (20 TPS). Handles hitboxes, startup, active frames, recovery, dodging, stamina, etc.
  - **Rules Layer (existing Showdown):** Resolves effects (damage, abilities, items, weather, status) as if the hit connected. Keeps competitive math intact.

This separation lets us build a 3D, dodgeable combat model without rewriting the trusted battle logic.

---

## Mapping Turn-Based to Real-Time
- **Accuracy/Evasion → Aim discipline.** Hitboxes define connection chances; optional small accuracy roll preserved for flavor.
- **Priority → Startup tuning.** High-priority moves have shorter windups.
- **Speed → Cadence.** Influences windup/cooldown scaling and movement rate during attacks.
- **Protect/Detect → Guard windows.** Invulnerability during active guard frames.
- **Flinch → Stagger.** Forced recovery.
- **Trick Room → Rate inversion.** Flip move cadence in real-time.
- **PP → Ammo.** Consumed when the move cast begins.

---

## Data Model
Each move gains a `realtime` block alongside Showdown’s existing definitions.

```kotlin
data class RTMoveTuning(
    val startupTicks: Int = 6,
    val activeTicks: Int = 4,
    val recoveryTicks: Int = 8,
    val staminaCost: Int = 10,
    val superArmorTicks: Int = 0,
    val iframesOnDodge: Int = 6,
    val hitbox: HitboxSpec,
    val projectile: ProjectileSpec? = null,
    val allowsStrafe: Boolean = true,
    val cancelsOnHit: Boolean = false
)

sealed interface HitboxSpec {
    data class Cone(val range: Double, val angleDeg: Double, val height: Double): HitboxSpec
    data class Arc(val radius: Double, val sweepDeg: Double, val thickness: Double): HitboxSpec
    data class AoECylinder(val radius: Double, val height: Double, val lingerTicks: Int): HitboxSpec
    data class Capsule(val length: Double, val radius: Double): HitboxSpec
}
```

---

## Server Flow
1. **Inputs:** Client sends intents (`StartMove`, `Dodge`).
2. **State Machine:**
   - Idle → Startup → Active → Recovery.
   - During Active, hitboxes sweep against target hurtboxes.
3. **ContactEvent:** Fired when hitbox overlaps a hurtbox.
4. **Rules Bridge:** Calls `simulateForcedHit(move, attacker, defender, contactMeta)` in Showdown.
5. **Results:** Showdown applies effects; server pushes damage/status + VFX/SFX.

---

## Battle Bridge Example
```ts
function simulateForcedHit(ctx, move, attacker, defender, contact) {
  const oldIgnoreAccuracy = ctx.flags.ignoreAccuracy;
  ctx.flags.ignoreAccuracy = true;

  ctx.contactMeta = { isMelee: contact.isMelee, backhit: contact.angle < 45 };
  ctx.useMove(move, attacker, defender);

  ctx.flags.ignoreAccuracy = oldIgnoreAccuracy;
}
```

---

## Hitbox Implementation
- **Melee:** Cone or capsule sweep per tick.
- **Projectiles:** Entities with raymarch collision, server-authoritative.
- **AoE:** Cylinder volumes or ground templates.
- **Performance:** Grid partitioning or simple BVH; limited combatants keeps it cheap.

---

## Networking
- **Client:** Sends intent, predicts animations.
- **Server:** Authoritative on hit detection and results.
- **Latency:** Dodge inputs given grace frames for fairness.

---

## AI Hooks
- **PrepareMove:** Face target, close distance if melee.
- **Decision_Dodge:** Predict collision trajectories.
- **MaintainRange vs Commit:** Based on Pokémon species “style.”

---

## Prototype Moves
- **Tackle:** Startup 8, Active 4, Recovery 10, cone 2.5m.
- **Flamethrower:** Projectile stream 10m, ticked ray hits.
- **Dodge:** Dash 8 ticks, 6 i-frames, stamina cost, cooldown 20.

---

## Edge Cases
- Multi-target moves: fire multiple contact events.
- Contact vs non-contact: used for abilities like Rough Skin.
- Invulnerable states (Dig/Fly): dodge/i-frame mechanics.
- Encore/Taunt/Trick Room: reinterpret in real-time context.

---

## Next Steps
1. Implement BattleBridge adapter in Kotlin.
2. Build JSON move tuning registry.
3. Prototype Tackle/Flamethrower/Dodge.
4. Layer in Protect, Quick Attack, Rock Slide, Thunderbolt, Will-O-Wisp.
5. Expand AI behaviors for wild Pokémon and NPC trainers.

