# CobbleCrew

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg?style=flat-square)](https://opensource.org/licenses/MPL-2.0)

![cobblecrew-icon](/common/src/main/resources/assets/cobblecrew/icon.png)

**Put your Pokémon to work — in the pasture AND by your side.**

CobbleCrew is a server-side [Cobblemon](https://cobblemon.com) addon that gives your Pokémon real jobs. Pokémon in Pasture Blocks harvest crops, mine resources, produce items, guard your base, buff nearby players, and much more. What makes CobbleCrew unique is that **your party Pokémon can work too** — your team helps you out in the world as you play, not just when they're sitting in a pasture.

> Originally forked from [Cobbleworkers](https://github.com/Accieo/cobbleworkers) by Accieo.

---

## Features

### 🎯 Party Pokémon Work With You
Your adventuring companions aren't just for battles. Party Pokémon can perform tasks while following you — harvest nearby crops, gather items, defend you from mobs, and apply buffs as you explore. Your team works **with** you, not just **for** you.

### 92+ Jobs Across 9 Categories

| Category | Jobs | Examples |
|----------|------|---------|
| **Gathering** | 38 | Log trees, mine stone, harvest crops, pick berries, collect honey |
| **Production** | 29 | Produce wool, spin silk, lay eggs, generate redstone, fish for loot |
| **Processing** | 10 | Smelt ores, cook food, make glass, grind bones, press dyes |
| **Placement** | 4 | Light up dark areas, plant saplings, sow crops, apply bone meal |
| **Defense** | 7 | Guard against hostiles, knockback, fire/poison/ice traps |
| **Support** | 10 | Heal, speed boost, night vision, water breathing, scout structures |
| **Environmental** | 11 | Freeze water, fuel furnaces, irrigate crops, douse fires, accelerate growth |
| **Logistics** | 2 | Consolidate items, pick up ground loot |
| **Combo** | 18 | Multi-move combos: vein mining, silk touch, fortune, deep sea trawling |

### Move-Based Eligibility
Pokémon qualify for jobs based on their **moves**, **species**, or **abilities**. A Tropius with Harvest can farm your fields. A Chansey with Heal Pulse keeps you alive. A Meowth with Pay Day mints gold nuggets. Each job has configurable move requirements and optional species fallbacks.

### Combo Moves
Pokémon that know multiple qualifying moves unlock powerful **combo jobs** — vein mining (Earthquake + Dig), silk touch harvesting (Psychic + Cut), fortune mining with doubled drops, and rare deep-sea trawling with a chance at tridents and hearts of the sea.

### Fully Configurable
Every job can be enabled/disabled, and you can customize required moves, cooldowns, species lists, and more through the config system. Uses [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) for in-game tweaking on integrated servers.

---

## Requirements

- [Cobblemon](https://cobblemon.com) 1.7.0+
- Minecraft 1.21.1
- Fabric or NeoForge
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) (Fabric) / [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) (NeoForge)

## Installation

Server-side only — drop the jar into your server's `mods/` folder. No client installation needed.

## License

Licensed under [MPL-2.0](https://mozilla.org/MPL/2.0/)

## Credits

Originally forked from [Cobbleworkers](https://github.com/Accieo/cobbleworkers) by [Accieo](https://github.com/Accieo). CobbleCrew is an independent continuation with party Pokémon support, expanded job categories, and additional features.
