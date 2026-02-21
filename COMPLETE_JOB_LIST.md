# Cobbleworkers v2.9.0 — Complete Job List (128 Total)

All jobs are now configured via JSON in `config/cobbleworkers/`. Every job supports move-based, type-based, species-based, or ability-based qualification.

---

## 🌲 Gathering Jobs (39 total)

### **Wood & Plant Harvesting**
1. **overworld_logger** — Harvests overworld logs (oak, birch, spruce, etc.)
   - Moves: `cut`, `furycutter`
   - Particle: Campfire Smoke

2. **jungle_chopper** — Harvests tropical/jungle wood
   - Moves: `xscissor`, `nightslash`, `sacredsword`
   - Particle: Campfire Smoke

3. **fungi_harvester** — Harvests nether wood (crimson, warped stems)
   - Moves: `shadowclaw`, `poltergeist`
   - Fallback Type: GHOST
   - Particle: Soul

4. **bamboo_chopper** — Harvests bamboo (top segment only)
   - Moves: `falseswipe`, `bulletpunch`
   - Fallback Species: Pangoro, Pancham
   - Particle: Composter

5. **sugar_cane_cutter** — Harvests sugar cane (top segment only)
   - Moves: `razorleaf`, `magicalleaf`
   - Particle: Composter

6. **cactus_pruner** — Harvests cactus (top segment only)
   - Moves: `needlearm`, `pinmissile`, `spikecannon`
   - Fallback Species: Cacturne, Cacnea, Maractus
   - Particle: Crit

7. **vine_trimmer** — Harvests vines
   - Moves: `vinewhip`, `powerwhip`
   - Fallback Species: Tangela, Tangrowth
   - Particle: Composter

### **Crops & Plants**
8. **beet_puller** — Harvests beetroot
   - Moves: `strength`, `rocksmash`
   - Particle: Composter

9. **sweet_berry_harvester** — Harvests sweet berry bushes
   - Moves: `stuffcheeks`
   - Fallback Species: Teddiursa, Greedent
   - Particle: Composter

10. **pumpkin_melon_harvester** — Harvests pumpkin & melon blocks
    - Moves: `stomp`, `slam`
    - Fallback Type: GRASS
    - Particle: Composter

11. **cocoa_harvester** — Harvests cocoa pods from jungle logs
    - Moves: `knockoff`, `covet`
    - Fallback Species: Tropius
    - Particle: Composter

12. **chorus_fruit_harvester** — Harvests chorus fruit
    - Moves: `teleport`, `psychocut`
    - Particle: Portal

13. **glowberry_picker** — Harvests glow berries from cave vines
    - Moves: `pluck`, `thief`
    - Particle: Glow

14. **dripleaf_harvester** — Harvests big/small dripleaf
    - Moves: `leafblade`, `leafage`
    - Particle: Composter

### **Stone & Earth**
15. **stone_breaker** — Breaks stone, cobblestone, andesite, diorite, granite
    - Moves: `brickbreak`, `smackdown`
    - Fallback Type: ROCK
    - Particle: Crit

16. **igneous_miner** — Mines igneous rocks (basalt, blackstone, tuff)
    - Moves: `drillrun`, `headsmash`
    - Particle: Crit

17. **deepslate_excavator** — Mines deepslate variants
    - Moves: `earthpower`, `earthquake`
    - Particle: Crit

18. **excavator** — Digs dirt, coarse dirt, rooted dirt
    - Moves: `mudshot`, `highhorsepower`, `magnitude`
    - Fallback Type: GROUND
    - Particle: Crit

19. **sand_miner** — Mines sand & red sand
    - Moves: `sandstorm`, `sandattack`, `sandtomb`, `scorchingsands`
    - Fallback Species: Sandshrew, Sandslash, Palossand, Sandygast
    - Particle: Crit

20. **clay_digger** — Mines clay blocks
    - Moves: `mudslap`, `mudbomb`, `shoreup`
    - Particle: Splash

### **Minerals & Crystals**
21. **sculk_harvester** — Harvests sculk blocks
    - Moves: `shadowball`, `spite`
    - Particle: Sculk Soul

22. **ice_miner** — Mines ice/packed ice/blue ice (silk touch equivalent)
    - Moves: `icepunch`, `icefang`, `iceshard`
    - Fallback Type: ICE
    - Particle: Snowflake

### **Nature & Cleanup**
23. **mushroom_forager** — Harvests mushrooms
    - Moves: `stunspore`, `poisonpowder`
    - Fallback Species: Breloom, Parasect, Shiinotic
    - Particle: Spore Blossom Air

24. **flower_picker** — Harvests all flowers
    - Moves: `petalblizzard`, `petaldance`
    - Fallback Species: Comfey, Florges
    - Particle: Cherry Leaves

25. **snow_scraper** — Harvests snow blocks
    - Moves: `icywind`, `avalanche`, `hail`
    - Fallback Type: ICE
    - Particle: Snowflake

26. **moss_scraper** — Harvests moss blocks/carpet
    - Moves: `grassknot`, `gigadrain`, `absorb`
    - Fallback Species: Tangela, Tangrowth
    - Particle: Composter

27. **decomposer** — Breaks leaves → bone meal
    - Moves: `phantomforce`, `nightshade`
    - Fallback Type: GHOST
    - Particle: Composter

28. **terrain_flattener** — Clears tall grass, ferns, flowers
    - Moves: `bulldoze`, `stompingtantrum`, `mudsport`
    - Fallback Type: GROUND
    - Particle: Crit

### **Cobblemon Growables**
29. **apricorn_harvester** — Harvests mature apricorns (resets to age 0)
    - Moves: `pluck`, `bugbite`, `furycutter`
    - Fallback Type: BUG
    - Particle: Happy Villager

30. **berry_harvester** — Harvests mature Cobblemon berries
    - Moves: `pluck`, `bugbite`, `stuffcheeks`
    - Fallback Type: GRASS
    - Particle: Happy Villager

31. **mint_harvester** — Harvests mature mints (resets to age 0)
    - Moves: `aromatherapy`, `sweetscent`, `grassysurge`
    - Fallback Type: FAIRY
    - Particle: Happy Villager

32. **amethyst_harvester** — Harvests amethyst clusters
    - Moves: `rockblast`, `rockslide`, `powergem`
    - Fallback Type: ROCK
    - Particle: End Rod

33. **tumblestone_harvester** — Harvests tumblestone clusters (configurable replant)
    - Moves: `ironhead`, `meteormash`, `bulletpunch`
    - Fallback Type: STEEL
    - Particle: Composter
    - Config: `replant: true/false`

34. **netherwart_harvester** — Harvests nether wart (configurable replant)
    - Moves: `shadowclaw`, `nightmare`, `hex`
    - Fallback Type: GHOST
    - Particle: Smoke
    - Config: `replant: true/false`

35. **crop_harvester** — Harvests wheat, carrots, potatoes (configurable replant)
    - Moves: `razorleaf`, `leafstorm`, `magicalleaf`, `harvest`
    - Fallback Type: GRASS
    - Particle: Happy Villager
    - Config: `replant: true/false`

36. **root_harvester** — Harvests beetroot, potatoes, carrots (configurable replant)
    - Moves: `dig`, `strength`, `mudshot`
    - Fallback Type: GROUND
    - Particle: Composter
    - Config: `replant: true/false`

37. **honey_harvester** — Harvests honeycomb from full beehives
    - Moves: `bugbuzz`, `attackorder`, `defendorder`
    - Fallback Species: Combee, Vespiquen
    - Particle: Happy Villager

---

## ⚙️ Production Jobs (29 total)

### **Self-Producing Workers**
38. **wool_producer** — Generates white wool
    - Moves: `cottonspore`, `cottonguard`
    - Fallback Species: Wooloo, Dubwool, Mareep, Flaaffy
    - Cooldown: 120s

39. **silk_spinner** — Generates string
    - Moves: `stringshot`, `spiderweb`, `electroweb`, `stickyweb`
    - Fallback Species: Spinarak, Ariados, Joltik, Galvantula, Snom
    - Cooldown: 90s

40. **slime_secretor** — Generates slime balls
    - Moves: `acidarmor`, `minimize`
    - Fallback Species: Goomy, Sliggoo, Goodra, Gulpin, Swalot, Ditto
    - Cooldown: 120s

41. **ink_squirter** — Generates ink sac (or glow ink sac for Octillery)
    - Moves: `octazooka`, `waterspout`
    - Fallback Species: Octillery, Inkay, Malamar
    - Cooldown: 90s

42. **bone_shedder** — Generates bone + bone meal
    - Moves: `bonerush`, `shadowbone`
    - Fallback Species: Cubone, Marowak, Mandibuzz
    - Cooldown: 90s

43. **pearl_creator** — Generates prismarine shards
    - Moves: `shellsmash`, `withdraw`
    - Fallback Species: Clamperl, Shellder, Cloyster
    - Cooldown: 180s

44. **feather_molter** — Generates feathers
    - Moves: `roost`, `bravebird`, `wingattack`, `fly`
    - Cooldown: 60s

45. **scale_shedder** — Generates turtle scute + prismarine shard
    - Moves: `shedtail`, `coil`
    - Fallback Species: Dragonair, Dragonite, Gyarados, Milotic, Arbok
    - Cooldown: 180s

46. **fruit_bearer** — Generates apples
    - Moves: `gravapple`, `appleacid`
    - Fallback Species: Tropius, Applin, Flapple, Appletun
    - Cooldown: 120s

47. **coin_minter** — Generates gold nuggets
    - Moves: `payday`
    - Fallback Species: Meowth, Persian, Perrserker
    - Cooldown: 180s

48. **gem_crafter** — Generates amethyst shard + emerald
    - Moves: `diamondstorm`, `meteorbeam`
    - Fallback Species: Sableye, Carbink, Diancie
    - Cooldown: 180s

49. **spore_releaser** — Generates red/brown mushroom
    - Moves: `spore`, `ragepowder`, `sleeppowder`
    - Fallback Species: Paras, Parasect, Foongus, Amoonguss
    - Cooldown: 90s

50. **pollen_packer** — Generates honey bottle
    - Moves: `pollenpuff`, `floralhealing`
    - Fallback Species: Ribombee, Cutiefly, Comfey
    - Cooldown: 120s

51. **gift_giver** — Generates random valuable items
    - Moves: `present`, `bestow`, `fling`
    - Fallback Species: Delibird
    - Cooldown: 300s

52. **egg_layer** — Generates eggs
    - Moves: `softboiled`
    - Fallback Species: Chansey, Blissey, Happiny, Exeggcute
    - Cooldown: 120s

53. **milk_producer** — Generates milk bucket
    - Moves: `milkdrink`
    - Fallback Species: Miltank, Gogoat
    - Cooldown: 120s

54. **electric_charger** — Generates glowstone dust
    - Moves: `charge`, `chargebeam`, `thunderbolt`
    - Fallback Species: Jolteon, Electrode, Magneton, Rotom
    - Cooldown: 120s

55. **wax_producer** — Generates honeycomb
    - Moves: `defendorder`, `healorder`
    - Fallback Species: Vespiquen
    - Cooldown: 120s

56. **powder_maker** — Generates gunpowder
    - Moves: `selfdestruct`, `explosion`, `mindblown`
    - Fallback Species: Voltorb, Electrode, Koffing, Weezing
    - Cooldown: 120s

57. **ash_collector** — Generates charcoal
    - Moves: `incinerate`
    - Fallback Species: Torkoal, Coalossal, Rolycoly, Carkol
    - Cooldown: 120s

58. **static_generator** — Generates redstone
    - Moves: `thunderwave`, `discharge`, `electrify`
    - Fallback Type: ELECTRIC
    - Cooldown: 120s

59. **sap_tapper** — Generates slime ball or honey bottle
    - Moves: `leechlife`, `hornleech`, `drainpunch`
    - Fallback Species: Heracross, Pinsir
    - Cooldown: 120s

60. **toxin_distiller** — Generates fermented spider eye
    - Moves: `poisonjab`, `poisonfang`
    - Type-Gated: `toxic` (POISON only)
    - Fallback Species: Arbok, Toxicroak, Salazzle
    - Cooldown: 120s

61. **crystal_grower** — Generates quartz
    - Moves: `ancientpower`, `rockpolish`
    - Fallback Species: Carbink, Boldore, Gigalith
    - Cooldown: 180s

62. **tear_collector** — Generates ghast tear
    - Moves: `fakeout`, `faketears`, `tearfullook`
    - Fallback Species: Duskull, Banette, Misdreavus
    - Cooldown: 180s

### **Loot-Based Workers**
63. **fishing_looter** — Generates fishing loot (configurable treasure chance)
    - Moves: `dive`, `surf`, `whirlpool`
    - Fallback Type: WATER
    - Cooldown: 120s
    - Config: `treasureChance: <0-100>`, `requiresWater: true`

64. **pickup_looter** — Generates loot via Pickup ability
    - Required Ability: `pickup`
    - Loot Tables: Configurable (default: `cobblemon:gameplay/pickup`)
    - Cooldown: 120s

65. **dive_collector** — Generates underwater loot
    - Moves: `dive`
    - Loot Tables: Configurable
    - Cooldown: 210s
    - Config: `requiresWater: true`

66. **dig_site_excavator** — Brushes suspicious blocks for loot
    - Moves: `dig`, `sandtomb`, `scorchingsands`
    - Fallback Type: GROUND
    - Loot Tables: Configurable
    - Cooldown: 120s

---

## 🌍 Environmental Jobs (11 total)

67. **frost_former** — Water → Ice → Packed Ice → Blue Ice (progressive chain)
    - Moves: `icebeam`, `sheercold`, `auroraveil`
    - Fallback Type: ICE

68. **obsidian_forge** — Lava → Obsidian (via Water-type cooling)
    - Moves: `hydropump`, `scald`, `aquatail`, `brine`
    - Fallback Type: WATER

69. **growth_accelerator** — Random-ticks crops/saplings to accelerate growth
    - Moves: `growth`, `sunnyday`, `grassyterrain`
    - Fallback Type: GRASS

70. **lava_cauldron_filler** — Fills cauldrons with lava
    - Moves: `lavaplume`, `heatwave`, `eruption`
    - Fallback Type: FIRE
    - Cooldown: 90s

71. **water_cauldron_filler** — Fills cauldrons with water
    - Moves: `surf`, `watergun`, `hydropump`
    - Fallback Type: WATER
    - Cooldown: 90s

72. **snow_cauldron_filler** — Fills cauldrons with powder snow
    - Moves: `blizzard`, `iciclecrash`, `powdersnow`
    - Fallback Type: ICE
    - Cooldown: 90s

73. **furnace_fueler** — Adds burn time to furnaces
    - Moves: `flamethrower`, `fireblast`, `firespin`
    - Fallback Type: FIRE
    - Cooldown: 80s
    - Config: `burnTimeSeconds: <ticks/20>`

74. **brewing_stand_fueler** — Adds fuel to brewing stands
    - Moves: `dragonbreath`, `dracometeor`, `dragonpulse`
    - Fallback Type: DRAGON
    - Cooldown: 80s
    - Config: `addedFuel: <count>`

75. **fire_douser** — Extinguishes fire blocks in radius
    - Moves: `waterpulse`, `raindance`, `muddywater`
    - Fallback Type: WATER
    - Config: `radius: <blocks>`

76. **crop_irrigator** — Hydrates farmland in radius
    - Moves: `aquaring`, `lifedew`, `waterpledge`
    - Fallback Type: WATER
    - Config: `radius: <blocks>`

77. **bee_pollinator** — Fills non-full beehives with honey
    - Moves: `pollenpuff`, `healorder`
    - Fallback Species: Combee, Vespiquen
    - Cooldown: 120s

---

## 🛡️ Support Jobs (10 total)

78. **healer** — Applies Regeneration
    - Moves: `wish`, `recover`, `moonlight`, `healpulse`, `lifedew`
    - Fallback Species: Happiny, Chansey, Blissey
    - Effect: Regeneration (30s)

79. **speed_booster** — Applies Speed
    - Moves: `tailwind`, `agility`, `extremespeed`
    - Effect: Speed (30s)

80. **strength_booster** — Applies Strength
    - Moves: `howl`, `swordsdance`, `coaching`
    - Type-Gated: `helpinghand` (FIGHTING only)
    - Effect: Strength (30s)

81. **resistance_provider** — Applies Resistance
    - Moves: `irondefense`, `barrier`, `shelter`
    - Effect: Resistance (30s)

82. **haste_provider** — Applies Haste
    - Moves: `nastyplot`, `calmmind`, `focusenergy`, `workup`
    - Effect: Haste (30s)

83. **jump_booster** — Applies Jump Boost
    - Moves: `bounce`, `highjumpkick`, `jumpkick`
    - Effect: Jump Boost (30s)

84. **night_vision_provider** — Applies Night Vision
    - Moves: `miracleeye`, `mindreader`
    - Fallback Species: Noctowl, Umbreon, Luxray
    - Effect: Night Vision (60s)

85. **water_breather** — Applies Water Breathing
    - Moves: `aquaring`, `raindance`
    - Effect: Water Breathing (60s)

86. **hunger_restorer** — Applies Saturation
    - Moves: `swallow`, `slackoff`
    - Fallback Species: Snorlax, Munchlax, Miltank
    - Effect: Saturation (5s)

87. **scout** — Picks up maps, converts to structure-locating filled maps
    - Moves: `fly`, `aerialace`, `bravebird`
    - Fallback Type: FLYING
    - Cooldown: 600s
    - Config: `structureTags: [list]`, `useAllStructures: true/false`, `mapNameIsHidden: true/false`

---

## 🔧 Processing Jobs (10 total)

88. **ore_smelter** — Raw Iron/Gold/Copper → Ingots
    - Moves: `flamethrower`, `fireblast`, `magmastorm`
    - Fallback Species: Magcargo, Coalossal, Torkoal

89. **food_cooker** — Raw meat/fish/potato → Cooked variants
    - Moves: `ember`, `firepunch`, `flamecharge`
    - Fallback Type: FIRE

90. **glass_maker** — Sand → Glass
    - Moves: `heatwave`, `overheat`, `mysticalfire`

91. **brick_baker** — Clay Ball → Brick
    - Moves: `firespin`, `lavaplume`
    - Fallback Species: Numel, Torkoal, Slugma, Rolycoly

92. **charcoal_burner** — Logs → Charcoal
    - Moves: `blastburn`, `burnup`
    - Fallback Type: FIRE

93. **paper_maker** — Sugar Cane (×3) → Paper (×3)
    - Moves: `slash`, `guillotine`

94. **bone_grinder** — Bone → Bone Meal (×3)
    - Moves: `bonemerang`, `boneclub`
    - Fallback Species: Cubone, Marowak

95. **flint_knapper** — Gravel → Flint
    - Moves: `karatechop`, `crosschop`
    - Fallback Species: Golem, Rhyperior

96. **pigment_presser** — Flowers → Dyes
    - Moves: `hammerarm`, `bodypress`
    - Fallback Species: Pangoro, Machamp

97. **composter** — Seeds/plants (×7) → Bone Meal (×1)
    - Moves: `stockpile`, `acidspray`
    - Fallback Species: Trubbish, Garbodor, Grimer, Muk

---

## 📦 Logistics Jobs (3 total)

98. **magnetizer** — Consolidates 9 nuggets → 1 ingot within containers
    - Moves: `magnetrise`, `flashcannon`, `steelbeam`, `magneticflux`
    - Fallback Species: Magnemite, Magneton, Magnezone

99. **ground_item_collector** — Picks up items from ground, deposits in containers
     - Moves: `psychic`, `telekinesis`, `confusion`
     - Fallback Type: PSYCHIC
     - Config: `radius: <blocks>`

---

## 🏗️ Placement Jobs (4 total)

101. **torch_lighter** — Places torches in dark areas (light level ≤ 7)
     - Moves: `flash`, `willowisp`
     - Fallback Species: Litwick, Lampent, Chandelure, Ampharos, Lanturn

102. **tree_planter** — Plants saplings on suitable soil
     - Moves: `ingrain`, `seedbomb`, `seedflare`
     - Fallback Species: Trevenant, Torterra, Celebi

103. **crop_sower** — Plants seeds on farmland
     - Moves: `bulletseed`, `leechseed`

104. **bonemeal_applicator** — Applies bone meal to crops/saplings
     - Moves: `synthesis`, `junglehealing`
     - Fallback Type: GRASS

---

## ⚔️ Defense Jobs (7 total)

105. **guard** — Attacks hostile mobs (4 damage)
     - Moves: `bite`, `crunch`, `closecombat`
     - Fallback Species: Growlithe, Arcanine, Lucario, Lycanroc

106. **sentry** — Emits electric sparks around hostile mobs (no damage, visual only)
     - Moves: `detect`, `foresight`, `odorsleuth`, `meanlook`
     - Fallback Species: Persian, Noctowl, Watchog

107. **repeller** — Knocks back + slows hostile mobs
     - Moves: `roar`, `whirlwind`, `dragontail`, `circlethrow`
     - Fallback Species: Gyarados, Salamence

108. **fearmonger** — Heavily knocks back + weakens hostile mobs
     - Moves: `scaryface`, `glare`, `snarl`, `screech`
     - Fallback Species: Gyarados, Arcanine

109. **fire_trap** — Sets hostile mobs on fire
     - Moves: `flamewheel`, `searingshot`, `firelash`
     - Fallback Type: FIRE

110. **poison_trap** — Applies poison to hostile mobs
     - Moves: `toxicspikes`, `sludgewave`, `venoshock`
     - Fallback Type: POISON

111. **ice_trap** — Applies slowness + mining fatigue to hostile mobs
     - Moves: `blizzard`, `freezedry`, `glaciate`
     - Fallback Type: ICE

---

## 🌟 Combo Jobs (18 total)
**Require ALL listed moves** (COMBO priority — highest)

### **Gathering Combos**
112. **demolisher** — Universal block breaker
     - Moves: `cut` + `rocksmash`

113. **fortune_miner** — Stone mining with doubled drops
     - Moves: `powergem` + `dig`

114. **silk_touch_extractor** — Silk-touch harvest
     - Moves: `psychic` + any of (`cut`, `rocksmash`, `dig`, `icebeam`)

115. **vein_miner** — Breaks target + connected same-type blocks
     - Moves: `earthquake` + `dig`

116. **tree_feller** — Breaks entire tree (all connected logs)
     - Moves: `cut` + `headbutt`

117. **fossil_hunter** — Fossil-weighted loot
     - Moves: `dig` + `rocksmash`

118. **gem_vein_finder** — Gem-weighted loot
     - Moves: `dig` + `powergem`

### **Production Combos**
119. **string_crafter** — Generates arrows/leads/fishing rods (cycles)
     - Moves: `cut` + `stringshot`
     - Cooldown: 120s

120. **book_binder** — Generates books
     - Moves: `cut` + `psychic`
     - Cooldown: 180s

121. **candle_maker** — Generates candles
     - Moves: `willowisp` + `stringshot`
     - Cooldown: 120s

122. **chain_forger** — Generates chains
     - Moves: `ironhead` + `firespin`
     - Cooldown: 120s

123. **lantern_builder** — Generates lanterns
     - Moves: `flashcannon` + `ironhead`
     - Cooldown: 180s

124. **banner_creator** — Generates random colored banners
     - Moves: `cut` + `sketch`
     - Cooldown: 240s

125. **deep_sea_trawler** — Treasure hunting (trident, heart of the sea, etc.)
     - Moves: `dive` + `whirlpool`
     - Cooldown: 300s

126. **magma_diver** — Rare loot from lava (netherite scrap, magma cream)
     - Moves: `dive` + `lavaplume`
     - Cooldown: 300s

### **Processing Combo**
127. **blast_furnace** — Smelts raw ores → ingots at 2x rate
     - Moves: `overheat` + `ironhead`

### **Support Combos**
128. **full_restore** — Regeneration II + clears all negative effects
     - Moves: `healbell` + `aromatherapy`
     - Effect: Regeneration II (30s)

129. **aura_master** — Applies Speed + Strength + Haste + Resistance
     - Moves: `calmmind` + `helpinghand`
     - Effect: Multi-buff (30s)

---

## 📊 Job Priorities

Workers are assigned jobs in this order:
1. **COMBO** (highest) — Combo jobs requiring ALL moves
2. **MOVE** — Jobs qualified via moves
3. **SPECIES** — Jobs qualified via fallbackSpecies
4. **TYPE** — Jobs qualified via fallbackType
5. **ABILITY** — Jobs requiring specific ability (e.g., Pickup)

**One Job at a Time:** Each Pokémon is assigned ONE random eligible job at a time. Jobs release when target is finished AND no active state remains (e.g., held items deposited).

---

## ⚙️ Configuration Structure

All jobs configured via JSON in `config/cobbleworkers/<category>/<job_name>.json`

Example (`config/cobbleworkers/gathering/apricorn_harvester.json`):
```json
{
  "enabled": true,
  "qualifyingMoves": ["pluck", "bugbite", "furycutter"],
  "fallbackType": "BUG",
  "fallbackSpecies": [],
  "replant": true,
  "cooldownSeconds": 30
}
```

**General Settings** (preserved in `cobbleworkers.json`):
```json
{
  "general": {
    "blocksScannedPerTick": 15,
    "searchRadius": 8,
    "searchHeight": 5
  }
}
```

---

## 🎯 Key Features

- **Zero Hardcoded Config** — Everything via JobConfigManager
- **Qualifying Moves** — Jobs prioritize Pokémon with specific moves
- **Type/Species Fallbacks** — Jobs can fallback to Pokémon type or species
- **Ability-Based Jobs** — Some jobs require specific abilities (e.g., Pickup)
- **Type-Gated Moves** — Some moves only work for specific types (e.g., `toxic` → POISON)
- **Configurable Replant** — Crop/tumblestone/netherwart harvesters support replant toggle
- **Configurable Cooldowns** — All production/loot jobs support custom cooldown
- **Configurable Radii** — Fire douser, irrigator, item collector support radius
- **Configurable Loot Tables** — Fishing/pickup/dive/excavator support custom loot tables

---

## 🔄 Migration from v2.8.0

All 22 legacy jobs have been replaced:
- **Apricorn/Berry/Mint Harvester** → `apricorn_harvester`, `berry_harvester`, `mint_harvester`
- **Amethyst/Tumblestone Harvester** → `amethyst_harvester`, `tumblestone_harvester`
- **Crop Harvester** → `crop_harvester` (grain) + `root_harvester` (roots)
- **Honey Collector** → `honey_harvester` (gathering) + `bee_pollinator` (environmental)
- **Lava/Water/Snow Generator** → `lava_cauldron_filler`, `water_cauldron_filler`, `snow_cauldron_filler`
- **Fuel Generator** → `furnace_fueler`
- **Brewing Stand Fuel Generator** → `brewing_stand_fueler`
- **Fire Extinguisher** → `fire_douser`
- **Crop Irrigator** → `crop_irrigator`
- **Fishing Loot Generator** → `fishing_looter`
- **Pickup Looter** → `pickup_looter`
- **Dive Looter** → `dive_collector`
- **Archeologist** → `dig_site_excavator`
- **Ground Item Gatherer** → `ground_item_collector`
- **Healer** → `healer` (support)
- **Scout** → `scout` (support)

Old `cobbleworkers.json` config file now only contains `general` settings. All per-job config migrated to category folders.
