# Cobbleworkers v2.0 — Master Plan

The single source of truth.

---

## 1. WHAT THIS MOD IS

Cobbleworkers turns Pasture Blocks into automation stations where the Pokémon you
assign — and the 4 active moves you give them — determine what work gets done.

**The core loop:**
1. Catch Pokémon with useful moves (or teach moves via TM/TR)
2. Set the 4 active moves to match the job you want
3. Build a work station: Pasture Block + relevant blocks + containers within 8 blocks
4. Assign the Pokémon — the mod auto-detects eligible jobs from the active moveset
5. Pokémon works autonomously: gathering, producing, processing, defending, buffing

**The fantasy:**
"My Scyther chops birch logs while my Excadrill breaks deepslate. My Chansey heals me
when I walk by, my Arcanine patrols for creepers, and my Magcargo smelts raw iron
without needing a furnace — all while I explore."

**Moveset as job loadout:** Pokémon know dozens of moves but only have 4 active slots.
Swapping is cheap, but while a Pokémon is in a Pasture, its 4 active moves define what
it can do. Want to switch your Machamp from quarrying to defense? Pull it out, swap
Rock Smash for Close Combat, put it back. Low friction, but intentional.

**Combo jobs cost slots:** A Fortune Miner needs both Power Gem AND Dig active — half
the moveset dedicated to one prestige job. That leaves only 2 slots for other qualifying
moves, limiting what else that Pokémon can do simultaneously.

---

## 2. DESIGN PRINCIPLES

1. **Moves are primary, type/species are fallbacks.** A Fire-type without fire moves
   shouldn't auto-qualify for fire jobs. A Normal-type that knows Flamethrower SHOULD.

2. **Niche is king.** Each job targets a specific block type or produces a specific item.
   No "harvest all crops" generalists. A Grain Reaper harvests wheat. A Root Digger
   harvests carrots and potatoes. Players need different Pokémon for different tasks.

3. **Every Pokémon has a role.** 100+ jobs means even "useless" competitive Pokémon become
   valuable workers. Luvdisc with Dive becomes a deep diver. Delibird with Present
   becomes a gift giver.

4. **Combo moves create prestige jobs.** Two qualifying moves active simultaneously
   unlock special jobs neither move provides alone. Cut + Rock Smash = Demolisher.

5. **Jobs produce real resources.** No vanity. Every job outputs items players actually
   need, or provides a visible service (defense, buffs, light).

6. **If the player can't see it working, it doesn't belong.** Jobs must have visible
   feedback: Pokémon walks to target, swings arm, particles appear, items are produced.
   Invisible "aura" effects are limited to proven patterns (Healer).

7. **Don't grief the player.** No autonomous fire placement, random cobweb scattering,
   or water flooding. Placement jobs are limited to safe, predictable actions (torches
   on dark blocks, saplings on dirt, seeds on farmland).

8. **One move = one job.** Each move qualifies for exactly ONE single-move job across
   the entire catalog (with very rare exceptions). A job can have multiple qualifying
   moves, but each of those moves points only to that job. This means knowing a move
   tells you EXACTLY what job it unlocks — no ambiguity, no OP moves. Combo jobs are
   exempt: a move can appear in its 1 dedicated single-move job plus any number of
   combo jobs (which already cost 2+ move slots).

### Eligibility Hierarchy (highest priority first)
```
1. Combo Moves  — Knows 2+ specific active moves? → Prestige combo job
2. Single Move  — Knows one qualifying active move? → Standard job
3. Ability       — Has qualifying ability (Pickup, Harvest)? → Ability-based job
4. Species       — Named in config species list? → Species job
5. Type          — Matching type? → Type-based job (broadest, weakest)
```

### Hard Constraints
- **Search radius:** 8 blocks horizontal, 5 blocks vertical from Pasture Block
- **Block scanning:** 15 blocks/tick deferred scan, 60-second rescan cycle
- **One job at a time** per Pokémon, with stickiness (keeps working until no targets)
- **Server-side only.** No client code, no rendering, no GUI
- **Yarn mappings.** World, BlockPos, Text, Identifier, PlayerEntity
- **Move access:** `pokemonEntity.pokemon.moveSet.getMoves()` — the 4 active moves
- **Move cap:** Each move → exactly 1 single-move job (combos exempt)

---

## 3. COMPLETE JOB CATALOG

### How to Read This

Each job lists:
- **Tier** = how well it fits existing infrastructure (S=exists, A=drop-in, B=one new
  base class, C=two new systems, D=major new subsystem)
- **Moves** = qualifying active moves (Showdown format, lowercase, no spaces).
  **Every move appears in exactly 1 single-move job. No sharing.**
- **Fallback** = type or species that also qualifies (broadest access)
- **Target** = BlockCategory scanned (or "none" for production/support)
- **Output** = what gets deposited in containers (or effect applied)

---

### A. GATHERING (41 jobs)

Break/harvest blocks within range, carry drops, deposit in containers.
Pattern: BaseHarvester (find cached target → navigate → arrive → break → hold → deposit).

#### Wood

| # | Job | Tier | Moves | Fallback | Target | Output |
|---|---|---|---|---|---|---|
| A1 | Overworld Logger | A | cut, furycutter | — | LOG_OVERWORLD | Oak/spruce/birch logs |
| A2 | Jungle Chopper | A | xscissor, nightslash, sacredsword | — | LOG_TROPICAL | Jungle/cherry/acacia/dark oak logs |
| A3 | Fungi Harvester | A | shadowclaw, poltergeist | Ghost type | LOG_NETHER | Crimson/warped stems |
| A4 | Bamboo Chopper | A | falseswipe, bulletpunch | Pangoro, Pancham | BAMBOO | Bamboo (top segments) |
| A5 | Sugar Cane Cutter | A | razorleaf, magicalleaf | — | SUGAR_CANE | Sugar cane (top segments) |
| A6 | Cactus Pruner | A | needlearm, pinmissile, spikecannon | Cacturne, Cacnea, Maractus | CACTUS | Cactus (top segments) |
| A7 | Vine Trimmer | A | vinewhip, powerwhip | Tangela, Tangrowth | VINE | Vine blocks |

#### Crops & Plants

| # | Job | Tier | Moves | Fallback | Target | Output |
|---|---|---|---|---|---|---|
| A8 | Grain Reaper | S | airslash, leafstorm | Grass type | CROP_GRAIN | Wheat |
| A9 | Root Digger | S | dig | Ground type | CROP_ROOT | Carrots, potatoes |
| A10 | Beet Puller | A | strength, rocksmash | — | CROP_BEET | Beetroot |
| A11 | Berry Picker | S | bugbite, naturalgift | Grass type | BERRY | Cobblemon berries |
| A12 | Apricorn Shaker | S | headbutt | Bug type | APRICORN | Cobblemon apricorns |
| A13 | Mint Harvester | S | sweetscent, grasswhistle | Fairy type | MINT | Cobblemon mints |
| A14 | Nether Wart Harvester | S | hex, curse | Ghost type | NETHERWART | Nether wart |
| A15 | Sweet Berry Harvester | A | stuffcheeks | Teddiursa, Greedent | SWEET_BERRY | Sweet berries |
| A16 | Pumpkin/Melon Harvester | A | stomp, slam | Grass type | PUMPKIN_MELON | Pumpkins, melons |
| A17 | Cocoa Harvester | A | knockoff, covet | Tropius | COCOA | Cocoa beans |
| A18 | Chorus Fruit Harvester | A | teleport, psychocut | — | CHORUS | Chorus fruit |
| A19 | Glowberry Picker | A | pluck, thief | — | CAVE_VINE | Glow berries |
| A20 | Dripleaf Harvester | A | leafblade, leafage | — | DRIPLEAF | Big dripleaf |

#### Stone & Earth

| # | Job | Tier | Moves | Fallback | Target | Output |
|---|---|---|---|---|---|---|
| A21 | Stone Breaker | A | brickbreak, smackdown | Rock type | STONE | Stone, cobblestone |
| A22 | Igneous Miner | A | drillrun, headsmash | — | IGNEOUS | Granite, andesite, diorite |
| A23 | Deepslate Excavator | A | earthpower, earthquake | — | DEEPSLATE | Deepslate, tuff, calcite |
| A24 | Excavator | A | mudshot, highhorsepower, magnitude | Ground type | DIRT | Dirt, gravel, coarse dirt (custom loot: flint, clay, nuggets) |
| A25 | Sand Miner | A | sandstorm, sandattack, sandtomb, scorchingsands | Sandshrew line, Palossand | SAND | Sand |
| A26 | Clay Digger | A | mudslap, mudbomb, shoreup | — | CLAY | Clay balls |

#### Minerals & Crystals

| # | Job | Tier | Moves | Fallback | Target | Output |
|---|---|---|---|---|---|---|
| A27 | Tumblestone Breaker | S | ironhead, smartstrike | Steel type | TUMBLESTONE | Cobblemon tumblestones |
| A28 | Amethyst Collector | S | powergem | Rock type | AMETHYST | Amethyst shards |
| A29 | Sculk Harvester | A | shadowball, spite | — | SCULK | Sculk blocks + XP |
| A30 | Ice Miner | A | icepunch, icefang, iceshard | Ice type | ICE | Ice blocks (silk-touch) |

#### Nature & Cleanup

| # | Job | Tier | Moves | Fallback | Target | Output |
|---|---|---|---|---|---|---|
| A31 | Mushroom Forager | A | stunspore, poisonpowder | Breloom, Parasect, Shiinotic | MUSHROOM | Brown/red mushrooms |
| A32 | Flower Picker | A | petalblizzard, petaldance | Comfey, Florges | FLOWER | Flowers + produces matching dyes |
| A33 | Snow Scraper | A | icywind, avalanche, hail | Ice type | SNOW | Snowballs |
| A34 | Moss Scraper | A | grassknot, gigadrain, absorb | Tangela, Tangrowth | MOSS | Moss blocks, azalea |
| A35 | Honey Extractor | S | attackorder | Combee, Vespiquen | HONEY | Honey bottles |
| A36 | Decomposer | A | phantomforce, nightshade | Ghost type | LEAVES | Bone meal (from leaves, tall grass, ferns) |
| A37 | Terrain Flattener | A | bulldoze, stompingtantrum, mudsport | Ground type | VEGETATION | Clears tall grass, ferns, dead bushes |

#### Aquatic (Pending underwater pathfinding test)

| # | Job | Tier | Moves | Fallback | Target | Output |
|---|---|---|---|---|---|---|
| A38 | Kelp Farmer | F? | dive, aquajet | Dhelmise, Dragalge | KELP | Kelp |
| A39 | Lily Pad Collector | F? | surf, waterpulse | Lotad, Ludicolo | LILY_PAD | Lily pads |
| A40 | Sea Pickle Harvester | F? | whirlpool | — | SEA_PICKLE | Sea pickles |
~~| A41 | Coral Collector | — | — | — | — | CUT (see Gap 16: no thematic move, pending aquatic) |~~

**Gathering total: 36 confirmed + 3 pending aquatic = 39**

---

### B. PRODUCTION (25 jobs)

Self-generate items on cooldown. No target blocks needed — just a container to deposit.
Pattern: BaseProducer (cooldown → generate items → hold → navigate to container → deposit).

| # | Job | Tier | Moves | Fallback Species | Output | Cooldown |
|---|---|---|---|---|---|---|
| B1 | Wool Producer | B | cottonspore, cottonguard | Wooloo, Dubwool, Mareep, Flaaffy | Wool | 120s |
| B2 | Silk Spinner | B | stringshot, spiderweb, electroweb, stickyweb | Spinarak, Ariados, Joltik, Galvantula, Snom | String | 90s |
| B3 | Slime Secretor | B | acidarmor, minimize | Goomy, Sliggoo, Goodra, Gulpin, Swalot, Ditto | Slimeballs | 120s |
| B4 | Ink Squirter | B | octazooka, waterspout | Octillery, Inkay, Malamar | Ink sacs (glow ink from Octillery) | 90s |
| B5 | Bone Shedder | B | bonerush, shadowbone | Cubone, Marowak, Mandibuzz | Bones, bone meal | 90s |
| B6 | Pearl Creator | B | shellsmash, withdraw | Clamperl, Shellder, Cloyster | Prismarine shards | 180s |
| B7 | Feather Molter | B | roost, bravebird, wingattack, fly | Any bird with flying move | Feathers | 60s |
| B8 | Scale Shedder | B | shedtail, coil | Dragonair, Dragonite, Gyarados, Milotic, Arbok | Scute, prismarine | 180s |
| B9 | Fruit Bearer | B | gravapple, appleacid | Tropius, Applin, Flapple, Appletun | Apples | 120s |
| B10 | Coin Minter | B | payday | Meowth, Persian, Perrserker | Gold nuggets | 180s |
| B11 | Gem Crafter | B | diamondstorm, meteorbeam | Sableye, Carbink, Diancie | Amethyst shards, emeralds | 180s |
| B12 | Spore Releaser | B | spore, ragepowder, sleeppowder | Paras, Parasect, Foongus, Amoonguss | Mushrooms | 90s |
| B13 | Pollen Packer | B | pollenpuff, floralhealing | Ribombee, Cutiefly, Comfey | Honey bottles | 120s |
| B14 | Gift Giver | B | present, bestow, fling | Delibird | Random loot table | 300s |
| B15 | Egg Layer | B | softboiled | Chansey, Blissey, Happiny, Exeggcute | Eggs | 120s |
| B16 | Milk Producer | B | milkdrink | Miltank, Gogoat | Milk buckets (needs empty bucket in container) | 120s |
| B17 | Electric Charger | B | charge, chargebeam, thunderbolt | Jolteon, Electrode, Magneton, Rotom | Glowstone dust | 120s |
| B18 | Wax Producer | B | defendorder, healorder | Vespiquen | Honeycombs | 120s |
| B19 | Powder Maker | B | selfdestruct, explosion, mindblown | Voltorb, Electrode, Koffing, Weezing | Gunpowder | 120s |
| B20 | Ash Collector | B | incinerate | Torkoal, Coalossal, Rolycoly, Carkol | Charcoal | 120s |
| B21 | Static Generator | B | thunderwave, discharge, electrify | Electric type | Redstone dust | 120s |
| B22 | Sap Tapper | B | leechlife, hornleech, drainpunch | Heracross, Pinsir | Slimeballs, honey (needs LOG blocks in range, doesn't break them) | 120s |
| B23 | Toxin Distiller | B | toxic†, poisonjab, poisonfang | Arbok, Toxicroak, Salazzle | Fermented spider eyes | 120s |
| B24 | Crystal Grower | B | ancientpower, rockpolish | Carbink, Boldore, Gigalith | Quartz | 180s |
| B25 | Tear Collector | B | fakeout, faketears, tearfullook | Duskull, Banette, Misdreavus | Ghast tears | 180s |

**Existing jobs migrated:** DiveLooter → "Deep Diver", FishingLootGenerator → "Fisher",
PickUpLooter → "Scavenger". These 3 already exist and become BaseProducer instances.

**Production total: 25 new + 3 existing = 28**

---

### C. PROCESSING (11 jobs)

Take items FROM a barrel (input container), transform, deposit results in a chest
(output container). Pattern: BaseProcessor (find barrel with matching input → navigate
→ extract → transform → hold result → navigate to chest → deposit).

**Player setup:** Place a barrel with raw materials near the pasture. Place a chest
for outputs. Pokémon pulls from barrel, deposits in chest.

| # | Job | Tier | Moves | Fallback | Input → Output |
|---|---|---|---|---|---|
| C1 | Ore Smelter | C | flamethrower, fireblast, magmastorm | Magcargo, Coalossal, Torkoal | Raw iron/gold/copper → ingots |
| C2 | Food Cooker | C | ember, firepunch, flamecharge | Fire type | Raw meat/fish/potato/kelp → cooked versions |
| C3 | Glass Maker | C | heatwave, overheat, mysticalfire | — | Sand → glass |
| C4 | Brick Baker | C | firespin, lavaplume | Numel, Torkoal, Slugma, Rolycoly | Clay balls → bricks |
| C5 | Charcoal Burner | C | blastburn, burnup | Fire type | Logs → charcoal |
| C6 | Paper Maker | C | slash, guillotine | — | Sugar cane → paper (3:3) |
| C7 | Bone Grinder | C | bonemerang, boneclub | Cubone, Marowak | Bone → bone meal (1:3) |
| C8 | Flint Knapper | C | karatechop, crosschop | Golem, Rhyperior | Gravel → flint (guaranteed) |
| C9 | Pigment Presser | C | hammerarm, bodypress | Pangoro, Machamp | Flowers → matching dye |
| C10 | Composter | C | stockpile, acidspray | Trubbish, Garbodor, Grimer, Muk | Compostable items → bone meal (needs composter block in range) |
| C11 | Blast Furnace ★ | C | overheat + ironhead (COMBO) | — | Raw ore → ingots 2x faster + ancient debris → netherite scrap |

**Processing total: 11**

---

### D. PLACEMENT (4 jobs)

Take items from containers, place them as blocks. Pattern: BasePlacer (find container
with item → extract → find valid placement position → navigate → place block).

Only safe, predictable placements. No random scattering.

| # | Job | Tier | Moves | Fallback | What's Placed | Placement Rule |
|---|---|---|---|---|---|---|
| D1 | Torch Lighter | C | flash, willowisp | Litwick line, Ampharos, Lanturn | Torches from container | On blocks with light level ≤ 7 |
| D2 | Tree Planter | C | ingrain, seedbomb, seedflare | Trevenant, Torterra, Celebi | Saplings from container | On dirt/grass with sky access |
| D3 | Crop Sower | C | bulletseed, leechseed | — | Seeds from container | On tilled farmland |
| D4 | Bonemeal Applicator | C | synthesis, junglehealing | Grass type | Bone meal from container | On crops, saplings, flowers |

**Placement total: 4**

---

### E. DEFENSE (7 jobs)

Interact with hostile mobs. Pattern: BaseDefender (find hostile in range → claim →
navigate → arrive → apply damage/effect → release).

Pokémon WILL take damage back. If fainted, they stop working until healed. Risk/reward.

| # | Job | Tier | Moves | Fallback | Effect on Mob |
|---|---|---|---|---|---|
| E1 | Guard | D | bite, crunch, closecombat | Growlithe, Arcanine, Lucario, Lycanroc | Melee damage |
| E2 | Sentry | D | detect, foresight, odorsleuth, meanlook | Persian, Noctowl, Watchog | Detection only (alarm particles + sound warning). No damage |
| E3 | Repeller | D | roar, whirlwind, dragontail, circlethrow | Gyarados, Salamence | Knockback + Slowness II (pushes mobs away) |
| E4 | Fearmonger | D | scaryface, glare, snarl, screech | Gyarados, Arcanine | Mobs flee the area entirely (modify AI goal to avoid zone) |
| E5 | Fire Trap | D | flamewheel, searingshot, firelash | Fire type | Sets mob on fire |
| E6 | Poison Trap | D | toxicspikes, sludgewave, venoshock | Poison type | Poison effect |
| E7 | Ice Trap | D | blizzard, freezedry, glaciate | Ice type | Extreme Slowness + Mining Fatigue |

**Defense total: 7**

---

### F. SUPPORT (12 jobs)

Apply positive effects to nearby players. Pattern: BaseSupport (find player without
effect → claim → navigate → arrive → apply status effect → release).

| # | Job | Tier | Moves | Fallback | Status Effect |
|---|---|---|---|---|---|
| F1 | Healer | S | wish, recover, moonlight, healpulse, lifedew | — | Regeneration I |
| F2 | Speed Booster | B | tailwind, agility, extremespeed | — | Speed I |
| F3 | Strength Booster | B | howl, swordsdance, helpinghand†, coaching | — | Strength I |
| F4 | Resistance Provider | B | irondefense, barrier, shelter | — | Resistance I |
| F5 | Haste Provider | B | nastyplot, calmmind, focusenergy, workup | — | Haste I |
| F6 | Jump Booster | B | bounce, highjumpkick, jumpkick | — | Jump Boost I |
| F7 | Night Vision Provider | B | miracleeye, mindreader | Noctowl, Umbreon, Luxray | Night Vision |
| F8 | Water Breather | B | aquaring, raindance | — | Water Breathing |
| F9 | Debuffer / Cleanser | B | healbell, aromatherapy, defog, safeguard | Aromatisse, Comfey | Removes all negative effects |
| F10 | Hunger Restorer | B | swallow, slackoff | Snorlax, Munchlax, Miltank | Saturation I |
| F11 | Full Restore ★ | B | healbell + aromatherapy (COMBO) | — | Regeneration II + clears all negatives |
| F12 | Aura Master ★ | B | calmmind + helpinghand (COMBO) | — | Speed I + Strength I + Haste I + Resistance I |

**Support total: 12**

> † = **Type-gated move** — only qualifies if the Pokémon also has the matching type.
> `toxic†` requires Poison type; `helpinghand†` requires Fighting type. See Section 4D.

---

### G. ENVIRONMENTAL (3 jobs)

Modify blocks in-world without breaking them. Custom implementations.

| # | Job | Tier | Moves | Fallback | Mechanic |
|---|---|---|---|---|---|
| G1 | Frost Former | B | icebeam, sheercold, auroraveil | Ice type | Water → ice → packed ice → blue ice (progressive chain, each visit advances one step, breaks at blue ice) |
| G2 | Obsidian Forge | B | hydropump, scald, aquatail, brine | Water type | Lava source → obsidian (setBlockState) → break → deposit obsidian |
| G3 | Growth Accelerator | B | growth, sunnyday, grassyterrain | Grass type | Applies random ticks to crops in range (speeds up growth without items) |

**Environmental total: 3**

---

### H. LOGISTICS (2 jobs)

Container-internal operations. Pattern: find container → navigate → modify inventory.

| # | Job | Tier | Moves | Fallback | Effect |
|---|---|---|---|---|---|
| H1 | Magnetizer | C | magnetrise, flashcannon, steelbeam, magneticflux | Magnemite line, Magnezone | Consolidates 9 nuggets → 1 ingot within containers |
| H2 | Trash Disposal | C | sludgebomb, gunkshot | — | Deletes configurable junk items from containers (rotten flesh, poisonous potato, etc.) |

**Logistics total: 2**

---

### I. COMBO JOBS (18 jobs)

Require 2+ qualifying active moves simultaneously. Highest priority tier.
Combo moves are EXEMPT from the 1-job-per-move rule — they already cost 2+ move slots.
Each component move has its own dedicated single-move job plus the combo upgrade.

#### Combo Gathering (7 jobs)

| # | Job | Tier | Required Moves | Pattern | Effect |
|---|---|---|---|---|---|
| CM1 | Demolisher | A | cut + rocksmash | BaseHarvester | Breaks ANY block type (universal gatherer) |
| CM11 | Fortune Miner | A | powergem + dig | BaseHarvester | Mining with doubled drop chance (Fortune-like loot context) |
| CM12 | Silk Touch Extractor | A | psychic + any gathering move (cut/rocksmash/dig/icebeam) | BaseHarvester | Harvests blocks as block-item form (fake silk-touch tool in loot context) |
| CM13 | Vein Miner | B | earthquake + dig | BaseHarvester + BFS | Breaks target + all connected same-type blocks (capped at configurable max) |
| CM14 | Tree Feller | B | cut + headbutt | BaseHarvester + BFS | Breaks entire tree (all connected logs) in one go |
| CM17 | Fossil Hunter | A | dig + rocksmash | BaseHarvester | Excavator with fossil-weighted loot table (bone blocks, skull items, rare treasures) |
| CM18 | Gem Vein Finder | A | dig + powergem | BaseHarvester | Excavator with gem-weighted loot table (emeralds, diamonds, lapis) |

#### Combo Production (8 jobs)

| # | Job | Tier | Required Moves | Output | Cooldown |
|---|---|---|---|---|---|
| CM3 | String Crafter | B | cut + stringshot | Arrows (3), or leads (1), or fishing rods (1) — cycles through | 120s |
| CM6 | Book Binder | B | cut + psychic | Books | 180s |
| CM7 | Candle Maker | B | willowisp + stringshot | Candles | 120s |
| CM8 | Chain Forger | B | ironhead + firespin | Chains | 120s |
| CM9 | Lantern Builder | B | flashcannon + ironhead | Lanterns | 180s |
| CM10 | Banner Creator | B | cut + sketch | Banners (random color) | 240s |
| CM15 | Deep Sea Trawler | B | dive + whirlpool | Nautilus shells, heart of the sea (very rare), tridents (extremely rare) | 300s |
| CM16 | Magma Diver | B | dive + lavaplume | Obsidian, magma cream, netherite scrap (extremely rare) | 300s |

#### Combo Processing (1 job)

Listed above as C11 (Blast Furnace).

#### Combo Support (2 jobs)

Listed above as F11 (Full Restore) and F12 (Aura Master).

**Combo total: 18** (counted within their parent categories above)

---

### Existing Jobs That Remain As Custom Implementations

These don't fit neatly into base classes and stay as individual objects:

| Job | Current File | Notes |
|---|---|---|
| Scout | Scout.kt | Async structure finding, fly-based. Unique map creation mechanic |
| Fuel Generator | FuelGenerator.kt | Puts fuel into furnaces via accessor mixin. Unique slot interaction |
| Brewing Stand Fueler | BrewingStandFuelGenerator.kt | Puts blaze powder into brewing stands via accessor mixin |
| Lava Generator | LavaGenerator.kt | Fills cauldrons with lava. Cauldron-based, unique |
| Fire Extinguisher | FireExtinguisher.kt | Kept for backward compat. Extinguishes fire blocks |
| Crop Irrigator | CropIrrigator.kt | Hydrates farmland. Kept for backward compat; Growth Accelerator is the upgrade |

---

### SUMMARY COUNTS

| Category | Count | Pattern |
|---|---|---|
| A. Gathering | 37 (+4 pending) | BaseHarvester |
| B. Production | 28 (25 new + 3 existing) | BaseProducer |
| C. Processing | 11 | BaseProcessor |
| D. Placement | 4 | BasePlacer |
| E. Defense | 7 | BaseDefender |
| F. Support | 12 | BaseSupport |
| G. Environmental | 3 | Custom |
| H. Logistics | 2 | Custom |
| Custom (existing) | 6 | Individual objects |
| **TOTAL** | **110 (+4 pending)** | |

Of these 110 jobs, 18 are combo-move jobs distributed across categories.

---

### MOVE DISTRIBUTION AUDIT

Every move in the catalog mapped to its ONE dedicated single-move job.
No move appears in more than 1 single-move job. Zero violations.
Combo appearances are listed separately and are exempt from the 1:1 rule.

**Format:** `move` → Job ID (also in combos: CM##, CM##)

#### Cutting / Slashing (16 moves)
`cut` → A1 (combos: CM1, CM3, CM6, CM10, CM12, CM14)
`furycutter` → A1
`xscissor` → A2
`nightslash` → A2
`sacredsword` → A2
`falseswipe` → A4
`bulletpunch` → A4
`razorleaf` → A5
`magicalleaf` → A5
`needlearm` → A6
`pinmissile` → A6
`spikecannon` → A6
`airslash` → A8
`leafstorm` → A8
`slash` → C6
`guillotine` → C6

#### Vine / Whip (2 moves)
`vinewhip` → A7
`powerwhip` → A7

#### Digging / Earth (18 moves)
`dig` → A9 (combos: CM11, CM12, CM13, CM17, CM18)
`mudslap` → A26
`mudbomb` → A26
`shoreup` → A26
`strength` → A10
`rocksmash` → A10 (combos: CM1, CM12, CM17)
`earthpower` → A23
`earthquake` → A23 (combo: CM13)
`drillrun` → A22
`headsmash` → A22
`mudshot` → A24
`highhorsepower` → A24
`magnitude` → A24
`sandstorm` → A25
`sandattack` → A25
`sandtomb` → A25
`scorchingsands` → A25
`bulldoze` → A37

#### Plant / Nature (17 moves)
`leafblade` → A20
`leafage` → A20
`grassknot` → A34
`gigadrain` → A34
`absorb` → A34
`sweetscent` → A13
`grasswhistle` → A13
`bugbite` → A11
`naturalgift` → A11
`stuffcheeks` → A15
`pluck` → A19
`petalblizzard` → A32
`petaldance` → A32
`stunspore` → A31
`poisonpowder` → A31
`stompingtantrum` → A37
`mudsport` → A37

#### Impact / Shaking (4 moves)
`headbutt` → A12 (combo: CM14)
`knockoff` → A17
`covet` → A17
`stomp` → A16
`slam` → A16

#### Ghost / Dark (8 moves)
`shadowclaw` → A3
`poltergeist` → A3
`phantomforce` → A36
`nightshade` → A36
`hex` → A14
`curse` → A14
`shadowball` → A29
`spite` → A29

#### Stone / Metal (4 moves)
`brickbreak` → A21
`smackdown` → A21
`ironhead` → A27 (combos: CM8, CM9, C11)
`smartstrike` → A27

#### Crystal / Gem (3 moves)
`powergem` → A28 (combos: CM11, CM18)
`ancientpower` → B24
`rockpolish` → B24

#### Ice (9 moves)
`icepunch` → A30
`icefang` → A30
`iceshard` → A30
`icywind` → A33
`avalanche` → A33
`hail` → A33
`icebeam` → G1 (combo: CM12)
`sheercold` → G1
`auroraveil` → G1

#### Water (8 moves)
`dive` → A38 (combos: CM15, CM16)
`aquajet` → A38
`surf` → A39
`waterpulse` → A39
`whirlpool` → A40 (combo: CM15)
`aquaring` → F8
`raindance` → F8
`hydropump` → G2
`scald` → G2

#### Bee / Honey (2 moves)
`attackorder` → A35
`pollenpuff` → B13

#### Fire / Heat (18 moves)
`ember` → C2
`firepunch` → C2
`flamecharge` → C2
`flamethrower` → C1
`fireblast` → C1
`magmastorm` → C1
`heatwave` → C3
`overheat` → C3 (combo: C11)
`mysticalfire` → C3
`firespin` → C4 (combo: CM8)
`lavaplume` → C4 (combo: CM16)
`blastburn` → C5
`burnup` → C5
`flash` → D1
`willowisp` → D1 (combo: CM7)
`flamewheel` → E5
`searingshot` → E5
`firelash` → E5
`incinerate` → B20

#### Fighting / Force (8 moves)
`hammerarm` → C9
`bodypress` → C9
`karatechop` → C8
`crosschop` → C8
`closecombat` → E1
`bite` → E1
`crunch` → E1
`bonemerang` → C7

#### Bone (3 moves)
`boneclub` → C7
`bonerush` → B5
`shadowbone` → B5

#### Fiber / Thread (4 moves)
`stringshot` → B2 (combos: CM3, CM7)
`spiderweb` → B2
`electroweb` → B2
`stickyweb` → B2

#### Wool / Fluff (2 moves)
`cottonspore` → B1
`cottonguard` → B1

#### Shell / Armor (2 moves)
`shellsmash` → B6
`withdraw` → B6

#### Flight / Aerial (4 moves)
`roost` → B7
`bravebird` → B7
`wingattack` → B7
`fly` → B7

#### Reptile / Scale (2 moves)
`shedtail` → B8
`coil` → B8

#### Acidic / Slime (4 moves)
`acidarmor` → B3
`minimize` → B3
`octazooka` → B4
`waterspout` → B4

#### Spore / Pollen (5 moves)
`spore` → B12
`ragepowder` → B12
`sleeppowder` → B12
`floralhealing` → B13
`pollenpuff` → B13

#### Commerce / Gift (4 moves)
`payday` → B10
`present` → B14
`bestow` → B14
`fling` → B14

#### Egg / Dairy (2 moves)
`softboiled` → B15
`milkdrink` → B16

#### Electric (6 moves)
`charge` → B17
`chargebeam` → B17
`thunderbolt` → B17
`thunderwave` → B21
`discharge` → B21
`electrify` → B21

#### Explosive / Self-Destruct (3 moves)
`selfdestruct` → B19
`explosion` → B19
`mindblown` → B19

#### Bee Command (2 moves)
`defendorder` → B18
`healorder` → B18

#### Sap / Drain (3 moves)
`leechlife` → B22
`hornleech` → B22
`drainpunch` → B22

#### Poison / Toxic (7 moves)
`toxic` → B23
`poisonjab` → B23
`poisonfang` → B23
`toxicspikes` → E6
`sludgewave` → E6
`venoshock` → E6
`sludgebomb` → H2

#### Gem Making (2 moves)
`diamondstorm` → B11
`meteorbeam` → B11

#### Tears / Fright (3 moves)
`fakeout` → B25
`faketears` → B25
`tearfullook` → B25

#### Waste / Compost (3 moves)
`stockpile` → C10
`acidspray` → C10
`gunkshot` → H2

#### Fruit / Plant Production (3 moves)
`gravapple` → B9
`appleacid` → B9
`teleport` → A18

#### Planting / Seeding (5 moves)
`ingrain` → D2
`seedbomb` → D2
`seedflare` → D2
`bulletseed` → D3
`leechseed` → D3

#### Growth / Healing (6 moves)
`synthesis` → D4
`junglehealing` → D4
`growth` → G3
`sunnyday` → G3
`grassyterrain` → G3
`aquatail` → G2

#### Scouting / Detection (4 moves)
`detect` → E2
`foresight` → E2
`odorsleuth` → E2
`meanlook` → E2

#### Intimidation (4 moves)
`scaryface` → E4
`glare` → E4
`snarl` → E4
`screech` → E4

#### Knockback (4 moves)
`roar` → E3
`whirlwind` → E3
`dragontail` → E3
`circlethrow` → E3

#### Frost / Blizzard (3 moves)
`blizzard` → E7
`freezedry` → E7
`glaciate` → E7

#### Healing (5 moves)
`wish` → F1
`recover` → F1
`moonlight` → F1
`healpulse` → F1
`lifedew` → F1

#### Cleansing / Purification (4 moves)
`healbell` → F9 (combo: F11)
`aromatherapy` → F9 (combo: F11)
`defog` → F9
`safeguard` → F9

#### Speed (3 moves)
`tailwind` → F2
`agility` → F2
`extremespeed` → F2

#### Strength / Power-Up (4 moves)
`howl` → F3
`swordsdance` → F3
`helpinghand` → F3 (combo: F12)
`coaching` → F3

#### Defense / Shield (3 moves)
`irondefense` → F4
`barrier` → F4
`shelter` → F4

#### Mind / Focus (4 moves)
`nastyplot` → F5
`calmmind` → F5 (combo: F12)
`focusenergy` → F5
`workup` → F5

#### Jumping (3 moves)
`bounce` → F6
`highjumpkick` → F6
`jumpkick` → F6

#### Perception (2 moves)
`miracleeye` → F7
`mindreader` → F7

#### Feeding / Rest (2 moves)
`swallow` → F10
`slackoff` → F10

#### Metal / Magnetic (4 moves)
`magnetrise` → H1
`flashcannon` → H1 (combo: CM9)
`steelbeam` → H1
`magneticflux` → H1

#### Misc singleton (1 move each)
`brine` → G2
`thief` → A19
`psychocut` → A18

**TOTAL: ~209 unique moves mapped. Each appears in exactly 1 single-move job.**

**Note:** Moves removed in the audit (section 4C–4D): `spikycannon` (typo), `tackle`,
`bodyslam`, `leer`, `quickattack`, `protect`, `muddywater`, `soak`, `aurasphere`,
`solarbeam`, `dazzlinggleam`, `psychic` (A41 cut). Type-gated moves (`toxic` in B23,
`helpinghand` in F3) remain listed but require matching type at runtime.

---

## 4. ARCHITECTURE

### 4.1 BlockCategory Enum (replaces JobType)

The scanner cares about BLOCK TYPES, not jobs. Multiple jobs share the same block targets.
Overworld Logger and Tree Feller both need logs. Stone Breaker and Vein Miner both need
stone. One validator per category, many jobs consume each category's cache.

```kotlin
enum class BlockCategory {
    CONTAINER,       // Chests, barrels, gilded chests
    APRICORN, BERRY, MINT, TUMBLESTONE, AMETHYST, NETHERWART,
    CROP_GRAIN, CROP_ROOT, CROP_BEET,   // Niche crop splits
    SWEET_BERRY, PUMPKIN_MELON, COCOA, CHORUS, CAVE_VINE, DRIPLEAF,
    LOG_OVERWORLD, LOG_TROPICAL, LOG_NETHER, BAMBOO, SUGAR_CANE, CACTUS,
    VINE, HONEY,
    STONE, IGNEOUS, DEEPSLATE, DIRT, SAND, CLAY,
    ICE, SNOW, SCULK, CORAL, MOSS,
    MUSHROOM, FLOWER, LEAVES, VEGETATION,
    FURNACE, BREWING_STAND, CAULDRON, COMPOSTER, SUSPICIOUS,
    WATER, LAVA, FARMLAND,
    KELP, LILY_PAD, SEA_PICKLE,
}
```

`PastureCache` stores `targetsByCategory: MutableMap<BlockCategory, MutableSet<BlockPos>>`.
`CobbleworkersCacheManager` uses BlockCategory keys. `DeferredBlockScanner` iterates
`BlockCategoryValidators.validators` (~45 entries) instead of per-job validators.

### 4.2 PokemonProfile (Eligibility Caching)

Computed ONCE when a Pokémon enters a pasture. Stores which jobs it qualifies for,
partitioned by priority tier.

```kotlin
data class PokemonProfile(
    val pokemonId: UUID,
    val moves: Set<String>,
    val types: Set<String>,
    val species: String,
    val ability: String,
    val comboEligible: List<Worker>,
    val moveEligible: List<Worker>,
    val speciesEligible: List<Worker>,
    val typeEligible: List<Worker>,
) {
    fun bestEligible(): List<Worker> =
        comboEligible.ifEmpty { moveEligible.ifEmpty { speciesEligible.ifEmpty { typeEligible } } }
}
```

**Performance:** Eliminates ~110 `shouldRun()` calls per Pokémon per tick. Profile is
rebuilt on config reload or pasture re-entry. Invalidated on `cleanup()`.

### 4.3 Worker Interface (Refactored)

```kotlin
interface Worker {
    val name: String
    val targetCategory: BlockCategory?
    val priority: WorkerPriority  // COMBO, MOVE, SPECIES, TYPE

    /** Static check — called once during profile build. Pure data, no entity. */
    fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean

    /** Dynamic check — are there targets right now? Called during job selection only. */
    fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean = true

    fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity)
    fun hasActiveState(pokemonId: UUID): Boolean = false
    fun cleanup(pokemonId: UUID) {}
}
```

### 4.4 Target-Aware Job Selection (Stickiness)

```kotlin
fun tickPokemon(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
    val pokemonId = pokemonEntity.pokemon.uuid
    val profile = getOrBuildProfile(pokemonEntity)
    val eligible = profile.bestEligible()
    if (eligible.isEmpty()) { returnToPasture(pokemonEntity, origin); return }

    val current = activeJobs[pokemonId]
    if (current != null && current in eligible) {
        // STICK with current job while it has work
        if (current.hasActiveState(pokemonId) || hasClaimedTarget(pokemonId)) {
            current.tick(world, origin, pokemonEntity)
            return
        }
    }

    // Select new job — prefer jobs with available targets
    val available = eligible.filter { it.isAvailable(world, origin, pokemonId) }
    val pool = available.ifEmpty { eligible }
    val job = pool.random()
    activeJobs[pokemonId] = job
    job.tick(world, origin, pokemonEntity)
}
```

No random bouncing. A Pokémon sticks with its job until targets run out.

### 4.5 Container I/O (Barrel = Input, Chest = Output)

**Convention:** Barrels are input containers. Chests are output containers.

- `CobbleworkersInventoryUtils.findInputContainer(world, origin, predicate)` — finds
  barrel with matching items
- `CobbleworkersInventoryUtils.extractFromContainer(world, pos, slot, amount)` — pulls
  items from barrel
- `CobbleworkersInventoryUtils.handleDepositing(...)` — existing system, deposits into
  chests (unchanged)

Players who don't use barrels: processing/logistics jobs simply don't activate. Self-
documenting: "put raw materials in barrels, get results in chests."

### 4.6 Incremental Cache Updates

When a worker breaks a block: `CobbleworkersCacheManager.removeTarget(origin, category, pos)`
When a worker places a block: `CobbleworkersCacheManager.addTarget(origin, category, pos)`

The 60-second full rescan remains as a safety net. Between rescans, the cache stays
accurate for worker-caused changes.

### 4.7 Base Classes

| Class | Generalizes | Used By |
|---|---|---|
| BaseHarvester | Existing (updated for BlockCategory) | 37+ gathering jobs |
| BaseProducer | DiveLooter pattern | 28 production jobs |
| BaseProcessor | New (barrel→transform→chest) | 11 processing jobs |
| BasePlacer | New (chest→extract→place block) | 4 placement jobs |
| BaseDefender | New (find mob→navigate→damage/effect) | 7 defense jobs |
| BaseSupport | Healer pattern | 12 support jobs |

### 4.8 DSL Job Definitions

Most jobs are 5-8 line constructor calls, not individual files:

```kotlin
// In GatheringJobs.kt — one file for all gathering jobs
val OVERWORLD_LOGGER = GatheringJob(
    name = "overworld_logger",
    targetCategory = BlockCategory.LOG_OVERWORLD,
    qualifyingMoves = setOf("cut", "furycutter"),
    particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
)
val JUNGLE_CHOPPER = GatheringJob(
    name = "jungle_chopper",
    targetCategory = BlockCategory.LOG_TROPICAL,
    qualifyingMoves = setOf("xscissor", "nightslash", "sacredsword"),
    particle = ParticleTypes.CAMPFIRE_COSY_SMOKE,
)
```

~9 registry files instead of 110+ individual object files.

### 4.9 Config Structure

```
config/cobbleworkers.json           ← AutoConfig general settings (trimmed, existing)
config/cobbleworkers/
  gathering.json                    ← Map<String, JobConfig> for all gathering jobs
  production.json                   ← 25 job configs
  processing.json                   ← 11 job configs
  placement.json                    ← 4 job configs
  defense.json                      ← 7 job configs
  support.json                      ← 12 job configs
  environmental.json                ← 3 job configs
  logistics.json                    ← 2 job configs
```

Each job's config entry (inside its category file):
```json
{
  "overworld_logger": {
    "enabled": true,
    "cooldownSeconds": 30,
    "qualifyingMoves": ["cut", "furycutter"],
    "fallbackType": "",
    "fallbackSpecies": []
  }
}
```

Default files auto-generated from DSL definitions on first launch. Server admins edit
per-category files. No 2400-line monolith. No in-game GUI needed (server-side mod).
Uses Gson (already a dependency via AutoConfig). See section 4B Gap 1 for full design.

### 4.10 Move Name Validation

At startup, after Cobblemon initialises:
```kotlin
val invalidMoves = allConfiguredMoves.filter { Moves.getByName(it) == null }
if (invalidMoves.isNotEmpty()) {
    logger.warn("[Cobbleworkers] Unknown move names: $invalidMoves")
}
```
Warns but doesn't crash. One-time O(n) check.

### 4.11 Mob Targeting (Defense)

New fields in `CobbleworkersNavigationUtils`:
```kotlin
private val pokemonToMobTarget = mutableMapOf<UUID, UUID>()
private val targetedMobs = mutableMapOf<UUID, Claim>()
```

Defense jobs use `world.getEntitiesByClass(HostileEntity::class.java, searchBox)` to find
mobs. Standard claim/release lifecycle. Pokémon navigates to mob, applies effect, releases.

Mobs will aggro back. If the Pokémon faints, the mixin already skips fainted Pokémon.
Configurable damage reduction multiplier for defending Pokémon.

### 4.12 Silk Touch via Fake Tool

For Silk Touch Extractor and Ice Miner:
```kotlin
val SILK_TOUCH_PICKAXE: ItemStack by lazy {
    val stack = ItemStack(Items.DIAMOND_PICKAXE)
    stack.enchant(Enchantments.SILK_TOUCH, 1)
    stack
}
// Added to LootContextParameterSet as LootContextParameters.TOOL
```

### 4.13 Container Overflow Protection

If a Pokémon holds items for 600+ ticks (30 seconds) without finding a container, items
are dropped on the ground and state is cleared. Prevents permanent stalling.

---

## 4B. SOLUTIONS TO KNOWN GAPS

Every issue surfaced by the codebase audit, with the chosen fix.

### Gap 1: Config System Won't Scale (HIGH)

**Problem:** AutoConfig + Gson stores everything in one `cobbleworkers.json` with one
inner class per job. Adding 90+ jobs means 90+ classes, a 2000+ line config file, and
an uneditable mess.

**Solution: Hybrid — keep AutoConfig for `general`, use data-driven JSON for jobs.**

Don't rip out AutoConfig entirely. It works fine for the handful of general settings
(searchRadius, searchHeight, blocksPerTick). The problem is per-job config. Fix that
specifically:

```kotlin
// GeneralConfig stays as AutoConfig (unchanged)
@Config(name = "cobbleworkers")
class CobbleworkersConfig : ConfigData {
    @ConfigEntry.Gui.CollapsibleObject
    var general = GeneralGroup()
    // NO per-job groups here anymore
}

// Per-job config is a single data class loaded from JSON files
data class JobConfig(
    val enabled: Boolean = true,
    val cooldownSeconds: Int = 30,
    val qualifyingMoves: List<String> = emptyList(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    // Job-specific overrides (null = use default from DSL definition)
    val replant: Boolean? = null,
    val lootTables: List<String>? = null,
    val effectDurationSeconds: Int? = null,
    val effectAmplifier: Int? = null,
)
```

**File layout:**
```
config/cobbleworkers.json          ← AutoConfig general settings (existing, trimmed)
config/cobbleworkers/
  gathering.json                   ← Map<String, JobConfig> for all gathering jobs
  production.json                  ← Map<String, JobConfig> for all production jobs
  processing.json                  ← ...
  placement.json
  defense.json
  support.json
  environmental.json
  logistics.json
```

**Loading:** At init, after AutoConfig registers, the mod scans `config/cobbleworkers/`
for JSON files. Each is deserialized as `Map<String, JobConfig>` via Gson (already a
dependency). Missing files are generated with defaults derived from the DSL job
definitions. Unknown job keys in config files are logged and ignored.

```kotlin
object JobConfigManager {
    private val configs = mutableMapOf<String, JobConfig>()
    private val configDir = FabricLoader.getInstance().configDir.resolve("cobbleworkers")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        configDir.toFile().mkdirs()
        CATEGORY_FILES.forEach { (category, defaultJobs) ->
            val file = configDir.resolve("$category.json").toFile()
            if (!file.exists()) {
                // Generate defaults from DSL registry
                file.writeText(gson.toJson(defaultJobs.associate { it.name to it.defaultConfig() }))
            }
            val loaded: Map<String, JobConfig> = gson.fromJson(file.reader(), typeToken)
            configs.putAll(loaded)
        }
    }

    fun get(jobName: String): JobConfig = configs[jobName] ?: JobConfig()
}
```

**Why this is elegant:**
- Zero changes to AutoConfig dependency or general settings
- JSON is human-readable and editable (server admins know JSON)
- One `JobConfig` data class serves ALL 110+ jobs (no per-job boilerplate)
- Defaults auto-generated from DSL definitions — zero manual config authoring
- Adding a new job = add DSL entry, done. Config file regenerates on next launch
- Job-specific fields use nullable overrides — `null` means "use DSL default"

**Migration:** Phase 0 trims `CobbleworkersConfig` to just `GeneralGroup`. Phase 1
creates `JobConfigManager`. Existing per-job config groups get mapped to the new
`JobConfig` entries in a one-time migration that reads the old `cobbleworkers.json`
and writes the new category files.

---

### Gap 2: Move Name Verification (MEDIUM)

**Problem:** ~210 move names designed on paper. Cobblemon doesn't implement every
Pokémon move. Some like `grasswhistle`, `mudbomb`, `shoreup`, `boneclub` might not
exist in the registry.

**Solution: Build-time validation script + runtime fallback.**

**Part A — Build-time audit (one-off, before Phase 2):**

Extract Cobblemon's move list from the jar at dev time using a Gradle task or script:

```kotlin
// In a test or init block during development
tasks.register("auditMoves") {
    doLast {
        val allMoves = Moves.all().map { it.name }.toSet()
        val ourMoves = JobRegistry.allJobs().flatMap { it.qualifyingMoves }.toSet()
        val missing = ourMoves - allMoves
        if (missing.isNotEmpty()) {
            println("MISSING MOVES: $missing")
            // Write to audits/MISSING_MOVES.txt
        }
    }
}
```

For any missing move, the solution is simple: **swap it for an existing alternative**
in the DSL definition. The 1:1 rule still holds — just pick a different real move that
thematically fits the same job. If no move exists, the job drops to species/type
fallback only (which is fine — many jobs already have those).

**Part B — Runtime validation (section 4.10, already in plan):**

```kotlin
// At startup, warn about unknown moves — but don't crash
val invalidMoves = allConfiguredMoves.filter { Moves.getByName(it) == null }
if (invalidMoves.isNotEmpty()) {
    logger.warn("[Cobbleworkers] Unknown moves in config (these won't match): $invalidMoves")
}
```

**Part C — Config-driven moves (the real safety net):**

Because qualifying moves live in config files, server admins can fix invalid moves
without a mod update. If Cobblemon adds `grasswhistle` in a future version, the admin
just adds it to the config. If a move gets renamed, the admin swaps it. No recompile.

```json
{
  "mint_harvester": {
    "enabled": true,
    "cooldownSeconds": 30,
    "qualifyingMoves": ["sweetscent", "grasswhistle"],
    "fallbackType": "FAIRY"
  }
}
```

If `grasswhistle` doesn't exist, that entry is simply dead weight — it'll never match
any Pokémon's moveset. The job still works via `sweetscent` or `FAIRY` type fallback.
No crash, no error, just a log warning.

---

### Gap 3: Scanner Config Cached as `val` (BUG)

**Problem:** `DeferredBlockScanner` stores config reference as `val config = ...` at
construction time. Config reloads don't take effect until server restart.

**Solution:** Change to a `get()` getter.

```kotlin
// Before (bug):
val config = CobbleworkersConfigHolder.config.general

// After (fixed):
val config get() = CobbleworkersConfigHolder.config.general
```

One-line fix. Applied in Phase 0 alongside other infrastructure changes. All other
config access in the codebase already uses `get()` — this was the lone holdout.

---

### Gap 4: Job Registration Pattern

**Problem:** `WorkerDispatcher` has a hardcoded `listOf(...)` with 22 entries. Adding
110+ entries manually is unwieldy and error-prone.

**Solution: `WorkerRegistry` with category-based auto-registration.**

```kotlin
object WorkerRegistry {
    private val _workers = mutableListOf<Worker>()
    val workers: List<Worker> get() = _workers

    fun register(worker: Worker) { _workers.add(worker) }
    fun registerAll(vararg workers: Worker) { _workers.addAll(workers) }

    /** Called from each registry file's init block */
    internal fun init() {
        // Each registry file registers its own jobs
        GatheringJobs.register()
        ProductionJobs.register()
        ProcessingJobs.register()
        PlacementJobs.register()
        DefenseJobs.register()
        SupportJobs.register()
        EnvironmentalJobs.register()
        LogisticsJobs.register()
        ComboJobs.register()
        CustomJobs.register()  // Scout, FuelGenerator, etc.

        logger.info("[Cobbleworkers] Registered ${_workers.size} jobs")
    }
}

// In GatheringJobs.kt:
object GatheringJobs {
    val OVERWORLD_LOGGER = GatheringJob(name = "overworld_logger", ...)
    val JUNGLE_CHOPPER = GatheringJob(name = "jungle_chopper", ...)
    // ...all 37 gathering jobs

    fun register() {
        WorkerRegistry.registerAll(
            OVERWORLD_LOGGER, JUNGLE_CHOPPER, FUNGI_HARVESTER, /* ... */
        )
    }
}
```

`WorkerDispatcher` changes from:
```kotlin
private val workers: List<Worker> = listOf(ApricornHarvester, ...)
```
to:
```kotlin
private val workers: List<Worker> get() = WorkerRegistry.workers
```

**Why this is elegant:**
- Each category file owns its own registrations — no central 110-line list
- Adding a job = define it + add to its category's `register()` call
- `WorkerDispatcher` doesn't change when jobs are added/removed
- Startup log confirms total count — easy to verify nothing was missed
- Category files double as browsable documentation

---

### Gap 5: BaseProducer Cooldown Granularity

**Problem:** Production jobs have cooldowns ranging from 60s (Feather Molter) to 300s
(Gift Giver, Deep Sea Trawler). Need to decide: per-job config vs tiered presets.

**Solution: Per-job config with sensible defaults from the DSL.**

Each `ProductionJob` DSL entry declares a `defaultCooldownSeconds`:

```kotlin
val FEATHER_MOLTER = ProductionJob(
    name = "feather_molter",
    qualifyingMoves = setOf("roost", "bravebird", "wingattack", "fly"),
    output = { listOf(ItemStack(Items.FEATHER, 1 + random.nextInt(3))) },
    defaultCooldownSeconds = 60,   // Fast — feathers are low value
    particle = ParticleTypes.CLOUD,
)

val GIFT_GIVER = ProductionJob(
    name = "gift_giver",
    qualifyingMoves = setOf("present", "bestow", "fling"),
    output = { lootTable("cobbleworkers:gift_box") },
    defaultCooldownSeconds = 300,  // Slow — random high-value loot
    particle = ParticleTypes.HAPPY_VILLAGER,
)
```

The config file can override any job's cooldown:
```json
{
  "feather_molter": { "cooldownSeconds": 45 },
  "gift_giver": { "cooldownSeconds": 600 }
}
```

**No tier system.** Tiers add abstraction for zero benefit. The DSL default IS the
balance decision. Config override IS the escape hatch. Two values per job, done.

---

### Gap 6: Berry Harvesting Is Special

**Problem:** `BerryHarvester` calls `berryBlockEntity.harvest()` instead of the generic
`harvestWithLoot()`. Berry blocks have a custom block entity with growth stages and a
special `.harvest()` method. Berry Picker (A11) can't be a vanilla `GatheringJob` DSL
entry.

**Solution: `harvestOverride` lambda on GatheringJob.**

```kotlin
class GatheringJob(
    // ... standard fields
    val harvestOverride: ((World, BlockPos, PokemonEntity) -> List<ItemStack>)? = null,
) : BaseHarvester() {

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        if (harvestOverride != null) {
            val drops = harvestOverride.invoke(world, targetPos, pokemonEntity)
            heldItemsByPokemon[pokemonEntity.pokemon.uuid] = drops.toMutableList()
        } else {
            harvestWithLoot(world, targetPos, pokemonEntity) { w, pos, _ ->
                w.setBlockState(pos, Blocks.AIR.defaultState)
            }
        }
    }
}

// Berry Picker uses the override:
val BERRY_PICKER = GatheringJob(
    name = "berry_picker",
    targetCategory = BlockCategory.BERRY,
    qualifyingMoves = setOf("bugbite", "naturalgift"),
    fallbackType = "GRASS",
    particle = ParticleTypes.HAPPY_VILLAGER,
    harvestOverride = { world, pos, _ ->
        val be = world.getBlockEntity(pos) as? BerryBlockEntity
        be?.harvest(world, pos, world.getBlockState(pos)) ?: emptyList()
    },
)
```

**Why this is elegant:**
- Berry Picker stays a DSL entry — no separate object file
- `harvestOverride` is null for 95% of jobs (standard `harvestWithLoot` path)
- Same pattern works for any future block with custom harvest logic (chorus, etc.)
- The lambda captures all the special behavior in 3 lines at the definition site

The same approach handles **Tumblestones** (replant after harvest):

```kotlin
val TUMBLESTONE_BREAKER = GatheringJob(
    name = "tumblestone_breaker",
    targetCategory = BlockCategory.TUMBLESTONE,
    qualifyingMoves = setOf("ironhead", "smartstrike"),
    harvestOverride = { world, pos, pokemon ->
        val state = world.getBlockState(pos)
        val drops = state.getDroppedStacks(/* standard loot params */)
        // Replant as small budding variant preserving facing
        if (JobConfigManager.get("tumblestone_breaker").replant != false) {
            val replacement = tumblestoneToSmallBud(state.block)
            world.setBlockState(pos, replacement.defaultState.with(
                Properties.FACING, state.get(Properties.FACING)))
        } else {
            world.setBlockState(pos, Blocks.AIR.defaultState)
        }
        drops
    },
)
```

---

### Gap 7: Input Container Extraction Is New

**Problem:** Current `CobbleworkersInventoryUtils` only deposits INTO containers. No
existing code reads FROM containers. Processing and Placement jobs need to pull items
from barrels.

**Solution: Mirror the deposit API with symmetric extract methods.**

```kotlin
// In CobbleworkersInventoryUtils:

/** Checks if a barrel-type container exists in range with items matching the predicate */
fun findInputContainer(
    world: World,
    origin: BlockPos,
    cache: PastureCache,
    predicate: (ItemStack) -> Boolean,
    ignoreSet: Set<BlockPos> = emptySet(),
): BlockPos? {
    return cache.getTargets(BlockCategory.CONTAINER)
        ?.filter { pos ->
            pos !in ignoreSet
            && world.getBlockState(pos).block == Blocks.BARREL  // Input = barrels only
            && world.getBlockEntity(pos) is Inventory
            && (world.getBlockEntity(pos) as Inventory).let { inv ->
                (0 until inv.size()).any { slot -> predicate(inv.getStack(slot)) }
            }
        }
        ?.minByOrNull { it.getSquaredDistance(origin) }
}

/** Extracts up to `maxAmount` of items matching predicate from a barrel */
fun extractFromContainer(
    world: World,
    pos: BlockPos,
    predicate: (ItemStack) -> Boolean,
    maxAmount: Int = 1,
): ItemStack {
    val inv = world.getBlockEntity(pos) as? Inventory ?: return ItemStack.EMPTY
    for (slot in 0 until inv.size()) {
        val stack = inv.getStack(slot)
        if (!stack.isEmpty && predicate(stack)) {
            val taken = stack.split(minOf(maxAmount, stack.count))
            inv.markDirty()
            return taken
        }
    }
    return ItemStack.EMPTY
}
```

**Container convention (final rule):**
- **Barrels** = input (processing jobs pull from here)
- **Chests/Gilded Chests** = output (all jobs deposit here)
- **Trapped Chests** = both input and output (for players who want a single container)

This is self-documenting: player places a barrel of raw iron near the pasture, a chest
next to it. Pokémon pulls from barrel, smelts, deposits ingots in chest. If there's no
barrel with matching items, the processing job simply doesn't activate — `isAvailable()`
returns false and the Pokémon picks a different eligible job.

**The flow in BaseProcessor:**

```kotlin
abstract class BaseProcessor : Worker {
    enum class Phase { IDLE, FETCHING, NAVIGATING_INPUT, EXTRACTING, PROCESSING, DEPOSITING }

    private val phases = mutableMapOf<UUID, Phase>()
    private val inputTargets = mutableMapOf<UUID, BlockPos>()

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val id = pokemonEntity.pokemon.uuid
        when (phases.getOrDefault(id, Phase.IDLE)) {
            Phase.IDLE -> {
                val barrel = findInputContainer(world, origin, cache, inputPredicate)
                    ?: return  // No barrel with matching items — skip
                inputTargets[id] = barrel
                phases[id] = Phase.NAVIGATING_INPUT
            }
            Phase.NAVIGATING_INPUT -> {
                navigateTo(pokemonEntity, inputTargets[id]!!)
                if (arrivedAt(pokemonEntity, inputTargets[id]!!)) {
                    val taken = extractFromContainer(world, inputTargets[id]!!, inputPredicate)
                    if (taken.isEmpty) { phases[id] = Phase.IDLE; return }
                    heldItems[id] = transform(taken)  // Subclass defines transform
                    phases[id] = Phase.DEPOSITING
                }
            }
            Phase.DEPOSITING -> {
                CobbleworkersInventoryUtils.handleDepositing(/* existing system */)
                if (!hasHeldItems(id)) phases[id] = Phase.IDLE
            }
        }
    }
}
```

---

### Gap 8: Chunk Loading Safety

**Problem:** No `isChunkLoaded()` guards on `getBlockState()` calls. Unloaded chunks
return air, which could cause false negatives or wasted pathfinding.

**Solution: Guard in the scanner only — the one place it matters.**

```kotlin
// In DeferredBlockScanner, wrap the per-block check:
val chunkPos = ChunkPos(blockPos)
if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) continue

// In cache access (findClosestTarget, findInputContainer):
// Already safe — if the block was cached previously and the chunk unloaded,
// the Pokémon will pathfind there and find air. The 15-second blacklist
// naturally handles this: target fails, gets blacklisted, Pokémon moves on.
// On next rescan (60s), unloaded-chunk positions won't re-enter the cache.
```

**Why only the scanner:** The scanner is the only place where iterating large block
volumes could hit chunk boundaries. Individual `getBlockState()` calls during
harvesting target blocks that were just scanned (recently loaded). The 8-block radius
makes chunk-boundary hits rare, and the existing blacklist handles false positives
gracefully. One guard line in the scanner, zero elsewhere.

---

### Gap 9: Overlapping Pasture Ranges

**Problem:** Two pastures within 16 blocks of each other share overlapping scan
volumes. Both will cache the same target blocks.

**Current behavior (already correct):** The global `targetedBlocks` map in
`CobbleworkersNavigationUtils` prevents double-harvesting. If Pasture A's Pokémon
claims a log, Pasture B's Pokémon sees it as claimed and picks another target.

**No change needed.** Just documenting that this is already handled. The one
improvement worth making:

```kotlin
// When a Pokémon harvests a block, also remove it from OTHER pastures' caches:
fun removeTargetGlobal(category: BlockCategory, pos: BlockPos) {
    pastureCaches.values.forEach { cache ->
        cache.getTargets(category)?.remove(pos)
    }
}
```

This prevents Pasture B's Pokémon from pathfinding to a block that Pasture A already
broke (before B's next rescan cycle). Small optimization, prevents wasted pathfinding.

---

### Gap 10: DeferredBlockScanner `config` Bug (Fix Detail)

**Problem:** Line ~25 of DeferredBlockScanner stores config as a `val`:
```kotlin
private val config = CobbleworkersConfigHolder.config.general
```

**Fix:** Change to property getter:
```kotlin
private val config get() = CobbleworkersConfigHolder.config.general
```

This ensures `blocksScannedPerTick`, `searchRadius`, and `searchHeight` reflect
live config values. Applied in Phase 0 step 4 (scanner update).

---

### Gap 11: `harvestWithLoot` Tool Parameter

**Problem:** `harvestWithLoot` always passes `ItemStack.EMPTY` as the tool. Silk Touch
Extractor and Fortune Miner combo jobs need enchanted tools in the loot context.

**Solution: Open up the tool parameter on BaseHarvester.**

```kotlin
// In BaseHarvester — make tool configurable:
protected open val harvestTool: ItemStack = ItemStack.EMPTY

protected fun harvestWithLoot(
    world: World,
    targetPos: BlockPos,
    pokemonEntity: PokemonEntity,
    afterHarvest: (World, BlockPos, BlockState) -> Unit,
) {
    val state = world.getBlockState(targetPos)
    val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
        .add(LootContextParameters.ORIGIN, targetPos.toCenterPos())
        .add(LootContextParameters.BLOCK_STATE, state)
        .add(LootContextParameters.TOOL, harvestTool)  // ← Was ItemStack.EMPTY
        .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)
    // ... rest unchanged
}
```

Combo jobs override `harvestTool`:

```kotlin
// In ComboJobs.kt:
val FORTUNE_MINER = GatheringJob(
    name = "fortune_miner",
    // ... combo of powergem + dig
    toolOverride = FORTUNE_PICKAXE,  // Lazy-initialized enchanted stack
)

val SILK_TOUCH_EXTRACTOR = GatheringJob(
    name = "silk_touch_extractor",
    toolOverride = SILK_TOUCH_PICKAXE,
)

// Lazy tool stacks (created once, reused forever):
private val FORTUNE_PICKAXE by lazy {
    ItemStack(Items.DIAMOND_PICKAXE).also {
        it.enchant(Enchantments.FORTUNE, 3)
    }
}
private val SILK_TOUCH_PICKAXE by lazy {
    ItemStack(Items.DIAMOND_PICKAXE).also {
        it.enchant(Enchantments.SILK_TOUCH, 1)
    }
}
```

Regular jobs don't touch `toolOverride` — it defaults to `ItemStack.EMPTY` and loot
tables behave normally (bare-hand drops, matching current behavior).

---

### Gap 12: Mod Dependency Changes

**Problem:** Moving from AutoConfig-only to AutoConfig + custom JSON might need
changed Gradle dependencies. Also: do we need NightConfig for TOML?

**Solution: Stay with JSON. No new dependencies.**

The plan originally called for TOML config files. But the mod **already depends on
Gson** (via AutoConfig's `GsonConfigSerializer`). Using JSON for per-job config means:
- Zero new dependencies
- Same serialization library for everything
- Server admins can validate files with any JSON linter
- Cobblemon itself uses JSON for data — consistent with the ecosystem

The config directory uses `.json` files, not `.toml`. The section 4.9 notation is
updated accordingly. AutoConfig stays for general settings. Cloth Config stays as a
dependency (needed for AutoConfig). No dependency additions, no dependency removals.

---

### Summary: All Gaps Resolved

| # | Gap | Solution | Phase |
|---|---|---|---|
| 1 | Config won't scale | Hybrid: AutoConfig for general + JSON files for jobs | 0-1 |
| 2 | Move name validation | Build-time audit script + runtime warn + config-driven fallback | 0 |
| 3 | Scanner `val` bug | Change to `get()` getter | 0 |
| 4 | Job registration | `WorkerRegistry` with per-category `register()` calls | 0 |
| 5 | Producer cooldowns | Per-job `defaultCooldownSeconds` in DSL, config override | 1 |
| 6 | Berry special harvest | `harvestOverride` lambda on GatheringJob | 1-2 |
| 7 | Container extraction | `findInputContainer()` + `extractFromContainer()` for barrels | 0 |
| 8 | Chunk loading | Guard in scanner only (`isChunkLoaded` check) | 0 |
| 9 | Overlapping pastures | Already handled; add `removeTargetGlobal()` optimization | 0 |
| 10 | Scanner config bug | `val` → `get()` one-liner | 0 |
| 11 | Tool parameter | `harvestTool` open property + `toolOverride` on GatheringJob | 1 |
| 12 | Mod dependencies | Stay JSON, no new deps | — |
| 13 | Missing/invalid move | `spikycannon` → `spikecannon` typo fix | 0 |
| 14 | 27 "Past" moves at risk | Cobblemon keeps most; runtime warn covers the rest | 0 |
| 15 | Overly-common moves dilute niche design | Swap worst offenders for tighter alternatives | 0 |
| 16 | Thematic mismatches | Reassign 8 moves to better-fitting jobs | 0 |
| 17 | Fragile single-move jobs | Add second qualifying move to 8 solo-move jobs | 0 |
| 18 | Legendary/Mythical-only moves | Keep as prestige; add common alternative per job | 0 |
| 19 | Enchantment API changed in 1.21 | Use `EnchantmentHelper` + registry lookup, not `stack.enchant()` | 1 |

---

## 4C. MOVE AUDIT RESULTS (Data-Driven, Feb 2026)

Full audit of every move in the catalog against Cobblemon 1.7.1's actual learnset
data (extracted from the jar, 826 unique moves across 1025 species). Also checked
against Showdown's `isNonstandard` flag to catch Gen 9 removals.

### Gap 13: Missing Move — `spikycannon` (TYPO)

**Problem:** `spikycannon` (A6 Cactus Pruner) doesn't exist in Showdown. The real
ID is `spikecannon` (no "y").

**Fix:** Rename to `spikecannon` in the DSL definition. 6 Cobblemon learners
(Cloyster, Corsola, Heracross, Omastar, Pineco, Forretress — plus Mareanie line).

### Gap 14: 27 Moves Marked `isNonstandard: Past` in Showdown

These moves existed in older generations but were removed in Gen 9. Showdown marks
them `isNonstandard: Past` or `Unobtainable`. **Cobblemon 1.7.1 still includes most
of them** in its learnsets (verified by extracting species data), because Cobblemon
targets broad Pokémon coverage, not strict Gen 9 compliance.

| Move | isNonstandard | In Cobblemon Learnsets? | Learners | Used In |
|---|---|---|---|---|
| `aromatherapy` | Past | YES | 64 | F9 Cleanser |
| `barrier` | Past | YES | 9 | F4 Resistance |
| `bestow` | Past | YES | 22 | B14 Gift Giver |
| `boneclub` | Past | YES | 3 | C7 Bone Grinder |
| `bonemerang` | Past | YES | 3 | C7 Bone Grinder |
| `burnup` | Unobtainable | YES | 5 | C5 Charcoal Burner |
| `cut` | Unobtainable | YES | 265 | A1 Overworld Logger |
| `electrify` | Past | YES | 3 | B21 Static Generator |
| `flash` | Past | YES | 310 | D1 Torch Lighter |
| `foresight` | Past | YES | 81 | E2 Sentry |
| `grasswhistle` | Past | YES | 7 | A13 Mint Harvester |
| `hail` | Past | YES | 183 | A33 Snow Scraper |
| `healorder` | Past | YES | 1 | B18 Wax Producer |
| `jumpkick` | Past | YES | 11 | F6 Jump Booster |
| `karatechop` | Past | YES | 26 | C8 Flint Knapper |
| `magnitude` | Past | YES | 10 | A24 Excavator |
| `mindblown` | Past | YES | 1 | B19 Powder Maker |
| `mindreader` | Past | YES | 17 | F7 Night Vision |
| `miracleeye` | Past | YES | 8 | F7 Night Vision |
| `mudbomb` | Past | YES | 11 | A26 Clay Digger |
| `mudsport` | Past | YES | 82 | A37 Terrain Flattener |
| `naturalgift` | Past | YES | 517 | A11 Berry Picker |
| `needlearm` | Past | YES | 4 | A6 Cactus Pruner |
| `octazooka` | Past | YES | 5 | B4 Ink Squirter |
| `odorsleuth` | Past | YES | 53 | E2 Sentry |
| `searingshot` | Past | YES | 1 | E5 Fire Trap |
| `shadowbone` | Past | YES | 1 | B5 Bone Shedder |
| `spiderweb` | Past | YES | 4 | B2 Silk Spinner |

**Verdict:** All 27 "Past" moves ARE present in Cobblemon 1.7.1 learnsets. No
immediate breakage. However, future Cobblemon updates could prune these. The
existing runtime validation (Gap 2 / section 4.10) already handles this: unknown
moves log a warning and fall through to other qualifying moves or type/species
fallback. No additional code needed — just awareness.

**Risk mitigation already in place:**
- Every job has 2+ qualifying moves (after Gap 17 fixes below)
- Config-driven moves let admins swap replacements without recompiling
- Type/species fallbacks catch remaining gaps

### Gap 15: Overly-Common Moves Dilute Niche Design

**Problem:** Design principle #3 says "Niche is king" but several move assignments
give broad access to jobs that should feel specialized. The worst offenders:

| Move | Learners | Job | Problem |
|---|---|---|---|
| `protect` | 1085 | F4 Resistance | Nearly every Pokémon qualifies |
| `toxic` | 853 | B23 Toxin Distiller | Nearly every Pokémon qualifies |
| `raindance` | 749 | F8 Water Breather | Extremely common |
| `sunnyday` | 739 | G3 Growth Accelerator | Extremely common |
| `bodyslam` | 629 | A16 Pumpkin/Melon | Extremely common; absurd for pumpkins |
| `helpinghand` | 604 | F3 Strength Booster | Extremely common |
| `naturalgift` | 517 | A11 Berry Picker | Very common (also isNonstandard: Past) |
| `tackle` | 400 | A12 Apricorn Shaker | Nearly every starter knows this |
| `leer` | 262 | E4 Fearmonger | Basic move; too broad for fear specialist |
| `dazzlinggleam` | 173 | B11 Gem Crafter | Fairy-typed light move; no gem connection |
| `quickattack` | 172 | F2 Speed Booster | Very common; dilutes "speed specialist" |
| `psychic` | 266 | A41 Coral Collector | Top-5 most common move; no coral theme |

**Solution: Replace worst offenders with tighter, more thematic alternatives.**

Changes (each move swap maintains the 1:1 rule because the old move is freed and the
new move was previously unclaimed):

| Job | Remove | Add Instead | Why Better | New Learners |
|---|---|---|---|---|
| A12 Apricorn Shaker | `tackle` | (keep `headbutt` only) | Tackle is learned by 400 Pokémon; headbutt (506) alone is already broad enough for a tree-shaking job. Removing tackle doesn't hurt availability, just removes absurd eligibility for things like Magikarp. | N/A — drop |
| A16 Pumpkin/Melon | `bodyslam` | `stomp` | Stomp (31 learners) is thematically perfect for squashing pumpkins. Body Slam at 629 learners is absurd. | 31 |
| A41 Coral Collector | `psychic` | `shelltrap` or remove job | Psychic (266) has zero coral theme. `shelltrap` learned by Turtonator only — too narrow. **Recommend cutting A41 entirely** (see Gap 16). Coral is a pending-aquatic job anyway. | — |
| B11 Gem Crafter | `dazzlinggleam` | `sparklingaria` or keep `diamondstorm` only | Dazzling Gleam is a Fairy light beam, not gem-related. If we keep the second move, `sparklingaria` (2 learners, Primarina) is thematically closer (crystalline sound → gems) but very narrow. Better: accept B11 is a prestige job with Diamond Storm only + Sableye/Carbink/Diancie species fallback. | — |
| B23 Toxin Distiller | `toxic` | ~~keep~~ `corrosivegas` or `toxicthread` | Toxic at 853 learners means every Koffing, Arbok, AND Pikachu qualifies. However, replacing it reduces reach. **Compromise:** keep `toxic` but the job ALSO requires a Poison-type Pokémon (add type-gating in addition to move check). This way 853 eligible moves are filtered down to ~200 Poison-types that actually know Toxic. | ~200 effective |
| E4 Fearmonger | `leer` | `glare` | Leer (262) is too basic. Glare (19 learners) is thematically perfect — a paralyzing stare that scares mobs. Keep `scaryface`, `snarl`, `screech` alongside. | 19 |
| F2 Speed Booster | `quickattack` | ~~remove~~ | Quick Attack (172) is too broad. `tailwind` (149), `agility` (301), `extremespeed` (15) already provide excellent coverage. Removing quickattack still leaves 3 qualifying moves with broad reach. | N/A — drop |
| F3 Strength Booster | `helpinghand` | ~~keep~~ but add type gate | Helping Hand at 604 learners is ridiculous for a strength buff. **However**, it's also in combo F12 (Aura Master). Compromise: keep `helpinghand` for the combo only; for single-move F3 qualification, gate behind Fighting type. | ~100 effective |
| F4 Resistance | `protect` | ~~remove~~ | Protect at 1085 learners = every Pokémon is a defense aura. Drop it. `irondefense` (267), `barrier` (9), `shelter` (2) remain. Iron Defense alone is plenty broad. | N/A — drop |

**Net effect:** 5 moves dropped, 2 moves swapped, 2 type-gates added. Jobs become
meaningfully more specialized without becoming inaccessible.

### Gap 16: Thematic Mismatches

**Problem:** Several moves are assigned to jobs where they don't make thematic sense.

| Move | Current Job | Issue | Recommendation |
|---|---|---|---|
| `psychic` | A41 Coral Collector | Zero connection to coral. 266 learners for a pending-aquatic job. | **Cut A41 entirely.** Coral is already in the "pending aquatic test" tier. If aquatic pathfinding works, assign `anchorshot` (6 learners) instead — it's an anchor throw, thematically close to ocean harvesting. Frees `psychic` for potential future use. |
| `solarbeam` | C3 Glass Maker | Solar Beam is a Grass-type photosynthesis move. Glass = melting sand with heat. | **Replace with `mysticalfire`** (54 learners). Mystical fire = magical fire that works nicely alongside `heatwave` and `overheat`. Or keep `solarbeam` with the justification that concentrated sunlight melts sand into glass (real-world solar furnaces do this). |
| `aurasphere` | B24 Crystal Grower | Fighting-type aura attack. No mineral/crystal connection. | **Replace with `rockpolish`** (124 learners). Rock Polish is literally polishing rocks/crystals. Thematically perfect for a crystal-growing job. Keep `ancientpower` (167 learners) alongside. |
| `absorb` | A34 Moss Scraper | Absorb drains HP. Not about moss. | **Replace with `megadrain`** (64 learners). Still a drain move but slightly less absurd. Or better: use `strengthsap` (19 learners) — draws energy from plants/nature. Keep `grassknot` and `gigadrain` (both already assigned and are fine thematically for grabbing/pulling moss). Actually — `absorb`, `gigadrain`, and `grassknot` are all "draining/grabbing vegetation" which works for ripping moss off blocks. **Keep as-is** — the "absorb nutrients from moss" reading is acceptable. |
| `muddywater` | A24 Excavator | Water-type attack, not earth/digging. | **Replace with `highhorsepower`** (119 learners). High Horsepower is a Ground-type physical attack — stomping/kicking dirt. Thematically perfect for an excavator. Keep `mudshot` alongside. Drop `magnitude` too (isNonstandard: Past, 10 learners) and replace with `landswrath` (2 learners, Zygarde — too rare) or just keep `mudshot` + `highhorsepower`. |
| `soak` | B4 Ink Squirter | Soak changes type to Water. Not ink. | **Replace with `darkpulse`** (189 learners — but too common). Better: **`nightdaze`** (2 learners, Zoroark). Too narrow though. Best compromise: keep `octazooka` (5 learners, the core thematic move) and add **`surf`** — wait, `surf` is taken by A39. Use **`muddywater`** — wait, that's taken by A24. Use **`waterspout`** (8 learners). Ink squirting is a water jet action. Waterspout = a powerful water jets. Acceptable. |
| `ragepowder` | B12 Spore Releaser | Rage Powder redirects attacks, not about releasing spores. | **Replace with `cottonspore`** — wait, taken by B1. Use **`poisonpowder`** — taken by A31. Use **`magicpowder`** (3 learners). Magic Powder has "powder" in the name. Alternatively: keep `ragepowder` since it IS literally a powder being released (the flavor text says "the user scatters irritating powder"). The thematic objection is overblown — it's a powder release action. **Keep as-is.** |
| `teleport` | A18 Chorus Fruit Harvester | Clever (chorus fruit teleports), but no harvesting action. | The Chorus→Teleport connection is a genuine game mechanic reference. Players will understand it. **Keep as-is.** |

**Final recommendations:**
- **Cut A41 (Coral Collector)** — already pending aquatic test, move `psychic` is wasted
- **Replace `solarbeam`→`mysticalfire`** in C3 (or keep with "solar furnace" justification)
- **Replace `aurasphere`→`rockpolish`** in B24
- **Replace `muddywater`→`highhorsepower`** in A24
- **Replace `soak`→`waterspout`** in B4
- **Keep** `absorb`, `ragepowder`, `teleport` as-is (thematic reads are acceptable)

### Gap 17: Fragile Single-Move Jobs

**Problem:** 13 jobs have only ONE qualifying move. If that move gets removed from
Cobblemon in a future update, or if no Pokémon the player owns knows it, the job is
completely inaccessible.

**Solution: Add a second qualifying move to each, plus ensure species/type fallback.**

| Job | Current Move(s) | Add Move | Rationale | New + Existing Learners |
|---|---|---|---|---|
| A9 Root Digger | `dig` (432) | — | 432 learners is plenty. No fragility risk. **Keep as-is.** | 432 |
| A15 Sweet Berry | `stuffcheeks` (7) | `crunch` — wait, taken by E1. Use `bite` — taken. Use **`stockpile`** — taken by C10. Use **`munch` — doesn't exist.** Use species fallback: add Ursaring, Snorlax, Munchlax, Greedent to species list (already has Teddiursa, Greedent). Actually `stuffcheeks` at 7 learners + Teddiursa/Greedent species = fine for a niche berry job. **Keep as-is** — niche by design. | 7 + species |
| A20 Dripleaf | `leafblade` (33) | Add **`leafage`** (26 learners). Leafage = small leaf attack, fits "cutting a dripleaf." | 33 + 26 = ~59 |
| A28 Amethyst | `powergem` (81) | — | 81 learners is plenty. **Keep as-is.** | 81 |
| A35 Honey Extractor | `attackorder` (1, Vespiquen only) | Add species fallback: **Combee, Ribombee, Cutiefly** (already listed). The job is intentionally Vespiquen-exclusive via moves. Species fallback provides access via Bee Pokémon. **Keep as-is** — prestige design. | 1 + species |
| A40 Sea Pickle | `whirlpool` (181) | — | 181 learners. Pending aquatic test anyway. **Keep as-is.** | 181 |
| B3 Slime Secretor | `acidarmor` (42) | Add **`minimize`** (47 learners). Minimize = shrinking/compressing body, thematically fits slime contraction. | 42 + 47 = ~89 |
| B9 Fruit Bearer | `gravapple` (1, Flapple only) | Add **`appleacid`** (1 learner, Appletun). Both are apple-themed moves from the Applin line. Also add species: Tropius, Applin, Flapple, Appletun. | 2 + species |
| B10 Coin Minter | `payday` (58) | — | 58 learners + Meowth/Persian species. Fine. **Keep as-is.** | 58 + species |
| B15 Egg Layer | `softboiled` (9) | Add **`wish`** — wait, taken by F1. Add species: Chansey, Blissey, Happiny, Exeggcute (already listed). 9 learners + species is fine for a niche production job. **Keep as-is.** | 9 + species |
| B16 Milk Producer | `milkdrink` (3) | Add species: Miltank, Gogoat, Skiddo (already listed). 3 move learners = the 3 species. This is an intentionally narrow job. **Keep as-is.** | 3 |
| B20 Ash Collector | `incinerate` (186) | — | 186 learners. Fine. **Keep as-is.** | 186 |

**Summary:** Only 2 jobs actually need fixes (A20 + B3). The rest are either well-
covered (81+ learners) or intentionally narrow prestige jobs with species fallbacks.

### Gap 18: Legendary/Mythical-Only Moves

**Problem:** Several moves are exclusive to Legendary/Mythical Pokémon, which most
players will never assign to a Pasture:

| Move | Learners | Species | Job |
|---|---|---|---|
| `diamondstorm` | 1 | Diancie | B11 Gem Crafter |
| `magmastorm` | 1 | Heatran | C1 Ore Smelter |
| `mindblown` | 1 | Blacephalon | B19 Powder Maker |
| `searingshot` | 1 | Victini | E5 Fire Trap |
| `glaciate` | 2 | Kyurem, Victini | E7 Ice Trap |
| `seedflare` | 2 | Shaymin | D2 Tree Planter |

**Verdict:** These moves are cherries on top, not load-bearing. Every affected job
already has common qualifying moves:

- **C1 Ore Smelter**: `flamethrower` (227), `fireblast` (203) — fine without `magmastorm`
- **B19 Powder Maker**: `selfdestruct` (107), `explosion` (101) — fine without `mindblown`
- **E5 Fire Trap**: `flamewheel` (75), `firelash` (12) — fine without `searingshot`
- **E7 Ice Trap**: `blizzard` (283), `freezedry` (31) — fine without `glaciate`
- **D2 Tree Planter**: `ingrain` (51), `seedbomb` (206) — fine without `seedflare`
- **B11 Gem Crafter**: `diamondstorm` is the ONLY move after dropping `dazzlinggleam` (Gap 15). Add species fallback (Sableye, Carbink, Diancie — already listed) + add **`rockpolish`** — wait, that's going to B24. Use **`meteorbeam`** (34 learners). Meteor Beam is a Rock-type special attack from a charging gem — thematically perfect for gem crafting.

**No moves need removing.** Legendary moves stay as prestige qualifiers. The fix is
ensuring each job has non-legendary common moves too (already true for all except B11,
fixed by adding `meteorbeam`).

### Gap 19: Enchantment API Changed in 1.21

**Problem:** The plan's Silk Touch / Fortune tool creation code uses:
```kotlin
stack.enchant(Enchantments.SILK_TOUCH, 1)
```
This API was removed in Minecraft 1.21. Enchantments are now registry-based, accessed
via `RegistryKey` and applied through `EnchantmentHelper`.

**Solution:** Use the 1.21 enchantment API:

```kotlin
private val SILK_TOUCH_PICKAXE by lazy {
    val stack = ItemStack(Items.DIAMOND_PICKAXE)
    val server = /* get server reference */
    val registry = server.registryManager.getOrThrow(RegistryKeys.ENCHANTMENT)
    val silkTouch = registry.getOrThrow(Enchantments.SILK_TOUCH)
    stack.addEnchantment(silkTouch, 1)
    stack
}
```

Or using `EnchantmentHelper.apply()`:
```kotlin
private fun createEnchantedTool(enchantmentKey: RegistryKey<Enchantment>, level: Int): ItemStack {
    val stack = ItemStack(Items.DIAMOND_PICKAXE)
    // Deferred until first use when server is available
    return stack
}
```

**Note:** The lazy initialization means the tool stack can't be created at mod init
(no server yet). Either:
1. Create it on first use in a job's `tick()` (when `world.server` is available), or
2. Store as `var` and initialize in a server-started event callback.

Option 1 is simpler and matches the existing `by lazy` pattern — just needs the
`World` parameter threaded through.

---

## 4D. RECOMMENDED MOVE SWAPS (Complete Changelog)

All move changes from Gaps 13–18 consolidated. Apply these to the Section 3 tables
and the Move Distribution Audit.

### Moves to ADD

| Move | Job | Replaces | Learners |
|---|---|---|---|
| `spikecannon` | A6 Cactus Pruner | `spikycannon` (typo fix) | 6 |
| `stomp` | A16 Pumpkin/Melon | `bodyslam` (too common) | 31 |
| `glare` | E4 Fearmonger | `leer` (too common) | 19 |
| `highhorsepower` | A24 Excavator | `muddywater` (wrong type) | 119 |
| `waterspout` | B4 Ink Squirter | `soak` (wrong theme) | 8 |
| `rockpolish` | B24 Crystal Grower | `aurasphere` (wrong type) | 124 |
| `mysticalfire` | C3 Glass Maker | `solarbeam` (wrong type) | 54 |
| `meteorbeam` | B11 Gem Crafter | `dazzlinggleam` (wrong theme) | 34 |
| `leafage` | A20 Dripleaf | (new addition, not a swap) | 26 |
| `minimize` | B3 Slime Secretor | (new addition, not a swap) | 47 |
| `appleacid` | B9 Fruit Bearer | (new addition, not a swap) | 1 |

### Moves to REMOVE (freed for future use)

| Move | Was In | Learners | Why Removed |
|---|---|---|---|
| `spikycannon` | A6 | 0 | Doesn't exist (typo) |
| `bodyslam` | A16 | 629 | Way too common for pumpkin harvesting |
| `tackle` | A12 | 400 | Way too common for apricorn shaking |
| `leer` | E4 | 262 | Way too common for fear specialist |
| `quickattack` | F2 | 172 | Way too common for speed specialist |
| `protect` | F4 | 1085 | Nearly every Pokémon qualifies |
| `muddywater` | A24 | 108 | Water-type move on an earth/dirt job |
| `soak` | B4 | 45 | Type-change move, no ink theme |
| `aurasphere` | B24 | 52 | Fighting move, no crystal theme |
| `solarbeam` | C3 | 333 | Grass move, not fire/heat |
| `dazzlinggleam` | B11 | 173 | Fairy light, not gem crafting |
| `psychic` | A41 | 266 | No coral theme; A41 cut entirely |

### Jobs to CUT

| Job | Reason |
|---|---|
| A41 Coral Collector | Already pending aquatic test. Only qualifying move (`psychic`) has zero coral theme and is the 3rd-most-common move in the game. If aquatic pathfinding passes, re-add with `anchorshot` or `shelltrap`. |

### Type-Gate Additions (move qualifies ONLY if Pokémon also has matching type)

| Job | Move | Required Type | Effect |
|---|---|---|---|
| B23 Toxin Distiller | `toxic` | Poison | Filters 853→~200 effective qualifiers |
| F3 Strength Booster | `helpinghand` | Fighting | Filters 604→~100 effective qualifiers |

These type-gates are implemented in `isEligible()`:
```kotlin
val TOXIN_DISTILLER = ProductionJob(
    name = "toxin_distiller",
    qualifyingMoves = setOf("poisonjab", "poisonfang"),  // Always qualify
    typeGatedMoves = mapOf("toxic" to "POISON"),         // Only if Poison-type
    // ...
)
```

This adds a new field to the DSL: `typeGatedMoves: Map<String, String>`. A move in
this map only qualifies the Pokémon if it ALSO has the specified type. Moves in the
regular `qualifyingMoves` set always qualify regardless of type. Simple, clean, and
solves the "too common" problem without removing moves entirely.

### Updated Job Count

Original: 110 confirmed + 4 pending = 114
After cutting A41: **109 confirmed + 3 pending aquatic = 112**

---

## 5. IMPLEMENTATION PHASES

### Phase 0 — Infrastructure (no new jobs)

Refactor core systems. All existing jobs continue working.

1. Replace `JobType` with `BlockCategory` enum
2. Update `PastureCache` and `CobbleworkersCacheManager` to use BlockCategory
3. Create `BlockCategoryValidators` registry
4. Update `DeferredBlockScanner` for category validators + fix `val→get()` bug + add `isChunkLoaded` guard
5. Migrate existing jobs to declare `targetCategory`
6. Create `PokemonProfile` and eligibility caching in `WorkerDispatcher`
7. Update `Worker` interface: add `isEligible()`, `isAvailable()`, `priority`
8. Implement target-aware job selection with stickiness
9. Add `removeTarget()` + `removeTargetGlobal()` / incremental cache to `CobbleworkersCacheManager`
10. Add `findInputContainer()`, `extractFromContainer()` to `CobbleworkersInventoryUtils`
11. Add mob targeting to `CobbleworkersNavigationUtils`
12. Create `WorkerRegistry` — replace hardcoded list in `WorkerDispatcher`
13. Move name validation at startup (`CobbleworkersMoveUtils`)
14. Container overflow drop-timeout
15. Open up `harvestTool` parameter on BaseHarvester (default `ItemStack.EMPTY`)
16. Apply move audit fixes: typo fix (`spikycannon`→`spikecannon`), remove A41
17. Add `typeGatedMoves` field to Worker interface for type-conditional move matching

**Verify:** Build, deploy, all existing jobs work identically.

### Phase 1 — Base Classes + DSL + Config

1. Trim `CobbleworkersConfig` to GeneralGroup only
2. Create `JobConfig` data class + `JobConfigManager` (loads/generates per-category JSON)
3. Migrate existing per-job config to new JSON files (one-time conversion)
4. Create BaseProducer (generalize DiveLooter) with per-job `defaultCooldownSeconds`
5. Create BaseProcessor (barrel → transform → chest) with phased state machine
6. Create BasePlacer (chest → extract → place)
7. Create BaseDefender (find mob → navigate → effect)
8. Create BaseSupport (generalize Healer)
9. Create DSL builders: GatheringJob (with `harvestOverride` + `toolOverride`), ProductionJob, ProcessingJob, etc.
10. Migrate existing jobs to new base classes + DSL where possible
11. Run move name audit against Cobblemon's `Moves` registry — fix any invalid names

**Verify:** Build, deploy, existing jobs work via new base classes.

### Phase 2 — Gathering Jobs (37 jobs, ~24 new)

13 existing jobs get move-check eligibility. Split broad jobs into niche variants.
Add ~24 new gathering jobs via GatheringJob DSL. Each is 5-8 lines + a BlockCategory
validator if the category is new.

**Running total after Phase 2: ~50 jobs**

### Phase 3 — Production Jobs (28 jobs, ~25 new)

BaseProducer already built. Each production job is a 5-line DSL definition.
Migrate DiveLooter, FishingLootGenerator, PickUpLooter to BaseProducer.

**Running total after Phase 3: ~78 jobs**

### Phase 4 — Support Jobs (12 jobs, ~11 new)

BaseSupport already built. Each support job is a 4-line DSL definition.
Migrate Healer to BaseSupport (or keep as custom with expanded move list).

**Running total after Phase 4: ~90 jobs**

### Phase 5 — Processing + Placement + Environmental (18 jobs)

BaseProcessor and BasePlacer already built. Each processing job needs an input→output
mapping. Each placement job needs a placement rule. Environmental jobs are custom.

**Running total after Phase 5: ~108 jobs**

### Phase 6 — Defense (7 jobs)

BaseDefender already built. Each defense job defines a mob-targeting strategy and
an effect (damage, knockback, status). Most testing-intensive phase.

**Running total after Phase 6: ~115 jobs (includes 6 existing custom jobs)**

### Phase 7 — Combo Jobs (18 jobs, overlapping with above)

Many combos are already implemented via their parent categories. This phase adds
combo-specific logic: Fortune loot context, Silk Touch tool, connected-block BFS,
crafting-themed production outputs.

### Phase 8 — Logistics + Polish

Magnetizer and Trash Disposal. Move name audit against Cobblemon registry. Config
defaults tuning. Performance profiling with 20+ pastures. Documentation.

### Phase 9 — Underwater Test

Test water Pokémon pathfinding in a Pasture near water. If they swim: add 4 aquatic
gathering jobs. If not: cut them permanently.

---

## 6. PERFORMANCE BUDGET

### Per-Tick Costs (10 pastures, 4 Pokémon each = 40 Pokémon)

| Operation | Before (22 jobs) | After (110 jobs) | Notes |
|---|---|---|---|
| `shouldRun()` eligibility | 880/tick | **0/tick** | Cached on pasture entry |
| Scanner validators/block | ~15/block | ~45/block | More categories but bounded |
| Scanner blocks/tick | 150 total | 150 total | Unchanged scan rate |
| Scanner validators/tick | 2,250 | 6,750 | 3x increase, still microseconds |
| `isAvailable()` | N/A | ~6/Pokémon on rotation only | Not every tick |
| Entity queries (defense) | 0 | ~3/tick/pasture with defenders | Standard MC AABB query |
| Active job tick | 40/tick | 40/tick | One job per Pokémon |

**Net impact:** Scanner does ~3x more work (still trivial). Eligibility caching saves
~800 calls/tick. Overall: neutral to improved.

### Memory

| Structure | Before | After |
|---|---|---|
| Worker objects | 22 | ~70 (DSL instances + custom objects) |
| PastureCache sets | 22 per pasture | ~45 per pasture |
| PokemonProfile | N/A | 1 per Pokémon (~200 bytes) |
| Config objects | 1 (338 lines) | ~10 (per-category files) |

Additional memory per pasture: ~1KB. For 10 pastures: ~10KB. Irrelevant.

---

## 7. SAMPLE POKÉMON BUILDS

### "The Focused Lumberjack" — Scyther
**Active moves:** Cut, Fury Cutter, X-Scissor, Air Slash
**Eligible jobs:**
- A1 Overworld Logger (cut or furycutter)
- A2 Jungle Chopper (xscissor)
- A8 Grain Reaper (airslash)
**Combos:** CM14 Tree Feller needs cut + headbutt — no headbutt, no combo.
**Total: 3 single-move jobs.** Previously Cut alone qualified for 9 jobs. Now: precise.

### "The Deep Miner" — Excadrill
**Active moves:** Dig, Drill Run, Earthquake, Rock Smash
**Eligible jobs:**
- A9 Root Digger (dig)
- A22 Igneous Miner (drillrun)
- A23 Deepslate Excavator (earthquake)
- A10 Beet Puller (rocksmash)
**Combos:** CM13 Vein Miner (earthquake + dig), CM17 Fossil Hunter (dig + rocksmash)
**Total: 4 single + 2 combo.** Excellent workhorse, combos reward the 4-move commitment.

### "The Guard Dog" — Arcanine
**Active moves:** Extreme Speed, Crunch, Flamethrower, Roar
**Eligible jobs:**
- F2 Speed Booster (extremespeed)
- E1 Guard (crunch)
- C1 Ore Smelter (flamethrower)
- E3 Repeller (roar)
**Total: 4 jobs, spread across 3 categories.** Versatile but not OP — each move does
exactly 1 thing.

### "The Full Support" — Blissey
**Active moves:** Heal Bell, Aromatherapy, Soft-Boiled, Helping Hand
**Eligible jobs:**
- F9 Cleanser (healbell or aromatherapy)
- B15 Egg Layer (softboiled)
- F3 Strength Booster (helpinghand)
**Combos:** F11 Full Restore (healbell + aromatherapy) — takes priority!
**Total: 1 combo + 3 single.** Full Restore is the "prestige" combo; costs 2 slots
but gives Regen II + cleanse in one.

### "The Master Forger" — Magcargo
**Active moves:** Overheat, Lava Plume, Fire Spin, Iron Head
**Eligible jobs:**
- C3 Glass Maker (overheat)
- C4 Brick Baker (lavaplume or firespin)
- A27 Tumblestone Breaker (ironhead)
**Combos:** C11 Blast Furnace (overheat + ironhead),
CM8 Chain Forger (ironhead + firespin)
**Total: 3 single + 2 combo.** Two prestige crafting combos from a dedicated build.

### "The Spider Specialist" — Galvantula
**Active moves:** String Shot, Electroweb, Sticky Web, Thunder Wave
**Eligible jobs:**
- B2 Silk Spinner (stringshot or electroweb or stickyweb)
- B21 Static Generator (thunderwave)
**Total: 2 jobs.** Focused specialist — every move points to 1 of 2 jobs.

### "The Nature Worker" — Torterra
**Active moves:** Seed Bomb, Leech Seed, Earthquake, Synthesis
**Eligible jobs:**
- D2 Tree Planter (seedbomb)
- D3 Crop Sower (leechseed)
- A23 Deepslate Excavator (earthquake)
- D4 Bonemeal Applicator (synthesis)
**Total: 4 jobs.** Each move opens exactly 1 job — farm management + mining.

---

## 8. WHAT WAS CUT (AND WHY)

### Weather Jobs (2 cut)
- Weather Clearer, Rain Caller — Weather is global in Minecraft. Cannot be per-area.

### Fragile Processing (9 cut)
- Compactor, Decompactor, Dye Mixer, Wool Dyer, Sugar Refiner, Tinted Glass Maker,
  Concrete Mixer, Stonecutter, Glazer — All are faster via crafting table/furnace.
  No player wants a Pokémon for a 1-click recipe.
- Drying Rack — Furnace already does kelp drying.
- Sand Blaster — Sand is everywhere.
- Berry Juicer — Species-only + needs custom items.
- Master Chef — Food Cooker already handles cooking.

### Griefing Placement (5 cut)
- Cobweb Weaver — Autonomous cobwebs = griefing own base
- Flower Planter — Random flowers on grass = ugly mess
- Fire Setter — Autonomous fire = griefing
- Snow Layer Placer — Random snow = messy
- Mycelium/Moss Spreader — Too niche, uncontrollable spread

### Directional Placement (3 cut)
- Rail Layer, Redstone Layer — No path planning. Would scatter randomly.
- Water Placer — Flooding risk. Placing water sources = griefing.

### Complex Defense (9 cut)
- Paralyzer, Sleep Inducer, Web Trapper — Status effects on mobs are invisible and
  feel like nothing happened vs just killing the mob.
- Spawn Blocker — Needs spawn event mixin. Torch Lighter achieves same effect.
- Projectile Defender — Ranged no-movement attack looks glitchy without animation.
- Fortress Guardian, Mirror Guard — Damage reduction auras / projectile reflection
  need event hooks the mod doesn't have.
- Poison Cloud, Electric Fence — Persistent AOE zones need complex tick-area tracking.

### Trust / Invisible (6 cut)
- Ender Chest Linker — Accessing player ender chest feels invasive.
- Container Sorter, Stacker — Invisible work. Player can't see what happened.
- Item Teleporter — Complex source/dest setup for marginal gain.
- Toolsmith — Durability modification is fragile and feels like cheating.
- Potion Brewer — Brewing stand interaction too complex/fragile.

**Total cut: ~38 jobs from original 160 vision. Final count: ~112 (109 confirmed + 3 pending aquatic).**

---

## 9. FILE STRUCTURE (After Rework)

```
common/src/main/kotlin/accieo/cobbleworkers/
├── Cobbleworkers.kt
├── cache/
│   ├── CobbleworkersCacheManager.kt     — BlockCategory keys + removeTargetGlobal()
│   └── PastureCache.kt                  — BlockCategory keys
├── config/
│   ├── CobbleworkersConfig.kt           — AutoConfig: GeneralGroup only (trimmed)
│   ├── CobbleworkersConfigHolder.kt     — Unchanged
│   ├── CobbleworkersConfigInitializer.kt — Unchanged
│   ├── JobConfig.kt                     — NEW: Universal per-job config data class
│   └── JobConfigManager.kt             — NEW: Loads/generates config/cobbleworkers/*.json
├── enums/
│   ├── BlockCategory.kt                 — NEW: Replaces JobType
│   └── WorkerPriority.kt               — NEW: COMBO, MOVE, SPECIES, TYPE
├── interfaces/
│   ├── Worker.kt                        — Updated: isEligible, isAvailable, priority
│   └── ModIntegrationHelper.kt
├── jobs/
│   ├── WorkerDispatcher.kt              — Updated: profile caching, tiered selection
│   ├── WorkerRegistry.kt               — NEW: Central registration, replaces hardcoded list
│   ├── PokemonProfile.kt               — NEW
│   ├── base/
│   │   ├── BaseHarvester.kt             — Updated: BlockCategory + open harvestTool
│   │   ├── BaseProducer.kt              — NEW (from DiveLooter pattern)
│   │   ├── BaseProcessor.kt            — NEW (barrel→transform→chest, phased state machine)
│   │   ├── BasePlacer.kt               — NEW (chest→extract→place)
│   │   ├── BaseDefender.kt             — NEW (mob targeting)
│   │   └── BaseSupport.kt              — NEW (from Healer pattern)
│   ├── dsl/
│   │   ├── GatheringJob.kt             — DSL builder (with harvestOverride + toolOverride)
│   │   ├── ProductionJob.kt            — DSL builder (with defaultCooldownSeconds)
│   │   ├── ProcessingJob.kt            — DSL builder (with inputPredicate + transform)
│   │   ├── PlacementJob.kt             — DSL builder
│   │   ├── DefenseJob.kt               — DSL builder
│   │   └── SupportJob.kt               — DSL builder
│   ├── registry/
│   │   ├── GatheringJobs.kt            — All gathering job instances + register()
│   │   ├── ProductionJobs.kt           — All production job instances + register()
│   │   ├── ProcessingJobs.kt
│   │   ├── PlacementJobs.kt
│   │   ├── DefenseJobs.kt
│   │   ├── SupportJobs.kt
│   │   ├── EnvironmentalJobs.kt
│   │   ├── LogisticsJobs.kt
│   │   ├── ComboJobs.kt               — Combo jobs + lazy tool stacks (Fortune/SilkTouch)
│   │   └── CustomJobs.kt              — Scout, FuelGenerator, etc.
│   └── custom/
│       ├── Scout.kt
│       ├── FuelGenerator.kt
│       ├── BrewingStandFuelGenerator.kt
│       ├── LavaGenerator.kt
│       ├── FireExtinguisher.kt
│       ├── CropIrrigator.kt
│       └── [complex combo/environmental jobs]
├── utilities/
│   ├── BlockCategoryValidators.kt       — NEW: Centralized block validators
│   ├── CobbleworkersCropUtils.kt
│   ├── CobbleworkersCauldronUtils.kt
│   ├── CobbleworkersInventoryUtils.kt  — Updated: +findInputContainer, +extractFromContainer
│   ├── CobbleworkersMoveUtils.kt        — NEW: Move/ability helpers + validation
│   ├── CobbleworkersNavigationUtils.kt — Updated: +mob targeting
│   ├── CobbleworkersTypeUtils.kt
│   ├── DeferredBlockScanner.kt         — Updated: BlockCategory + isChunkLoaded + val→get()
│   └── WorkerVisualUtils.kt            — Updated: +handleMobArrival

config/cobbleworkers.json              ← AutoConfig general settings
config/cobbleworkers/
├── gathering.json                     ← Auto-generated from GatheringJobs defaults
├── production.json
├── processing.json
├── placement.json
├── defense.json
├── support.json
├── environmental.json
└── logistics.json
```

~55 Kotlin files + 3 Java mixins. Without DSL approach: would be ~140 files.

---

## 10. DIMINISHING RETURNS CURVE

| After Phase | Jobs Live | % of Total | New Systems Built |
|---|---|---|---|
| 0 (infra) | ~20 (existing) | 18% | BlockCategory, PokemonProfile, container I/O |
| 1 (base classes) | ~20 | 18% | 6 base classes + DSL |
| 2 (gathering) | ~50 | 45% | BlockCategory validators |
| 3 (production) | ~78 | 71% | — (BaseProducer reuse) |
| 4 (support) | ~90 | 82% | — (BaseSupport reuse) |
| 5 (processing+placement+env) | ~108 | 98% | — (base class reuse) |
| 6 (defense) | ~115 | 100% | Mob targeting |

**Sweet spot: Phase 3** — 71% of all jobs live with only gathering + production.
These are the two categories that work cleanly, look good visually, and don't require
complex player setup.

**Phase 5 gets to 98%** and adds the "wow factor" of processing (Pokémon smelting ore
without a furnace) and placement (Pokémon planting saplings, lighting torches).

**Defense (Phase 6) is highest risk, lowest job count** but adds the most unique gameplay.
Defer if schedule is tight.
