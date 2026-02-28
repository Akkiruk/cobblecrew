/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.api

import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.jobs.WorkerRegistry

/**
 * Public API for CobbleCrew. Other mods can depend on this at compile time
 * to query job eligibility rules directly.
 */
object CobbleCrewApi {

    data class JobRule(
        val id: String,
        val displayName: String,
        val description: String,
        val enabled: Boolean,
        val requiredType: String?,
        val designatedSpecies: List<String>,
        val requiredMoves: List<String>,
        val requiredAbility: String?,
        val hardcodedSpecies: List<String>,
        val hardcodedSpeciesEnabled: Boolean,
        val priority: String,
    )

    private val descriptions = mapOf(
        // Gathering
        "logger" to "Fells entire trees and deposits logs in chests. Works on all log types.",
        "bamboo_chopper" to "Chops bamboo stalks and deposits them in chests.",
        "sugar_cane_cutter" to "Cuts sugar cane tops and deposits them in chests.",
        "cactus_pruner" to "Prunes cactus tops and deposits them in chests.",
        "vine_trimmer" to "Trims vines and deposits them in chests.",
        "sweet_berry_harvester" to "Picks ripe sweet berries and deposits them in chests.",
        "pumpkin_melon_harvester" to "Harvests pumpkins and melons, depositing them in chests.",
        "cocoa_harvester" to "Picks mature cocoa pods and deposits them in chests.",
        "chorus_fruit_harvester" to "Harvests chorus fruit and deposits it in chests.",
        "glowberry_picker" to "Picks glow berries from cave vines.",
        "dripleaf_harvester" to "Harvests dripleaf and deposits it in chests.",
        "stone_breaker" to "Breaks stone blocks and deposits drops in chests.",
        "igneous_miner" to "Mines igneous stone variants and deposits drops in chests.",
        "deepslate_excavator" to "Excavates deepslate and deposits drops in chests.",
        "excavator" to "Digs dirt and soil blocks, depositing them in chests.",
        "sand_miner" to "Digs sand and deposits it in chests.",
        "clay_digger" to "Digs clay blocks and deposits clay balls in chests.",
        "ore_miner" to "Mines ore blocks and deposits drops in chests.",
        "sculk_harvester" to "Breaks sculk blocks and deposits them in chests.",
        "ice_miner" to "Mines ice blocks with silk-touch effect and deposits them in chests.",
        "mushroom_forager" to "Forages mushrooms and deposits them in chests.",
        "flower_picker" to "Picks flowers and deposits them in chests.",
        "snow_scraper" to "Scrapes snow blocks and deposits them in chests.",
        "moss_scraper" to "Scrapes moss blocks and deposits them in chests.",
        "decomposer" to "Breaks leaves and converts them to bone meal.",
        "terrain_flattener" to "Clears tall grass and vegetation.",
        "apricorn_harvester" to "Picks ripe Apricorns and deposits them in chests.",
        "berry_harvester" to "Picks ripe Berries and deposits them in chests.",
        "mint_harvester" to "Picks mature Mints and deposits them in chests.",
        "amethyst_harvester" to "Breaks amethyst clusters and deposits shards in chests.",
        "tumblestone_harvester" to "Breaks Tumblestone clusters and deposits them in chests.",
        "netherwart_harvester" to "Harvests mature nether wart and replants it.",
        "crop_harvester" to "Harvests mature grain crops like wheat and replants them.",
        "root_harvester" to "Digs up mature root crops like carrots and potatoes.",
        "honey_harvester" to "Collects honey from full beehives.",
        // Production
        "wool_producer" to "Periodically produces wool and deposits it in chests.",
        "silk_spinner" to "Periodically produces string and deposits it in chests.",
        "slime_secretor" to "Periodically produces slime balls and deposits them in chests.",
        "ink_squirter" to "Periodically produces ink sacs and deposits them in chests.",
        "bone_shedder" to "Periodically produces bones and bone meal.",
        "pearl_creator" to "Periodically produces prismarine shards.",
        "feather_molter" to "Periodically molts feathers and deposits them in chests.",
        "scale_shedder" to "Periodically sheds scales (scutes and prismarine shards).",
        "fruit_bearer" to "Periodically produces apples and deposits them in chests.",
        "coin_minter" to "Periodically produces gold nuggets.",
        "gem_crafter" to "Periodically produces amethyst shards and emeralds.",
        "spore_releaser" to "Periodically produces mushrooms.",
        "pollen_packer" to "Periodically produces honey bottles.",
        "gift_giver" to "Periodically produces random gifts (ores, food, or trinkets).",
        "egg_layer" to "Periodically lays eggs and deposits them in chests.",
        "milk_producer" to "Periodically produces milk buckets.",
        "electric_charger" to "Periodically produces glowstone dust.",
        "wax_producer" to "Periodically produces honeycomb.",
        "powder_maker" to "Periodically produces gunpowder.",
        "ash_collector" to "Periodically produces charcoal.",
        "static_generator" to "Periodically generates redstone dust.",
        "sap_tapper" to "Periodically taps tree sap (slime balls or honey).",
        "toxin_distiller" to "Periodically distills toxins into fermented spider eyes.",
        "crystal_grower" to "Periodically grows quartz crystals.",
        "tear_collector" to "Periodically collects ghast tears.",
        "fishing_looter" to "Fishes in nearby water and deposits catches in chests.",
        "pickup_looter" to "Uses the Pickup ability to find random items.",
        "dive_collector" to "Dives underwater to retrieve random loot.",
        "dig_site_excavator" to "Excavates suspicious blocks for archaeological loot.",
        // Processing
        "ore_smelter" to "Smelts raw ores from barrels into ingots.",
        "food_cooker" to "Cooks raw food from barrels into cooked meals.",
        "glass_maker" to "Smelts sand from barrels into glass.",
        "brick_baker" to "Fires clay balls from barrels into bricks.",
        "charcoal_burner" to "Burns logs from barrels into charcoal.",
        "paper_maker" to "Crafts sugar cane from barrels into paper.",
        "bone_grinder" to "Grinds bones from barrels into bone meal.",
        "flint_knapper" to "Knaps gravel from barrels into flint.",
        "pigment_presser" to "Presses flowers from barrels into dye.",
        "composter" to "Composts plant matter from barrels into bone meal.",
        // Placement
        "torch_lighter" to "Places torches in dark areas from chest supplies.",
        "tree_planter" to "Plants saplings from chests in suitable locations.",
        "crop_sower" to "Plants seeds from chests on farmland.",
        "bonemeal_applicator" to "Applies bone meal from chests to growing crops.",
        // Defense
        "guard" to "Attacks hostile mobs near the pasture.",
        "sentry" to "Detects hostile mobs and alerts with visual signals.",
        "repeller" to "Pushes hostile mobs away from the pasture.",
        "fearmonger" to "Launches hostile mobs far away with fear effects.",
        "fire_trap" to "Sets hostile mobs on fire.",
        "poison_trap" to "Poisons hostile mobs near the pasture.",
        "ice_trap" to "Slows and weakens hostile mobs with ice.",
        // Support
        "healer" to "Grants Regeneration to nearby players.",
        "speed_booster" to "Grants Speed to nearby players.",
        "strength_booster" to "Grants Strength to nearby players.",
        "resistance_provider" to "Grants Resistance to nearby players.",
        "haste_provider" to "Grants Haste to nearby players.",
        "jump_booster" to "Grants Jump Boost to nearby players.",
        "night_vision_provider" to "Grants Night Vision to nearby players.",
        "water_breather" to "Grants Water Breathing to nearby players.",
        "hunger_restorer" to "Restores hunger for nearby players.",
        "scout" to "Converts blank maps into structure-locating explorer maps.",
        // Environmental
        "frost_former" to "Freezes water into ice, packed ice, then blue ice.",
        "obsidian_forge" to "Converts lava source blocks into obsidian.",
        "growth_accelerator" to "Speeds up nearby crop growth with random ticks.",
        "lava_cauldron_filler" to "Fills empty cauldrons with lava.",
        "water_cauldron_filler" to "Fills empty cauldrons with water.",
        "snow_cauldron_filler" to "Fills empty cauldrons with powder snow.",
        "furnace_fueler" to "Adds fuel to furnaces with items waiting to be smelted.",
        "brewing_stand_fueler" to "Adds blaze fuel charges to brewing stands.",
        "fire_douser" to "Extinguishes fire blocks in a radius.",
        "crop_irrigator" to "Moisturizes dry farmland blocks.",
        "bee_pollinator" to "Increases honey levels in nearby beehives.",
        // Logistics
        "magnetizer" to "Consolidates 9 nuggets into ingots inside chests.",
        "ground_item_collector" to "Picks up items from the ground and stores them in chests.",
        // Combo
        "demolisher" to "Breaks any block type. Requires Rock Smash + Smack Down.",
        "fortune_miner" to "Mines with doubled drops. Requires Ancient Power + Psychic.",
        "silk_touch_extractor" to "Mines with silk touch. Requires Mind Reader + Iron Head.",
        "vein_miner" to "Breaks connected same-type blocks. Requires Earthquake + Cross Chop.",
        "tree_feller" to "Fells entire trees and strips leaves for bonus loot. Requires Headbutt + Power Whip.",
        "fossil_hunter" to "Mines fossil-weighted loot. Requires Bone Club + Fury Cutter.",
        "gem_vein_finder" to "Mines gem-weighted loot. Requires Flash Cannon + Glare.",
        "string_crafter" to "Crafts arrows, leads, or fishing rods. Requires String Shot + X-Scissor.",
        "book_binder" to "Crafts books. Requires Sketch + Shadow Claw.",
        "candle_maker" to "Crafts candles. Requires Will-O-Wisp + Aromatherapy.",
        "chain_forger" to "Forges chains. Requires Scorching Sands + Body Press.",
        "lantern_builder" to "Builds lanterns. Requires Sunny Day + Ice Fang.",
        "banner_creator" to "Creates random banners. Requires Leech Seed + Whirlwind.",
        "deep_sea_trawler" to "Trawls deep sea for treasure. Requires Whirlpool + Rain Dance.",
        "magma_diver" to "Dives into magma for rare materials. Requires Overheat + Night Shade.",
        "blast_furnace" to "Smelts ores at 2x efficiency. Requires Scald + Avalanche.",
        "full_restore" to "Grants Regeneration II and clears negatives. Requires Heal Bell + Life Dew.",
        "aura_master" to "Grants Speed, Strength, Haste, and Resistance. Requires Calm Mind + Helping Hand.",
    )

    /**
     * Returns the current list of all job eligibility rules from the WorkerRegistry.
     */
    fun getJobRules(): List<JobRule> {
        val seen = mutableSetOf<String>()
        return WorkerRegistry.workers.mapNotNull { worker ->
            if (!seen.add(worker.name)) return@mapNotNull null

            val config = JobConfigManager.get(worker.name)
            if (config.qualifyingMoves.isEmpty()
                && config.fallbackType.isEmpty()
                && config.fallbackSpecies.isEmpty()
                && config.requiredAbility.isNullOrEmpty()
            ) return@mapNotNull null

            JobRule(
                id = worker.name,
                displayName = formatName(worker.name),
                description = descriptions[worker.name] ?: "",
                enabled = config.enabled,
                requiredType = config.fallbackType.takeIf { it.isNotEmpty() },
                designatedSpecies = emptyList(),
                requiredMoves = config.qualifyingMoves,
                requiredAbility = config.requiredAbility?.takeIf { it.isNotEmpty() },
                hardcodedSpecies = config.fallbackSpecies,
                hardcodedSpeciesEnabled = config.fallbackSpecies.isNotEmpty(),
                priority = worker.priority.name,
            )
        }
    }

    private fun formatName(snakeCase: String): String =
        snakeCase.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /**
     * Generates a full diagnostic report of all registered jobs and their eligibility data.
     */
    fun generateDiagnosticReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== CobbleCrew Diagnostic Report ===")
        sb.appendLine("Generated: ${java.time.Instant.now()}")
        sb.appendLine()

        sb.appendLine("--- Worker Registry ---")
        sb.appendLine("Total registered workers: ${WorkerRegistry.workers.size}")
        sb.appendLine()

        for (worker in WorkerRegistry.workers) {
            val config = JobConfigManager.get(worker.name)
            sb.appendLine("  [${worker.name}]")
            sb.appendLine("    Priority: ${worker.priority.name}")
            sb.appendLine("    Enabled: ${config.enabled}")
            sb.appendLine("    Moves: ${config.qualifyingMoves.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine("    Fallback Type: ${config.fallbackType.ifEmpty { "(none)" }}")
            sb.appendLine("    Fallback Species: ${config.fallbackSpecies.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine()
        }

        sb.appendLine("--- API Rules ---")
        val rules = getJobRules()
        sb.appendLine("Total API rules: ${rules.size}")
        sb.appendLine()

        for (rule in rules) {
            sb.appendLine("  [${rule.id}] ${rule.displayName}")
            sb.appendLine("    Enabled: ${rule.enabled}")
            sb.appendLine("    Priority: ${rule.priority}")
            sb.appendLine("    Required Type: ${rule.requiredType ?: "(none)"}")
            sb.appendLine("    Required Moves: ${rule.requiredMoves.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine("    Required Ability: ${rule.requiredAbility ?: "(none)"}")
            sb.appendLine("    Fallback Species: ${rule.hardcodedSpecies.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine()
        }

        sb.appendLine("--- JobConfigManager ---")
        val allNames = JobConfigManager.allJobNames()
        sb.appendLine("Total config entries: ${allNames.size}")
        sb.appendLine("Job names: ${allNames.sorted().joinToString(", ")}")

        return sb.toString()
    }
}
