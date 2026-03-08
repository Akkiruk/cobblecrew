# CobbleCrew Job Reassignment — Full Implementation Plan

## Overview
- **85 job changes** total (+ 9 additional fixes from design review)
- **35 jobs removed** entirely
- **~42 move reassignments** on surviving jobs
- **~32 party-mode enables**
- **~13 behavior modifications** (code logic changes)
- **1 rename** (terrain_flattener → lawnmower)
- **1 category change** (full_restore: combo → support)

---

## Phase 1: Job Removals (35 jobs)

Remove from `register()` calls and delete the `val`/`object` declarations. Do NOT delete configs — disabled jobs stay inert.

### Files & jobs to remove:

| File | Jobs to Remove |
|------|---------------|
| `GatheringJobs.kt` | `DEEPSLATE_EXCAVATOR` (stone_breaker replaces it) |
| `ProductionJobs.kt` | `DiveCollector`, `DigSiteExcavator`, `PickupLooter` (generates items from nothing) |
| `ProcessingJobs.kt` | `FOOD_COOKER`, `GLASS_MAKER`, `BRICK_BAKER`, `CHARCOAL_BURNER`, `BONE_GRINDER`, `FLINT_KNAPPER`, `PIGMENT_PRESSER` (7 of 10 — only `ORE_SMELTER`, `PAPER_MAKER`, `COMPOSTER` survive) |
| `PlacementJobs.kt` | None removed |
| `DefenseJobs.kt` | `SENTRY`, `POISON_TRAP`, `ICE_TRAP` |
| `SupportJobs.kt` | `ScoutWorker` (generates filled maps from nothing — same "something from nothing" rule as combo producers) |
| `LogisticsJobs.kt` | `Magnetizer`, `GroundItemCollector` (entire file's jobs removed) |
| `ComboJobs.kt` | `DEMOLISHER`, `FORTUNE_MINER`, `SILK_TOUCH_EXTRACTOR`, `TREE_FELLER`, `FOSSIL_HUNTER`, `GEM_VEIN_FINDER`, `STRING_CRAFTER`, `BOOK_BINDER`, `CANDLE_MAKER`, `CHAIN_FORGER`, `LANTERN_BUILDER`, `BANNER_CREATOR`, `DEEP_SEA_TRAWLER`, `MAGMA_DIVER`, `BLAST_FURNACE` (15 of 18 — only `VEIN_MINER`, `FULL_RESTORE`, `AURA_MASTER` survive) |

### For each removal:
1. Delete the `val` declaration (or `object` declaration) and all its code
2. Remove from `register()` → `WorkerRegistry.registerAll(...)` call
3. Remove any now-unused imports

---

## Phase 2: Move Reassignments (on surviving jobs)

Change the `qualifyingMoves = setOf("old")` to `setOf("new")` in each DSL declaration.

| File | Job | Old Move(s) | New Move(s) |
|------|-----|------------|-------------|
| GatheringJobs.kt | `LOGGER` | `"cut"` | `"dualchop"` |
| GatheringJobs.kt | `BAMBOO_CHOPPER` | `"falseswipe"` | `"smartstrike"` |
| GatheringJobs.kt | `VINE_TRIMMER` | `"grassknot"` | `"breakingswipe"` |
| GatheringJobs.kt | `SWEET_BERRY_HARVESTER` | `"covet"` | `"stockpile"` |
| GatheringJobs.kt | `PUMPKIN_MELON_HARVESTER` | `"stomp"` | `"switcheroo"` |
| GatheringJobs.kt | `COCOA_HARVESTER` | `"knockoff"` | `"leechseed"` |
| GatheringJobs.kt | `STONE_BREAKER` | `"brickbreak"` | `"hammerarm"` |
| GatheringJobs.kt | `IGNEOUS_MINER` | `"stompingtantrum"` | `"explosion"` |
| GatheringJobs.kt | `EXCAVATOR` | `"mudshot"` | `"sludgewave"` |
| GatheringJobs.kt | `CLAY_DIGGER` | `"mudslap"` | `"mistyterrain"` |
| GatheringJobs.kt | `SCULK_HARVESTER` | `"shadowball"` | `"psychicterrain"` |
| GatheringJobs.kt | `ICE_MINER` | `"icepunch"` | `"icefang"` |
| GatheringJobs.kt | `FLOWER_PICKER` | `"grassyterrain"` | `"flatter"` |
| GatheringJobs.kt | `SNOW_SCRAPER` | `"icywind"` | `"snowscape"` |
| GatheringJobs.kt | `MOSS_SCRAPER` | `"scratch"` | `"leechlife"` |
| GatheringJobs.kt | `DECOMPOSER` | `"sludgebomb"` | `"perishsong"` |
| GatheringJobs.kt | `TERRAIN_FLATTENER` | `"bulldoze"` | `"razorleaf"` |
| GatheringJobs.kt | `APRICORN_HARVESTER` | `"bugbite"` | `"peck"` |
| GatheringJobs.kt | `NETHERWART_HARVESTER` | `"hex"` | `"flipturn"` |
| GatheringJobs.kt | `CROP_HARVESTER` | `"trailblaze"` | `"naturepower"` |
| GatheringJobs.kt | `ROOT_HARVESTER` | `"strength"` | `"mudsport"` |
| ProcessingJobs.kt | `ORE_SMELTER` | `"flamethrower"` | `"temperflare"` |
| PlacementJobs.kt | `TORCH_LIGHTER` | `"flash"` | `"nightslash"` |
| PlacementJobs.kt | `TREE_PLANTER` | `"seedbomb"` | `"leafstorm"` |
| DefenseJobs.kt | `GUARD` | `"crunch"` | `"furyattack"` |
| DefenseJobs.kt | `REPELLER` | `"roar"` | `"psychicnoise"` |
| DefenseJobs.kt | `FEARMONGER` | `"scaryface"` | `"poltergeist"` |
| DefenseJobs.kt | `FIRE_TRAP` | `"firefang"` | `"mysticalfire"` |
| SupportJobs.kt | `HEALER` | `"drainingkiss"` | `"healbell"` |
| SupportJobs.kt | `STRENGTH_BOOSTER` | `"swordsdance"` | `"charge"` |
| SupportJobs.kt | `RESISTANCE_PROVIDER` | `"irondefense"` | `"defensecurl"` |
| SupportJobs.kt | `NIGHT_VISION_PROVIDER` | `"laserfocus"` | `"futuresight"` |
| SupportJobs.kt | `WATER_BREATHER` | `"brine"` | `"aquaring"` |
| SupportJobs.kt | `HUNGER_RESTORER` | `"yawn"` | `"swallow"` |
| EnvironmentalJobs.kt | `FrostFormer` | `"icebeam"` | `"icespinner"` |
| EnvironmentalJobs.kt | `ObsidianForge` | `"hydropump"` | `"frostbreath"` |
| EnvironmentalJobs.kt | `LavaCauldronFiller` | `"flareblitz"` | `"heatcrash"` |
| EnvironmentalJobs.kt | `WaterCauldronFiller` | `"surf"` | `"watergun"` |
| EnvironmentalJobs.kt | `SnowCauldronFiller` | `"blizzard"` | `"hail"` |
| EnvironmentalJobs.kt | `FurnaceFueler` | `"fireblast"` | `"flameburst"` |
| EnvironmentalJobs.kt | `FireDouser` | `"waterpulse"` | `"watersport"` |
| EnvironmentalJobs.kt | `BeePollinator` | `"signalbeam"` | `"grassyglide"` |
| ComboJobs.kt | `VEIN_MINER` | `"earthquake", "crosschop"` | `"earthquake", "brickbreak"` |
| ComboJobs.kt | `FULL_RESTORE` | `"healbell", "lifedew"` | `"lifedew"` only (now single-move, `isCombo=false`) |
| ComboJobs.kt | `AURA_MASTER` | `"calmmind", "helpinghand"` | `"workup", "psychup"` |

---

## Phase 3: Job Restructuring

### 3a. `full_restore` — combo → support, single move

- Move from `ComboJobs.kt` to `SupportJobs.kt`
- Change: `category = "combo"` → `category = "support"`
- Change: `isCombo = true` → `isCombo = false`
- Change: `priority = WorkerPriority.COMBO` → `WorkerPriority.MOVE`
- Change moves: `setOf("healbell", "lifedew")` → `setOf("lifedew")`
- Remove from `ComboJobs.register()`, add to `SupportJobs.register()`
- Move the `object : SupportJob(...) { override fun applyEffect }` block

### 3b. `terrain_flattener` → `lawnmower` rename

- In `GatheringJobs.kt`: Change `name = "terrain_flattener"` to `name = "lawnmower"`
- Rename the val: `TERRAIN_FLATTENER` → `LAWNMOWER`
- Update the `register()` call reference
- This will create a new config entry; old config stays orphaned (harmless)

---

## Phase 4: Party-Mode Configuration

### Current Architecture
The mod has NO per-job pasture/party toggle. ALL jobs run on both pasture and party by default. The only filtering is via `party.blockedJobs` and `party.blockedCategories` in the global config, plus per-player `PartyJobPreferences`.

### Implementation Approach
Add a `partyEnabled` flag to `JobConfig` that defaults to `false`, and check it in `BaseJob.isAvailable()`.

### Steps:
1. Add `var partyEnabled: Boolean? = null` to `JobConfig.kt`
2. In each DSL builder's `buildDefaultConfig()`, set `partyEnabled = true` or `false`
3. In `BaseJob.isAvailable()`, add:
   ```kotlin
   if (context is JobContext.Party && config.partyEnabled != true) return false
   ```
4. Set `partyEnabled = true` in the config defaults for the 32 party-enabled jobs
5. All other jobs default to `partyEnabled = false`

### Jobs that SHOULD have `partyEnabled = true` (32 jobs):

**Gathering (5):**
- `flower_picker`, `ore_miner`, `apricorn_harvester`, `berry_harvester`, `tumblestone_harvester`

**Production (1):**
- `fishing_looter`

**Placement (2):**
- `torch_lighter`, `crop_sower`

**Defense (4):**
- `guard`, `repeller`, `fire_trap`, `fearmonger`

**Support (9):**
- `healer`, `speed_booster`, `strength_booster`, `resistance_provider`, `haste_provider`, `jump_booster`, `night_vision_provider`, `water_breather`, `hunger_restorer`

**Environmental (7):**
- `growth_accelerator`, `furnace_fueler`, `crop_irrigator`, `bee_pollinator`, `brewing_stand_fueler`, `fire_douser`, `bonemeal_applicator`

**Gathering (add 1):**
- `honey_harvester` (matches other farming harvesters that are party-enabled)

**Combo (2):**
- `vein_miner`, `aura_master`

### Jobs that remain pasture-only (`partyEnabled = false`):
All surviving jobs NOT in the above list.

---

## Phase 5: Behavior Modifications (14 items)

### 5.1 `logger` — Look through leaves for adjacent logs
**Files**: `GatheringJobs.kt`, `FloodFillHarvest.kt` (`treeHarvest()`)
**Change**: In the BFS log-scanning phase of `treeHarvest()`, when checking neighbors: if a neighbor is a `LeavesBlock`, peek through it to check if blocks adjacent to THAT leaf are logs, and if so, add those logs to the BFS queue. Add a depth limit (max 2 leaf hops) to prevent infinite loops.

### 5.2 `bamboo_chopper` — Mine second-to-lowest block
**File**: `GatheringJobs.kt` (BAMBOO_CHOPPER)
**Change**: Replace the `readyCheck` (currently checks top segment only) with logic that targets the second-lowest bamboo block (one above ground). When this block breaks, all above fall as items naturally. The `readyCheck` should find a bamboo block where the block below is NOT bamboo (ground level), so the job targets `pos.up()` — the second block. Alternatively, add a custom `harvestOverride` or adjust the target finding.

### 5.3 `stone_breaker` — Add deepslate and cobbled deepslate
**File**: `BlockCategoryValidators.kt`
**Change**: Add `Blocks.DEEPSLATE` and `Blocks.COBBLED_DEEPSLATE` to `STONE_BLOCKS` set:
```kotlin
private val STONE_BLOCKS = setOf(
    Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
    Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
)
```

### 5.4 `excavator` — Also mine grass blocks (not tall grass)
**File**: `BlockCategoryValidators.kt`
**Change**: Add `Blocks.GRASS_BLOCK` to `DIRT_BLOCKS` set:
```kotlin
private val DIRT_BLOCKS = setOf(
    Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.ROOTED_DIRT,
    Blocks.GRASS_BLOCK,
)
```

### 5.5 `ice_miner` — Work with all forms of ice including packed and blue
**File**: `BlockCategoryValidators.kt`
**Change**: Add `Blocks.BLUE_ICE` to ICE validator:
```kotlin
BlockCategory.ICE to { block, _ -> block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE },
```

### 5.6 `moss_scraper` — Work with moss carpets and azalea bushes
**File**: `BlockCategoryValidators.kt`
**Status**: **ALREADY DONE** — The MOSS category already includes `Blocks.MOSS_BLOCK`, `Blocks.MOSS_CARPET`, `Blocks.AZALEA`, `Blocks.FLOWERING_AZALEA`. No code change needed.

### 5.7 `decomposer` — Vein mine the leaves
**File**: `GatheringJobs.kt` (DECOMPOSER)
**Change**: Replace the simple `harvestOverride` (breaks one block → bone meal) with a flood-fill through connected `LeavesBlock` blocks. Each leaf broken → 1 bone meal. Cap at 64 leaves max:
```kotlin
harvestOverride = { world, pos, _ ->
    val drops = mutableListOf<ItemStack>()
    val visited = mutableSetOf(pos)
    val queue = ArrayDeque<BlockPos>()
    queue.add(pos)
    var count = 0
    while (queue.isNotEmpty() && count < 64) {
        val current = queue.removeFirst()
        val state = world.getBlockState(current)
        if (state.block is LeavesBlock || current == pos) {
            world.breakBlock(current, false)
            drops.add(ItemStack(Items.BONE_MEAL, 1))
            count++
            for (dir in Direction.entries) {
                val neighbor = current.offset(dir)
                if (neighbor !in visited && world.getBlockState(neighbor).block is LeavesBlock) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
    }
    drops
}
```

### 5.8 `terrain_flattener` → `lawnmower` rename
Already covered in Phase 3b.

### 5.9 `tree_planter` — 3-block spacing instead of 4
**File**: `PlacementJobs.kt`
**Change**: `private const val SAPLING_SPACING = 4` → `private const val SAPLING_SPACING = 3`

### 5.10 `speed_booster` — Speed 2 instead of Speed 1
**File**: `SupportJobs.kt`
**Change**: `effectAmplifier = 0` → `effectAmplifier = 1` (amplifier 1 = level II)

### 5.11 `haste_provider` — Haste 2
**File**: `SupportJobs.kt`
**Change**: `effectAmplifier = 0` → `effectAmplifier = 1`

### 5.12 `crop_irrigator` — 3x3 area
**File**: `EnvironmentalJobs.kt`
**Change**: `defaultRadius = 2` (which gives 5x5) → `defaultRadius = 1` (which gives 3x3)

### 5.13 `fishing_looter` — Drop cooldown to 45 seconds
**File**: `ProductionJobs.kt`
**Change**: In `FishingLooter`:
- Change `LootProducer("fishing_looter", 120)` → `LootProducer("fishing_looter", 45)`
- Change `cooldownSeconds = 120` → `cooldownSeconds = 45` in `buildDefaultConfig()`

### 5.14 `crop_sower` — Support modded crops/seeds
**File**: `PlacementJobs.kt`
**Change**: Currently `itemCheck` is a hardcoded set of vanilla seed items. Replace with generic detection: an item qualifies as a seed if `Block.getBlockFromItem(item)` returns a block that is an instance of `CropBlock` (or `StemBlock`). This naturally supports any modded seed that follows vanilla crop conventions (Farmer's Delight, etc.). Keep the existing vanilla seeds as a fallback `setOf()` just in case, but the primary check should be the `CropBlock` test:
```kotlin
itemCheck = { stack ->
    val block = Block.byItem(stack.item)
    block is CropBlock || block is StemBlock
}
```

---

## Phase 6: Config Schema Bump

Since `qualifyingMoves` defaults are changing for many jobs, bump `CURRENT_SCHEMA_VERSION` in `JobConfigManager.kt` from `2` to `3`. This forces existing configs to re-migrate, picking up the new default moves.

---

## Phase 7: Cleanup

1. Remove now-unused imports in each modified file
2. If `LogisticsJobs.kt` is empty after removing both jobs, keep the file with empty `register()` or remove from `WorkerRegistry`
3. Verify `WorkerRegistry.kt` calls all `register()` functions correctly
4. Keep `BlockCategory.DEEPSLATE` enum entry (configs may reference it, harmless)

---

## Execution Order

| Step | Action | Files |
|------|--------|-------|
| 1 | Add `partyEnabled` to `JobConfig.kt` | JobConfig.kt |
| 2 | Add party check to `BaseJob.isAvailable()` | BaseJob.kt |
| 3 | Bump schema version | JobConfigManager.kt |
| 4 | Remove 35 jobs (declarations + register calls) | All 9 registry files |
| 5 | Apply move reassignments | All surviving registry files |
| 6 | Restructure full_restore (combo → support) | ComboJobs.kt, SupportJobs.kt |
| 7 | Rename terrain_flattener → lawnmower | GatheringJobs.kt |
| 8 | Set `partyEnabled = true` in defaults for 32 jobs | All DSL builders + custom jobs |
| 9 | Behavior: stone_breaker gets deepslate, excavator gets grass, ice_miner gets blue ice | BlockCategoryValidators.kt |
| 10 | Behavior: logger leaf-peek, bamboo 2nd block, decomposer vein-mine | GatheringJobs.kt, FloodFillHarvest.kt |
| 11 | Behavior: tree_planter spacing, speed/haste amplifiers, irrigator radius, fishing cooldown | PlacementJobs.kt, SupportJobs.kt, EnvironmentalJobs.kt, ProductionJobs.kt |
| 12 | Cleanup unused imports | All files |
| 13 | Build + test | gradle build |

---

## Risk Assessment

- **Config migration**: Schema bump to v3 will force-reset `qualifyingMoves` in existing configs. Users who customized moves via config will lose those customizations. This is intentional — the moves are being reassigned.
- **Party mode**: Adding `partyEnabled` to JobConfig is backward-compatible. Old configs without the field will get `null`, which the `!= true` check treats as false (party blocked). Server admins can override in config.
- **Removed jobs**: Pokémon that only knew removed-job moves will become idle. This is expected.
- **Leaf-peek for logger**: Must be carefully bounded to avoid exponential BFS through large forests.

---

## Notes from User Export

### Jobs with notes requiring behavior changes:
- **logger**: Leaves next to logs should look through for other logs (1 block gap with leaves)
- **bamboo_chopper**: Mine second-to-lowest block (above falls gracefully)
- **stone_breaker**: Add deepslate and cobbled deepslate to targets
- **excavator**: Also mine grass blocks (not tall grass)
- **ice_miner**: All forms of ice including packed and blue
- **moss_scraper**: All moss-type things (already done in validators)
- **decomposer**: Vein mine the leaves
- **terrain_flattener**: Rename to lawnmower
- **tree_planter**: Spacing check 3 blocks instead of 4
- **speed_booster**: Speed 2 instead of Speed 1
- **haste_provider**: Haste 2
- **crop_irrigator**: 3x3 hydration area
- **fishing_looter**: Cooldown to 45 seconds
- **crop_sower**: Work with modded crops/seeds (needs investigation — current `itemCheck` is hardcoded vanilla seeds)

### Jobs removed with justification notes:
- **deepslate_excavator**: Stone breaker replaces this
- **dive_collector**: Remove entirely
- **food_cooker**: Remove entirely
- **glass_maker**: Remove entirely
- **brick_baker**: Remove entirely
- **charcoal_burner**: Remove entirely
- **bone_grinder**: Remove entirely
- **flint_knapper**: Remove entirely
- **pigment_presser**: Remove entirely
- **sentry**: Remove entirely
- **poison_trap**: Remove entirely
- **ice_trap**: Remove entirely
- **magnetizer**: Remove entirely
- **ground_item_collector**: Should be a basic function ALL pokemon do, not a job
- **All combo production jobs**: No jobs should generate resources from nothing
- **book_binder**: Removed — no job should produce something out of nothing
- **lantern_builder**: Removed — no jobs should generate resources from nothing

### Undecided items (user notes suggest uncertainty):
- **dig_site_excavator**: "either remove this or make it function the same as if a player was excavating + Lootr compatibility" — export shows empty moves = **removing**
- **pickup_looter**: Generates items from nothing — violates "no something from nothing" rule — **removing**
- **scout**: Generates filled maps from nothing — violates same rule — **removing**
- **crop_sower**: "make sure this works with modded crops and seeds" — needs generic CropBlock/StemBlock detection (Phase 5.14)

---

## Final Surviving Job Count

After all removals:

| Category | Before | After | Removed |
|----------|--------|-------|---------|
| Gathering | 35 | 34 | 1 (deepslate_excavator) |
| Production | 4 | 1 | 3 (dive_collector, dig_site_excavator, pickup_looter) |
| Processing | 10 | 3 | 7 |
| Placement | 4 | 4 | 0 |
| Defense | 7 | 4 | 3 (sentry, poison_trap, ice_trap) |
| Support | 10 | 10 | 1 (scout) (+1 full_restore from combo) |
| Environmental | 11 | 11 | 0 |
| Logistics | 2 | 0 | 2 |
| Combo | 18 | 2 | 15 (-1 full_restore to support) |
| **TOTAL** | **101** | **69** | **32 net** |

Note: 35 jobs removed, but full_restore moves from combo to support (net -32 from the total).
