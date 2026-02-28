# CobbleCrew Codebase Audit

**Date:** 2025-07-22  
**Scope:** Full codebase (`cobbleworkers/common/`, `fabric/`, `neoforge/`)  
**Method:** Static analysis — no code changes made

---

## CRITICAL

### 1. Thread-unsafe world access in ScoutWorker `CompletableFuture.supplyAsync`
| | |
|---|---|
| **File** | `jobs/registry/SupportJobs.kt` L252 |
| **Category** | Thread Safety |
| **Problem** | `CompletableFuture.supplyAsync { cg.locateStructure(world, ...) }` runs on the ForkJoinPool common pool, calling `ChunkGenerator.locateStructure()` off the server thread. Minecraft world/chunk access is **not thread-safe** — this can cause `ConcurrentModificationException`, corrupt chunk data, or crash the server under load. |
| **Fix** | Run the lookup on the server thread via `server.execute { ... }` and store the result in a `CompletableFuture` that the tick loop polls, or use `server.submit { ... }` to get a future that completes on the server thread. Alternatively, use Cobblemon's `Schedulable` or a queued async task that only reads immutable registry data off-thread and does the actual locate on-thread. |

### 2. PastureCache inner maps are not thread-safe
| | |
|---|---|
| **File** | `cache/PastureCache.kt` L16–17, `cache/CobbleCrewCacheManager.kt` L21 |
| **Category** | Thread Safety |
| **Problem** | `CobbleCrewCacheManager.caches` is a `ConcurrentHashMap<CacheKey, PastureCache>`, but `PastureCache.targetsByCategory` is a plain `MutableMap<BlockCategory, MutableSet<BlockPos>>`. If the ScoutWorker's async future (or any future off-thread code) triggers a cache read while the tick thread is mutating inner sets via `addTarget()` / `removeTarget()`, you get a `ConcurrentModificationException`. Even without the ScoutWorker issue, `getTargets()` returns the raw `MutableSet` reference — any caller iterating it while another tick modifies it will crash. |
| **Fix** | Either (a) wrap `targetsByCategory` values in `ConcurrentHashMap.newKeySet()`, or (b) return defensive copies from `getTargets()` (e.g. `Set(set)`), or (c) ensure all cache access is strictly single-threaded (fix #1 first). |

### 3. Mixin targets fragile lambda method name
| | |
|---|---|
| **File** | `mixin/PokemonPastureBlockEntityMixin.java` L37 |
| **Category** | Mixin Safety |
| **Problem** | `@Inject(method = "TICKER$lambda$0")` targets a compiler-generated lambda name inside `PokemonPastureBlockEntity`. This name is not stable across Cobblemon versions — if Cobblemon reorders lambdas, adds a new lambda, or changes the TICKER field initialization, this mixin silently fails (with `defaultRequire = 1` in the mixin JSON, it will crash on startup instead). |
| **Fix** | Use `@Accessor` or `@Shadow` to get the ticker, or target the `tick()` static method directly if possible. If the lambda must be targeted, add a comment documenting which Cobblemon version this was verified against, and add a build-time check or startup warning. |

---

## HIGH

### 4. `unreachableCache` grows unbounded
| | |
|---|---|
| **File** | `utilities/CobbleCrewNavigationUtils.kt` L76, L285–295 |
| **Category** | Memory Leak |
| **Problem** | `unreachableCache` is a `mutableMapOf<Pair<UUID, BlockPos>, Long>()`. Entries are only removed on individual lookups (`isUnreachable()` removes expired entries for the queried key) or on `cleanupPokemon()` (removes by UUID prefix). There is no periodic sweep. If a Pokémon repeatedly marks blocks unreachable but never re-queries those specific blocks, entries accumulate indefinitely. With many pastures over a long server session, this map can grow to tens of thousands of entries. |
| **Fix** | Add a periodic sweep in `releaseExpiredClaims()` (already runs every 20 ticks) that iterates `unreachableCache` and removes entries where `currentTick >= expiry`. |

### 5. `WorkSpeedBoostManager.boosts` never cleaned on pasture removal
| | |
|---|---|
| **File** | `utilities/WorkSpeedBoostManager.kt` L28 |
| **Category** | Memory Leak |
| **Problem** | `boosts` is keyed by `BlockPos` (pasture origin). When a pasture is broken/unloaded, entries expire naturally after 30 ticks (TTL), but the key itself remains in the map with an empty list. `getSpeedMultiplier()` does remove empty lists, but only if queried — orphaned keys from removed pastures may never be queried again. Over time with many pasture placements/removals, stale empty-list entries accumulate. |
| **Fix** | Call `cleanup()` or add a `removeOrigin(pos)` method, invoked from wherever pasture removal logic runs. The existing `cleanup()` clears everything — a targeted removal is better. |

### 6. PathfindingBudget global limit starves workers
| | |
|---|---|
| **File** | `utilities/PathfindingBudget.kt` (inferred from usage) |
| **Category** | Performance / Scalability |
| **Problem** | `PathfindingBudget` allows only 3 pathfind attempts per tick globally, shared across ALL pasture workers AND party workers. With 4+ pastures each having 6 Pokémon (24+ workers total), the budget is exhausted almost immediately each tick. Workers that don't get a pathfind slot appear idle. Since the throttle in `navigateTo()` already limits per-Pokémon pathfinding to every 5 ticks, the global budget is overly conservative. |
| **Fix** | Raise the global budget to scale with active worker count (e.g. `max(5, activeWorkerCount / 4)`) or make it per-pasture rather than global. |

### 7. `navigateToPlayer()` has no pathfind throttle
| | |
|---|---|
| **File** | `utilities/CobbleCrewNavigationUtils.kt` L121–128 |
| **Category** | Performance |
| **Problem** | `navigateTo(BlockPos)` checks `lastPathfindTick` and skips if called within 5 ticks. `navigateToPlayer(player)` does not — it calls `navigation.startMovingTo()` every tick it's invoked. Pathfinding is expensive; this can cause frame drops with many party workers following their owner. |
| **Fix** | Add the same `lastPathfindTick` throttle that `navigateTo()` uses. |

### 8. `tryIdlePickup` race condition with `item.discard()`
| | |
|---|---|
| **File** | `jobs/WorkerDispatcher.kt` L233–234 |
| **Category** | Bug |
| **Problem** | When a Pokémon picks up a ground item: `val stack = item.stack.copy()` then `item.discard()`. Between scanning for the item and discarding it, another Pokémon (or a hopper, or another mod) could have already picked it up. The `copy()` would succeed (copying an empty/changed stack), and the `discard()` would remove an already-consumed entity. This results in phantom items being deposited (duplicated) or an empty stack being stored. |
| **Fix** | Check `item.isRemoved` and `item.stack.isEmpty` before copying. Use `item.stack.split(item.stack.count)` instead of `copy()` + `discard()` to atomically take the items and let the entity self-remove when empty. |

---

## MEDIUM

### 9. Dead enum values — no validator, no job
| | |
|---|---|
| **File** | `enums/BlockCategory.kt` L26 (`CROP_BEET`), L48 (`CORAL`), L52 (`COMPOSTER`) |
| **Category** | Dead Code |
| **Problem** | Three `BlockCategory` enum values exist but have no corresponding entry in `BlockCategoryValidators.validators`. The deferred scanner will never classify any block into these categories, so any job targeting them would find zero targets. `CROP_BEET` is particularly misleading — beetroots are already handled by `CROP_ROOT` (which explicitly checks `Blocks.BEETROOTS`). `CORAL` and `COMPOSTER` appear to be planned-but-unimplemented categories. |
| **Fix** | Remove `CROP_BEET` (dead duplication). Either add validators for `CORAL`/`COMPOSTER` or remove them with a `// TODO` in the enum. |

### 10. `PokemonProfile.allEligible()` allocates on every call
| | |
|---|---|
| **File** | `jobs/PokemonProfile.kt` (~L50) |
| **Category** | Performance |
| **Problem** | `allEligible()` concatenates 4 lists (`combo + move + species + type`) on every invocation, creating a new list each time. This is called at least once per tick per Pokémon (from `tickPokemon()`), and often multiple times (availability checks). With 30 active workers, that's 30+ list allocations per tick, every 5 ticks. |
| **Fix** | Cache the concatenated list as a `lazy` property or compute it once at construction time. |

### 11. `getSpeedMultiplier()` has mutation side effects
| | |
|---|---|
| **File** | `utilities/WorkSpeedBoostManager.kt` L60–66 |
| **Category** | Code Quality |
| **Problem** | `getSpeedMultiplier()` mutates the `boosts` list by calling `list.removeAll { expired }`. A getter that silently modifies state is unexpected. If called from a debug display or logging path, it changes production behavior. Also, `getActiveBoostCount()` (L73) does the same removal — calling both methods causes double-removal attempts. |
| **Fix** | Extract expiry cleanup into a dedicated `pruneExpired(origin, worldTime)` method called from `registerBoost()` or a periodic tick. Make getters pure. |

### 12. `Math.random()` used instead of world random
| | |
|---|---|
| **File** | `jobs/registry/ComboJobs.kt` L136, L159, L180, L253, L267, L287 |
| **Category** | Code Quality / Reproducibility |
| **Problem** | 6 uses of `Math.random()` for loot rolls. `Math.random()` uses a global `ThreadLocalRandom`, which is non-deterministic and not seeded by the world. For Minecraft mods, convention is to use `world.random` so behavior is reproducible per-seed and compatible with replay/debug tools. |
| **Fix** | Pass `world.random` (or `Random` from the `ServerWorld`) into loot roll lambdas and use `random.nextDouble()` / `random.nextInt()`. |

### 13. `optedOutPlayers` not persisted across server restarts
| | |
|---|---|
| **File** | `jobs/PartyWorkerManager.kt` L54 |
| **Category** | Feature Gap |
| **Problem** | `optedOutPlayers` is an in-memory `mutableSetOf<UUID>()`. If a player toggles party jobs off via `/cobblecrew party toggle`, the preference is lost on server restart. Players must re-toggle every session. |
| **Fix** | Persist to a JSON file (e.g. `config/cobblecrew-party-prefs.json`) on toggle and load on server start. Or use the player's persistent data NBT (`player.persistentData`). |

### 14. Duplicate ground-item-pickup logic
| | |
|---|---|
| **File** | `jobs/WorkerDispatcher.kt` L198–237 (idle pickup), `jobs/registry/LogisticsJobs.kt` (GroundItemCollector) |
| **Category** | Code Quality / Behavior Conflict |
| **Problem** | Ground item pickup is implemented in two places: WorkerDispatcher's `tryIdlePickup()` (runs for ALL idle Pokémon) and LogisticsJobs' GroundItemCollector (a formal job). Both scan for `ItemEntity` in the area, both navigate to it, both call `discard()`. If a Pokémon is eligible for GroundItemCollector AND has no available work, both paths can compete for the same item. The idle pickup also runs for Pokémon that are eligible for GroundItemCollector but currently on cooldown, creating unpredictable behavior. |
| **Fix** | Either (a) skip idle pickup for Pokémon eligible for GroundItemCollector, or (b) remove idle pickup entirely and rely on the formal job, or (c) document that idle pickup is intentionally a superset. |

### 15. `BaseHarvester.findReachableTarget()` sorts entire target set
| | |
|---|---|
| **File** | `jobs/BaseHarvester.kt` (~L140–160) |
| **Category** | Performance |
| **Problem** | `findReachableTarget()` calls `getTargets().sortedBy { it.getSquaredDistance(origin) }` on the full target set, then iterates the sorted list checking pathfinding (up to 3 attempts). For large target sets (e.g. 200+ berry bushes), sorting is O(N log N) every tick where the harvester needs a new target. |
| **Fix** | Use `minByOrNull` with a filter, or `asSequence().filter { ... }.sortedBy { ... }.take(3)` to avoid sorting the full list. Most environmental/DSL jobs already use this pattern. |

### 16. `JobConfigManager` not thread-safe
| | |
|---|---|
| **File** | `config/JobConfigManager.kt` |
| **Category** | Thread Safety |
| **Problem** | `configs` and `defaults` are plain `mutableMapOf`. `reload()` calls `configs.clear()` then repopulates. If a worker reads a config value (via `JobConfigManager.get(name)`) during reload, it could see an empty map and fall back to defaults — not a crash, but briefly produces different behavior. The `load()` method does file I/O synchronously on whatever thread calls it. |
| **Fix** | Use `ConcurrentHashMap` or perform atomic swap (`val new = ...; configs = new`). Low risk since reload is rare (command-triggered), but worth hardening. |

### 17. `getTargets()` returns raw mutable reference
| | |
|---|---|
| **File** | `cache/CobbleCrewCacheManager.kt` L39 |
| **Category** | Encapsulation / Bug Risk |
| **Problem** | `getTargets()` returns `cache.targetsByCategory[category]` directly — the raw `MutableSet<BlockPos>`. Callers can (accidentally) mutate the set. Several jobs iterate the returned set with `.filter { ... }.minByOrNull { ... }` — if another job removes from the same set concurrently (same tick, different Pokémon in the same pasture iteration), the iterator could skip elements or throw. |
| **Fix** | Return `Set<BlockPos>` (immutable view via `set.toSet()`) or at minimum document that callers must not mutate the returned set. |

---

## LOW

### 18. `RESCAN_DISTANCE_SQ` is a local val, not configurable
| | |
|---|---|
| **File** | `jobs/PartyWorkerManager.kt` L268 |
| **Category** | Config Gap |
| **Problem** | `val RESCAN_DISTANCE_SQ = 9` is hardcoded inside `runEagerScan()`. The search radius and interval are configurable, but the rescan distance threshold is not. |
| **Fix** | Move to config or make it a `const val` companion object member for visibility. |

### 19. `pendingCloses` in InventoryUtils not thread-safe
| | |
|---|---|
| **File** | `utilities/CobbleCrewInventoryUtils.kt` |
| **Category** | Thread Safety (theoretical) |
| **Problem** | `pendingCloses` is a `mutableListOf` iterated with `.iterator()`. In practice this is single-threaded (server tick), but if any future async path adds to it, it will throw `ConcurrentModificationException`. |
| **Fix** | No immediate action needed — document single-thread assumption. |

### 20. `world as ServerWorld` casts without safe-cast
| | |
|---|---|
| **File** | `utilities/FloodFillHarvest.kt` L37, various job tick methods |
| **Category** | Defensive Code |
| **Problem** | Several places cast `world as ServerWorld` directly. Workers only run server-side, so this is always safe in practice, but a `world as? ServerWorld ?: return` pattern (already used in some places like EnvironmentalJobs L108) is more defensive. |
| **Fix** | Normalize to safe-cast pattern for consistency. Low priority. |

### 21. `floodFillHarvest` has no block-break event firing
| | |
|---|---|
| **File** | `utilities/FloodFillHarvest.kt` L43–44 |
| **Category** | Mod Compatibility |
| **Problem** | `floodFillHarvest()` calls `world.setBlockState(current, Blocks.AIR.defaultState)` directly without firing `BlockBreakEvent` or equivalent Fabric/NeoForge block break hooks. Other mods that listen for block breaks (e.g. land claims, logging mods) won't see these breaks. |
| **Fix** | Use `world.breakBlock()` or fire the platform-specific block break event. May need Architectury's `BlockEvent.BREAK` or similar. |

### 22. `structuresCache` in CacheManager is a plain nullable `Set`
| | |
|---|---|
| **File** | `cache/CobbleCrewCacheManager.kt` L22 |
| **Category** | Thread Safety (theoretical) |
| **Problem** | `structuresCache` is `var structuresCache: Set<Identifier>? = null`. While `Set` is inherently immutable once created, the `var` itself is not volatile. In theory, a non-volatile reference can be stale across threads. In practice this is always read from the server thread. |
| **Fix** | Annotate with `@Volatile` or leave as-is with a comment. |

### 23. `onBattleStartPre` swallows all exceptions silently
| | |
|---|---|
| **File** | `jobs/PartyWorkerManager.kt` L85 |
| **Category** | Error Handling |
| **Problem** | `catch (_: Exception) {}` — any error in the battle-start handler is silently swallowed. If cleanup fails (e.g. `discard()` throws), items could be lost. |
| **Fix** | At minimum log at debug level: `catch (e: Exception) { CobbleCrew.LOGGER.debug("...", e) }`. |

### 24. `selectBestAvailableJob` shuffles candidates every tick
| | |
|---|---|
| **File** | `jobs/WorkerDispatcher.kt` L168 |
| **Category** | Performance (minor) |
| **Problem** | `candidates.shuffled()` creates a new shuffled copy of the candidate list every tick for every idle Pokémon. With 4 priority tiers checked per Pokémon, that's up to 4 list copies per tick per idle worker. |
| **Fix** | Only shuffle when actually assigning (not during stickiness checks). Or shuffle once per Pokémon per dispatch cycle (cache the shuffled order for JOB_STICKINESS_TICKS). |

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 3 |
| HIGH | 5 |
| MEDIUM | 9 |
| LOW | 7 |
| **Total** | **24** |

**Top 3 priorities:**
1. Fix ScoutWorker's off-thread world access (#1) — potential server crash
2. Harden PastureCache inner maps or ensure strict single-thread access (#2)
3. Add periodic sweep for `unreachableCache` (#4) — slow memory leak on long sessions
