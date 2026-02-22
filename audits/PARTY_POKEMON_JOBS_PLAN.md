# Party Pokémon Jobs — Implementation Plan

## Goal

Allow Pokémon sent out from a player's party (right-click throw) to perform jobs, the same way pastured Pokémon currently do. The Pokémon follows the player around and works on blocks/mobs/players near the **player's current position** instead of being anchored to a static pasture block.

---

## The Core Problem: `origin: BlockPos`

Every layer of the system currently revolves around a fixed `pastureOrigin: BlockPos`:

| Layer | How it uses `origin` |
|---|---|
| **Mixin** | Gets `blockPos` from `PokemonPastureBlockEntity`, passes to `tickAreaScan()` and `tickPokemon()` |
| **WorkerDispatcher** | Passes `origin` to `tickPokemon()`, `returnToPasture()`, every `worker.tick()` and `worker.isAvailable()` |
| **Worker interface** | `tick(world, origin, entity)` and `isAvailable(world, origin, pokemonId)` — origin is a method parameter |
| **Base classes** | `BaseHarvester.findClosestTarget()` sorts by distance to `origin`; `BaseProducer.tick()` passes `origin` to deposit; `BaseDefender/BaseSupport` use `origin` for search box; `BaseProcessor/BasePlacer` use `origin` for container search |
| **CobbleCrewCacheManager** | All data is keyed by `pastureOrigin: BlockPos` — `getTargets(pastureOrigin, category)` |
| **DeferredBlockScanner** | `tickPastureAreaScan(world, pastureOrigin)` — scans blocks in a box centered on `origin`, caches results keyed by `pastureOrigin` |
| **CobbleCrewInventoryUtils** | `handleDepositing()` and `findInputContainer()` both call `CacheManager.getTargets(origin, CONTAINER)` — containers are found relative to the keyed origin |
| **CobbleCrewNavigationUtils** | `returnToPasture()` navigates back to `origin` when idle |
| **FarmlandBlockMixin** | Checks `pokemon.getTethering() != null` to prevent trampling — party Pokémon have no tethering |

**Summary:** `origin` serves three purposes:
1. **Cache key** — which DeferredBlockScanner results to use
2. **Search center** — where to look for blocks, mobs, containers, players
3. **Home base** — where to idle when no jobs are available

For party Pokémon, all three must become **the player's current position** (dynamic, not static).

---

## Architecture Plan

### New Concept: `JobContext`

Instead of passing bare `origin: BlockPos` everywhere, introduce a sealed interface that distinguishes the two sources:

```kotlin
sealed interface JobContext {
    val origin: BlockPos  // current work center (may move)
    val world: World

    /** Pasture Pokémon — origin is fixed, cache key is the block pos. */
    data class Pasture(
        override val origin: BlockPos,
        override val world: World,
    ) : JobContext

    /** Party Pokémon — origin is a pinned work zone, cache key is player UUID. */
    data class Party(
        val player: ServerPlayerEntity,
        override val world: World,
    ) : JobContext {
        /** Pinned when a scan completes. Stays fixed until zone is exhausted or player leaves area. */
        var pinnedOrigin: BlockPos? = null

        override val origin: BlockPos
            get() = pinnedOrigin ?: player.blockPos
    }
}
```

**This is the single biggest refactor.** Every method that currently takes `(world: World, origin: BlockPos, ...)` changes to `(context: JobContext, ...)`. The existing logic continues to work identically because it still reads `context.origin` — but party jobs get a moving origin.

### Phase 1: Refactor `origin` → `JobContext` (Zero Behavior Change)

**Goal:** Mechanical signature change only. No party features yet. Pasture behavior is identical.

#### 1a. Create `JobContext` sealed interface (new file)
- `common/src/main/kotlin/akkiruk/cobblecrew/jobs/JobContext.kt`

#### 1b. Change `Worker` interface
```kotlin
// Before:
fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean
fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity)

// After:
fun isAvailable(context: JobContext, pokemonId: UUID): Boolean
fun tick(context: JobContext, pokemonEntity: PokemonEntity)
```
Same for `cleanup()` (no change needed, it's UUID-only).

#### 1c. Change all 6 base classes
- `BaseHarvester` — `tick()`, `handleHarvesting()`, `findClosestTarget()`, `isAvailable()`, `harvestWithLoot()`
- `BaseProducer` — `tick()`, `handleProduction()`, `isAvailable()`
- `BaseProcessor` — `tick()`, `isAvailable()`
- `BasePlacer` — `tick()`, `isAvailable()`
- `BaseDefender` — `tick()`, `isAvailable()`, `findNearbyHostiles()`
- `BaseSupport` — `tick()`, `isAvailable()`, `findNearbyPlayers()`

All just replace `world`/`origin` params with `context`, then use `context.world` and `context.origin`.

#### 1d. Change all 6 DSL job classes
- `GatheringJob`, `ProductionJob`, `ProcessingJob`, `PlacementJob`, `DefenseJob`, `SupportJob`
- Override methods that changed signature.

#### 1e. Change all 9 job registry files
- `GatheringJobs`, `ProductionJobs`, `ProcessingJobs`, `PlacementJobs`, `DefenseJobs`, `SupportJobs`, `EnvironmentalJobs`, `LogisticsJobs`, `ComboJobs`
- Any inline `Worker` implementations (like `FrostFormer`, `ObsidianForge`, etc.) need signature updates.
- **`EnvironmentalJobs` has 11 inline `Worker` implementations** (FrostFormer, ObsidianForge, GrowthAccelerator, LavaCauldronFiller, WaterCauldronFiller, SnowCauldronFiller, FurnaceFueler, BrewingStandFueler, FireDouser, CropIrrigator, BeePollinator). Each has `tick(world, origin, entity)` and `isAvailable(world, origin, pokemonId)` that need signature updates. These are NOT DSL-based — they implement `Worker` directly with their own state maps.
- **`LogisticsJobs`** has 2 inline workers: `Magnetizer` (consolidates container items in-place) and `GroundItemCollector` (live-scans for `ItemEntity` each tick, not cached). GroundItemCollector is ideal for party workers. Magnetizer only makes sense near infrastructure — `isAvailable` naturally gates this.

#### 1f. Change `WorkerDispatcher`
- `tickPokemon(world, pastureOrigin, entity)` → `tickPokemon(context: JobContext, entity: PokemonEntity)`
- `tickAreaScan(world, pastureOrigin)` → `tickAreaScan(context: JobContext)`
- `returnToPasture()` → `returnToOrigin()` (name change)
- `cleanupPokemon(pokemonId, world)` — also call `CobbleCrewInventoryUtils.cleanupPokemon(pokemonId)` to clear deposit state maps
- **Confirm shared singleton is intentional:** `activeJobs`, `profiles`, `jobAssignedTick`, `idleLogTick` maps are global — both pasture and party workers share them. One UUID = one job globally. This is correct and prevents a Pokémon from double-assignment.

#### 1g. Change utility classes
- `CobbleCrewInventoryUtils.handleDepositing()` — `origin` param → `context`
- `CobbleCrewInventoryUtils.findInputContainer()` — `origin` param → `context`
- `CobbleCrewInventoryUtils.findClosestInventory()` — takes origin, needs context
- `DeferredBlockScanner.tickPastureAreaScan()` — needs to accept either a `BlockPos` key (pasture) or `UUID` key (player)
- `DeferredBlockScanner.lastScanCompletion` — **must change from `Map<BlockPos, Long>` to `Map<CacheKey, Long>`**. Otherwise party scans never record completion, the 60-second cooldown never applies, and party Pokémon rescan every tick
- `DeferredBlockScanner.isScanActive()` — **must accept `CacheKey`**, not just `BlockPos`. Called from `CobbleCrewInventoryUtils.handleDepositing()` to decide whether to hold items during scan
- `CobbleCrewInventoryUtils` — add `cleanupPokemon(pokemonId: UUID)` that clears `depositArrival`, `depositRetryTimers`, and `lastDepositWarning` maps for that UUID. These maps currently leak entries when a party worker is recalled mid-deposit
- `CobbleCrewCauldronUtils.findClosestCauldron()` — takes `origin` as param, passes through to cache. Compatible with refactor (just reads `context.origin`)

#### 1h. Change `CobbleCrewCacheManager`
This is the **trickiest part of Phase 1**. Currently keyed by `BlockPos`. Needs to support both:
- `BlockPos` key for pasture caches (fixed location, stable)
- `UUID` key for party caches (player, moves with player)

**Option A (recommended):** Generalize the cache key:
```kotlin
sealed interface CacheKey {
    data class PastureKey(val pos: BlockPos) : CacheKey
    data class PlayerKey(val playerId: UUID) : CacheKey
}
```
All `pastureCaches: MutableMap<BlockPos, PastureCache>` → `MutableMap<CacheKey, PastureCache>`. `getTargets(key, category)` etc.

**Option B:** Two separate maps. Simpler but duplicates API surface. Probably not worth it.

#### 1i. Update Mixin
- `PokemonPastureBlockEntityMixin` creates `JobContext.Pasture(blockPos, world)` and passes it.
- `FarmlandBlockMixin` — needs to also protect party Pokémon (check `ownerUuid != null` in addition to `tethering != null`).

#### 1j. Validate
Build both loaders. All existing behavior should be completely unchanged. The `JobContext.Pasture` path is identical to the old `origin: BlockPos` path.

---

### Phase 2: Party Pokémon Tracking System

**Goal:** Detect when players send out party Pokémon, track them, and tick their jobs.

#### 2a. `PartyWorkerManager` (new file)
```kotlin
// common/src/main/kotlin/akkiruk/cobblecrew/jobs/PartyWorkerManager.kt
object PartyWorkerManager {
    // Tracks which party Pokémon are "working" (sent out + opted in)
    private val activePartyWorkers = mutableMapOf<UUID, PartyWorkerEntry>()

    // Per-player context — persists across ticks so pinnedOrigin is stable
    private val playerContexts = mutableMapOf<UUID, JobContext.Party>()

    data class PartyWorkerEntry(
        val pokemonId: UUID,
        val pokemonEntity: PokemonEntity,
        val owner: ServerPlayerEntity,
    )

    fun onPokemonSentOut(event: PokemonSentEvent.Post) { ... }
    fun onPokemonRecalled(event: PokemonRecallEvent.Post) { ... }
    fun tick(server: MinecraftServer) { ... }
}
```

> **IMPORTANT:** `pinnedOrigin` lives on the `JobContext.Party` instance stored in
> `playerContexts`, NOT created fresh each tick. All Pokémon for the same
> player share one context (and therefore one pinned work zone + one scan cache).

#### 2b. Hook Cobblemon Events
Register listeners for:
- `CobblemonEvents.POKEMON_SENT_POST` → add Pokémon to `activePartyWorkers`
- `CobblemonEvents.POKEMON_RECALL_POST` → remove from `activePartyWorkers` + cleanup

**Registration location:** These go in **common code** (e.g., `PartyWorkerManager.init()` or `CobbleCrew.init()`), NOT in platform entrypoints. `CobblemonEvents` is a Kotlin object in common — `.subscribe {}` works cross-platform. Only the server tick hook needs per-platform registration.

**Important subtleties:**

1. `PokemonSentEvent.Post` fires "after animations are finished" — the entity is fully spawned. This is the right hook.

2. **Battle sends fire the same event.** `sendOutWithAnimation()` fires `POKEMON_SENT_POST` for both battle and non-battle sends. Must filter:
   ```kotlin
   fun onPokemonSentOut(event: PokemonSentEvent.Post) {
       if (event.pokemonEntity.battleId != null) return  // battle send, not a party worker
       ...
   }
   ```

3. **No player field on the event.** `PokemonSentEvent.Post` has `pokemon`, `level`, `position`, `pokemonEntity` — but no `ServerPlayerEntity`. Get the owner via:
   ```kotlin
   val owner = event.pokemon.getOwnerPlayer() ?: return  // nullable — guard it
   ```
   `Pokemon.getOwnerPlayer()` returns `ServerPlayer?` via `storeCoordinates` lookup.

4. **`RecallEvent.Post.oldEntity` may be null.** The recall method sets `state = InactivePokemonState()` and calls `state?.recall()` (which removes the entity) BEFORE firing `POKEMON_RECALL_POST`. By the time the event fires, `this.entity` may already be null. **Don't rely on `oldEntity`** — use `event.pokemon.uuid` to look up from `activePartyWorkers` map instead:
   ```kotlin
   fun onPokemonRecalled(event: PokemonRecallEvent.Post) {
       val pokemonId = event.pokemon.uuid
       val entry = activePartyWorkers[pokemonId] ?: return
       // Deliver any held items before cleanup (see 2e)
       deliverHeldItemsOnRecall(entry)
       cleanup(pokemonId)
   }
   ```

#### 2c. Server Tick Hook
Need a periodic tick to drive party Pokémon jobs. Options:
- **Mixin on `MinecraftServer.tick()`** — very reliable
- **Cobblemon's own tick events** — not sure if exposed
- **Fabric/NeoForge server tick event** — platform-specific, needs Architectury abstraction

**Recommended:** Use Architectury's `TickEvent.SERVER_PRE` or add a mixin on `ServerLevel.tick()`. Server tick hook requires per-platform registration:
- Fabric: `ServerTickEvents.END_SERVER_TICK`
- NeoForge: `TickEvent.ServerTickEvent`

**Important:** `CobbleCrewInventoryUtils.INSTANCE.tickAnimations(world)` must also be called from `PartyWorkerManager.tick()`. Currently this is called inside the pasture mixin's tick — if no pasture blocks exist, chest open/close animations never fire. Party worker tick must also drive this.

Each tick:
```kotlin
fun tick(server: MinecraftServer) {
    // Deduplicate scans: one scan per player, not per Pokémon
    val playerGroups = activePartyWorkers.values.groupBy { it.owner.uuid }

    for ((playerId, entries) in playerGroups) {
        val first = entries.first()
        val context = playerContexts.getOrPut(playerId) {
            JobContext.Party(first.owner, first.owner.serverWorld)
        }

        // Shared area scan — once per player
        WorkerDispatcher.tickAreaScan(context)

        // Tick each Pokémon individually
        for (entry in entries) {
            if (entry.pokemonEntity.isRemoved) { cleanup(entry.pokemonId); continue }
            if (entry.owner.isRemoved) { cleanup(entry.pokemonId); continue }
            if (entry.pokemonEntity.pokemon.isFainted()) { cleanup(entry.pokemonId); continue }
            if (entry.pokemonEntity.battleId != null) { cleanup(entry.pokemonId); continue }

            WorkerDispatcher.tickPokemon(context, entry.pokemonEntity)
        }

        // Cleanup player context if no more workers
        if (activePartyWorkers.values.none { it.owner.uuid == playerId }) {
            playerContexts.remove(playerId)
        }
    }
}
```

> **Key fix:** `playerContexts` stores one `JobContext.Party` per player that
> persists across ticks — `pinnedOrigin` survives. Multiple Pokémon from the
> same player share the same context and scan cache.

#### 2d. Cache Management for Party Pokémon
- DeferredBlockScanner runs with `CacheKey.PlayerKey(playerId)` for party Pokémon
- Scan centered on player's current position at the time the scan starts
- When scan completes, `JobContext.Party.pinnedOrigin` is set to the scan center — origin becomes stable
- Cache stays valid as long as the pinned origin holds (see Phase 3f for zone transition rules)
- When zone transitions, old cache is cleared and a new scan starts at the player's new position
- **`transitionZone()` must also clear `DeferredBlockScanner.lastScanCompletion` for the player's CacheKey** — otherwise the 60-second scan cooldown blocks the new scan from starting
- **`transitionZone()` must abort any in-progress scan** for the player's CacheKey — remove from `activeScans` to prevent the old scan from completing and overwriting the new zone's data
- Multiple Pokémon from the same player share one scan cache (keyed by player UUID)

#### 2e. Held Items Recovery on Recall
When a Pokémon is recalled while holding harvested/produced items, those items must be delivered before cleanup:

```kotlin
fun deliverHeldItemsOnRecall(entry: PartyWorkerEntry) {
    val pokemonId = entry.pokemonId
    // Check every base class's held items maps
    for (worker in WorkerRegistry.workers) {
        val heldItems = worker.getHeldItems(pokemonId) ?: continue
        if (heldItems.isNotEmpty()) {
            CobbleCrewInventoryUtils.deliverToPlayer(entry.owner, heldItems, entry.pokemonEntity)
        }
    }
}
```

This requires adding an optional `getHeldItems(pokemonId): List<ItemStack>?` to the `Worker` interface (default returns null). Base classes that track held items (`BaseHarvester`, `BaseProducer`, `BaseProcessor`, `BasePlacer`) override it to return their `heldItemsByPokemon[pokemonId]`.

Alternatively, since `WorkerDispatcher.cleanupPokemon()` already calls `cleanup()` on every worker, we could add a `flushItems()` step before `cleanup()` that checks context type and delivers to player.

---

### Phase 3: Behavior Differences (Party vs Pasture)

#### 3a. "Return to Origin" Behavior
- **Pasture:** idle Pokémon navigate back to pasture block
- **Party:** idle Pokémon follow the player (like a pet). Use `CobbleCrewNavigationUtils.navigateToPlayer()` instead of `navigateTo(pastureOrigin)`

`WorkerDispatcher.returnToOrigin()`:
```kotlin
private fun returnToOrigin(context: JobContext, pokemonEntity: PokemonEntity) {
    when (context) {
        is JobContext.Pasture -> {
            if (!NavUtils.isPokemonAtPosition(pokemonEntity, context.origin, 3.0))
                NavUtils.navigateTo(pokemonEntity, context.origin)
        }
        is JobContext.Party -> {
            if (!NavUtils.isPokemonAtPosition(pokemonEntity, context.player.blockPos, 5.0))
                NavUtils.navigateToPlayer(pokemonEntity, context.player)
        }
    }
}
```

#### 3b. Farmland Trampling
Current `FarmlandBlockMixin` only checks `getTethering() != null`. Party Pokémon don't have tethering. Need to also check:
```java
if (entity instanceof PokemonEntity pokemon && pokemon.getOwnerUuid() != null) {
    ci.cancel();
}
```

#### 3c. Work Range Limits
Party Pokémon shouldn't wander infinitely far from the player. Add a max leash distance:
- If Pokémon is >32 blocks from player, abort current job + navigate back
- If player moves >64 blocks from Pokémon (e.g. teleport), auto-recall or teleport the Pokémon

**Note on catch-up teleport + stale state:** When `resetAssignment()` fires after a teleport, it calls `cleanup()` on every Worker. This clears stale `BlockPos` values stored in `BaseProcessor.inputTargets`, `BasePlacer.sourceTargets`/`placementTargets`, `BaseDefender.cachedHostiles`, and all EnvironmentalJob `targets` maps. This cleanup path is critical — without it, Pokémon would try to pathfind to blocks 100+ blocks away from their new position.

#### 3d. Battle Exclusion

When a player enters battle, Cobblemon's `startShowdown()` iterates **all** party Pokémon entities and stamps `battleId` on them. Then it creates a **brand new** entity for the battle lead via `sendOut()`, overwriting the Pokemon's `state` to a new `SentOutState`. This **orphans** any existing worker entity — it's still alive in the world but disconnected from the Pokemon data, so `SentOutState.recall()` will never clean it up.

**Hook: `BATTLE_STARTED_PRE` (not POST)**

PRE fires *before* `startShowdown()` runs. This gives us a clean window to recall worker entities before Cobblemon touches them.

```kotlin
CobblemonEvents.BATTLE_STARTED_PRE.subscribe { event ->
    val playerUUIDs = event.battle.actors
        .filterIsInstance<PlayerBattleActor>()
        .map { it.uuid }
        .toSet()
    
    for (playerUUID in playerUUIDs) {
        val entries = activePartyWorkers[playerUUID] ?: continue
        for (entry in entries.values.toList()) {
            // Deliver any held items before cleanup
            val player = entry.pokemon.getOwnerPlayer() ?: continue
            val heldItems = getHeldItems(entry.pokemonId)
            if (heldItems.isNotEmpty()) {
                CobbleCrewInventoryUtils.deliverToPlayer(player, heldItems, entry.pokemonEntity)
            }
            
            // Clean up the active job
            entry.activeJob?.cleanup(entry.pokemonId)
            
            // Recall the entity (discard it) before Cobblemon can orphan it
            entry.pokemonEntity.discard()
        }
        // Mark player as in-battle so tick loop skips them
        playersInBattle.add(playerUUID)
        activePartyWorkers.remove(playerUUID)
    }
}
```

**Resume on battle end: `BATTLE_VICTORY` + `BATTLE_FLED`**

Both events provide `battle.actors` to extract player UUIDs. Just clear the in-battle flag — workers will naturally get reassigned when the player sends Pokémon out again.

```kotlin
fun onBattleEnd(battle: PokemonBattle) {
    val playerUUIDs = battle.actors
        .filterIsInstance<PlayerBattleActor>()
        .map { it.uuid }
    playerUUIDs.forEach { playersInBattle.remove(it) }
}

CobblemonEvents.BATTLE_VICTORY.subscribe { onBattleEnd(it.battle) }
CobblemonEvents.BATTLE_FLED.subscribe { onBattleEnd(it.battle) }
```

**Why not pause-and-resume?** Trying to freeze job state and restore it after battle introduces stale state bugs (target block may have changed, entity may have moved). Simpler to just clean up and let the dispatcher reassign fresh jobs on the next cycle.

**Edge case: Battle cancelled by another mod.** If something cancels the battle via `BATTLE_STARTED_PRE`, we've already recalled worker entities. This is acceptable — they can be re-sent by the player. Better than orphaned entities.

**State needed in `PartyWorkerManager`:**
```kotlin
val playersInBattle = mutableSetOf<UUID>()
```
Checked in the tick loop: `if (playerUUID in playersInBattle) continue`

#### 3e. Item Delivery: Direct to Player Inventory
Party Pokémon skip the entire container-search/deposit flow. Items go straight into the player's inventory.

**How it works:**
- `BaseHarvester.tick()` — after harvesting, when `context` is `Party`, call `insertIntoPlayerInventory()` instead of `CobbleCrewInventoryUtils.handleDepositing()`
- `BaseProducer.tick()` — after producing, same thing
- `BaseProcessor.tick()` — after transforming, same thing (but see note below)
- `ComboJobs` — same as harvesters

**Implementation in `CobbleCrewInventoryUtils`:**
```kotlin
fun deliverToPlayer(
    player: ServerPlayerEntity,
    items: List<ItemStack>,
    pokemonEntity: PokemonEntity,
): List<ItemStack> {
    val overflow = mutableListOf<ItemStack>()
    for (stack in items) {
        if (!player.inventory.insertStack(stack)) {
            // Inventory full — drop at player's feet
            player.dropItem(stack, false)
        }
    }
    return overflow
}
```

**Where it branches:** In each base class's `tick()` method, the deposit path checks `context`:
```kotlin
// In BaseHarvester.tick(), BaseProducer.tick(), etc:
if (heldItems.isNullOrEmpty()) {
    handleHarvesting(context, pokemonEntity)
} else {
    when (context) {
        is JobContext.Party -> {
            CobbleCrewInventoryUtils.deliverToPlayer(context.player, heldItems, pokemonEntity)
            heldItemsByPokemon.remove(pokemonId)
        }
        is JobContext.Pasture -> {
            CobbleCrewInventoryUtils.handleDepositing(context, pokemonEntity, heldItems, ...)
        }
    }
}
```

**What this eliminates for party workers:**
- No container scanning overhead
- No failed-deposit tracking / retry logic
- No navigation-to-container pathfinding
- No chest open/close animations
- No 5-minute overflow timer

**Processor/Placer input containers:** `BaseProcessor` and `BasePlacer` also *pull* items FROM containers (barrels). For party Pokémon, these jobs either:
1. Only work when the player is near infrastructure with barrels (isAvailable returns false otherwise — existing behavior handles this)
2. Or we could add a future option to pull from the player's inventory instead — but that's a separate feature, not needed for v1

#### 3f. Pinned Work Zones
The single most important stability mechanism. Instead of `origin = player.blockPos` every tick (which changes constantly and invalidates everything), **pin a work zone when a scan completes** and keep it stable.

**Lifecycle:**
1. Pokémon sent out → `pinnedOrigin = null` → `origin` falls back to `player.blockPos` → scan kicks off
2. Scan completes → `pinnedOrigin` = where the scan was centered → origin is now **fixed**
3. Pokémon works that zone using the stable pinned origin — no interruptions from player movement
4. Zone transition triggers (see below) → `pinnedOrigin` cleared → re-pin at player's new location → new scan

**Zone transition conditions** (checked only when Pokémon is IDLE — no active job, no held items, no nav target):
- All targets in the zone exhausted (no `isAvailable` returns true)
- Player is >48 blocks from `pinnedOrigin`

```kotlin
// In PartyWorkerManager:
fun shouldTransitionZone(pokemonId: UUID, context: JobContext.Party, world: World): Boolean {
    if (WorkerDispatcher.hasActiveJob(pokemonId)) return false
    if (CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null) return false

    val pinned = context.pinnedOrigin ?: return true  // no zone yet
    return pinned.getSquaredDistance(context.player.blockPos) > 48.0 * 48.0
}

fun transitionZone(context: JobContext.Party) {
    context.pinnedOrigin = null  // will re-pin when next scan completes
    CobbleCrewCacheManager.removeCache(CacheKey.PlayerKey(context.player.uuid))
}
```

**Effect:** A player walking through a wheat farm gets one scan, the Pokémon harvests everything in that area undisturbed, then catches up and scans the next area. A player sprinting across a field → Pokémon never gets a pinned zone → just follows.

#### 3g. Active Job Immunity
Already partially handled by `JOB_STICKINESS_TICKS = 100L` and `hasActiveState()`, but reinforced for party:

- If the Pokémon has a claimed navigation target, **never interrupt** — even if a zone transition would normally trigger
- If the Pokémon is holding items (harvested, produced), let it finish delivery to player before reassigning
- Zone transitions only happen when Pokémon is truly IDLE (no active job, no held items, no nav target)
- `WorkerDispatcher.hasActiveJob(pokemonId)` — new accessor that checks `activeJobs` map

#### 3h. Catch-Up Teleport
When the player gets far away (sprinting, riding, nether portal), don't pathfind — just teleport like wolves:

```kotlin
// In PartyWorkerManager.tick(), per Pokémon:
val distSq = pokemonEntity.squaredDistanceTo(context.player)
if (distSq > teleportDistance * teleportDistance) {
    pokemonEntity.teleport(context.player.x, context.player.y, context.player.z)
    WorkerDispatcher.resetAssignment(pokemonId)
    context.pinnedOrigin = null  // fresh start in new area
}
```

`teleportDistance` defaults to 64 blocks (configurable).

#### Movement Stability Timeline
```
Player walks through wheat farm...
  t=0    Pokémon sent out. pinnedOrigin=null. origin=player pos. Scan starts.
  t=212  Scan completes. pinnedOrigin pinned. 14 CROP targets found.
         Pokémon assigned wheat_harvester. Works undisturbed.
  t=400  Player walks 20 blocks away. Pokémon still in pinned zone, harvesting.
         origin is stable (pinned), not affected by player movement.
  t=600  Pokémon finishes all crops. IDLE. shouldTransitionZone → player is
         35 blocks from pinnedOrigin (< 48) → Pokémon follows player.
  t=700  Player stops 50 blocks from old zone. shouldTransitionZone → true.
         Zone clears. New scan at player pos. Cycle repeats.

Player sprinting across open field...
  t=0    pinnedOrigin=null. Scan starts at player pos.
  t=100  Player 60 blocks from scan center. Scan still running.
  t=212  Scan completes at old position. pinnedOrigin pinned.
         But player is 80 blocks away → immediate zone transition.
         pinnedOrigin cleared. New scan at player pos. Repeat.
         Pokémon never gets targets → just follows player. No wasted work.

Player teleports...
  t=0    Pokémon at old location, 200 blocks away.
         distSq > 64² → teleport to player. Reset all state. Fresh start.
```

#### 3i. Target Reachability & Pathfinding Failures

**The underlying problem (affects BOTH pasture and party, but much worse for party):**

The current system has **zero reachability validation** at any layer:

| Layer | Current Behavior | Problem |
|---|---|---|
| `DeferredBlockScanner` | Caches every block matching the type (e.g., `STONE`, `ORE`, `CROP`) | Includes blocks fully enclosed underground with no exposed faces |
| `findClosestTarget()` | Picks closest by distance, no accessibility check | Picks buried ores, blocks behind walls |
| `navigateTo()` | Calls `startMovingTo()`, **ignores the boolean return** | Pokémon walks into walls with no feedback |
| Claim timeout | 5s timeout → 15s blacklist → re-eligible | Pokémon endlessly re-claims unreachable blocks in a cycle |

**For pasture workers this is tolerable** — the search area is small (8-block radius) and centered on a player-placed block (likely on the surface). Most targets are reachable.

**For party workers this is critical** — the player walks near a mountain and the scanner finds 200 stone/ore blocks underground. The Pokémon spends its entire time claiming buried blocks, timing out, re-claiming. It never does useful work.

**Solution: Multi-layered reachability filtering**

Three independent filters, applied at different stages:

**Filter 1: Exposed-face check at scan time (DeferredBlockScanner)**

When the scanner finds a matching block, run a cheap face exposure check before adding to cache:
```kotlin
fun hasExposedFace(world: World, pos: BlockPos): Boolean {
    return Direction.entries.any { dir ->
        val adjacent = pos.offset(dir)
        val adjacentState = world.getBlockState(adjacent)
        !adjacentState.isOpaqueFullCube(world, adjacent)
    }
}
```
A block with ALL 6 faces covered by opaque blocks is unreachable by any mob — skip it. This eliminates buried ores, underground stone, etc.

**Cost:** 6 block state lookups per candidate. The scanner already does 15 blocks/tick, adding 6*15=90 lookups is negligible.

**Which categories need this:**
- `STONE`, `DEEPSLATE`, `ORE` — **absolutely** (massive underground volumes)
- `DIRT`, `SAND`, `GRAVEL` — **yes** (also underground)
- `CROP`, `BERRY`, `LOG` — **no** (always surface/exposed by nature)
- `CONTAINER`, `CAULDRON`, `FURNACE` — **no** (player-placed, always accessible)

Add a `requiresExposedFace: Boolean` property to `BlockCategory` so the scanner can check conditionally.

**Filter 2: Nav-path validation on target selection (findClosestTarget)**

After picking the closest target, check if the Pokémon can actually pathfind to it:
```kotlin
fun isReachable(pokemonEntity: PokemonEntity, targetPos: BlockPos): Boolean {
    val path = pokemonEntity.navigation.createPath(targetPos, 1)
    return path != null && path.canReach()
}
```

If unreachable, skip to the next-closest target. **Don't blacklist permanently** — the player might mine toward it, exposing a path later.

**Cost:** Path calculation is moderately expensive. Mitigate by:
- Only calling when the Pokémon is picking a NEW target (not every tick)
- Capping at 3 pathfind attempts per tick — if the 3 closest targets are all unreachable, the Pokémon idles
- Caching "unreachable" results for 10 seconds (200 ticks) per `(pokemonId, targetPos)` pair

```kotlin
val unreachableCache = mutableMapOf<Pair<UUID, BlockPos>, Long>()  // pokemonId+pos → expiry tick

fun findClosestReachableTarget(
    context: JobContext,
    pokemonEntity: PokemonEntity,
    targets: List<BlockPos>,
    currentTick: Long
): BlockPos? {
    val pokemonId = pokemonEntity.uuid
    var attempts = 0
    for (target in targets.sortedBy { it.getSquaredDistance(context.origin) }) {
        // Skip if recently found unreachable
        val cacheKey = pokemonId to target
        val expiry = unreachableCache[cacheKey]
        if (expiry != null && currentTick < expiry) continue

        // Check pathfinding
        val path = pokemonEntity.navigation.createPath(target, 1)
        if (path != null && path.canReach()) return target

        // Mark unreachable for 10 seconds
        unreachableCache[cacheKey] = currentTick + 200L
        attempts++
        if (attempts >= 3) break  // cap pathfind attempts per tick
    }
    return null
}
```

**Where this applies:** `BaseHarvester.findClosestTarget()`, `BaseDefender.findNearbyHostiles()`, `BaseSupport.findNearbyPlayers()`, all EnvironmentalJob target selection, `BasePlacer.findPlacementTarget()`. Processors/Placers that find containers already use cache-based lookup — containers are player-placed and always reachable.

**Filter 3: Escalating blacklist on repeated nav failures**

If a Pokémon claims a target and the claim expires (5s timeout without arrival) MORE THAN ONCE for the same block:
- 1st timeout: normal 15s blacklist (existing behavior)
- 2nd timeout for same block: 60s blacklist
- 3rd+ timeout: 5 minute blacklist (effectively permanent for one work session)

This prevents the infinite claim/timeout/re-claim cycle on blocks that pass the exposure check but are still unreachable (e.g., behind a 1-block wall, across a lava pool).

```kotlin
// In CobbleCrewNavigationUtils:
val targetFailCounts = mutableMapOf<Pair<UUID, BlockPos>, Int>()  // pokemonId+pos → fail count

fun getBlacklistDuration(pokemonId: UUID, pos: BlockPos): Long {
    val key = pokemonId to pos
    val fails = targetFailCounts.getOrDefault(key, 0)
    return when {
        fails <= 1 -> EXPIRED_TARGET_TIMEOUT_TICKS      // 15s
        fails == 2 -> 60 * 20L                          // 60s
        else -> 5 * 60 * 20L                            // 5 min
    }
}
```

**Cleanup:** Clear `targetFailCounts` and `unreachableCache` in `cleanupPokemon()` and on zone transition.

**Benefits for party workers specifically:**
- Player walks near a cave → scanner finds 50 exposed ores → Filter 1 removes buried ones → Filter 2 picks only pathfindable ones → Pokémon mines the accessible ores efficiently
- Player walks through a forest → all logs are surface-exposed → Filter 1 passes everything → Filter 2 confirms paths → Pokémon chops trees
- Player near a mountain wall with ores visible but unreachable → Filter 2 skips them after 3 attempts → Pokémon idles or picks other work

**Benefits for pasture workers (bonus):**
- Pasture placed on surface near a cliff → underground ores no longer clog the cache
- Existing claim timeout loop bug is fixed by escalating blacklist

**Implementation phase:** Part of Phase 3 (party-specific behaviors) since it's most critical for party workers, but the exposed-face check (Filter 1) could be added in Phase 1 as a no-behavior-visible optimization for pasture workers.

**Config:**
```kotlin
/** Check if target blocks have exposed faces before caching. */
var requireExposedFace = true

/** Validate pathfinding before claiming a target. */
var validatePathfinding = true

/** Max pathfind validation attempts per Pokémon per tick. */
@ConfigEntry.BoundedDiscrete(min = 1, max = 10)
var maxPathfindAttemptsPerTick = 3
```

---

### Phase 4: Config & Commands

#### 4a. New Config Section
```kotlin
class PartyGroup {
    /** Enable party Pokémon jobs globally. */
    var enabled = true

    /** Max distance a party worker can travel from the player. */
    @ConfigEntry.BoundedDiscrete(min = 8, max = 64)
    var maxWorkDistance = 32

    /** Distance from pinned origin at which zone transitions. */
    @ConfigEntry.BoundedDiscrete(min = 24, max = 96)
    var zoneTransitionDistance = 48

    /** Auto-teleport party workers to player if they fall behind. */
    var teleportIfTooFar = true

    /** Distance at which auto-teleport triggers. */
    @ConfigEntry.BoundedDiscrete(min = 32, max = 128)
    var teleportDistance = 64
}
```

#### 4b. Command Extensions
Existing `/cobblecrew` commands should show party Pokémon too:
- `/cobblecrew status` — show pasture workers AND party workers
- `/cobblecrew reset <player>` — reset party worker assignments for a player
- `/cobblecrew cache clear` — currently takes X/Y/Z coords (pasture). Needs a `<player>` variant for player-keyed caches

#### 4c. Player Disconnect/Death Cleanup
**Must be added to platform entrypoints** (per-platform events):
- Fabric: `ServerPlayConnectionEvents.DISCONNECT`
- NeoForge: `PlayerEvent.PlayerLoggedOutEvent`

On disconnect or death:
1. Iterate `activePartyWorkers` for this player UUID
2. For each entry: deliver held items (if player alive), call `cleanup()`, discard entity
3. Remove player from `playerContexts` and `playersInBattle`
4. Clear player's cache from `CobbleCrewCacheManager` (by `CacheKey.PlayerKey`)
5. Clear player's scan from `DeferredBlockScanner` (`lastScanCompletion`, `activeScans`)

---

### Phase 5: Opt-in / Opt-out Mechanism

Not every sent-out Pokémon should auto-work. Options to consider:

**Option A: All sent-out Pokémon work** (simplest)
- Any Pokémon you throw out starts working automatically
- Recall to stop

**Option B: Toggle command**
- `/cobblecrew party enable/disable` — per-player toggle
- When enabled, all sent-out Pokémon work

**Option C: Per-Pokémon toggle** (most complex)
- Some interaction (shift+right-click the entity?) toggles work mode
- Visual indicator (particle/nameplate) shows working vs idle

**Recommendation:** Start with Option A (auto-work on send out). Add Option B later if needed. Option C is overengineered for now.

---

## File Change Summary

| File | Change Type |
|---|---|
| `jobs/JobContext.kt` | **NEW** — sealed interface |
| `jobs/PartyWorkerManager.kt` | **NEW** — tracks party workers, tick loop |
| `interfaces/Worker.kt` | **MODIFY** — signature change `(world, origin)` → `(context)` |
| `jobs/WorkerDispatcher.kt` | **MODIFY** — accepts `JobContext`, `returnToOrigin()` |
| `jobs/PokemonProfile.kt` | No change (eligibility is data-only) |
| `jobs/BaseHarvester.kt` | **MODIFY** — signature change |
| `jobs/BaseProducer.kt` | **MODIFY** — signature change |
| `jobs/BaseProcessor.kt` | **MODIFY** — signature change |
| `jobs/BasePlacer.kt` | **MODIFY** — signature change |
| `jobs/BaseDefender.kt` | **MODIFY** — signature change |
| `jobs/BaseSupport.kt` | **MODIFY** — signature change |
| `jobs/dsl/*.kt` (6 files) | **MODIFY** — signature change |
| `jobs/registry/*.kt` (9 files) | **MODIFY** — signature change in inline workers |
| `cache/CobbleCrewCacheManager.kt` | **MODIFY** — generalize cache key |
| `utilities/DeferredBlockScanner.kt` | **MODIFY** — generalize key + player movement rescan |
| `utilities/CobbleCrewInventoryUtils.kt` | **MODIFY** — accept `JobContext` |
| `utilities/CobbleCrewNavigationUtils.kt` | Minor — `returnToOrigin()` calls it |
| `config/CobbleCrewConfig.kt` | **MODIFY** — add `PartyGroup` |
| `mixin/PokemonPastureBlockEntityMixin.java` | **MODIFY** — wrap origin in `JobContext.Pasture` |
| `mixin/FarmlandBlockMixin.java` | **MODIFY** — also protect party Pokémon |
| Platform entrypoints (fabric + neoforge) | **MODIFY** — register server tick hook (Cobblemon events go in common) |
| `utilities/CobbleCrewCauldronUtils.kt` | Minor — `origin` param flows through from `context.origin` |
| `enums/BlockCategory.kt` | **MODIFY** — add `requiresExposedFace` property |

**Estimated: ~32 files touched, 2 new files.**

---

## Suggested Implementation Order

1. **Phase 1** first — pure refactor, zero behavior change, easy to validate
2. **Phase 2** next — wire up the event hooks + tick driver, see party Pokémon get job profiles
3. **Phase 3** — party-specific behavior (follow player, leash distance)
4. **Phase 4** — config + commands
5. **Phase 5** — opt-in mechanism (start with auto-work)

Each phase is independently testable and deployable. Phase 1 is the biggest but safest since it's a mechanical refactor.

---

## Risks & Edge Cases

| Risk | Mitigation |
|---|---|
| Player logs off with party Pokémon working | `PlayerLoggedOutEvent` → cleanup all their party workers |
| Player dies | Same — cleanup on death/respawn |
| Pokémon faints while working | Check `isFainted()` each tick in `PartyWorkerManager` |
| Player enters a dimension portal | Pokémon entity is in old dimension — cleanup + let Cobblemon handle the recall |
| Multiple party Pokémon sent out | Support N simultaneously — shared context per player, deduplicated scans |
| Scan performance (player moves a lot) | Pinned work zones prevent constant rescanning; zone transitions only when IDLE |
| Battle sends fire POKEMON_SENT_POST | Filter by `pokemonEntity.battleId != null` in event handler |
| Items lost on recall mid-harvest | `deliverHeldItemsOnRecall()` flushes held items to player inventory before cleanup |
| `RecallEvent.Post.oldEntity` is null | Look up by `pokemon.uuid` from `activePartyWorkers` map, don't use `oldEntity` |
| `PokemonSentEvent.Post` has no player field | Use `pokemon.getOwnerPlayer()` with null guard |
| Party Pokémon entity removed by Cobblemon | Check `isRemoved` every tick |
| Party Pokémon enters battle (wild/trainer) | `BATTLE_STARTED_PRE` → force-recall all worker entities, deliver held items, cleanup jobs. `BATTLE_VICTORY`/`BATTLE_FLED` → clear in-battle flag. PRE is critical — POST is too late because `startShowdown()` already orphaned entities |
| Two players' party workers overlapping areas | Each has independent cache keyed by player UUID — no conflicts |
| Cache bloat from many players | Cleanup on recall/logout, TTL on unused caches |
| Unreachable targets (buried ores, blocks behind walls) | 3-layer reachability: (1) exposed-face check at scan, (2) pathfind validation at selection, (3) escalating blacklist on repeated nav failures |
| `DeferredBlockScanner.lastScanCompletion` keyed by `BlockPos` | Must change to `CacheKey` — otherwise party scans have no cooldown, causing rescan every tick |
| Zone transition doesn't clear scan cooldown | `transitionZone()` must clear `lastScanCompletion` AND abort in-progress scans for the player's CacheKey |
| `InventoryUtils` state leak on party recall | Add `cleanupPokemon(pokemonId)` that clears `depositArrival`, `depositRetryTimers`, `lastDepositWarning` |
| No pasture blocks on server | `tickAnimations(world)` must also be called from `PartyWorkerManager.tick()`, not just the pasture mixin |
| `NavigationUtils.recentlyExpiredTargets` growth | Party workers moving constantly accumulate entries from many areas. 5-min TTL prevents unbounded growth, but monitor memory on busy servers |
| Player disconnect with active party workers | Platform-specific disconnect events → full cleanup (items, cache, scan state, entities) |
| `ScoutWorker.lastGenTime` keyed by ownerUuid | Pre-existing bug: `cleanup(pokemonId)` doesn't clear it because map uses owner UUID. For party workers where owner IS the player, cooldown persists. Fix in Phase 3 |
| `BasePlacer.heldItems` is `UUID → ItemStack` (singular) | `getHeldItems()` override must wrap in `listOf(stack)` to match the `List<ItemStack>?` return type |
