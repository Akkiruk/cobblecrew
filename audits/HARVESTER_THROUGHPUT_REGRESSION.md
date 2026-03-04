# Harvester Throughput Regression Audit

**Date**: 2026-03-03  
**Scope**: All systems affecting harvester cycle time — dispatch, availability, navigation, visual delays, deposit, idle behaviors  
**Symptom**: 8 Pidgeys with Pluck can't keep 10% of a berry field clear; ~1 harvest/minute. Previously 4 Pidgeys kept it fully picked.

---

## BUG 1 (CRITICAL): `isAvailable()` self-sabotage — claimed targets count as unavailable

**File**: `common/.../jobs/BaseHarvester.kt` lines 55-67  
**Introduced**: commit `890dab9` ("resolve stickiness trap")

The `isAvailable()` check added `!CobbleCrewNavigationUtils.isTargeted(pos, world)` to filter out claimed targets. This was intended to fix stickiness, but it **also excludes the asking Pokémon's own claim**.

When the dispatcher calls `selectBestAvailableJob()` → `isAvailable(context, pokemonId)`, the Pokémon may already have a claimed target on this job (e.g. navigating to a berry). That claim makes its own target show up as `isTargeted()`, causing `isAvailable()` to potentially return `false` if there are no OTHER unclaimed targets. This means:

1. Pokémon A claims berry at (10,64,20) and starts navigating
2. Next tick, dispatcher re-evaluates: calls `isAvailable()` for berry_harvester
3. `isAvailable()` iterates targets. (10,64,20) returns `isTargeted=true` (by Pokémon A itself!)
4. If that was the only ripe berry, `isAvailable()` returns `false`
5. Dispatcher drops the job, Pokémon goes idle. Claim still held for 100 ticks.
6. Next selection cycle: same thing happens. Pokémon is stuck alternating between sticky (with no work visible) and idle.

**With 8 Pidgeys**: All 8 claim their targets. All 8 then see 0 unclaimed targets on re-evaluation. All 8 drop to idle. Claims time out after 100 ticks, blacklisting starts. Only after blacklist expiry (5+ seconds) can targets be retried.

**The same bug exists in `EnvironmentalJob.isAvailable()`** (line 89-91 of EnvironmentalJob.kt) — it calls `findTarget()` then checks `!isTargeted(found, world)`, but `findTarget` already filters out targeted blocks. So if Pokémon A has claimed the closest growable, `isAvailable()` may return the *next* closest but then check it's not targeted — but the Pokémon's own active target is invisible to this check. This is less severe for environmental since there are usually many growables, but it's architecturally the same bug.

**Impact**: This is the #1 cause of the regression. Every active worker potentially fails its own availability check.

---

## BUG 2 (HIGH): Stickiness path is silently unproductive — no `tick()` call when sticking "by stickiness"

**File**: `common/.../jobs/WorkerDispatcher.kt` lines 128-147

In the stickiness block:
```kotlin
val hasRealWork = current.hasActiveState(pokemonId)
    || CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null
    || CobbleCrewNavigationUtils.getPlayerTarget(pokemonId, world) != null
val stickyAndAvailable = !hasRealWork
    && now - assignedAt < JOB_STICKINESS_TICKS
    && current.isAvailable(context, pokemonId)
if (hasRealWork || stickyAndAvailable) {
    current.tick(context, pokemonEntity)
    return
}
```

When `stickyAndAvailable` is true, the Pokémon ticks the job BUT the job's `tick()` → `handleHarvesting()` → `findReachableTarget()` is called. This is fine on its own. But due to Bug 1, `isAvailable()` in the stickiness check now does a full `targets.any { ... && !isTargeted }` scan. If all ready targets are claimed (including this Pokémon's own), `isAvailable()` returns false, stickiness is broken early, and the job is dropped.

So stickiness **never protects a worker with an active claim** because its own claim poisons the availability check.

---

## BUG 3 (HIGH): PathfindingBudget starves workers — 6 pathfinds/tick shared across all Pokémon

**File**: `common/.../utilities/PathfindingBudget.kt`

`MAX_PATHFINDS_PER_TICK = 6` is a global budget. With 8 Pidgeys all trying to find berry targets via `findReachableTarget()`, each Pokémon tries up to 3 candidates. That's potentially 24 pathfind requests per tick, but only 6 are allowed. The remaining 18 get `DEFERRED`, meaning those Pokémon get NO target this tick and fall through to idle.

**Compounding with Bug 1**: After Bug 1 causes mass job-drops, all 8 Pidgeys simultaneously try to re-acquire targets next tick. Only 2 can pathfind (6 ÷ 3 attempts each). The other 6 get DEFERRED → null target → idle → idle pickup → consumes ticks. By the time they get another chance, the budget is eaten again by a different batch.

This creates a pathfinding starvation spiral in high-density worker setups.

---

## BUG 4 (HIGH): Idle pickup hijacks workers mid-recovery — no cooldown after job ends

**File**: `common/.../jobs/WorkerDispatcher.kt` lines 222-230, 272-355

When a Pokémon falls to idle (due to Bug 1 or pathfinding starvation), `handleIdleBehavior` immediately runs `tryIdlePickup()`. The berry harvest itself produces drops that land on the ground briefly. So:

1. Pidgey A harvests a berry → items drop on ground
2. Pidgey B just lost its job (Bug 1) → goes idle → `tryIdlePickup()` scans
3. Pidgey B claims the ground item, navigates to it, picks it up
4. Pidgey B now holds items, enters deposit loop
5. Deposit takes 15 ticks delay + navigation to chest + chest animation
6. Total time wasted: 3-5 seconds per spurious pickup

The `IDLE_PICKUP_ATTEMPT_COOLDOWN = 60L` (3 seconds) only throttles the **scan**, not the claim+navigate phase. Once a claim is made, the Pokémon is locked into the pickup for up to `IDLE_PICKUP_STALE_TICKS = 100L` (5 seconds).

**Worse**: there's no `lastPickupAttemptTick` reset when transitioning FROM a real job TO idle. The cooldown timer may have already elapsed during the previous job's ticks, so the very first idle tick triggers an immediate scan.

---

## BUG 5 (MEDIUM): Target claim timeout (100 ticks) is too short for the work cycle

**File**: `common/.../utilities/CobbleCrewNavigationUtils.kt` line 70

`DEFAULT_CLAIM_TIMEOUT = 100L` (5 seconds). A full harvest cycle is:
- Navigate to target: variable, could be 2-4 seconds for distant blocks
- Work delay (handleArrival): 30 ticks = 1.5 seconds
- Harvest action: 1 tick
- Total: 2.5-5.5 seconds

If navigation takes more than 3.5 seconds, the claim expires before the Pokémon arrives. This triggers:
1. Escalating blacklist on the position (5s → 30s → 120s)
2. Other Pokémon may claim the now-released target
3. When the original Pokémon arrives, the target may be gone or re-claimed

`BaseHarvester.handleHarvesting()` calls `renewClaim()` on every tick, but only AFTER the claim is established. If the first `navigateTo()` is called and then pathfinding budget defers for several ticks, the claim can expire before renewal starts.

Actually — looking more carefully, `renewClaim` IS called every tick in `handleHarvesting()` (line 149). So this may not expire during navigation. But it IS vulnerable during the `findReachableTarget()` phase, which can span multiple ticks due to `PathfindingBudget.DEFERRED`. The claim is only created AFTER a reachable target is found, so this is less impactful than initially assessed. Revising to MEDIUM.

---

## BUG 6 (MEDIUM): `selectBestAvailableJob` calls `isAvailable()` on EVERY candidate — O(N×M) per Pokémon per tick

**File**: `common/.../jobs/WorkerDispatcher.kt` lines 207-220

```kotlin
val sorted = candidates
    .groupBy { it.importance }
    .toSortedMap()
    .flatMap { (_, workers) -> workers.shuffled() }
val available = sorted.firstOrNull { it.isAvailable(context, pokemonId) }
```

`isAvailable()` for each `BaseHarvester` calls `CobbleCrewCacheManager.getTargets()` (which does `raw.toSet()` — allocating a new Set), then iterates ALL cached positions checking block state, readiness, expiry, targeting, and unreachability. For a berry field with 50+ berry blocks, that's 50 block state reads per candidate.

With 8 Pidgeys evaluating every tick, and multiple eligible jobs per Pidgey, this is thousands of block state reads per tick. This doesn't cause the throughput bug directly but amplifies the cost of Bug 1's mass re-evaluation.

---

## BUG 7 (MEDIUM): Seedot Growth Accelerator targets mature crops despite fix

**File**: `common/.../jobs/registry/EnvironmentalJobs.kt` lines 91-96

The v3.0.42 fix added `!CobbleCrewCropUtils.isMatureCrop(world, it)` to GrowthAccelerator's `findTarget`. The `isMatureCrop` check handles vanilla crops and Cobblemon crops. **But it does NOT handle BerryBlock** — look at the `isMatureCrop` function:

```kotlin
return when {
    block is HeartyGrainsBlock -> ...
    block is CropBlock -> ...
    block is CaveVines -> ...
    block is SweetBerryBushBlock -> ...
    block is NutBushBlock -> ...
    blockId in FarmersDelightBlocks.MUSHROOMS -> ...
    else -> false  // ← BerryBlock falls here!
}
```

`BerryBlock` (Cobblemon) is NOT a `CropBlock`. It's not handled by `isMatureCrop`. So `isMatureCrop` returns `false` for mature BerryBlocks, meaning the Growth Accelerator's filter `!isMatureCrop()` passes them through — it sees them as "not mature" and valid targets.

HOWEVER, the GROWABLE category validator is `block is CropBlock || block is SaplingBlock`. BerryBlock is neither of these, so BerryBlocks should NOT appear in the GROWABLE cache at all. So this specific path shouldn't trigger for berries.

**The Seedot issue is likely about vanilla crops**: Seedot stands near wheat/carrots that ARE mature. The `isMatureCrop` fix is in `findTarget` but NOT in the `action` lambda. The action still checks `!CobbleCrewCropUtils.isMatureCrop(world, pos)`, which should prevent it from ticking mature crops. But between `findTarget` selecting a non-mature crop and the Pokémon arriving (30+ ticks), the crop may have matured. The target is claimed, the Pokémon walks there, the arrival delay fires, and only then does the action check maturity — wasting the entire navigation+delay cycle.

More critically: the GROWABLE scanner caches block positions at scan time. The scan might find an immature wheat, cache it, and then by the time the Growth Accelerator finds it via cache, it's already mature. `findTarget` filters by `!isMatureCrop()` each tick, so this should work... **unless** there are multiple immature crops constantly becoming mature while the Pokémon is en route, causing a cycle of pick→walk→already mature→release→pick next→walk→already mature.

---

## BUG 8 (MEDIUM): GrowthAccelerator `priority = WorkerPriority.MOVE` conflicts with Harvesters

**File**: `common/.../jobs/dsl/EnvironmentalJob.kt` lines 56-57, `common/.../jobs/registry/EnvironmentalJobs.kt` line 89

GrowthAccelerator has `priority = WorkerPriority.MOVE` and `importance = JobImportance.LOW`.  
Berry Harvester has `priority = WorkerPriority.TYPE` (default from GatheringJob) and `importance = JobImportance.HIGH`.

Wait — checking GatheringJob defaults: `override val priority: WorkerPriority = WorkerPriority.TYPE`. But BERRY_HARVESTER has `qualifyingMoves = setOf("pluck")`. So for a Pidgey with Pluck, the profile builder checks `worker.priority` — which is `WorkerPriority.TYPE` for GatheringJob by default. But the `isEligible` for GatheringJob calls `dslEligible()` which returns true if any qualifying move matches. The profile builder then places it under `worker.priority` (TYPE), not based on HOW it matched.

So Pidgey with Pluck gets BERRY_HARVESTER in the `TYPE` tier (because GatheringJob.priority defaults to TYPE).

Meanwhile, GrowthAccelerator has `priority = WorkerPriority.MOVE`.

In `selectBestAvailableJob`, the dispatcher checks tiers in order: COMBO → MOVE → SPECIES → TYPE. **MOVE is checked before TYPE**. So GrowthAccelerator (MOVE tier, LOW importance) is checked BEFORE Berry Harvester (TYPE tier, HIGH importance).

If Seedot/Pidgey has "growth" (for GrowthAccelerator) AND "pluck" (for Berry Harvester), the dispatcher will:
1. Check COMBO tier → empty
2. Check MOVE tier → finds GrowthAccelerator → calls isAvailable() → if any non-mature growable exists, returns it
3. **Never reaches TYPE tier** where Berry Harvester lives

**This is the #1 reason Seedot does Growth instead of Harvesting.** Priority tiers completely override importance. A MOVE-tier LOW-importance job always wins over a TYPE-tier HIGH-importance job.

For Pidgey specifically: Pidgey learns Pluck but probably doesn't learn Growth. Need to verify, but if Pidgey only has Pluck (no Growth), this doesn't apply to Pidgey — only to Pokémon that have both moves.

---

## BUG 9 (LOW): `isAvailable()` doesn't check if the Pokémon already has this job active

**File**: `common/.../jobs/BaseHarvester.kt` lines 55-67

When the dispatcher evaluates stickiness and it fails (due to Bug 1), it falls through to `selectBestAvailableJob()`. This calls `isAvailable()` for the same job it just failed stickiness on. `isAvailable()` might now return false (due to the Pokémon's own claim), causing the dispatcher to pick a different job or go idle — even though the Pokémon was actively navigating to a valid target.

The fix for Bug 1 would resolve this, but it's worth noting as a design gap: `isAvailable()` has no concept of "this Pokémon is asking for itself."

---

## BUG 10 (LOW): Escalating blacklist compounds with `isAvailable()` filtering

**File**: `common/.../utilities/CobbleCrewNavigationUtils.kt` lines 277-290

When claims timeout (Bug 5), positions are blacklisted with escalating durations (5s → 30s → 120s). Combined with Bug 1 causing mass claim timeouts, positions accumulate fail counts rapidly. After 3 failures (which can happen in under 30 seconds with 8 workers), a position gets 120-second blacklisted.

For a berry field, this means harvested-then-regrown berries can be blacklisted for 2 minutes despite being perfectly valid targets. However, the `890dab9` fix passes `blacklist = false` on clean harvests, so this only applies to claim timeouts. Still, Bug 1 causes many timeouts, so the escalation kicks in frequently.

---

## BUG 11 (LOW): Navigation throttle interacts poorly with work delay reset

**File**: `common/.../utilities/CobbleCrewNavigationUtils.kt` line 97, `common/.../utilities/WorkerVisualUtils.kt` lines 63-65

`NavigationUtils.navigateTo()` has `PATHFIND_INTERVAL_TICKS = 5L`. `WorkerVisualUtils.handleArrival()` now calls `pokemonEntity.navigation.stop()` on every tick while at the target (commit `94c47e6`). But `BaseHarvester.handleHarvesting()` calls BOTH `navigateTo(pokemonEntity, currentTarget)` (line 149) AND `handleArrival()` (line 151) on the same tick. So:

1. `navigateTo()` fires (if 5-tick interval passed) — starts pathing
2. `handleArrival()` immediately stops navigation
3. Next tick: `navigateTo()` is throttled (< 5 ticks) — does nothing
4. Pokémon stands still, potentially slightly drifting

This alternating start/stop doesn't cause the throughput bug but adds 5 ticks of wasted time per arrival cycle where the Pokémon might not be moving towards the target.

---

## SUMMARY: Root Cause Chain

1. **Bug 1** (isAvailable self-sabotage) causes workers to lose their jobs every re-evaluation
2. **Bug 2** (stickiness broken by same check) means stickiness can't protect active workers
3. **Bug 3** (pathfinding starvation) means dropped workers can't re-acquire targets quickly
4. **Bug 4** (idle pickup hijack) means idle Pokémon waste time on ground items instead of re-acquiring harvest targets
5. **Bug 8** (priority tier inversion) means Pokémon with both Growth and harvest moves do Growth preferentially

The pre-regression state (v3.0.39 and earlier) didn't have the `isTargeted` check in `isAvailable()`, so workers kept their jobs through stickiness and never entered the dropout→idle→pickup spiral. The stickiness fix was well-intentioned but introduced a worse problem.

---

## RECOMMENDED FIX PRIORITY

1. **Bug 1** — Make `isAvailable()` exclude the asking Pokémon's own claim from the `isTargeted` check
2. **Bug 8** — Ensure harvesters use MOVE priority when matched by move (not just TYPE default)
3. **Bug 3** — Increase pathfinding budget or exempt workers with existing targets
4. **Bug 4** — Add cooldown before idle pickup engages after a real job ends
5. **Bug 7** — Add BerryBlock to `isMatureCrop` for completeness
6. Remaining bugs are secondary and may self-resolve once Bug 1 is fixed
