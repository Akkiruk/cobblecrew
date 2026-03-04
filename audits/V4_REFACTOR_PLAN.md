# CobbleCrew v4.0 — Full Architecture Refactor Plan

**Status:** Ready to implement  
**Scope:** 8,541 lines across 63 common Kotlin files + 4 mixins + 7 platform files  
**Goal:** One lifecycle, one state store, one deposit path — then let the DSL handle 129 job definitions as pure data  

---

## Execution Order & Dependencies

```
Phase 0: Branch + safety snapshot
    │
Phase 1: PokemonWorkerState ──────────────────┐
    │                                          │
Phase 2: DepositHelper extraction              │
    │                                          │
Phase 3: Unified BaseJob state machine ◄───────┘
    │
Phase 4: DSL → BaseJob migration
    │
Phase 5: Claims into PokemonWorkerState
    │
Phase 6: Per-world spatial cache
    │
Phase 7: Event-driven pasture hook
    │
Phase 8: Config auto-generation from DSL
    │
Phase 9: Entity fixes + polish
    │
Phase 10: Build, deploy, tag v4.0.0
```

---

## Phase 0 — Branch + Snapshot

**What:** Create a `refactor/v4` branch from current `main`. Tag current HEAD as `v3-final`.

**Commands:**
```bash
git tag v3-final
git checkout -b refactor/v4
```

**Risk:** Zero — no code changes.

---

## Phase 1 — Centralized PokemonWorkerState

### Problem
77 `mutableMapOf<UUID, ...>` scattered across 18+ singletons. Cleanup must touch every map manually. Miss one → memory leak. Add a new map → forget cleanup → bug.

### Solution
One `PokemonWorkerState` class per active Pokémon, stored in a single `ConcurrentHashMap<UUID, PokemonWorkerState>` managed by a new `StateManager` singleton.

### New Files

**`state/PokemonWorkerState.kt`**
```kotlin
data class PokemonWorkerState(
    val pokemonId: UUID,

    // --- Job assignment ---
    var activeJob: Worker? = null,
    var profile: PokemonProfile? = null,
    var jobAssignedTick: Long = 0L,
    var lastMoveSet: Set<String> = emptySet(),

    // --- Idle behavior ---
    var idleSinceTick: Long = 0L,
    var lastWanderTick: Long = 0L,
    var lastPickupAttemptTick: Long = 0L,
    var idlePickupClaimTick: Long = 0L,
    var returningHome: Boolean = false,
    var lastReturnNavTick: Long = 0L,

    // --- Items ---
    val heldItems: MutableList<ItemStack> = mutableListOf(),
    val failedDeposits: MutableSet<BlockPos> = mutableSetOf(),
    var heldSinceTick: Long = 0L,
    var depositRetryTick: Long = 0L,
    var depositArrivalTick: Long? = null,

    // --- Navigation / Claims ---
    var claim: NavigationClaim? = null,
    var lastPathfindTick: Long = 0L,

    // --- Visual ---
    var arrivalTick: Long? = null,
    var graceTick: Long? = null,
    var lastAnimationTick: Long = 0L,

    // --- Job-specific scratch ---
    // Typed containers for per-job-category state
    var phase: JobPhase = JobPhase.IDLE,
    var targetPos: BlockPos? = null,
    var secondaryTargetPos: BlockPos? = null,
    var lastActionTime: Long = 0L,
    var cooldownUntil: Long = 0L,
    var cachedHostileIds: List<UUID> = emptyList(),
    var lastHostileScan: Long = 0L,
)
```

**`state/StateManager.kt`**
```kotlin
object StateManager {
    private val states = ConcurrentHashMap<UUID, PokemonWorkerState>()

    fun getOrCreate(pokemonId: UUID): PokemonWorkerState =
        states.getOrPut(pokemonId) { PokemonWorkerState(pokemonId) }

    fun get(pokemonId: UUID): PokemonWorkerState? = states[pokemonId]

    fun cleanup(pokemonId: UUID) { states.remove(pokemonId) }

    fun cleanupAll() { states.clear() }

    fun all(): Collection<PokemonWorkerState> = states.values

    fun count(): Int = states.size
}
```

### Migration Strategy
- Create the new files first.
- DO NOT remove old maps yet — Phase 3 (BaseJob) will do that.
- WorkerDispatcher starts writing to StateManager in addition to its existing maps.
- Once all consumers are migrated (Phases 2-5), the old maps get deleted.

### Files Touched
| File | Change |
|------|--------|
| **NEW** `state/PokemonWorkerState.kt` | New centralized state |
| **NEW** `state/StateManager.kt` | New state manager |
| `jobs/WorkerDispatcher.kt` | Dual-write: populate StateManager alongside old maps |
| `jobs/PartyWorkerManager.kt` | Cleanup calls `StateManager.cleanup()` |

### Lines Added/Removed
- +120 new, ~0 removed (dual-write phase)

---

## Phase 2 — DepositHelper Extraction

### Problem
The same deposit-or-deliver-to-player block is copy-pasted 11 times. Only BaseHarvester has overflow protection (5-minute drop timer). The other 4 base classes hold items forever.

### Solution
Extract one `DepositHelper` object with:
- `deliverOrDeposit(state, context, pokemonEntity)` — single entry point  
- Overflow protection (configurable timeout, default 5 min)
- Retry-with-cooldown (already in InventoryUtils for harvester, now universal)

### New File

**`utilities/DepositHelper.kt`**
```kotlin
object DepositHelper {
    private const val OVERFLOW_TIMEOUT_TICKS = 6000L // 5 minutes

    /**
     * Universal deposit/deliver handler. Reads items from state.heldItems,
     * writes to state.failedDeposits. Handles party delivery, container
     * deposit with animation, retry cooldown, and overflow drop.
     */
    fun tick(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity) {
        if (state.heldItems.isEmpty()) return

        // Party → deliver directly to player
        if (context is JobContext.Party) {
            CobbleCrewInventoryUtils.deliverToPlayer(context.player, state.heldItems, pokemonEntity)
            state.heldItems.clear()
            state.failedDeposits.clear()
            state.depositRetryTick = 0L
            return
        }

        // Overflow protection
        if (state.heldSinceTick > 0 && context.world.time - state.heldSinceTick > OVERFLOW_TIMEOUT_TICKS) {
            dropItems(context.world, pokemonEntity, state.heldItems)
            state.heldItems.clear()
            state.failedDeposits.clear()
            state.heldSinceTick = 0L
            return
        }

        // Delegate to existing handleDepositing logic (refactored to use state)
        CobbleCrewInventoryUtils.handleDepositing(
            context.world, context.origin, pokemonEntity,
            state.heldItems, state.failedDeposits, state
        )
    }
}
```

### Migration Strategy
1. Create `DepositHelper.kt`
2. Refactor `CobbleCrewInventoryUtils.handleDepositing` to accept `PokemonWorkerState` instead of raw maps
3. Each base class's deposit block becomes a one-liner: `DepositHelper.tick(state, context, entity)`
4. The 4 non-harvester base classes gain overflow protection for free

### Files Touched
| File | Change |
|------|--------|
| **NEW** `utilities/DepositHelper.kt` | Unified deposit logic |
| `utilities/CobbleCrewInventoryUtils.kt` | Overload `handleDepositing` to accept state |
| `jobs/BaseHarvester.kt` | Replace inline deposit with `DepositHelper.tick()` |
| `jobs/BaseProducer.kt` | Replace inline deposit with `DepositHelper.tick()` |
| `jobs/BaseProcessor.kt` | Replace inline deposit with `DepositHelper.tick()` |
| `jobs/WorkerDispatcher.kt` | Replace idle pickup deposit with `DepositHelper.tick()` |

### Lines Added/Removed
- +60 new (DepositHelper), -110 removed (11 copies), net: **-50 lines**

---

## Phase 3 — Unified BaseJob Lifecycle

### Problem
6 abstract base classes (BaseHarvester, BaseProducer, BaseProcessor, BasePlacer, BaseDefender, BaseSupport) each implement their own tick state machine, navigation, cleanup, and arrival animations independently. Total: ~800 lines of duplicated lifecycle logic.

### Solution
One `BaseJob` abstract class with a single state machine:

```
IDLE → FINDING_TARGET → NAVIGATING → ARRIVING → WORKING → DEPOSITING → IDLE
                                                    ↓
                                              (non-item jobs skip to IDLE)
```

### New File

**`jobs/BaseJob.kt`** (~180 lines — replaces ~800 across 6 files)
```kotlin
abstract class BaseJob : Worker {
    // --- Job identity (set by DSL / subclass) ---
    abstract override val name: String
    abstract val arrivalParticle: ParticleEffect
    open val arrivalTolerance: Double = 3.0
    open val workPhase: WorkPhase = WorkPhase.HARVESTING
    open val producesItems: Boolean = true  // false for defense, support, environmental

    // --- Lifecycle hooks (the ONLY things subclasses override) ---

    /** Find the next target. Return null = no work available. */
    abstract fun findTarget(state: PokemonWorkerState, context: JobContext): Target?

    /** Do the actual work. Return produced items (empty for non-item jobs). */
    abstract fun doWork(
        state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity
    ): List<ItemStack>

    /** Optional: validate target still exists each tick. Default: block not air. */
    open fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean { ... }

    /** Optional: cooldown between work cycles. Default: 0. */
    open fun getCooldownTicks(state: PokemonWorkerState): Long = 0L

    /** Optional: post-work hook (e.g. replant, apply effect). */
    open fun afterWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity) {}

    // --- The universal tick (never override this) ---
    final override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
        val state = StateManager.getOrCreate(pokemonEntity.pokemon.uuid)

        when (state.phase) {
            IDLE -> {
                if (cooldownActive(state, context)) return
                val target = findTarget(state, context) ?: return
                state.targetPos = target.pos
                claimAndNavigate(state, pokemonEntity, target)
                state.phase = NAVIGATING
            }
            NAVIGATING -> {
                if (!validateTarget(state, context)) { release(state); return }
                if (atTarget(pokemonEntity, state)) state.phase = ARRIVING
                else reNavigate(state, pokemonEntity)
            }
            ARRIVING -> {
                if (WorkerVisualUtils.handleArrival(...)) state.phase = WORKING
            }
            WORKING -> {
                val items = doWork(state, context, pokemonEntity)
                afterWork(state, context, pokemonEntity)
                releaseTarget(state)
                if (items.isNotEmpty() && producesItems) {
                    state.heldItems.addAll(items)
                    state.heldSinceTick = context.world.time
                    state.phase = DEPOSITING
                } else {
                    state.phase = IDLE
                    state.cooldownUntil = context.world.time + getCooldownTicks(state)
                }
            }
            DEPOSITING -> {
                DepositHelper.tick(state, context, pokemonEntity)
                if (state.heldItems.isEmpty()) {
                    state.failedDeposits.clear()
                    state.phase = IDLE
                }
            }
        }
    }

    // cleanup is now trivial — StateManager handles it
    final override fun cleanup(pokemonId: UUID) {} // no-op, StateManager.cleanup() does it
    final override fun hasActiveState(pokemonId: UUID) =
        StateManager.get(pokemonId)?.phase != JobPhase.IDLE
    final override fun getHeldItems(pokemonId: UUID) =
        StateManager.get(pokemonId)?.heldItems?.toList()
}
```

### Target sealed interface (replaces BlockPos/Entity/Player params)
```kotlin
sealed interface Target {
    data class Block(val pos: BlockPos) : Target
    data class Player(val playerId: UUID, val pos: BlockPos) : Target
    data class Mob(val entityId: UUID, val pos: BlockPos) : Target
    val pos: BlockPos
}
```

### Files Created
| File | Purpose |
|------|---------|
| **NEW** `jobs/BaseJob.kt` | Universal state machine (~180 lines) |
| **NEW** `jobs/Target.kt` | Sealed target interface |
| **NEW** `enums/JobPhase.kt` | `IDLE, NAVIGATING, ARRIVING, WORKING, DEPOSITING` |

### Files Deleted (after migration complete)
| File | Lines Removed |
|------|---------------|
| `jobs/BaseHarvester.kt` | 236 |
| `jobs/BaseProducer.kt` | 88 |
| `jobs/BaseProcessor.kt` | 122 |
| `jobs/BasePlacer.kt` | 122 |
| `jobs/BaseDefender.kt` | 95 |
| `jobs/BaseSupport.kt` | 134 |
| **Total removed** | **797 lines** |

### Files Modified
| File | Change |
|------|--------|
| `interfaces/Worker.kt` | Simplify: remove `hasActiveState`, `cleanup`, `getHeldItems` (now in BaseJob) |
| `jobs/WorkerDispatcher.kt` | Strip all per-Pokémon state maps, delegate to StateManager |

### Net Impact
- +180 (BaseJob + Target + JobPhase), -797 (6 base classes), -~100 (dispatcher state) = **-717 lines**

---

## Phase 4 — DSL → BaseJob Migration

### Problem
DSL classes extend old base classes (GatheringJob extends BaseHarvester, etc.). Registry jobs like the 4 manual `ProductionJobs` objects bypass base classes entirely. Inconsistent behavior.

### Solution
DSL classes extend `BaseJob` instead, implementing only `findTarget()` and `doWork()`. Manual registry jobs get rewritten as DSL calls.

### DSL Rewrite: GatheringJob Example
```kotlin
open class GatheringJob(
    override val name: String,
    override val targetCategory: BlockCategory,
    val qualifyingMoves: Set<String> = emptySet(),
    val fallbackSpecies: List<String> = emptyList(),
    val particle: ParticleEffect = ParticleTypes.CAMPFIRE_COSY_SMOKE,
    override val priority: WorkerPriority = WorkerPriority.TYPE,
    override val importance: JobImportance = JobImportance.HIGH,
    val harvestOverride: ((World, BlockPos, PokemonEntity) -> List<ItemStack>)? = null,
    val toolOverride: ItemStack = ItemStack.EMPTY,
    val readyCheck: ((World, BlockPos) -> Boolean)? = null,
    val afterHarvestAction: ((World, BlockPos, BlockState) -> Unit)? = null,
    val tolerance: Double = 3.0,
    val isCombo: Boolean = false,
) : BaseJob() {

    override val arrivalParticle = particle
    override val arrivalTolerance = tolerance
    override val workPhase = WorkPhase.HARVESTING

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
        // Find closest valid, unclaimed, pathfind-reachable block from cache
        return findCachedBlockTarget(state, context, targetCategory) { world, pos ->
            readyCheck?.invoke(world, pos) ?: true
        }
    }

    override fun doWork(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity): List<ItemStack> {
        val pos = state.targetPos ?: return emptyList()
        if (harvestOverride != null) return harvestOverride.invoke(context.world, pos, entity)
        return harvestWithLoot(context.world, pos, entity, toolOverride, afterHarvestAction)
    }

    override fun isEligible(...) = dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)
    override fun matchPriority(...) = dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)
}
```

### Per-DSL Class Changes

| DSL Class | Old Parent | New Parent | Key Hook Changes |
|-----------|-----------|------------|-----------------|
| `GatheringJob` | `BaseHarvester` | `BaseJob` | `findTarget` from cache, `doWork` → harvestWithLoot |
| `ProductionJob` | `BaseProducer` | `BaseJob` | `findTarget` returns null (no target needed), `doWork` → `output()` lambda |
| `ProcessingJob` | `BaseProcessor` | `BaseJob` | `findTarget` → find barrel, `doWork` → extract+transform |
| `PlacementJob` | `BasePlacer` | `BaseJob` | `findTarget` → delegate to findTarget lambda, `doWork` → place block |
| `DefenseJob` | `BaseDefender` | `BaseJob` | `findTarget` → closest hostile, `doWork` → `effectFn()`, `producesItems=false` |
| `SupportJob` | `BaseSupport` | `BaseJob` | `findTarget` → nearby player, `doWork` → apply effect, `producesItems=false` |
| `EnvironmentalJob` | `Worker` directly | `BaseJob` | `findTarget` → lambda, `doWork` → action lambda, `producesItems=false` |

### Registry Job Migration
All manual `object : Worker { ... }` in ProductionJobs.kt, LogisticsJobs.kt, SupportJobs.kt become `ProductionJob(...)` or `GatheringJob(...)` DSL instances.

| Registry File | Current Objects | After |
|--------------|-----------------|-------|
| `ProductionJobs.kt` (346 lines) | 4 manual objects + DSL | All DSL (~120 lines) |
| `LogisticsJobs.kt` (180 lines) | 2 manual objects | DSL or GatheringJob (~80 lines) |
| `SupportJobs.kt` (282 lines) | 2 manual objects + DSL | All DSL (~130 lines) |

### Files Touched
| File | Change |
|------|--------|
| `jobs/dsl/GatheringJob.kt` | Rewrite to extend BaseJob |
| `jobs/dsl/ProductionJob.kt` | Rewrite to extend BaseJob |
| `jobs/dsl/ProcessingJob.kt` | Rewrite to extend BaseJob |
| `jobs/dsl/PlacementJob.kt` | Rewrite to extend BaseJob |
| `jobs/dsl/DefenseJob.kt` | Rewrite to extend BaseJob |
| `jobs/dsl/SupportJob.kt` | Rewrite to extend BaseJob |
| `jobs/dsl/EnvironmentalJob.kt` | Rewrite to extend BaseJob |
| `jobs/registry/ProductionJobs.kt` | Convert manual objects to DSL |
| `jobs/registry/LogisticsJobs.kt` | Convert manual objects to DSL |
| `jobs/registry/SupportJobs.kt` | Convert manual objects to DSL |

### Net Impact
- ~-330 lines (manual objects collapsed to DSL calls)
- DSL files get ~5-10 lines shorter each (no more `heldItemsByPokemon`, `failedDepositLocations`, `cleanup()`)

---

## Phase 5 — Claims Into PokemonWorkerState

### Problem
`CobbleCrewNavigationUtils` (295 lines) maintains its own bidirectional maps (`pokemonToClaim`, `targetToPokemon`), plus escalating blacklists, unreachable cache, and pathfind throttles — all in parallel with the state already tracked by the dispatcher and base classes.

### Solution
Claims live in `PokemonWorkerState.claim`. The bidirectional reverse map (`targetToPokemon`) stays as a lightweight index in `ClaimManager`, but all per-Pokémon data moves into the state object. Pathfind throttle uses `state.lastPathfindTick`. Unreachable positions use `state.failedDeposits` pattern.

### Refactored Structure

**`state/ClaimManager.kt`** (~120 lines, replaces 295-line NavigationUtils)
```kotlin
object ClaimManager {
    // Reverse index: target → pokemonId (for O(1) "is this targeted?" checks)
    private val targetIndex = ConcurrentHashMap<Target, UUID>()

    // Escalating blacklist (shared, not per-Pokémon)
    private val blockFailCounts = ConcurrentHashMap<BlockPos, Int>()
    private val blacklist = ConcurrentHashMap<BlockPos, Long>() // pos → expiry tick

    fun claim(state: PokemonWorkerState, target: Target, world: World) { ... }
    fun release(state: PokemonWorkerState, world: World, blacklistTarget: Boolean = true) { ... }
    fun isTargetedByOther(target: Target, pokemonId: UUID): Boolean { ... }
    fun isBlacklisted(pos: BlockPos, currentTick: Long): Boolean { ... }
    fun navigateTo(entity: PokemonEntity, pos: BlockPos, state: PokemonWorkerState): Boolean { ... }
    fun sweepExpired(currentTick: Long) { ... } // periodic cleanup
}
```

### Files Touched
| File | Change |
|------|--------|
| **NEW** `state/ClaimManager.kt` | Simplified claim + blacklist |
| **DELETE** `utilities/CobbleCrewNavigationUtils.kt` | Replaced (-295 lines) |
| `jobs/BaseJob.kt` | Use ClaimManager instead of NavigationUtils |
| `jobs/WorkerDispatcher.kt` | Use ClaimManager for idle behavior |

### Net Impact
- +120 (ClaimManager), -295 (NavigationUtils) = **-175 lines**

---

## Phase 6 — Per-World Spatial Cache

### Problem
Block targets are cached per-pasture (`CacheKey = PastureKey`). Overlapping pastures trigger global sweeps on every harvest. Party workers use a completely separate scanning path with its own cache keys.

### Solution
One `WorldBlockCache` per dimension. Any worker — pasture or party — queries the same spatial index. Deferred scanning and eager scanning both feed into it.

### New File

**`cache/WorldBlockCache.kt`** (~150 lines)
```kotlin
object WorldBlockCache {
    // dimension → (category → sorted set of BlockPos)
    private val caches = ConcurrentHashMap<RegistryKey<World>, CategoryCache>()

    class CategoryCache {
        private val byCategory = ConcurrentHashMap<BlockCategory, MutableSet<BlockPos>>()

        fun add(category: BlockCategory, pos: BlockPos) { ... }
        fun remove(category: BlockCategory, pos: BlockPos) { ... }
        fun getInRadius(category: BlockCategory, center: BlockPos, radius: Int): List<BlockPos> { ... }
        fun replaceRegion(center: BlockPos, radius: Int, height: Int, data: Map<BlockCategory, Set<BlockPos>>) { ... }
    }

    fun get(world: World): CategoryCache = caches.getOrPut(world.registryKey) { CategoryCache() }
    fun remove(pos: BlockPos, category: BlockCategory, world: World) { get(world).remove(category, pos) }
    fun clearAll() { caches.clear() }
}
```

### Files Touched
| File | Change |
|------|--------|
| **NEW** `cache/WorldBlockCache.kt` | Per-world spatial cache |
| **DELETE** `cache/CobbleCrewCacheManager.kt` | Replaced |
| **DELETE** `cache/CacheKey.kt` | No longer needed |
| **DELETE** `cache/PastureCache.kt` | No longer needed |
| `utilities/DeferredBlockScanner.kt` | Write to WorldBlockCache instead |
| `jobs/PartyWorkerManager.kt` | Eager scan writes to WorldBlockCache |
| `jobs/BaseJob.kt` | Query WorldBlockCache for targets |
| `utilities/CobbleCrewInventoryUtils.kt` | Query WorldBlockCache for containers |

### Net Impact
- +150 (WorldBlockCache), -125 (CacheManager) -12 (CacheKey) -14 (PastureCache) = **~0 net** but much cleaner

---

## Phase 7 — Event-Driven Pasture Hook

### Problem
The core pasture hook is a mixin injecting at `@At("TAIL"), method = "TICKER$lambda$0"` — targeting a lambda inside Cobblemon's pasture block entity. Any Cobblemon refactor that renames the lambda silently breaks the mod.

### Solution
Replace the mixin with a `ServerTickEvent` listener that iterates all loaded pasture block entities, same as the party system already works. This unifies the two tick paths.

### New Architecture
```
ServerTickEvent → PastureWorkerManager.tick(server) 
    → for each loaded world:
        → find all loaded PokemonPastureBlockEntity (chunk scan)
        → for each tethered Pokémon: WorkerDispatcher.tickPokemon()
    
ServerTickEvent → PartyWorkerManager.tick(server)
    → for each player with sent-out Pokémon:
        → WorkerDispatcher.tickPokemon()
```

### Implementation

The mixin currently does 3 things we need to preserve:
1. **Tick area scan** (deferred block scanner)
2. **Detect recalled Pokémon** (diff tethered UUIDs)  
3. **Tick each tethered Pokémon** (dispatch to WorkerDispatcher)

All three move into a new **`PastureWorkerManager`** object driven by the server tick event.

**`jobs/PastureWorkerManager.kt`** (~100 lines)
```kotlin
object PastureWorkerManager {
    // pasturePos → last known tethered UUIDs (for recall detection)
    private val pastureState = ConcurrentHashMap<BlockPos, MutableSet<UUID>>()

    fun tick(server: MinecraftServer) {
        for (world in server.worlds) {
            // Get all loaded chunks and find pasture block entities
            for (chunk in world.chunkManager.loadedChunks) {
                for ((pos, be) in chunk.blockEntities) {
                    if (be !is PokemonPastureBlockEntity) continue
                    tickPasture(be, world, pos)
                }
            }
        }
    }

    private fun tickPasture(pasture: PokemonPastureBlockEntity, world: World, pos: BlockPos) {
        // Stagger: only tick every 5 ticks (same as mixin)
        if ((world.time + (pos.hashCode() and 0x7FFFFFFF)) % 5 != 0L) return

        val context = JobContext.Pasture(pos, world)
        DeferredBlockScanner.tickAreaScan(context)
        CobbleCrewInventoryUtils.tickAnimations(world)

        val tethered = pasture.tetheredPokemon
        // Detect recalls...
        // Tick each Pokémon...
    }
}
```

### Mixin Changes
- **DELETE** `PokemonPastureBlockEntityMixin.java` (the big 120-line one)
- **KEEP** `FarmlandBlockMixin.java` (farmland trample prevention — still needed)
- **KEEP** `AbstractFurnaceBlockEntityAccessor.java` (furnace fueling)
- **KEEP** `BrewingStandBlockEntityAccessor.java` (brewing stand fueling)

### Platform Entrypoint Changes
```kotlin
// Fabric
ServerTickEvents.END_SERVER_TICK { server ->
    PastureWorkerManager.tick(server)  // NEW
    PartyWorkerManager.tick(server)    // existing
}

// NeoForge
@SubscribeEvent fun onTick(event: ServerTickEvent.Post) {
    PastureWorkerManager.tick(event.server)  // NEW
    PartyWorkerManager.tick(event.server)    // existing
}
```

### Risk Assessment
**Medium risk.** The mixin approach hooks directly into the pasture's own ticker, which guarantees timing even with server lag. The event-driven approach iterates chunks, which may miss unloaded chunks. However:
- Pasture blocks only work when their chunk is loaded (entities despawn otherwise)
- The party system already uses this exact pattern and works fine
- We gain resilience against Cobblemon API changes

### Fallback
If chunk iteration proves too expensive, we can track pasture positions on placement/load and iterate a known set instead of scanning chunks every tick.

### Files Touched
| File | Change |
|------|--------|
| **NEW** `jobs/PastureWorkerManager.kt` | Event-driven pasture ticker |
| **DELETE** `mixin/PokemonPastureBlockEntityMixin.java` | -120 lines |
| `cobblecrew.mixins.json` | Remove PokemonPastureBlockEntityMixin entry |
| `fabric/CobbleCrewFabric.kt` | Add PastureWorkerManager.tick to server tick |
| `neoforge/CobbleCrewNeoForge.kt` | Add PastureWorkerManager.tick to server tick |

### Net Impact
- +100 (PastureWorkerManager), -120 (mixin) = **-20 lines**

---

## Phase 8 — Config Auto-Generation From DSL

### Problem
Every DSL job manually calls `JobConfigManager.registerDefault(category, name, JobConfig(...))` in its `init {}` block. Adding a job requires touching both the job definition AND remembering to register config. The `JobConfig` data class has 18 nullable fields, most of which only apply to specific job types.

### Solution
The DSL builder declares its config needs via a `ConfigSpec` that auto-registers. The `JobConfig` data class stays flat (it's serialized to JSON), but the registration is automatic.

### Implementation
Move config registration into `BaseJob.init {}`:

```kotlin
abstract class BaseJob : Worker {
    // Each DSL class provides its config spec
    protected abstract val configSpec: ConfigSpec

    data class ConfigSpec(
        val category: String,
        val enabled: Boolean = true,
        val cooldownSeconds: Int = 30,
        val qualifyingMoves: List<String> = emptyList(),
        val fallbackSpecies: List<String> = emptyList(),
        // ... pass-through to JobConfig
    )

    init {
        JobConfigManager.registerDefault(configSpec.category, name, configSpec.toJobConfig())
    }

    val config: JobConfig get() = JobConfigManager.get(name)
}
```

DSL classes no longer need their own `init {}` block or their own `config` property — it's inherited.

### Files Touched
| File | Change |
|------|--------|
| `jobs/BaseJob.kt` | Add ConfigSpec + auto-registration |
| `jobs/dsl/*.kt` (7 files) | Remove `init {}` blocks, add `configSpec` |
| `config/JobConfigManager.kt` | Minor: accept ConfigSpec |

### Net Impact
- -7 `init {}` blocks (-35 lines), + ConfigSpec in BaseJob (+20 lines) = **-15 lines**

---

## Phase 9 — Entity Fixes + Polish

### 9a. BaseDefender: Store Entity IDs, Not References

**Problem:** `cachedHostiles` stores raw `HostileEntity` references → dangling refs.  
**Fix:** Store `List<UUID>`, resolve via `world.getEntity(uuid)` on use.

This is now handled by `BaseJob` using `state.cachedHostileIds: List<UUID>` + `state.lastHostileScan`.

### 9b. Share Hostile Scans Per Area

**Problem:** 6 defenders in one pasture run 6 independent `getEntitiesByClass` queries.  
**Fix:** Cache hostile scan results per-area per-tick in a lightweight map.

```kotlin
object HostileScanCache {
    private data class ScanResult(val tick: Long, val hostiles: List<UUID>)
    private val cache = ConcurrentHashMap<BlockPos, ScanResult>()

    fun getHostiles(world: World, center: BlockPos, radius: Double): List<UUID> {
        val key = BlockPos(center.x shr 4, center.y shr 4, center.z shr 4) // chunk-granularity
        val cached = cache[key]
        if (cached != null && world.time - cached.tick < 20L) return cached.hostiles
        val result = world.getEntitiesByClass(HostileEntity::class.java, ...) { ... }
            .map { it.uuid }
        cache[key] = ScanResult(world.time, result)
        return result
    }
}
```

### 9c. WorkerDispatcher Slimming

After Phases 1-5, WorkerDispatcher should be dramatically slimmer:
- No per-Pokémon state maps (all in StateManager)
- Idle behavior uses StateManager
- Profile building/caching uses StateManager
- Job selection is the only real logic left

**Target:** WorkerDispatcher goes from 384 lines → ~120 lines.

### 9d. Bounded Maps With TTL

Any remaining maps in ClaimManager or HostileScanCache get periodic sweep in the server tick (every 200 ticks) to remove stale entries.

### Files Touched
| File | Change |
|------|--------|
| **NEW** `utilities/HostileScanCache.kt` | Shared hostile scanning |
| `jobs/WorkerDispatcher.kt` | Strip to ~120 lines |
| `jobs/dsl/DefenseJob.kt` | Use HostileScanCache |

### Net Impact
- +40 (HostileScanCache), -264 (dispatcher shrink) = **-224 lines**

---

## Phase 10 — Build, Deploy, Tag

1. Bump version to `4.0.0+1.7.0` in `gradle.properties`
2. Update `CHANGELOG.txt`:
   ```
   v4.0.0 — Architecture refactor
   - Centralized per-Pokémon state (eliminates memory leak potential)
   - Universal overflow protection for all item-producing jobs
   - Event-driven pasture hook (no more Lambda mixin fragility)
   - Per-world block cache (better performance with overlapping pastures)
   - Shared hostile scanning (6 defenders no longer do 6 redundant scans)
   - ~1,200 fewer lines of code with zero feature changes
   ```
3. Build both loaders
4. Run `deploy-exaroton.ps1`
5. Git commit + tag `v4.0.0`
6. Git push

---

## Total Impact Summary

| Metric | Before (v3) | After (v4) | Delta |
|--------|-------------|------------|-------|
| **Common Kotlin lines** | 8,541 | ~7,300 | **-1,241** |
| **UUID-keyed mutable maps** | 77 | 5 (StateManager + indexes) | **-72** |
| **Base classes** | 6 | 1 (BaseJob) | **-5** |
| **Deposit implementations** | 11 | 1 (DepositHelper) | **-10** |
| **Mixin injection points** | 1 fragile | 0 | **-1** |
| **Overflow-protected jobs** | Harvesters only | All item jobs | **+100%** |
| **Hostile scan queries/tick (6 defenders)** | 6 | 1 | **-83%** |

### File Disposition (Common Kotlin)

| Action | Files | Key Examples |
|--------|-------|-------------|
| **NEW** | 6 | `state/PokemonWorkerState.kt`, `state/StateManager.kt`, `state/ClaimManager.kt`, `utilities/DepositHelper.kt`, `cache/WorldBlockCache.kt`, `jobs/PastureWorkerManager.kt` |
| **DELETE** | 10 | 6 old Base* classes, `CobbleCrewNavigationUtils.kt`, `CobbleCrewCacheManager.kt`, `CacheKey.kt`, `PastureCache.kt` |
| **REWRITE** | 9 | 7 DSL classes, `WorkerDispatcher.kt`, `PartyWorkerManager.kt` |
| **MODIFY** | 12 | 9 registry files, `Worker.kt`, `InventoryUtils.kt`, `DeferredBlockScanner.kt` |
| **UNCHANGED** | ~26 | Commands, debug logger, crop utils, tags, animations, network, integration, etc. |

---

## Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| State migration misses a field | Medium | Jobs get stuck | Systematic field-by-field audit against old maps |
| BaseJob state machine doesn't cover edge case | Medium | Specific job type breaks | EnvironmentalJob `shouldContinue` pattern → add to BaseJob |
| Chunk iteration misses pastures | Low | Pasture workers stop | Fallback: maintain known-pastures set |
| Config schema version bump confuses existing installs | Low | Jobs re-enable/disable wrong | Bump to schemaVersion 3, test migration path |
| Cobblemon API change during refactor | Low | Won't compile | Pin Cobblemon version, update after refactor lands |

---

## Implementation Order (for the coding agent)

Execute phases sequentially. Each phase should compile and pass a manual smoke test before proceeding.

1. Phase 0: Git branch
2. Phase 1: Create state/PokemonWorkerState.kt, state/StateManager.kt — dual-write in dispatcher
3. Phase 2: Create utilities/DepositHelper.kt — refactor InventoryUtils
4. Phase 3: Create jobs/BaseJob.kt, jobs/Target.kt, update enums/JobPhase.kt — **this is the big one**
5. Phase 4: Rewrite all 7 DSL classes, migrate manual registry jobs
6. Phase 5: Create state/ClaimManager.kt, delete NavigationUtils
7. Phase 6: Create cache/WorldBlockCache.kt, delete old cache files
8. Phase 7: Create PastureWorkerManager, delete pasture mixin
9. Phase 8: Move config registration into BaseJob
10. Phase 9: HostileScanCache, dispatcher slimming, TTL sweeps
11. Phase 10: Version bump, build, deploy
