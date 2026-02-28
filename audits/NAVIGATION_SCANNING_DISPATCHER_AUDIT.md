# CobbleCrew Core Systems Audit: Navigation, Block Scanning, & Dispatcher

**Date**: 2026-02-28  
**Scope**: `CobbleCrewNavigationUtils`, `DeferredBlockScanner`, `CobbleCrewCacheManager`, `WorkerDispatcher`, and all `Base*` job classes  
**Purpose**: Identify structural weaknesses, performance bottlenecks, and inconsistencies — then propose what a ground-up rewrite could look like

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [NavigationUtils — Findings](#2-navigationutils--findings)
3. [Block Scanning (DeferredBlockScanner + CacheManager) — Findings](#3-block-scanning--findings)
4. [WorkerDispatcher — Findings](#4-workerdispatcher--findings)
5. [Cross-Cutting Issues](#5-cross-cutting-issues)
6. [Rewrite Proposal: What We'd Do Differently](#6-rewrite-proposal)
7. [Priority Matrix](#7-priority-matrix)

---

## 1. System Overview

The current architecture has three pillars:

```
DeferredBlockScanner  →  CobbleCrewCacheManager  →  WorkerDispatcher
     (discovers)            (stores)                  (assigns + ticks)
                                                           ↓
                                                    CobbleCrewNavigationUtils
                                                     (pathfinding + claims)
```

**Data flow per tick (pasture)**:
1. `DeferredBlockScanner.tickAreaScan()` scans N blocks, classifies by `BlockCategory`, stages results
2. On scan completion, `CobbleCrewCacheManager.replaceAllCategoryTargets()` atomically swaps cache
3. For each Pokémon, `WorkerDispatcher.tickPokemon()` builds/retrieves profile, picks a job
4. The job's `tick()` reads from cache, uses `NavigationUtils` to claim/navigate/release targets

**Data flow per tick (party)**:
1. `PartyWorkerManager.runEagerScan()` does a synchronous full scan (small radius)
2. Same cache manager path
3. Same dispatcher path, but `JobContext.Party` with player-relative origin

---

## 2. NavigationUtils — Findings

### 2.1 — State Fragmentation: 9 Independent Maps

```
pokemonToTarget        — UUID → BlockPos
targetedBlocks         — BlockPos → Claim
pokemonToPlayerTarget  — UUID → UUID
targetedPlayers        — UUID → Claim
pokemonToMobTarget     — UUID → Int
targetedMobs           — Int → UUID
recentlyExpiredTargets — BlockPos → ExpiredTarget
expiredQueue           — PriorityQueue<ExpiredTarget>
targetFailCounts       — Pair<UUID,BlockPos> → Int
unreachableCache       — Pair<UUID,BlockPos> → Long
lastPathfindTick       — UUID → Long
```

**Problem**: These are all independent `mutableMapOf()` with no transactional guarantees. A crash or exception mid-update silently orphans entries. For example, if `releaseTarget()` succeeds on `pokemonToTarget.remove()` but throws before `targetedBlocks.remove()`, the target is permanently claimed by a ghost.

**Problem**: `Pair<UUID, BlockPos>` as map keys is allocation-heavy. Every `isUnreachable()` check allocates a new `Pair` to look up the map. With 20 Pokémon × 3 pathfind attempts × every tick, that's ~60 pair allocations/tick just for unreachable checks.

**Problem**: The expired target system (`recentlyExpiredTargets` + `expiredQueue` + `targetFailCounts`) is complex but only solves a narrow case: preventing immediate re-targeting of a block that just timed out. Three data structures to track one concept.

### 2.2 — Claim Timeout Is Global, Not Per-Job

`CLAIM_TIMEOUT_TICKS = 100L` (5 seconds) applies uniformly. But:
- A tree-felling combo job legitimately takes 8+ seconds navigating to a far tree
- A crop harvester next to the crop only needs 1-2 seconds

When a slow job legitimately takes longer than 5s, the claim expires, the target enters the blacklist, `targetFailCounts` increments, and the Pokémon eventually gets blacklisted from a perfectly valid target.

### 2.3 — `navigateTo()` Throttle Is Per-Pokémon, Not-Per-Target

`PATHFIND_INTERVAL_TICKS = 5L` prevents re-pathing more than once per 5 ticks. But if a Pokémon's target changes (e.g. mob moved, previous target broke), the throttle still blocks the new path. The throttle tracks pokemonId but not which target it was computed for.

### 2.4 — Three Target Systems, Three APIs

Block targets, player targets, and mob targets are completely separate codepaths:
- `claimTarget(pokemonId, BlockPos, World)` / `releaseTarget()`
- `claimTarget(pokemonId, PlayerEntity, World)` / `releasePlayerTarget()`
- `claimMobTarget(pokemonId, Int)` / `releaseMobTarget()`

A Pokémon can theoretically hold a block claim AND a player claim AND a mob claim simultaneously. No code prevents it, and cleanup must remember to call all three release functions. This is error-prone and already visible in `cleanupPokemon()` which calls all three.

### 2.5 — `canPathfindTo()` Is Synchronous and Expensive

```kotlin
fun canPathfindTo(pokemonEntity: PokemonEntity, targetPos: BlockPos): Boolean {
    val path = pokemonEntity.navigation.findPathTo(targetPos, 1)
    return path != null && path.reachesTarget()
}
```

This is called up to 3 times per target selection in `findReachableTarget()`. Each `findPathTo()` is an A* search that can block the server thread for multiple milliseconds, especially in complex terrain. With 20 Pokémon all re-evaluating targets, this can cause visible lag spikes.

### 2.6 — Blacklist Escalation Scans All Fail Counts on Every Check

```kotlin
fun isRecentlyExpired(pos: BlockPos, world: World): Boolean {
    // ...
    val maxFails = targetFailCounts.entries
        .filter { it.key.second == pos }
        .maxOfOrNull { it.value } ?: 0
```

This iterates the entire `targetFailCounts` map every time, filtering by position. The map grows unboundedly (pairs are never cleaned except via `cleanupPokemon()`). With many Pokémon and many targets, this becomes O(N*M) per check.

---

## 3. Block Scanning — Findings

### 3.1 — `BlockPos.stream()` Allocates Massively

```kotlin
ScanJob(BlockPos.stream(searchArea).iterator(), currentTick - 1)
```

`BlockPos.stream()` in Minecraft returns a `Stream<BlockPos>` that creates mutable `BlockPos` instances. The iterator stores the stream state. For radius=8, height=5, this is `(17 × 11 × 17) = 3,179` positions. The issue is that `Stream.iterator()` isn't designed for pause-resume across ticks — it holds onto the stream pipeline, preventing GC of intermediate state.

### 3.2 — Every Block Is Tested Against Every Validator

```kotlin
for ((category, validator) in categoryValidators) {
    if (category !in needed) continue
    if (validator(world, pos)) { ... }
}
```

With ~55 `BlockCategory` entries and ~40 needed categories, each block position runs ~40 validator lambdas. Each validator calls `world.getBlockState(pos)` independently. That's `40 × getBlockState()` calls per position — the block state is fetched from the chunk section 40 times for the same position.

**With BLOCKS_PER_TICK = 50**: That's 2,000 `getBlockState()` calls per tick, when it should be ~50 (one per position, then match the result).

### 3.3 — Staged Results Use `MutableSet<BlockPos>` — No Spatial Indexing

Cache is a flat `MutableSet<BlockPos>` per category. Every target lookup (`findClosestTarget`, `isAvailable`, etc.) iterates the entire set:

```kotlin
targets.filter { ... }.minByOrNull { it.getSquaredDistance(origin) }
```

With 200+ targets in a category (e.g., DIRT, STONE), this is O(N) per lookup. Multiple Pokémon doing this per tick compounds linearly.

### 3.4 — Party Scan Duplicates the Entire Scan Logic

`PartyWorkerManager.runEagerScan()` is a copy-paste of the deferred scanner's inner loop, minus the deferral. Same validator iteration, same staged map, same `replaceAllCategoryTargets()`. Two codepaths that must stay in sync but have no shared implementation.

### 3.5 — No Delta/Incremental Updates

When a harvester breaks a block, it calls `CobbleCrewCacheManager.removeTarget()`. But when a player places a new chest, or a crop grows to maturity, or a tree grows from a sapling — nothing updates the cache until the next full scan (20+ seconds later). The cache is read-write but only reliably written during scans.

### 3.6 — Global `removeTargetGlobal()` Iterates ALL Caches

```kotlin
fun removeTargetGlobal(category: BlockCategory, pos: BlockPos) {
    caches.values.forEach { cache ->
        cache.targetsByCategory[category]?.remove(pos)
    }
}
```

With overlapping pasture ranges, this is correct but O(# pastures) per call. In a server with 50 pastures, every harvest triggers 50 set removals.

---

## 4. WorkerDispatcher — Findings

### 4.1 — Job Selection Is O(eligible × isAvailable)

```kotlin
val shuffled = eligible.shuffled()
val job = shuffled.firstOrNull { it.isAvailable(context, pokemonId) }
```

Each `isAvailable()` for a `BaseHarvester` iterates the entire target set, checks airness, readiness, blacklisting, and unreachable state. A Pokémon eligible for 15 jobs runs up to 15 `isAvailable()` calls before finding work. Worst case (all unavailable) is O(15 × N_targets).

### 4.2 — `eligible.shuffled()` Every Tick

`shuffled()` creates a new list and randomizes it every tick, even when the same Pokémon is idle and repeatedly finding nothing. The shuffle is pointless when no jobs are available — it should only shuffle when switching jobs.

### 4.3 — Profile Invalidation Only Checks Moves

```kotlin
profiles[pokemonId]?.let { cached ->
    if (cached.moves == moves) return cached
```

The profile caches types, species, ability, and moves. But only move changes trigger rebuild. If a Pokémon's type changed (form change), or ability changed (ability capsule), the stale profile is used indefinitely until the Pokémon is recalled.

### 4.4 — Idle Pickup Lives in the Dispatcher

`tryIdlePickup()` is ~50 lines of item entity scanning, navigation, and depositing logic embedded directly in the dispatcher. It's not a Worker, doesn't go through profiles, and has its own parallel state maps (`idleHeldItems`, `idleFailedDeposits`). This creates a shadow job system that bypasses all the formal job lifecycle.

### 4.5 — `returnToOrigin()` Doesn't Use Claims

When idle, `returnToOrigin()` calls `navigateTo()` without claiming any target. This means the pathfind throttle uses the pokemonId globally, and if the Pokémon was recently navigating to a work target, the 5-tick cooldown can delay the return-to-origin path start.

### 4.6 — No Priority Weighting in Job Selection

`allEligible()` returns `combo + move + species + type` — priority tiers are concatenated in order. But `shuffled()` randomizes the entire list. A COMBO priority job has no actual advantage over a TYPE job in selection. The priority system is conceptual but not enforced.

```kotlin
fun allEligible(): List<Worker> =
    comboEligible + moveEligible + speciesEligible + typeEligible
```

After `.shuffled()`, position in the original list is irrelevant.

---

## 5. Cross-Cutting Issues

### 5.1 — No Unified State Machine

Each base class implements its own lifecycle:
- `BaseHarvester`: implicit FSM (no target → navigating → arrived → harvesting → holding → depositing)
- `BaseProcessor`: explicit `Phase` enum (IDLE → NAVIGATING_INPUT → DEPOSITING)
- `BasePlacer`: explicit `Phase` enum (IDLE → FETCHING → NAVIGATING_PLACEMENT)
- `BaseProducer`: implicit (cooldown → produce → hold → deposit)
- `BaseDefender`: implicit (scan → claim mob → navigate → apply → release)
- `BaseSupport`: implicit (scan players → claim → navigate → apply → cooldown)

Six different lifecycle implementations with six different sets of state maps. The "am I navigating, arrived, or working?" question is answered differently by each. This makes it hard to add cross-cutting features (e.g., "interrupt all navigating Pokémon when a pasture is broken").

### 5.2 — Deposit Logic Duplicated Across 4 Classes

`BaseHarvester`, `BaseProducer`, `BaseProcessor`, and `BasePlacer` all have their own `heldItemsByPokemon` + `failedDepositLocations` + deposit handling. The dispatcher's idle pickup also has `idleHeldItems` + `idleFailedDeposits`. That's 5 independent implementations of "hold items → navigate to container → deposit".

### 5.3 — `mutableMapOf<UUID, ...>()` Everywhere, No Cleanup Guarantees

Every class has 3-8 `mutableMapOf<UUID, ...>()` tracking per-Pokémon state. Each has a `cleanup(pokemonId)` that must remove from all maps. If any map is forgotten, state leaks. There's no compile-time or runtime check that cleanup is comprehensive.

Current cleanup map counts:
- `BaseHarvester`: 3 maps
- `BaseProducer`: 3 maps
- `BaseProcessor`: 4 maps
- `BasePlacer`: 4 maps
- `BaseDefender`: 3 maps (2 in base + mob target in NavUtils)
- `BaseSupport`: 2 maps (1 in base + player target in NavUtils)
- `WorkerDispatcher`: 8 maps
- `NavigationUtils`: 11 maps

**Total: ~38 maps** that must be manually kept in sync.

### 5.4 — Thread Safety Is Inconsistent

`CobbleCrewCacheManager` uses `ConcurrentHashMap` for its outer maps, but the inner `MutableSet<BlockPos>` within `PastureCache` is a plain `mutableSetOf()`. Concurrent reads during scan + job ticking can throw `ConcurrentModificationException` if the scan writes while a job iterates.

`NavigationUtils` and `WorkerDispatcher` use plain `mutableMapOf()` — fine if always on the server thread, but there's no assertion or enforcement of this.

---

## 6. Rewrite Proposal

### 6.1 — Unified Target System with Typed Claims

Replace three separate claim systems with one:

```kotlin
sealed interface Target {
    data class Block(val pos: BlockPos) : Target
    data class Player(val playerId: UUID) : Target
    data class Mob(val entityId: Int) : Target
}

class ClaimManager {
    // One Pokémon → one target. Period.
    private val claims = mutableMapOf<UUID, Claim>()
    
    data class Claim(
        val target: Target,
        val claimTick: Long,
        val timeoutTicks: Long,  // per-job, not global
    )
    
    fun claim(pokemonId: UUID, target: Target, world: World, timeout: Long): Boolean
    fun release(pokemonId: UUID)
    fun renew(pokemonId: UUID, world: World)
    fun isClaimed(target: Target): Boolean
    fun cleanup(pokemonId: UUID)  // One call cleans everything
}
```

**Benefits**: One map, one cleanup path, enforced single-target-per-Pokémon, per-job timeout instead of global 5s.

### 6.2 — Block State Cache in Scanner

Fetch block state once per position, then match against validators:

```kotlin
repeat(BLOCKS_PER_TICK) {
    val pos = iterator.next()
    val state = world.getBlockState(pos)  // ONE call
    val block = state.block
    
    for ((category, matcher) in blockMatchers) {
        if (category !in needed) continue
        if (matcher.matches(block, state)) {
            staged.getOrPut(category) { mutableSetOf() }.add(pos.toImmutable())
        }
    }
}
```

Validators should take `(Block, BlockState)` not `(World, BlockPos)`. This cuts `getBlockState()` calls from ~2,000/tick to ~50/tick.

### 6.3 — Spatial Index for Target Lookups

Replace `MutableSet<BlockPos>` with a spatial structure:

```kotlin
class SpatialTargetIndex {
    // Chunk-bucketed: ChunkPos → Set<BlockPos>
    private val byChunk = mutableMapOf<Long, MutableSet<BlockPos>>()
    
    fun nearest(origin: BlockPos, predicate: (BlockPos) -> Boolean): BlockPos? {
        // Spiral outward from origin's chunk, early-terminate when 
        // distance exceeds best found so far
    }
    
    fun add(pos: BlockPos)
    fun remove(pos: BlockPos)
    fun all(): Sequence<BlockPos>
}
```

**Why not a full octree?** Overkill for the scale. Chunk buckets are free (already computed), and spiral search gives O(nearby) instead of O(all) for nearest lookups.

### 6.4 — Unified Job State Machine

Replace per-class FSMs with a single state type:

```kotlin
enum class WorkPhaseState {
    SEEKING_TARGET,       // Looking for something to do
    NAVIGATING,           // Walking to target  
    ARRIVAL_DELAY,        // Visual delay at target
    WORKING,              // Doing the thing (harvest, apply effect, etc.)
    HOLDING_ITEMS,        // Carrying items, need to deposit
    NAVIGATING_DEPOSIT,   // Walking to container
    DEPOSITING,           // Deposit animation + delay
    COOLDOWN,             // Post-work cooldown (producers, support)
}

data class PokemonWorkState(
    val phase: WorkPhaseState,
    val job: Worker?,
    val target: Target?,
    val heldItems: List<ItemStack>,
    val phaseStartTick: Long,
    val data: Map<String, Any>,  // job-specific extras
)
```

Then each `Base*` only implements the `WORKING` phase. Navigation, deposit, arrival delay, and cooldown are handled by the state machine runner. This eliminates 5 duplicate deposit implementations and 6 duplicate navigation loops.

### 6.5 — Lazy Availability with Priority Buckets

Instead of shuffling all eligible jobs and checking `isAvailable()` on each:

```kotlin
fun selectJob(profile: PokemonProfile, context: JobContext, pokemonId: UUID): Worker? {
    // Check priority tiers in order — stop at first tier with available work
    for (tier in WorkerPriority.entries) {
        val candidates = profile.getByPriority(tier)
        if (candidates.isEmpty()) continue
        
        // Shuffle only within this tier
        val available = candidates.shuffled().firstOrNull { 
            it.isAvailable(context, pokemonId) 
        }
        if (available != null) return available
    }
    return null
}
```

**Benefits**: COMBO jobs actually take priority. If a COMBO job is available, we never even check TYPE jobs. Reduces `isAvailable()` calls by short-circuiting at the first productive tier.

### 6.6 —  Event-Driven Cache Invalidation

Instead of relying solely on periodic full rescans:

```kotlin
object BlockChangeListener {
    fun onBlockBreak(world: World, pos: BlockPos) {
        // Remove from all relevant caches immediately
        val block = ... // old block state
        val category = classifyBlock(block)
        CobbleCrewCacheManager.removeTargetGlobal(category, pos)
    }
    
    fun onBlockPlace(world: World, pos: BlockPos) {
        // Add to relevant caches if within any pasture radius
        val state = world.getBlockState(pos)
        val category = classifyBlock(state)
        if (category != null) {
            for ((key, cache) in activePastures) {
                if (pos.isWithinRange(key.pos, searchRadius)) {
                    CobbleCrewCacheManager.addTarget(key, category, pos)
                }
            }
        }
    }
}
```

Register on Architectury's block break/place events. This makes the cache eventually-consistent in real time instead of eventually-consistent every 20 seconds. Full rescans still run as backup, but at much longer intervals (minutes, not seconds).

### 6.7 — Per-Pokémon State Container

Replace 38 independent maps with a single state object per Pokémon:

```kotlin
class PokemonWorkerState(val pokemonId: UUID) {
    var activeJob: Worker? = null
    var jobAssignedTick: Long = 0L
    var profile: PokemonProfile? = null
    var workPhase: WorkPhaseState = WorkPhaseState.SEEKING_TARGET
    var target: Target? = null
    var heldItems: MutableList<ItemStack> = mutableListOf()
    var failedDeposits: MutableSet<BlockPos> = mutableSetOf()
    var lastPathfindTick: Long = 0L
    var idleSinceTick: Long? = null
    // ... all per-Pokémon state in one place
    
    fun cleanup() {
        // One function, one object, nothing forgotten
    }
}

// WorkerDispatcher just has:
private val pokemonStates = mutableMapOf<UUID, PokemonWorkerState>()
```

**Benefits**: Cleanup is one `remove()` call. No state leaks. Easy to serialize for debugging. Easy to snapshot for commands.

### 6.8 — Async Pathfinding Budget

Instead of synchronous `canPathfindTo()` calls:

```kotlin
object PathfindingBudget {
    private const val MAX_PATHFINDS_PER_TICK = 3  // server-wide budget
    private var usedThisTick = 0
    
    fun tryPathfind(entity: PokemonEntity, target: BlockPos): PathResult {
        if (usedThisTick >= MAX_PATHFINDS_PER_TICK) return PathResult.DEFERRED
        usedThisTick++
        val path = entity.navigation.findPathTo(target, 1)
        return if (path?.reachesTarget() == true) PathResult.REACHABLE else PathResult.UNREACHABLE
    }
    
    fun resetBudget() { usedThisTick = 0 }  // call at tick start
}
```

This caps pathfinding cost to a fixed budget per tick regardless of how many Pokémon are searching. Pokémon that exceed the budget defer their pathfind check to the next tick. Dramatically reduces worst-case tick time.

### 6.9 — Shared Scan Implementation

Merge deferred and eager scanning into one configurable scanner:

```kotlin
class AreaScanner(
    val key: CacheKey,
    val center: BlockPos,
    val radius: Int,
    val height: Int,
    val mode: ScanMode,  // DEFERRED (N per tick) or EAGER (all at once)
) {
    fun tick(world: World, budget: Int = Int.MAX_VALUE): Boolean { /* returns true if complete */ }
}
```

`PartyWorkerManager` creates a scanner with `mode = EAGER`. Pasture manager creates with `mode = DEFERRED`. Same validation logic, no duplication.

---

## 7. Priority Matrix

| Issue | Impact | Effort | Priority |
|-------|--------|--------|----------|
| **6.2 — Block state cache in scanner** | HIGH (40x fewer getBlockState calls) | LOW (1 file) | **P0** |
| **6.8 — Pathfinding budget** | HIGH (prevents lag spikes) | LOW (new small utility) | **P0** |
| **6.5 — Priority-ordered job selection** | MED (fixes priority being meaningless) | LOW (1 method) | **P1** |
| **6.1 — Unified claim system** | MED (eliminates ghost claims, simplifies) | MED (refactor nav utils + all callers) | **P1** |
| **6.7 — Per-Pokémon state container** | MED (eliminates state leaks long-term) | HIGH (touches everything) | **P2** |
| **6.4 — Unified state machine** | MED (eliminates deposit duplication) | HIGH (rewrite base classes) | **P2** |
| **6.6 — Event-driven cache invalidation** | MED (20s staleness → instant) | MED (new event hooks) | **P2** |
| **6.3 — Spatial index** | LOW-MED (helps with 200+ targets) | MED (new data structure) | **P3** |
| **6.9 — Shared scan impl** | LOW (code hygiene) | LOW (refactor) | **P3** |

### Recommended Execution Order

1. **6.2** — Immediate performance win, minimal risk, touch one file
2. **6.8** — Immediate lag spike protection, small self-contained addition
3. **6.5** — Fix priority system to actually work, small change to dispatcher
4. **6.1** — Unify claims once confident in the above, reduces Nav complexity
5. **6.6 + 6.9** — Event-driven updates + shared scanner in parallel
6. **6.7 + 6.4** — The big restructure: state containers + unified FSM together

---

*This audit reflects the codebase as of 2026-02-28. All line references are against the current `main` branch.*
