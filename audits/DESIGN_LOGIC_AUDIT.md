# Cobbleworkers Design/Logic Audit

Comprehensive review of all job logic, conflict patterns, and architectural issues.
Audited: All 9 registry files, 6 base classes, 5 DSL wrappers, Worker interface, WorkerDispatcher, PokemonProfile.

---

## 1. Jobs That Do Things Pointlessly / Never Stop

### 1A. Bonemeal Applicator — targets fully-grown crops
**Job:** `bonemeal_applicator` (PlacementJobs)
**Problem:** `findTarget` matches ANY `CropBlock` or `SaplingBlock` regardless of maturity. Extracts bone meal from a container, navigates to a crop that's already at max age, calls `BoneMealItem.useOnFertilizable` which does nothing — and the bone meal is already consumed (extracted from container, never returned).
**Severity:** Critical
**Fix:** Add a maturity check to `findTarget`: exclude `CropBlock` instances where `block.getAge(state) == block.maxAge`.

### 1B. Growth Accelerator — visits mature crops for nothing
**Job:** `growth_accelerator` (EnvironmentalJobs)
**Problem:** Finds ANY `BlockCategory.GROWABLE` target, navigates to it, then checks `if (state.block is CropBlock)` and applies 3 random ticks. Random ticks on a mature crop do nothing, so the Pokémon wastes its entire walk cycle. There's no maturity filter in `findGrowable()`.
**Severity:** Annoying
**Fix:** Filter out mature crops in `findGrowable()` using `CobbleworkersCropUtils.isMatureCrop()`.

### 1C. Frost Former — progressive chain converts all water to blue ice
**Job:** `frost_former` (EnvironmentalJobs)
**Problem:** Chain is Water → Ice → Packed Ice → Blue Ice. It scans for both WATER and ICE cache targets. This means it will relentlessly convert every water source in range into blue ice — destroying water features, farms, and aquatic habitats. Once a block becomes blue ice it stops, but the damage to the environment is permanent. There's no config to stop the chain at a specific tier (e.g., "just make ice, don't upgrade further").
**Severity:** Critical
**Fix:** Add a `maxTier` config option (e.g., `"ice"`, `"packed_ice"`, or `"blue_ice"`) so players can control how far the chain goes. Default to just Water → Ice.

### 1D. Bee Pollinator + Honey Harvester — infinite loop
**Job:** `bee_pollinator` + `honey_harvester` (EnvironmentalJobs + GatheringJobs)
**Problem:** Bee Pollinator increases honey level on non-full hives. Honey Harvester harvests full hives (resets to 0). If both eligible Pokémon are in the same pasture, they create an infinite cycle: Pollinator fills → Harvester empties → Pollinator fills again. Neither job has awareness of the other, so two Pokémon endlessly cycle the same beehive.
**Severity:** Annoying
**Fix:** Not necessarily broken (this IS a honey farm), but worth documenting. Could add a cooldown after harvest before the Pollinator re-targets the same hive, or let the Pollinator skip hives that were recently emptied.

---

## 2. Jobs With No Practical Exit Condition

### 2A. Stone Breaker / Igneous Miner / Deepslate Excavator / Sand Miner / Excavator
**Jobs:** All destructive gathering jobs (GatheringJobs)
**Problem:** These jobs break and collect naturally-occurring blocks within the search radius. They will strip-mine the entire area around the pasture until every block of that type is gone, then idle. There's no "quota" or "stop when container is full" check.
**Severity:** Annoying (intentional but aggressive)
**Fix:** Add an optional `maxOperations` or `stopWhenFull` config — stop harvesting when nearby containers have no remaining capacity for deposits.

### 2B. Terrain Flattener — clears all vegetation permanently
**Job:** `terrain_flattener` (GatheringJobs)
**Problem:** Removes all `BlockCategory.VEGETATION` in range. Grass, ferns, flowers — all gone. Once cleared, there's nothing left to do. The job essentially runs once and becomes permanently idle.
**Severity:** Minor
**Fix:** By nature it's a 'clean once' job. Could convert to a periodic check or just accept it as self-limiting.

### 2C. Decomposer — removes all nearby leaves
**Job:** `decomposer` (GatheringJobs)
**Problem:** Converts leaves to bone meal and removes them. Will strip all leaf blocks in range, which is destructive to tree canopies and aesthetics. Doesn't distinguish between natural leaf decay candidates and placed/persistent leaves.
**Severity:** Annoying
**Fix:** Only target non-persistent leaves (check `LeavesBlock.PERSISTENT` property).

---

## 3. Jobs That Conflict With Each Other

### 3A. Three cauldron fillers compete for the same empty cauldrons
**Jobs:** `lava_cauldron_filler`, `water_cauldron_filler`, `snow_cauldron_filler` (EnvironmentalJobs)
**Problem:** All three fillers call `CobbleworkersCauldronUtils.findClosestCauldron()` which only matches `Blocks.CAULDRON` (empty). If a pasture has Fire, Water, and Ice Pokémon, they all race for the same empty cauldrons and fill them with random fluids. No way to designate "this cauldron gets water, that one gets lava."
**Severity:** Annoying
**Fix:** Either add proximity-based fluid zoning (e.g., cauldrons near a lava source get lava), or let players mark cauldrons via adjacent block signals (e.g., cauldron on soul sand = lava).

### 3B. Crop Harvester vs Bonemeal Applicator — harvesting crops that just got bone-mealed
**Jobs:** `crop_harvester` + `bonemeal_applicator` (GatheringJobs + PlacementJobs)
**Problem:** Bonemeal Applicator targets immature crops. Crop Harvester targets mature crops. In theory they're complementary. BUT: Bonemeal Applicator has no maturity filter (1A), so it targets mature crops too, wasting bone meal on crops the Harvester is about to take. If both fire on the same tick, the Harvester sees a mature crop, starts navigating — then the bone-mealer also targets it (different priority tier, different Pokémon). No real conflict since target claiming prevents simultaneous work, but wasted resources.
**Severity:** Minor (symptom of 1A)
**Fix:** Fixing 1A's maturity check resolves this.

### 3C. Crop Sower vs Crop Harvester with replanting
**Jobs:** `crop_sower` + `crop_harvester` (PlacementJobs + GatheringJobs)
**Problem:** Crop Harvester has a `replant = true` config default — it resets crop age to 0 after harvesting (crop stays planted). Crop Sower plants seeds on empty farmland. If replant is on, Sower has nothing to do because farmland is never empty. If replant is off, Sower can replant — but it needs seeds IN a container, whereas the harvester drops seeds into a container. This is actually synergistic if configured correctly, but with default settings (replant=true), the Crop Sower is entirely redundant.
**Severity:** Minor
**Fix:** Document that Crop Sower is only useful when `crop_harvester.replant = false`.

### 3D. Obsidian Forge competes with Lava Cauldron Filler for lava
**Jobs:** `obsidian_forge` + `lava_cauldron_filler` (EnvironmentalJobs)
**Problem:** Obsidian Forge converts lava source blocks to obsidian. Lava Cauldron Filler generates lava from thin air (no lava source needed — it just fills cauldrons). Wait — actually the cauldron filler doesn't consume lava, it just fills a cauldron. But if a player is using lava for anything, Obsidian Forge will convert it all to obsidian. Not a direct conflict between the two jobs, but Obsidian Forge could destroy lava the player intended to keep.
**Severity:** Minor
**Fix:** Consider a config radius or blacklist for Obsidian Forge, or require the lava to be within a specific area.

---

## 4. Jobs That Are Effectively Useless / Don't Make Sense

### 4A. Sentry — only spawns particles at hostiles ⚠️
**Job:** `sentry` (DefenseJobs)
**Problem:** The entire `effectFn` is `spawnParticles(ELECTRIC_SPARK, ...)`. No damage, no knockback, no status effect, no alert system, no mob highlighting. The Pokémon walks up to a hostile mob and sparkles at it. The mob is completely unaffected. This is the most useless job in the system.
**Severity:** Critical
**Fix:** Give it actual functionality: apply Glowing effect (so the player can see hostiles through walls), or Slowness, or at minimum deal 1 damage. Alternatively, rename to "Spotter" and give Glowing + a chat alert.

### 4B. Growth Accelerator — 3 random ticks is near-meaningless
**Job:** `growth_accelerator` (EnvironmentalJobs)
**Problem:** Applies 3 `randomTick()` calls after navigating to a crop. Normal Minecraft applies ~3 random ticks per chunk section per game tick (20x/sec). The time spent navigating to the crop (several seconds) far exceeds the growth benefit of 3 extra random ticks. Net effect: negligible crop growth improvement.
**Severity:** Annoying
**Fix:** Either increase tick count significantly (e.g., 15–30) or switch to directly incrementing crop age by 1 per visit. The latter would be more impactful and predictable.

### 4C. Combo gathering jobs with no special behavior
**Jobs:** `demolisher`, `fortune_miner`, `silk_touch_extractor`, `vein_miner`, `tree_feller`, `fossil_hunter`, `gem_vein_finder` (ComboJobs)
**Problem:** These are all `GatheringJob` subclasses with COMBO priority. Their names promise special behavior (silk touch drops, fortune doubles, vein mining, tree felling) but they just use the default `BaseHarvester.harvest()` with no `harvestOverride`, no `toolOverride`, and no custom logic. They break one stone block and drop normal loot — identical to `stone_breaker`. The combo move requirement is harder to satisfy for the exact same result.
**Severity:** Critical
**Fix:** Each needs actual special behavior:
- `fortune_miner`: `harvestOverride` that doubles drops
- `silk_touch_extractor`: `toolOverride` with silk touch enchanted pickaxe
- `vein_miner`: `harvestOverride` that flood-fills to connected same-type blocks
- `tree_feller`: `harvestOverride` that breaks all connected logs upward
- `fossil_hunter`: `harvestOverride` with weighted fossil/archaeology loot table
- `gem_vein_finder`: `harvestOverride` with gem-weighted loot table
- `demolisher`: should target multiple block categories, not just STONE

### 4D. Aura Master — only applies Speed, not the promised multi-buff
**Job:** `aura_master` (ComboJobs)
**Problem:** Comment says "Speed + Strength + Haste + Resistance" but the implementation is a plain `SupportJob` with `statusEffect = StatusEffects.SPEED`. There's no override of `applyEffect()` to add the other three effects. It's just a harder-to-qualify Speed Booster.
**Severity:** Critical
**Fix:** Override `applyEffect()` to apply all four status effects.

### 4E. Full Restore — doesn't clear negative status effects
**Job:** `full_restore` (ComboJobs)
**Problem:** Comment says "Regeneration II + clears all negatives" but the implementation only applies Regeneration II via the default `BaseSupport.applyEffect()`. No override exists to clear negative effects.
**Severity:** Critical
**Fix:** Override `applyEffect()` to iterate `player.activeStatusEffects` and remove harmful ones.

### 4F. Banner Creator — requires Sketch (Smeargle-only move)
**Job:** `banner_creator` (ComboJobs)
**Problem:** Requires `cut` + `sketch`. Sketch is Smeargle's signature move. If Smeargle isn't in Cobblemon's species registry, or doesn't naturally learn both Cut and Sketch, this combo is impossible to activate. Even if Smeargle exists, it needs BOTH moves simultaneously in its 4-move set.
**Severity:** Minor (depending on Cobblemon species availability)
**Fix:** Replace `sketch` with a more accessible move like `slash`, `furycutter`, or add a species fallback.

---

## 5. isAvailable() Issues

### 5A. Most jobs return `true` by default — dispatcher picks unworkable jobs
**Jobs:** All GatheringJob, ProductionJob, SupportJob, DefenseJob, most Environmental custom workers
**Problem:** The Worker interface defaults `isAvailable()` to `true`. Only `BaseProcessor` and `BasePlacer` override it. The dispatcher does:
```kotlin
val available = eligible.filter { it.isAvailable(world, pastureOrigin, pokemonId) }
val pool = available.ifEmpty { eligible }
val job = pool.random()
```
Since most jobs always claim they're available, the filter passes everything through. The Pokémon gets randomly assigned a job that may have zero targets, then wastes the 5-second stickiness timer doing nothing.

**Affected job types and what happens:**
| Base Class | isAvailable | Idle behavior |
|---|---|---|
| GatheringJob (BaseHarvester) | `true` (default) | tick() calls findClosestTarget() → null → returns early. Pokémon idles. |
| ProductionJob (BaseProducer) | `true` (default) | Always eventually produces (cooldown-based), so `true` is correct. |
| SupportJob (BaseSupport) | `true` (default) | tick() finds no nearby players → returns early. Pokémon idles near pasture. |
| DefenseJob (BaseDefender) | `true` (default) | tick() scans for hostiles every 20 ticks → none → returns early. Pokémon idles. |
| ProcessingJob (BaseProcessor) | ✅ Checks for input containers | Correctly filtered. |
| PlacementJob (BasePlacer) | ✅ Checks for targets + items | Correctly filtered. |
| Environmental (FrostFormer) | ✅ Checks water/ice cache | Correctly filtered. |
| Environmental (cauldron fillers, fuelers, irrigator, etc.) | `true` (default) | tick() checks for targets → none → returns early. |

**Severity:** Critical
**Fix:** GatheringJobs should check cache for non-empty targets. SupportJobs should check for nearby players. DefenseJobs should check for nearby hostiles. Environmental workers with explicit targets should check cache availability.

### 5B. isAvailable returning true causes bad job randomization
**Problem:** When the dispatcher builds the `available` pool, jobs with proper isAvailable checks get mixed with jobs that always return true. If 3 out of 5 eligible jobs actually have work, and 2 always return true, the Pokémon has a 40% chance of picking an idle job. With many TYPE-tier eligible jobs (e.g., a Fire-type Pokémon qualifies for 5+ fire-related jobs), most of which may have no nearby targets, the hit rate drops further.
**Severity:** Critical
**Fix:** This is the same root cause as 5A. Implementing proper isAvailable checks across all base classes would make job selection dramatically more efficient.

---

## 6. Priority Tier Problems

### 6A. bestEligible() locks Pokémon into highest tier permanently
**Problem:** `PokemonProfile.bestEligible()` returns only the highest non-empty tier:
```kotlin
comboEligible.ifEmpty { moveEligible.ifEmpty { speciesEligible.ifEmpty { typeEligible } } }
```
If a Pokémon qualifies for ANY combo job, it can **never** do move/species/type jobs. Examples:
- Pokémon with Cut + Rock Smash → locked into Demolisher (combo). Can never do Healer, Guard, Ore Smelter, or any other MOVE/TYPE job even though it may qualify for dozens.
- Pokémon with Dive + Whirlpool → locked into Deep Sea Trawler. Can never fish, guard, or heal.
- A single combo match eliminates ALL other work, forever, with no fallback even if the combo job has no targets.

**Severity:** Critical
**Fix:** Options:
1. **Merge tiers with weighting** — combo jobs get higher pick probability (e.g., 60%) but don't exclude other tiers entirely.
2. **Fall through on unavailability** — if no combo job `isAvailable()`, try MOVE tier, then SPECIES, then TYPE. This requires fixing isAvailable first (see 5A).
3. **Let dispatching consider all tiers** — flatten with weighted random selection.

### 6B. Combo tier is too exclusive given isAvailable defaults
**Problem:** Combo jobs with `isAvailable() = true` will never release a Pokémon to lower tiers. Even if the Demolisher has no stone in range, bestEligible() still returns `[Demolisher]`. The Pokémon is permanently stuck trying (and failing) to do one job.
**Severity:** Critical (exacerbates 6A)
**Fix:** Same as above — must fix isAvailable checks so bestEligible can fall through when the top tier has no available work.

### 6C. Species tier is lower than Move tier — counterintuitive
**Problem:** A Wooloo qualifies for `wool_producer` via species fallback → MOVE priority (because ProductionJob defaults to MOVE). A Wooloo with Cotton Guard would qualify via move match → also MOVE priority. But if Wooloo has Tackle (matching a Guard defense job), the move job (Guard) is in the same MOVE tier as Wool Producer. The species-specific job doesn't get priority over generic move matches.
**Severity:** Minor
**Fix:** This is mostly fine since both are in MOVE tier and get randomized. But species-designated jobs could benefit from a dedicated SPECIES priority tier that sits between MOVE and TYPE.

---

## 7. Additional Design Issues

### 7A. BaseSupport health filter blocks non-healing buffs
**Job:** All SupportJobs (healer, speed_booster, strength_booster, resistance_provider, haste_provider, jump_booster, night_vision_provider, water_breather, hunger_restorer)
**Problem:** BaseSupport.tick() filters target players with:
```kotlin
.filter { it.health < it.maxHealth || !skipIfAlreadyActive }
```
Since `skipIfAlreadyActive = true` by default, this reduces to `health < maxHealth`. Speed Booster, Strength Booster, Resistance, Haste, Jump Boost, Night Vision, Water Breathing will **ONLY** be applied to players who are currently damaged. A full-health player in peaceful mode will never receive any buff.
**Severity:** Critical
**Fix:** The health check should only apply to healing-related effects (Regeneration, Saturation). Non-healing support jobs should skip the health filter entirely. Add an `open val requiresDamage: Boolean = false` to BaseSupport and only apply the health filter when true. Set it to true only for Healer and Hunger Restorer.

### 7B. ProductionJob overflow — no drop timeout
**Jobs:** All ProductionJobs (BaseProducer)
**Problem:** BaseHarvester has a 30-second overflow timeout (OVERFLOW_TIMEOUT_TICKS = 600) that drops items if a container can't be found. BaseProducer has NO such timeout. If all containers are full, the Pokémon holds items in `heldItemsByPokemon` indefinitely and never produces again. It becomes permanently stuck in deposit mode.
**Severity:** Annoying
**Fix:** Add the same overflow timeout logic from BaseHarvester to BaseProducer.

### 7C. Cauldron fillers only target empty cauldrons — no partial fill
**Jobs:** `water_cauldron_filler`, `snow_cauldron_filler` (EnvironmentalJobs)
**Problem:** `findClosestCauldron()` only matches `Blocks.CAULDRON` (empty). Water and powder snow cauldrons support levels 1-3, but partially filled cauldrons (`Blocks.WATER_CAULDRON` with level < 3) are never targeted. Only empty → full in one step. This isn't broken per se, but means partially-used cauldrons (e.g., after a player bottle-fills) are never topped up.
**Severity:** Minor
**Fix:** Also match `Blocks.WATER_CAULDRON` / `Blocks.POWDER_SNOW_CAULDRON` when their level < MAX_LEVEL.

### 7D. FishingLooter and DiveCollector require isTouchingWater — pasture Pokémon may never be in water
**Jobs:** `fishing_looter`, `dive_collector` (ProductionJobs)
**Problem:** Both check `pokemonEntity.isTouchingWater` before producing. But Pokémon in pastures are land-based entities. Unless the pasture is placed in/near water, the Pokémon will never be touching water and both jobs become permanently non-functional.
**Severity:** Annoying
**Fix:** Either remove the water check (they're magical Pokémon, they can fish from anywhere), or make it radius-based (check for water blocks near the Pokémon, not whether the entity is literally submerged).

### 7E. Scout requires dropped Map items on the ground
**Job:** `scout` (SupportJobs)
**Problem:** The Scout job scans for ItemEntity instances on the ground that are Maps. A player must physically drop blank maps on the ground near the pasture for the Scout to pick them up. This is an unusual UX — no other job requires items dropped on the ground. It should consume maps from containers like other jobs.
**Severity:** Minor
**Fix:** Add container-based map sourcing as an alternative to ground pickup.

### 7F. Combo job move requirements are extremely narrow
**Jobs:** Multiple ComboJobs
**Problem:** Combo jobs require ALL listed moves. Some combos require moves that very few or no Pokémon can learn together:
- `banner_creator`: cut + sketch (Smeargle-only, if it exists)
- `book_binder`: cut + psychic (very few Pokémon learn both)
- `candle_maker`: willowisp + stringshot (almost no overlap)
- `chain_forger`: ironhead + firespin (very narrow)
- `lantern_builder`: flashcannon + ironhead (Steel-types only, needs both)

These combos may be functionally impossible in practice, meaning they're dead code.
**Severity:** Minor per combo, Annoying in aggregate
**Fix:** Audit each combo against Cobblemon's actual learnset data. Replace impossible combos with achievable ones, or add ability/species-based alternatives.

---

## Summary by Severity

### Critical (8 issues)
| # | Issue | Impact |
|---|---|---|
| 1A | Bonemeal Applicator wastes bone meal on mature crops | Resources destroyed for nothing |
| 1C | Frost Former converts all water to blue ice with no tier limit | Destroys water features permanently |
| 4A | Sentry only spawns particles — zero gameplay effect | Completely useless job |
| 4C | 7 combo gathering jobs have no special behavior — same as basic stone breaker | Combo tier is all cost, no benefit |
| 4D | Aura Master only applies Speed, not the promised 4 buffs | False advertising, wasted combo slot |
| 4E | Full Restore doesn't clear negative effects | False advertising, wasted combo slot |
| 5A | Most jobs default isAvailable()=true — dispatcher picks idle jobs | Pokémon waste time on jobless assignments |
| 6A | bestEligible() locks Pokémon into highest tier permanently | Combo-eligible Pokémon can never do regular work |
| 7A | BaseSupport health filter blocks ALL non-healing buffs on full-health players | Speed/Strength/Resistance/etc. never applied to healthy players |

### Annoying (7 issues)
| # | Issue | Impact |
|---|---|---|
| 1B | Growth Accelerator visits mature crops for nothing | Wasted navigation cycles |
| 1D | Bee Pollinator + Honey Harvester infinite loop | Two Pokémon cycle one hive forever |
| 2A | Mining jobs strip-mine entire area with no quota | Destructive, no container-full check |
| 2C | Decomposer destroys all leaves including persistent ones | Visual destruction |
| 4B | Growth Accelerator: 3 random ticks is meaningless | Negligible benefit for full navigation cost |
| 7B | BaseProducer has no overflow timeout | Pokémon stuck holding items forever if no container space |
| 7D | FishingLooter/DiveCollector require literal water contact | Non-functional unless pasture is in water |

### Minor (7 issues)
| # | Issue | Impact |
|---|---|---|
| 2B | Terrain Flattener is run-once then idle | Self-limiting by nature |
| 3B | Bonemeal + Harvester waste (symptom of 1A) | Indirect resource waste |
| 3C | Crop Sower redundant when replant=true | Works as designed, needs documentation |
| 3D | Obsidian Forge destroys potentially wanted lava | Environmental damage |
| 4F | Banner Creator requires Sketch (Smeargle) | Potentially impossible combo |
| 7C | Cauldron fillers don't top up partial fills | Missed UX convenience |
| 7E | Scout requires ground-dropped maps | Unusual UX inconsistency |
