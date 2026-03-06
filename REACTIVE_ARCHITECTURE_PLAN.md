# CobbleCrew Reactive Architecture — Implementation Plan

## Goal
Replace the polling-based "scan → stagger → shuffle → sort → delay" pipeline with an event-driven, spatially-indexed, budget-limited system that makes pasture Pokémon feel instantly responsive.

**Zero job definition changes.** All 129 DSL-defined jobs (GatheringJobs, ProductionJobs, etc.) keep their exact same `findTarget()` / `doWork()` overrides. Only the plumbing underneath changes.

---

## Current Flow (What's Slow)
```
Every 5 ticks per pasture:
  For EACH tethered Pokémon:
    1. Build/check profile             (cheap, cached)
    2. shuffled() job candidates        (O(n) per idle Pokémon)
    3. findCachedBlockTarget()          (O(k log k) sort by distance)
    4. navigateTo()                     (vanilla pathfinding)
    5. 30-tick arrival animation        (HARD FREEZE)
    6. doWork()                         (actual work)
    7. deposit                          (walk to container)
```
**Worst case idle → work: 35+ ticks (1.75s+)**

## Target Flow (What We're Building)
```
On block change near pasture:
  → SpatialGrid.update(pos)
  → WorkQueue wakes nearby idle Pokémon

Every tick (budgeted):
  1. Process N idle Pokémon from queue  (longest-idle first)
  2. Grid.findNearest()                 (O(cells) not O(k log k))
  3. navigateTo()                       (same vanilla pathfinding)
  4. Per-job arrival (0/10/30 ticks)    (INSTANT for production)
  5. doWork()                           (same)
  6. deposit                            (same)
```
**Target idle → work: 1–10 ticks (50–500ms) for most jobs**

---

## Phase 1: ArrivalStyle (Quick Win)
**Files changed: 4 | New files: 1 | Risk: Low**
**Impact: Removes 1.5s delay for 60%+ of jobs**

### What
Add `ArrivalStyle` enum (`INSTANT`, `QUICK`, `ANIMATED`) and let each job declare how much visual delay it needs.

### New File
- `enums/ArrivalStyle.kt` — 3-value enum with tick counts

### Changes

| File | What Changes |
|------|-------------|
| `enums/ArrivalStyle.kt` | **NEW** — `enum class ArrivalStyle(val delayTicks: Long) { INSTANT(0), QUICK(10), ANIMATED(30) }` |
| `jobs/BaseJob.kt` | Add `open val arrivalStyle: ArrivalStyle = ArrivalStyle.ANIMATED`. Wire into `checkArrival()` to use `arrivalStyle.delayTicks` instead of hard-coded `WORK_DELAY_TICKS`. |
| `utilities/WorkerVisualUtils.kt` | `handleArrival()` takes `delayTicks: Long` parameter instead of using `WORK_DELAY_TICKS` constant. |
| `jobs/dsl/ProductionJob.kt` | Override `arrivalStyle = ArrivalStyle.INSTANT` |
| `jobs/dsl/ProcessingJob.kt` | Override `arrivalStyle = ArrivalStyle.QUICK` |
| `jobs/dsl/EnvironmentalJob.kt` | Override `arrivalStyle = ArrivalStyle.INSTANT` |
| `jobs/dsl/SupportJob.kt` | Override `arrivalStyle = ArrivalStyle.INSTANT` |
| `jobs/dsl/PlacementJob.kt` | Override `arrivalStyle = ArrivalStyle.QUICK` |

### Implementation Steps
1. Create `ArrivalStyle.kt` enum
2. Add `open val arrivalStyle` to `BaseJob` (default `ANIMATED` — no behavior change for existing jobs)
3. Change `WorkerVisualUtils.handleArrival()` to accept `delayTicks` parameter
4. Update `BaseJob.checkArrival()` to pass `arrivalStyle.delayTicks`
5. Override `arrivalStyle` in DSL job classes that should be faster
6. Build + test

### Backward Compatibility
- Default is `ANIMATED` (30 ticks) — identical to current behavior
- Only jobs that explicitly opt in get faster arrival
- No config changes needed

---

## Phase 2: SpatialGrid (Replaces Distance Sort)
**Files changed: 3 | New files: 1 | Risk: Medium**
**Impact: O(k log k) → O(cells) per target lookup**

### What
Replace the flat `Set<BlockPos>` per category in `CobbleCrewCacheManager` with a grid-indexed spatial structure. Target queries spiral outward from the Pokémon's position instead of sorting all targets by distance.

### New File
- `cache/SpatialGrid.kt` — Grid-indexed spatial data structure

### Design
```kotlin
class SpatialGrid(private val cellSize: Int = 16) {
    // ConcurrentHashMap<GridCell, ConcurrentHashMap<BlockCategory, MutableSet<BlockPos>>>
    // GridCell = data class(cx: Int, cz: Int) — aligned to cellSize boundaries
    
    fun add(pos: BlockPos, category: BlockCategory)
    fun remove(pos: BlockPos, category: BlockCategory)
    fun removeAll(pos: BlockPos)  // remove from all categories
    
    fun findNearest(
        origin: BlockPos,
        category: BlockCategory,
        maxRadius: Int,
        exclude: (BlockPos) -> Boolean  // claim/blacklist check
    ): BlockPos?
    // Spirals outward from origin cell. Returns first unclaimed match.
    
    fun getAll(category: BlockCategory): Set<BlockPos>  // backward compat
    fun clear()
}
```

### Changes

| File | What Changes |
|------|-------------|
| `cache/SpatialGrid.kt` | **NEW** — Grid-indexed block position storage |
| `cache/CobbleCrewCacheManager.kt` | Replace `caches: ConcurrentHashMap<BlockPos, Map<BlockCategory, Set<BlockPos>>>` with per-origin `SpatialGrid` instances. `getTargets()` delegates to grid. Add `findNearest()` method. |
| `jobs/BaseJob.kt` | `findCachedBlockTarget()` calls `CobbleCrewCacheManager.findNearest()` instead of filter+sort. Falls back to `getTargets()` if grid returns null. |
| `utilities/DeferredBlockScanner.kt` | `classifyBlock()` calls grid `.add()` during scan. `replaceAllCategoryTargets()` rebuilds grid atomically. |

### Implementation Steps
1. Build `SpatialGrid` class with add/remove/findNearest/clear
2. Add `findNearest()` to `CobbleCrewCacheManager` that delegates to grid
3. Wire `DeferredBlockScanner` to populate grid during scan
4. Change `BaseJob.findCachedBlockTarget()` to use `findNearest()` with a lambda that checks claims/blacklist
5. Keep `getTargets()` working (returns `grid.getAll()`) for backward compat with `isAvailable()` checks
6. Build + test

### Key Decision: Per-Origin vs Global Grid
- **Per-origin** (current): Each pasture has its own grid. Overlapping pastures have duplicate entries.
- **Global**: Single grid, pasture range check at query time.
- **Recommendation**: Start per-origin (matches current architecture), migrate to global in Phase 5 if needed.

---

## Phase 3: BlockChangeNotifier (Replaces Timer-Based Scan Cooldown)
**Files changed: 3 | New files: 2 | Risk: Medium-High**
**Impact: 30s block detection delay → instant**

### What
Hook into block changes near pastures. When a block is placed/broken/changed, immediately update the spatial grid. This eliminates the scan cooldown — new blocks are visible to workers the tick they appear.

### New Files
- `listeners/BlockChangeNotifier.kt` — Singleton that receives block change events
- `mixin/ServerWorldMixin.java` — Mixin to intercept `setBlockState()` calls

### Design
```kotlin
object BlockChangeNotifier {
    // Track which pasture origins are active (registered by PastureWorkerManager)
    private val activePastures = ConcurrentHashMap<BlockPos, Int>()  // origin → searchRadius
    
    fun registerPasture(origin: BlockPos, radius: Int)
    fun unregisterPasture(origin: BlockPos)
    
    fun onBlockChanged(world: World, pos: BlockPos, oldState: BlockState, newState: BlockState) {
        // 1. Find which pastures are in range of this block
        // 2. For those pastures, classify the new block state
        // 3. Update spatial grid: remove old category, add new category
        // 4. If a new target appeared, wake nearby idle Pokémon (Phase 4)
    }
}
```

### Mixin
```java
@Mixin(ServerWorld.class)
public class ServerWorldMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("RETURN"))
    private void onBlockChanged(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, 
                                CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            BlockChangeNotifier.INSTANCE.onBlockChanged(
                (ServerWorld)(Object)this, pos, /* oldState from capture */, state);
        }
    }
}
```

### Changes

| File | What Changes |
|------|-------------|
| `listeners/BlockChangeNotifier.kt` | **NEW** — Block change → grid update → wake idle |
| `mixin/ServerWorldMixin.java` | **NEW** — Intercepts setBlockState(), forwards to notifier |
| `jobs/PastureWorkerManager.kt` | On first tick: `BlockChangeNotifier.registerPasture(pos, radius)`. On pasture broken: `unregisterPasture(pos)`. |
| `utilities/DeferredBlockScanner.kt` | **Still needed** for initial scan on pasture placement + periodic full re-scan (safety net). But cooldown can be much longer (5 min instead of 30s) since live updates handle 99% of changes. |
| `cobblecrew.mixins.json` | Add `ServerWorldMixin` to mixin list |

### Implementation Steps
1. Create `BlockChangeNotifier` singleton
2. Create `ServerWorldMixin` (Yarn mappings: `setBlockState` in `ServerWorld`)
3. Register/unregister pastures in `PastureWorkerManager`
4. On block change: classify → update grid → (Phase 4: wake idle)
5. Increase `scanCooldownSeconds` default to 300 (5 min safety net)
6. Build + test

### Risk Mitigation
- `setBlockState()` is called VERY frequently. The notifier must bail out fast if no pastures are in range.
- Use a coarse spatial check first: `if (no pasture within maxRadius of pos) return` — O(pasture count) which is typically 1-5.
- Only classify blocks matching `neededCategories` — skip air, skip categories workers don't care about.

---

## Phase 4: WorkQueue (Replaces 5-Tick Stagger + Shuffle)
**Files changed: 3 | New files: 1 | Risk: High**
**Impact: 0-250ms idle stall → processed within 1 tick of becoming idle**

### What
Replace the "every 5 ticks, iterate all Pokémon" loop with a priority queue of idle Pokémon. A fixed budget of Pokémon are assigned jobs per tick (e.g., 5). Longest-idle gets served first. BlockChangeNotifier can boost priority of nearby idle Pokémon.

### New File
- `jobs/WorkQueue.kt` — Priority queue of idle Pokémon awaiting job assignment

### Design
```kotlin
object WorkQueue {
    // Idle Pokémon waiting for job assignment, sorted by idleSinceTick (oldest first)
    private val queue = PriorityBlockingQueue<QueueEntry>(
        64, compareBy { it.idleSince }
    )
    
    data class QueueEntry(
        val pokemonId: UUID,
        val idleSince: Long,
        val context: JobContext,
        val pokemonEntity: WeakReference<PokemonEntity>,
    )
    
    fun enqueue(pokemonId: UUID, context: JobContext, entity: PokemonEntity)
    fun remove(pokemonId: UUID)  // recalled or started working
    
    /**
     * Process up to [budget] idle Pokémon this tick.
     * Called once per server tick from platform hook.
     */
    fun processBudgeted(budget: Int = 5) {
        repeat(budget) {
            val entry = queue.poll() ?: return
            val entity = entry.pokemonEntity.get() ?: return@repeat
            // Attempt job assignment via WorkerDispatcher
            // If no job available, re-enqueue with updated idleSince
        }
    }
    
    /** Wake idle Pokémon near a position (called by BlockChangeNotifier). */
    fun wakeNearby(pos: BlockPos, radius: Int) {
        // Boost priority of matching entries so they're processed sooner
    }
}
```

### Changes

| File | What Changes |
|------|-------------|
| `jobs/WorkQueue.kt` | **NEW** — Budgeted idle Pokémon processing |
| `jobs/PastureWorkerManager.kt` | **Major rewrite.** Remove the 5-tick stagger loop. Instead: (1) detect recalls same as today, (2) for each tethered Pokémon, if idle and not in queue → enqueue. Working Pokémon still tick their active job directly. |
| `jobs/PartyWorkerManager.kt` | Same pattern — idle party Pokémon go into WorkQueue instead of being evaluated every tick. |
| `jobs/WorkerDispatcher.kt` | Extract `assignJob()` from `tickPokemon()` — called by WorkQueue. `tickPokemon()` becomes `tickWorkingPokemon()` — only ticks Pokémon that already have an active job. |
| `listeners/BlockChangeNotifier.kt` | Call `WorkQueue.wakeNearby()` when a target block appears |

### Implementation Steps
1. Create `WorkQueue` with priority-based processing
2. Split `WorkerDispatcher.tickPokemon()` into:
   - `tryAssignJob()` — called by WorkQueue for idle Pokémon
   - `tickActiveJob()` — called by PastureWorkerManager for working Pokémon
3. Rewrite `PastureWorkerManager.tickPasture()`:
   - Still iterate tethered Pokémon every tick (cheap)
   - If Pokémon has active job → `tickActiveJob()` (direct, no queue)
   - If Pokémon is idle and not queued → `WorkQueue.enqueue()`
4. Call `WorkQueue.processBudgeted()` once per server tick from platform hook
5. Wire `BlockChangeNotifier` → `WorkQueue.wakeNearby()`
6. Build + test

### Critical Design Choice: Budget Size
- Too low (1-2): Pokémon wait in queue while others get served first. Feels "slower" for large pastures.
- Too high (20+): CPU spike when all Pokémon become idle at once.
- **Default 5, configurable.** 5 Pokémon assigned per tick = 100 Pokémon from idle to working in 1 second.

### Tick Model After Phase 4
```
Every server tick:
  1. WorkQueue.processBudgeted(5)         — assign up to 5 idle → job
  2. For each pasture:
     a. DeferredBlockScanner.tick()       — safety-net rescan (long cooldown)
     b. For each tethered Pokémon:
        - If has active job → job.tick()  — direct, no queue
        - If idle, not queued → enqueue   — cheap check
  3. WorkerDispatcher.tickMaintenance()   — sweep expired (every 200 ticks)
```

---

## Phase 5: Event-Driven State Transitions (Full Reactive)
**Files changed: 5 | New files: 1 | Risk: High**
**Impact: Architecture cleanup, foundation for future features**

### What
Replace the phase-checking `when (state.phase)` dispatch in `BaseJob.tick()` with explicit event-driven transitions. When work completes, an event fires; the event handler transitions to DEPOSITING immediately rather than waiting for the next tick evaluation.

### New File
- `events/WorkerEvent.kt` — Sealed class hierarchy of internal events

### Design
```kotlin
sealed class WorkerEvent {
    data class BecameIdle(val pokemonId: UUID, val context: JobContext) : WorkerEvent()
    data class ArrivedAtTarget(val pokemonId: UUID) : WorkerEvent()
    data class WorkCompleted(val pokemonId: UUID, val result: WorkResult) : WorkerEvent()
    data class TargetInvalidated(val pos: BlockPos) : WorkerEvent()
    data class PokemonRecalled(val pokemonId: UUID) : WorkerEvent()
    data class BlockChanged(val pos: BlockPos, val category: BlockCategory?) : WorkerEvent()
}

object WorkerEventBus {
    private val handlers = mutableMapOf<KClass<out WorkerEvent>, MutableList<(WorkerEvent) -> Unit>>()
    
    fun <T : WorkerEvent> on(type: KClass<T>, handler: (T) -> Unit)
    fun emit(event: WorkerEvent)
}
```

### Changes

| File | What Changes |
|------|-------------|
| `events/WorkerEvent.kt` | **NEW** — Event sealed class + bus |
| `jobs/BaseJob.kt` | State transitions emit events instead of directly changing `state.phase`. `tick()` still dispatches by phase, but transitions are event-driven (same tick). |
| `jobs/WorkerDispatcher.kt` | Subscribe to `BecameIdle` → enqueue in WorkQueue. Subscribe to `PokemonRecalled` → cleanup. |
| `listeners/BlockChangeNotifier.kt` | Emit `BlockChanged` event instead of directly calling grid + queue |
| `jobs/PastureWorkerManager.kt` | Emit `PokemonRecalled` when detecting recalled Pokémon |
| `state/ClaimManager.kt` | When target invalidated by block change → emit `TargetInvalidated` |

### Implementation Steps
1. Create `WorkerEvent` sealed class and `WorkerEventBus`
2. Wire `BaseJob` state transitions to emit events
3. Move `PastureWorkerManager` recall detection to emit `PokemonRecalled`
4. Wire `BlockChangeNotifier` to emit `BlockChanged`
5. Register handlers in `WorkerDispatcher.init()`
6. Build + test

### Why Last
This phase is mostly architectural cleanup. Phases 1-4 deliver the user-visible responsiveness improvements. Phase 5 makes the codebase cleaner and more extensible for future features (e.g., Pokémon leveling, XP, inter-Pokémon cooperation).

---

## Phase 6: TickBudget System (Performance Guarantee)
**Files changed: 2 | New files: 1 | Risk: Low**
**Impact: Prevents lag spikes with many Pokémon/pastures**

### What
Cap the total work done per server tick. If budget is exhausted, remaining work defers to next tick. Ensures consistent TPS even with 100+ working Pokémon.

### New File
- `utilities/TickBudget.kt`

### Design
```kotlin
object TickBudget {
    private var remaining = 0
    val maxPerTick get() = CobbleCrewConfigHolder.config.general.maxOperationsPerTick
    
    fun beginTick() { remaining = maxPerTick }
    fun consume(cost: Int = 1): Boolean {
        if (remaining < cost) return false
        remaining -= cost
        return true
    }
    fun remaining(): Int = remaining
}
```

### Changes

| File | What Changes |
|------|-------------|
| `utilities/TickBudget.kt` | **NEW** |
| `jobs/WorkQueue.kt` | Use `TickBudget.consume()` before processing each idle Pokémon |
| `jobs/PastureWorkerManager.kt` | Use `TickBudget.consume()` before ticking each working Pokémon |
| `config/CobbleCrewConfig.kt` | Add `maxOperationsPerTick: Int = 30` to general config |

### Implementation Steps
1. Create `TickBudget` singleton
2. Add config field
3. Wire into WorkQueue and PastureWorkerManager tick loops
4. Build + test

---

## Execution Order & Dependencies

```
Phase 1: ArrivalStyle ──────────────────────── (standalone, no deps)
    │
Phase 2: SpatialGrid ──────────────────────── (standalone, no deps)
    │
Phase 3: BlockChangeNotifier ──────────────── (depends on Phase 2 for grid updates)
    │
Phase 4: WorkQueue ─────────────────────────── (depends on Phase 3 for wake notifications)
    │
Phase 5: Event Bus ─────────────────────────── (depends on Phase 4 for queue integration)
    │
Phase 6: TickBudget ────────────────────────── (depends on Phase 4 for queue budgeting)
```

**Phases 1 and 2 can be done in parallel** — they touch different files.
**Phase 3 depends on Phase 2** (needs grid to update).
**Phase 4 depends on Phase 3** (needs notifier to wake idle).
**Phases 5 and 6 depend on Phase 4** but are independent of each other.

---

## File Impact Summary

### New Files (8)
| File | Phase | Lines (est.) |
|------|-------|-------------|
| `enums/ArrivalStyle.kt` | 1 | 15 |
| `cache/SpatialGrid.kt` | 2 | 120 |
| `listeners/BlockChangeNotifier.kt` | 3 | 80 |
| `mixin/ServerWorldMixin.java` | 3 | 30 |
| `jobs/WorkQueue.kt` | 4 | 100 |
| `events/WorkerEvent.kt` | 5 | 60 |
| `utilities/TickBudget.kt` | 6 | 25 |
| | | **~430 total** |

### Modified Files (12)
| File | Phases | Scope of Change |
|------|--------|----------------|
| `jobs/BaseJob.kt` (295 lines) | 1, 2 | Add `arrivalStyle` property, change `findCachedBlockTarget()` to use grid |
| `utilities/WorkerVisualUtils.kt` (118 lines) | 1 | `handleArrival()` accepts delay parameter |
| `jobs/dsl/ProductionJob.kt` | 1 | Add `arrivalStyle = INSTANT` |
| `jobs/dsl/ProcessingJob.kt` | 1 | Add `arrivalStyle = QUICK` |
| `jobs/dsl/EnvironmentalJob.kt` | 1 | Add `arrivalStyle = INSTANT` |
| `jobs/dsl/SupportJob.kt` | 1 | Add `arrivalStyle = INSTANT` |
| `jobs/dsl/PlacementJob.kt` | 1 | Add `arrivalStyle = QUICK` |
| `cache/CobbleCrewCacheManager.kt` (180 lines) | 2 | Delegate to SpatialGrid |
| `utilities/DeferredBlockScanner.kt` (173 lines) | 2, 3 | Wire grid population, increase cooldown |
| `jobs/PastureWorkerManager.kt` (101 lines) | 3, 4 | Register pasture, rewrite tick loop |
| `jobs/WorkerDispatcher.kt` (211 lines) | 4, 5 | Split tickPokemon(), subscribe to events |
| `config/CobbleCrewConfig.kt` (~340 lines) | 6 | Add maxOperationsPerTick |

### Untouched Files
- **All 129 job definitions** (registry/*.kt) — zero changes
- **All DSL builders** (except arrivalStyle additions) — minimal changes
- **StateManager.kt, PokemonWorkerState.kt** — no structural changes
- **ClaimManager.kt** — no changes until Phase 5
- **Worker interface** — no changes
- **Platform code** (fabric/, neoforge/) — no changes
- **Mixins** (except new ServerWorldMixin) — no changes

---

## Risk Assessment

| Phase | Risk | Mitigation |
|-------|------|-----------|
| 1 (ArrivalStyle) | **Low** | Default is current behavior. Opt-in per job. |
| 2 (SpatialGrid) | **Medium** | Keep `getTargets()` backward compat. Grid is additive, not replacing flat sets. |
| 3 (BlockChangeNotifier) | **Medium-High** | `setBlockState()` is hot path. Bail fast if no pastures in range. Keep deferred scanner as safety net. |
| 4 (WorkQueue) | **High** | Biggest behavioral change. Must handle edge cases: recalled while in queue, entity despawned, etc. |
| 5 (EventBus) | **Medium** | Mostly wiring changes. Test state transitions carefully. |
| 6 (TickBudget) | **Low** | Pure addition, configurable, default high enough to not throttle. |

---

## Testing Strategy

Each phase gets its own version bump and deploy cycle:

| Phase | Version | Focus |
|-------|---------|-------|
| 1 | +0.1.0 | Verify production/support/env jobs work instantly, gathering/defense still have animation |
| 2 | +0.2.0 | Verify target finding still works, measure CPU improvement with `/cobblecrew debug` |
| 3 | +0.3.0 | Break a block near pasture → verify workers react within 1-2 seconds |
| 4 | +0.4.0 | Place 6 Pokémon in pasture → all should start working within 1 second |
| 5 | +0.5.0 | Recall/place/break block mid-work → verify clean state transitions |
| 6 | +0.6.0 | Spawn 50+ Pokémon → verify TPS stays above 18 |

---

## Git Branch Strategy

```
main (current stable)
  └── feat/reactive-architecture
        ├── feat/arrival-style          (Phase 1)
        ├── feat/spatial-grid           (Phase 2)
        ├── feat/block-change-notifier  (Phase 3)
        ├── feat/work-queue             (Phase 4)
        ├── feat/event-bus              (Phase 5)
        └── feat/tick-budget            (Phase 6)
```

Each phase merges into `feat/reactive-architecture`, which merges into `main` when all phases are stable.

---

## Timeline Estimate

| Phase | Complexity | Dependencies |
|-------|-----------|-------------|
| Phase 1 | Small (4-5 files, ~50 lines new) | None |
| Phase 2 | Medium (1 new class, 3 files changed) | None |
| Phase 3 | Medium (1 new class, 1 mixin, 3 files changed) | Phase 2 |
| Phase 4 | Large (1 new class, 3 files rewritten) | Phase 3 |
| Phase 5 | Medium (1 new class, 5 files wired) | Phase 4 |
| Phase 6 | Small (1 new class, 3 files wired) | Phase 4 |
