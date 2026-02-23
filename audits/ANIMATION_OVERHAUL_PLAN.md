# CobbleCrew Animation Overhaul — Implementation Plan

## Goal

Replace the basic "swing hand + particles + random cry" visual feedback with Cobblemon's native Bedrock animation system. Pokémon should visibly **perform relevant animations** during each phase of their work — navigating, arriving, working, producing, attacking, healing, and idling. This makes CobbleCrew feel alive rather than like invisible task executors.

---

## The Animation API: What We Have

### `PokemonEntity.playAnimation(name: String, expressions: List<String>)`

This is Cobblemon's server-side method that sends a `PlayPosableAnimationPacket` to all nearby clients. It takes:
- **`name`** — The animation identifier. Format is just the suffix after `animation.<species>.` (e.g. `"physical"`, `"cry"`, `"happy"`). Cobblemon resolves the species prefix automatically.
- **`expressions`** — MoLang expressions to run alongside the animation (usually empty).

This works **server-side**, which is exactly where CobbleCrew runs.

### Available Behaviour Flags

| Flag | Effect |
|------|--------|
| `EXCITED` | Pokémon bounces/moves energetically (already used) |
| `LOOKING` | Pokémon tracks a look target |
| `FLYING` | Flying pose |
| `PASTURE_CONFLICT` | Conflict state |

### Universal Animations (available on most/all Pokémon)

Scanned all 922 Pokémon in Cobblemon 1.7.1. These are available on enough Pokémon to use reliably:

| Animation | Count | Use Case |
|-----------|-------|----------|
| `ground_idle` | 868 | Default idle, return-to-origin |
| `cry` | 772 | Celebration on job completion |
| `ground_walk` | 549 | Navigating to target (already handled by pathfinding) |
| `sleep` | 505 | Extended idle / waiting on cooldown |
| `battle_idle` | 386 | Alert stance while defending or looking for targets |
| `faint` | 211 | Dramatic failure animation (optional) |
| `happy` | 194 | Successful deposit, production complete |
| `sad` | 194 | Failed to find target, container full |
| `angry` | 187 | Defense jobs — spotting hostiles |
| `recoil` | 170 | Taking damage / being bumped off course |
| `hurt` | 124 | Got hit while working |
| `physical` | 76 | Melee-style work: harvesting, mining, chopping |
| `special` | 76 | Ranged/magical work: production, environmental |
| `status` | 77 | Support jobs: applying buffs/effects |
| `battle_cry` | 89 | Aggressive cry before engaging hostile |
| `shock`/`shocked` | 103/20 | Surprised by something (target disappeared) |
| `unamused` | 65 | No jobs available, no targets |
| `pose` | 112 | Standing alert/ready |
| `hold_item` | 28 | Carrying harvested items |
| `dance` | 11 | Rare celebration (Pikachu etc.) |

### Fallback Strategy

Not every Pokémon has every animation. The system must **try the ideal animation, fall back to a safer one, and ultimately do nothing** rather than crash. Cobblemon silently ignores `playAnimation()` calls for animations that don't exist on a given Pokémon — so we just need a preference order.

---

## Architecture: `WorkerAnimationUtils`

### New File: `utilities/WorkerAnimationUtils.kt`

A new utility object parallel to `WorkerVisualUtils` — or potentially a replacement/merge.

```kotlin
object WorkerAnimationUtils {

    // Track per-pokemon animation cooldowns to avoid spamming
    private val lastAnimationTick = mutableMapOf<UUID, Long>()
    private const val ANIM_COOLDOWN_TICKS = 20L  // Don't spam animations faster than 1/sec

    /**
     * Play a context-appropriate animation with fallback chain.
     * Safe to call every tick — internally throttled.
     */
    fun playWorkAnimation(
        entity: PokemonEntity,
        phase: WorkPhase,
        world: World,
    ) {
        val pokemonId = entity.pokemon.uuid
        val now = world.time
        if (now - (lastAnimationTick[pokemonId] ?: 0L) < ANIM_COOLDOWN_TICKS) return
        lastAnimationTick[pokemonId] = now

        val anims = phase.animationChain
        // playAnimation silently no-ops for missing animations, so just play the first
        // The chain ordering means we try the most specific first
        for (anim in anims) {
            entity.playAnimation(anim, emptyList())
            break  // Only play one — the first in the chain is our best choice
        }
    }

    fun cleanup(pokemonId: UUID) {
        lastAnimationTick.remove(pokemonId)
    }
}
```

### New Enum: `WorkPhase`

Defines the distinct visual phases a working Pokémon can be in, each with an ordered animation fallback chain:

```kotlin
enum class WorkPhase(val animationChain: List<String>) {
    // -- LOCOMOTION --
    NAVIGATING(listOf("ground_walk")),          // Handled by pathfinding already, mostly implicit

    // -- WORK ACTIONS --
    HARVESTING(listOf("physical", "cry")),       // Chopping, mining, picking — melee physical motion
    PRODUCING(listOf("special", "happy", "cry")),// Self-generating items — magical/ranged feel
    PROCESSING(listOf("physical", "cry")),       // Pulling items, transforming — physical labor
    PLACING(listOf("physical", "cry")),          // Placing blocks — physical motion
    ATTACKING(listOf("physical", "battle_cry", "angry")), // Hitting a hostile mob
    DEBUFFING(listOf("special", "status", "angry")),     // Status effects on hostiles
    HEALING(listOf("status", "happy")),          // Applying buffs to players
    ENVIRONMENTAL(listOf("special", "cry")),     // Freezing water, fueling furnaces, etc.

    // -- REACTIONS --
    WORK_COMPLETE(listOf("happy", "cry")),       // Successfully finished a task
    DEPOSIT_SUCCESS(listOf("happy", "cry")),     // Deposited items in container
    DEPOSIT_FAILED(listOf("sad", "unamused")),   // Couldn't find container / container full
    TARGET_LOST(listOf("shocked", "shock", "sad")),  // Target block/mob disappeared
    NO_TARGETS(listOf("unamused", "sad")),       // Nothing to do
    COOLDOWN_WAITING(listOf("sleep", "ground_idle")), // Producer on cooldown, just chilling

    // -- COMBAT-SPECIFIC --
    HOSTILE_SPOTTED(listOf("angry", "battle_cry", "battle_idle")),  // Found a hostile
    COMBAT_READY(listOf("battle_idle", "pose")),  // Alert stance while defending

    // -- IDLE --
    IDLE_AT_ORIGIN(listOf("ground_idle")),        // Waiting at pasture/player with no jobs
    IDLE_BORED(listOf("unamused", "sleep", "ground_idle")), // Long idle, nothing happening
}
```

---

## Integration Points: Where Animations Trigger

### 1. `WorkerVisualUtils.handleArrival()` — The Core Work Moment

**Current behavior:** Swing hand → look at target → 30-tick delay → swing hand + particles + 20% cry.

**New behavior:**

| Stage | Current | New |
|-------|---------|-----|
| First arrival | `swingHand()` | `playWorkAnimation(HARVESTING/ATTACKING/etc)` + keep lookAt |
| During delay | lookAt only | lookAt only (animation is playing out) |
| Work complete | `swingHand()` + particles + cry | `playWorkAnimation(WORK_COMPLETE)` + particles |

To make this work, `handleArrival` needs to know **what kind of work** is happening. Add a `WorkPhase` parameter:

```kotlin
fun handleArrival(
    pokemonEntity: PokemonEntity,
    targetPos: BlockPos,
    world: World,
    particleType: ParticleEffect? = null,
    offset: Double = 3.0,
    workPhase: WorkPhase = WorkPhase.HARVESTING,  // NEW — what animation to play
): Boolean
```

### 2. `BaseHarvester` — Physical Work Animations

```
Navigate → arrive → HARVESTING animation → harvest → WORK_COMPLETE → carry items → DEPOSIT_SUCCESS/FAILED
```

| Event | Animation Phase |
|-------|----------------|
| `handleHarvesting()` claims target | (pathfinding handles walk) |
| `handleArrival()` first tick | `HARVESTING` |
| `handleArrival()` returns true | `WORK_COMPLETE` |
| `handleDepositing()` success | `DEPOSIT_SUCCESS` |
| `handleDepositing()` all containers full | `DEPOSIT_FAILED` |

### 3. `BaseProducer` — Magical Production Animations

```
Cooldown wait → produce → PRODUCING animation → carry items → deposit
```

| Event | Animation Phase |
|-------|----------------|
| On cooldown | `COOLDOWN_WAITING` |
| `produce()` returns items | `PRODUCING` |
| Deposit success | `DEPOSIT_SUCCESS` |

### 4. `BaseProcessor` — Transform Animations

```
Navigate to barrel → extract → PROCESSING animation → transform → deposit
```

| Event | Animation Phase |
|-------|----------------|
| Arriving at input container | `PROCESSING` |
| Transform complete | `WORK_COMPLETE` |
| Deposit success | `DEPOSIT_SUCCESS` |

### 5. `BaseDefender` — Combat Animations

```
Spot hostile → HOSTILE_SPOTTED → navigate → arrive → ATTACKING/DEBUFFING → release
```

| Event | Animation Phase |
|-------|----------------|
| New hostile found | `HOSTILE_SPOTTED` |
| During navigation | `COMBAT_READY` (EXCITED flag already handles this) |
| `handleArrival()` first tick | `ATTACKING` or `DEBUFFING` (based on job type) |
| Effect applied | `WORK_COMPLETE` |
| Target dead | `WORK_COMPLETE` |

### 6. `BaseSupport` — Healing Animations

```
Spot player → navigate → arrive → STATUS animation → apply effect
```

| Event | Animation Phase |
|-------|----------------|
| Player target found | (navigate) |
| `handlePlayerArrival()` first tick | `HEALING` |
| Effect applied | `WORK_COMPLETE` |

### 7. `BasePlacer` — Placement Animations

```
Fetch item → navigate to spot → PLACING animation → place block
```

| Event | Animation Phase |
|-------|----------------|
| Navigating to placement | (pathfinding) |
| Arrived, placing block | `PLACING` |
| Block placed | `WORK_COMPLETE` |

### 8. Environmental Jobs — Special Animations

```
Find target → navigate → arrive → ENVIRONMENTAL animation → modify world
```

Each environmental job (FrostFormer, ObsidianForge, GrowthAccelerator, etc.) gets `ENVIRONMENTAL`.

### 9. Logistics Jobs — Custom Handling

`ItemConsolidator` and `GroundItemCollector` already have custom ticks. Add `PROCESSING` for item sorting and `WORK_COMPLETE` for successful pickups.

### 10. Combo Jobs — Inherit From Base

Combo jobs extend the base classes so they automatically inherit whatever animation system the base uses.

### 11. `WorkerDispatcher` — Idle Animations

| Event | Animation Phase |
|-------|----------------|
| No eligible jobs, returning to origin | `IDLE_AT_ORIGIN` |
| Extended idle (30+ seconds) | `IDLE_BORED` |
| Job assigned | (EXCITED flag already handles this) |

---

## Implementation: Per-Base-Class Changes

### `WorkerVisualUtils` Changes

```kotlin
object WorkerVisualUtils {
    // ... existing fields ...

    fun handleArrival(
        pokemonEntity: PokemonEntity,
        targetPos: BlockPos,
        world: World,
        particleType: ParticleEffect? = null,
        offset: Double = 3.0,
        workPhase: WorkPhase = WorkPhase.HARVESTING,  // NEW
    ): Boolean {
        // ... existing proximity / grace period logic ...

        if (arrived == null) {
            arrivalTick[pokemonId] = now
            WorkerAnimationUtils.playWorkAnimation(pokemonEntity, workPhase, world)  // REPLACES swingHand
            lookAt(pokemonEntity, targetPos)
            pokemonEntity.navigation.stop()
            return false
        }

        if (now - arrived < WORK_DELAY_TICKS) {
            lookAt(pokemonEntity, targetPos)
            return false
        }

        // Work delay complete
        arrivalTick.remove(pokemonId)
        WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.WORK_COMPLETE, world) // REPLACES swingHand
        if (particleType != null) spawnParticles(world, targetPos, particleType)
        // cry is now part of the animation, remove random cry
        return true
    }
}
```

### `BaseHarvester` Changes

```kotlin
// In handleHarvesting, pass workPhase to handleArrival:
if (WorkerVisualUtils.handleArrival(pokemonEntity, currentTarget, world, arrivalParticle, arrivalTolerance,
    workPhase = WorkPhase.HARVESTING)) {
    harvest(world, currentTarget, pokemonEntity)
    ...
}
```

Add deposit feedback:
```kotlin
// After successful deposit in tick():
WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.DEPOSIT_SUCCESS, world)

// After failed deposit:
WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.DEPOSIT_FAILED, world)
```

### `BaseDefender` Changes

```kotlin
// When claiming a new hostile:
WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.HOSTILE_SPOTTED, world)

// In handleArrival call — pass workPhase based on job type:
// Subclasses override a new property:
abstract class BaseDefender : Worker {
    open val combatPhase: WorkPhase = WorkPhase.ATTACKING
    // ...
    if (WorkerVisualUtils.handleArrival(pokemonEntity, targetMob.blockPos, world, attackParticle, 2.0,
        workPhase = combatPhase)) {
        applyEffect(world, pokemonEntity, targetMob)
    }
}
```

Defense jobs that debuff (Repeller, Fearmonger, Poison Trap, Ice Trap) override `combatPhase = WorkPhase.DEBUFFING`.

### DSL Changes

The DSL builders need a new optional parameter:

```kotlin
open class GatheringJob(
    // ... existing params ...
    val workPhase: WorkPhase = WorkPhase.HARVESTING,  // NEW — most gathering is HARVESTING
) : BaseHarvester() {
    // BaseHarvester reads this when calling handleArrival
}

open class DefenseJob(
    // ... existing params ...
    val combatPhase: WorkPhase = WorkPhase.ATTACKING,  // NEW
) : BaseDefender() {
    override val combatPhase get() = field
}
```

This lets individual job registrations override the phase if needed:
```kotlin
val REPELLER = DefenseJob(
    name = "repeller",
    combatPhase = WorkPhase.DEBUFFING,  // Status-style animation instead of physical
    ...
)
```

---

## Idle Animation System (WorkerDispatcher)

Add idle tracking to `WorkerDispatcher`:

```kotlin
private val idleSinceTick = mutableMapOf<UUID, Long>()
private const val BORED_THRESHOLD_TICKS = 600L  // 30 seconds

// In tickPokemon, when no job is available:
val idleSince = idleSinceTick.getOrPut(pokemonId) { now }
if (now - idleSince >= BORED_THRESHOLD_TICKS) {
    WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.IDLE_BORED, world)
} else {
    WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.IDLE_AT_ORIGIN, world)
}

// When a job IS assigned, clear idle tracking:
idleSinceTick.remove(pokemonId)
```

---

## Cooldown Visualization (BaseProducer)

When a producer is on cooldown, play the waiting animation:

```kotlin
if (now - lastTime < cooldownTicks) {
    // Play cooldown animation every ~10 seconds
    WorkerAnimationUtils.playWorkAnimation(pokemonEntity, WorkPhase.COOLDOWN_WAITING, world)
    return
}
```

---

## File Changes Summary

| File | Changes |
|------|---------|
| **NEW** `utilities/WorkerAnimationUtils.kt` | Animation utility with cooldown tracking and phase-based playback |
| **NEW** `enums/WorkPhase.kt` | Enum defining all work phases with animation fallback chains |
| `utilities/WorkerVisualUtils.kt` | Add `workPhase` parameter to `handleArrival()` and `handlePlayerArrival()`. Replace `swingHand()` with animation calls. Remove random `cry()` (now part of animations). |
| `jobs/BaseHarvester.kt` | Pass `WorkPhase.HARVESTING` to arrival. Add deposit success/fail animations. |
| `jobs/BaseProducer.kt` | Add `PRODUCING` animation on produce, `COOLDOWN_WAITING` on cooldown. |
| `jobs/BaseProcessor.kt` | Pass `WorkPhase.PROCESSING` to arrival. Add phases for transform. |
| `jobs/BaseDefender.kt` | Add `combatPhase` property. `HOSTILE_SPOTTED` on target claim. |
| `jobs/BaseSupport.kt` | Pass `WorkPhase.HEALING` to player arrival. |
| `jobs/BasePlacer.kt` | Pass `WorkPhase.PLACING` to arrival. |
| `jobs/WorkerDispatcher.kt` | Add idle boredom tracking with `IDLE_BORED` / `IDLE_AT_ORIGIN` animations. |
| `jobs/dsl/GatheringJob.kt` | Add optional `workPhase` parameter. |
| `jobs/dsl/DefenseJob.kt` | Add optional `combatPhase` parameter. |
| `jobs/dsl/SupportJob.kt` | Add optional `workPhase` parameter. |
| `jobs/registry/DefenseJobs.kt` | Set `combatPhase = DEBUFFING` on debuff-type jobs. |
| Environmental/Logistics jobs | Pass `WorkPhase.ENVIRONMENTAL` or appropriate phase to arrival calls. |

---

## Rollout Order

### Phase 1: Foundation (no behavior changes yet)
1. Create `WorkPhase` enum
2. Create `WorkerAnimationUtils` utility
3. Add `workPhase` parameter to `WorkerVisualUtils.handleArrival()` (default = `HARVESTING` for backward compat)
4. **Test:** Build, deploy, verify no regressions — everything should work identically since default is the same behavior.

### Phase 2: Base Classes
5. Wire `BaseHarvester` → `HARVESTING` + deposit animations
6. Wire `BaseProducer` → `PRODUCING` + `COOLDOWN_WAITING`
7. Wire `BaseProcessor` → `PROCESSING`
8. Wire `BasePlacer` → `PLACING`
9. Wire `BaseDefender` → `ATTACKING`/`DEBUFFING` + `HOSTILE_SPOTTED`
10. Wire `BaseSupport` → `HEALING`
11. **Test:** Build, deploy, observe Pokémon playing appropriate animations.

### Phase 3: Idle & Reactions
12. Add idle boredom tracking to `WorkerDispatcher`
13. Add `DEPOSIT_FAILED` and `TARGET_LOST` reactions
14. Tune animation cooldowns — make sure animations aren't too frequent or too sparse

### Phase 4: DSL & Registry Polish
15. Add `workPhase`/`combatPhase` to DSL builders
16. Override specific jobs where the default phase isn't ideal
17. Environmental jobs get `ENVIRONMENTAL` phase

### Phase 5: Cleanup
18. Remove `swingHand()` calls (replaced by animations)
19. Remove standalone `cry()` calls (now embedded in animation phases)
20. Merge `WorkerAnimationUtils` cleanup into `WorkerVisualUtils.cleanup()`

---

## Edge Cases & Considerations

### Not Every Pokémon Has Every Animation
`playAnimation` on a missing animation is a silent no-op. The animation chain in `WorkPhase` provides ordered fallbacks — `physical` covers 76/922, but `cry` covers 772/922. The worst case is a Pokémon that just does nothing visible, which is the same as current behavior.

### Animation Duration vs. Work Delay
The 30-tick (1.5s) work delay in `handleArrival` is long enough for most Cobblemon animations to complete. No timing changes needed. If an animation is shorter, the Pokémon just stands still for the remainder (looking at target). If longer, the work fires mid-animation — still looks fine.

### Server-Side Only
`playAnimation()` sends a packet to clients. CobbleCrew is server-side, calls it from tick methods — this is the intended server-to-client flow. No client-side code needed.

### Animation Spam Prevention
The `ANIM_COOLDOWN_TICKS` (20 ticks = 1 second) prevents hammering the animation system. Combined with the 30-tick work delay, the max animation rate is about once per 1.5 seconds — feels natural.

### Particle System Stays
Particles are still valuable as visual feedback since they're always visible regardless of Pokémon species/model. The animation system adds to the particle system, doesn't replace it.

### Backward Compatibility
All `handleArrival` calls that don't pass `workPhase` get `WorkPhase.HARVESTING` by default. No existing behavior changes until explicitly wired.

---

## Config Integration (Future)

Optional config to let server admins disable animations if they cause performance issues:

```kotlin
// CobbleCrewConfig.kt, general group
var enableWorkAnimations: Boolean = true
var animationCooldownTicks: Int = 20
```

This would be checked in `WorkerAnimationUtils.playWorkAnimation()` before calling `playAnimation()`.
