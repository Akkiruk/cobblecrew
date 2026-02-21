/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.api

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.jobs.WorkerRegistry

/**
 * Public API for Cobbleworkers. Other mods can depend on this at compile time
 * to query job eligibility rules directly.
 */
object CobbleworkersApi {

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

    /**
     * Returns the current list of all job eligibility rules based on the active config.
     * Includes both legacy config-based jobs and DSL-registered jobs from WorkerRegistry.
     */
    fun getJobRules(): List<JobRule> {
        val rules = mutableListOf<JobRule>()
        val legacyIds = mutableSetOf<String>()

        // Legacy rules from CobbleworkersConfigHolder (original 22 jobs)
        val legacy = legacyRules()
        legacy.forEach { legacyIds += it.id }
        rules.addAll(legacy)

        // Dynamic rules from WorkerRegistry — skip legacy duplicates
        val seen = legacyIds.toMutableSet()
        for (worker in WorkerRegistry.workers) {
            if (worker.name in seen) continue
            seen += worker.name

            val config = JobConfigManager.get(worker.name)
            if (config.qualifyingMoves.isEmpty() && config.fallbackType.isEmpty() && config.fallbackSpecies.isEmpty()) continue

            rules += JobRule(
                id = worker.name,
                displayName = formatName(worker.name),
                description = "",
                enabled = config.enabled,
                requiredType = config.fallbackType.takeIf { it.isNotEmpty() },
                designatedSpecies = emptyList(),
                requiredMoves = config.qualifyingMoves,
                requiredAbility = null,
                hardcodedSpecies = config.fallbackSpecies,
                hardcodedSpeciesEnabled = config.fallbackSpecies.isNotEmpty(),
                priority = worker.priority.name,
            )
        }

        return rules
    }

    private fun formatName(snakeCase: String): String =
        snakeCase.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /**
     * Generates a full diagnostic report of all registered jobs and their eligibility data.
     */
    fun generateDiagnosticReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Cobbleworkers Diagnostic Report ===")
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
            sb.appendLine("    Designated Species: ${rule.designatedSpecies.ifEmpty { listOf("(none)") }.joinToString(", ")}")
            sb.appendLine("    Hardcoded Species: ${rule.hardcodedSpecies.ifEmpty { listOf("(none)") }.joinToString(", ")} (enabled: ${rule.hardcodedSpeciesEnabled})")
            sb.appendLine()
        }

        sb.appendLine("--- JobConfigManager ---")
        val allNames = JobConfigManager.allJobNames()
        sb.appendLine("Total config entries: ${allNames.size}")
        sb.appendLine("Job names: ${allNames.sorted().joinToString(", ")}")

        return sb.toString()
    }

    private fun legacyRules(): List<JobRule> {
        val config = CobbleworkersConfigHolder.config
        return listOf(
            // --- Harvesters ---
            JobRule(
                id = "apricorn_harvester",
                displayName = "Apricorn Harvester",
                description = "Harvests ripe apricorns from nearby trees",
                enabled = config.apricorn.apricornHarvestersEnabled,
                requiredType = config.apricorn.typeHarvestsApricorns.name,
                designatedSpecies = config.apricorn.apricornHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "amethyst_harvester",
                displayName = "Amethyst Harvester",
                description = "Harvests amethyst clusters from budding blocks",
                enabled = config.amethyst.amethystHarvestersEnabled,
                requiredType = config.amethyst.typeHarvestsAmethyst.name,
                designatedSpecies = config.amethyst.amethystHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "berry_harvester",
                displayName = "Berry Harvester",
                description = "Harvests ripe berries from berry trees",
                enabled = config.berries.berryHarvestersEnabled,
                requiredType = config.berries.typeHarvestsBerries.name,
                designatedSpecies = config.berries.berryHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "crop_harvester",
                displayName = "Crop Harvester",
                description = "Harvests mature crops and replants them",
                enabled = config.cropHarvest.cropHarvestersEnabled,
                requiredType = config.cropHarvest.typeHarvestsCrops.name,
                designatedSpecies = config.cropHarvest.cropHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "mint_harvester",
                displayName = "Mint Harvester",
                description = "Harvests mature mint plants",
                enabled = config.mints.mintHarvestersEnabled,
                requiredType = config.mints.typeHarvestsMints.name,
                designatedSpecies = config.mints.mintHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "netherwart_harvester",
                displayName = "Nether Wart Harvester",
                description = "Harvests mature nether wart and replants",
                enabled = config.netherwartHarvest.netherwartHarvestersEnabled,
                requiredType = config.netherwartHarvest.typeHarvestsNetherwart.name,
                designatedSpecies = config.netherwartHarvest.netherwartHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "tumblestone_harvester",
                displayName = "Tumblestone Harvester",
                description = "Harvests tumblestone clusters",
                enabled = config.tumblestone.tumblestoneHarvestersEnabled,
                requiredType = config.tumblestone.typeHarvestsTumblestone.name,
                designatedSpecies = config.tumblestone.tumblestoneHarvesters.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),

            // --- Crop Irrigator ---
            JobRule(
                id = "crop_irrigator",
                displayName = "Crop Irrigator",
                description = "Hydrates nearby farmland for faster crop growth",
                enabled = config.irrigation.cropIrrigatorsEnabled,
                requiredType = config.irrigation.typeIrrigatesCrops.name,
                designatedSpecies = config.irrigation.cropIrrigators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),

            // --- Cauldron Generators ---
            JobRule(
                id = "lava_generator",
                displayName = "Lava Generator",
                description = "Fills empty cauldrons with lava",
                enabled = config.lava.lavaGeneratorsEnabled,
                requiredType = config.lava.typeGeneratesLava.name,
                designatedSpecies = config.lava.lavaGenerators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "water_generator",
                displayName = "Water Generator",
                description = "Fills empty cauldrons with water",
                enabled = config.water.waterGeneratorsEnabled,
                requiredType = config.water.typeGeneratesWater.name,
                designatedSpecies = config.water.waterGenerators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "snow_generator",
                displayName = "Snow Generator",
                description = "Fills empty cauldrons with powder snow",
                enabled = config.snow.snowGeneratorsEnabled,
                requiredType = config.snow.typeGeneratesSnow.name,
                designatedSpecies = config.snow.snowGenerators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),

            // --- Fuel Generators ---
            JobRule(
                id = "fuel_generator",
                displayName = "Furnace Fuel Generator",
                description = "Adds fuel to nearby furnaces",
                enabled = config.fuel.fuelGeneratorsEnabled,
                requiredType = config.fuel.typeGeneratesFuel.name,
                designatedSpecies = config.fuel.fuelGenerators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "brewing_stand_fuel_generator",
                displayName = "Brewing Fuel Generator",
                description = "Adds blaze fuel to nearby brewing stands",
                enabled = config.brewingStandFuel.fuelGeneratorsEnabled,
                requiredType = config.brewingStandFuel.typeGeneratesFuel.name,
                designatedSpecies = config.brewingStandFuel.fuelGenerators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),

            // --- Fishing ---
            JobRule(
                id = "fishing_loot_generator",
                displayName = "Fisher",
                description = "Catches fish and deposits them into containers",
                enabled = config.fishing.fishingLootGeneratorsEnabled,
                requiredType = config.fishing.typeGeneratesFishingLoot.name,
                designatedSpecies = config.fishing.fishingLootGenerators.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),

            // --- Looters ---
            JobRule(
                id = "pickup_looter",
                displayName = "Pickup Looter",
                description = "Uses Pickup ability to find random loot",
                enabled = config.pickup.pickUpLootersEnabled,
                requiredType = null,
                designatedSpecies = emptyList(),
                requiredMoves = emptyList(),
                requiredAbility = "pickup",
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "MOVE",
            ),
            JobRule(
                id = "dive_looter",
                displayName = "Dive Looter",
                description = "Dives underwater to retrieve treasure",
                enabled = config.diving.divingLootersEnabled,
                requiredType = null,
                designatedSpecies = emptyList(),
                requiredMoves = listOf("dive"),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "MOVE",
            ),

            // --- Utility ---
            JobRule(
                id = "ground_item_gatherer",
                displayName = "Ground Item Gatherer",
                description = "Picks up dropped items and stores them",
                enabled = config.groundItemGathering.groundItemGatheringEnabled,
                requiredType = config.groundItemGathering.typeGathersGroundItems.name,
                designatedSpecies = config.groundItemGathering.groundItemGatherers.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "fire_extinguisher",
                displayName = "Fire Extinguisher",
                description = "Extinguishes nearby fires and burning blocks",
                enabled = config.extinguisher.extinguishersEnabled,
                requiredType = config.extinguisher.typeExtinguishesFire.name,
                designatedSpecies = config.extinguisher.extinguishers.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "honey_collector",
                displayName = "Honey Collector",
                description = "Collects honey from nearby beehives",
                enabled = config.honey.honeyCollectorsEnabled,
                requiredType = config.honey.typeHarvestsHoney.name,
                designatedSpecies = config.honey.honeyCollectors.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = listOf("combee", "vespiquen"),
                hardcodedSpeciesEnabled = config.honey.combeeLineCollectsHoney,
                priority = "TYPE",
            ),
            JobRule(
                id = "archeologist",
                displayName = "Archeologist",
                description = "Brushes suspicious blocks to find artifacts",
                enabled = config.archeology.archeologistsEnabled,
                requiredType = config.archeology.typeDoesArcheology.name,
                designatedSpecies = config.archeology.archeologists.toList(),
                requiredMoves = emptyList(),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "TYPE",
            ),
            JobRule(
                id = "healer",
                displayName = "Healer",
                description = "Heals nearby players with regeneration",
                enabled = config.healing.healersEnabled,
                requiredType = null,
                designatedSpecies = config.healing.healers.toList(),
                requiredMoves = config.healing.healingMoves.toList(),
                requiredAbility = null,
                hardcodedSpecies = listOf("happiny", "chansey", "blissey"),
                hardcodedSpeciesEnabled = config.healing.chanseyLineHealsPlayers,
                priority = "MOVE",
            ),
            JobRule(
                id = "scout",
                displayName = "Scout",
                description = "Creates explorer maps to nearby structures",
                enabled = config.scouts.scoutsEnabled,
                requiredType = config.scouts.typeScouts.name,
                designatedSpecies = config.scouts.scouts.toList(),
                requiredMoves = listOf("fly"),
                requiredAbility = null,
                hardcodedSpecies = emptyList(),
                hardcodedSpeciesEnabled = false,
                priority = "MOVE",
            ),
        )
    }
}
