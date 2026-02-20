/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.integration.cobbledex

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Exports job eligibility rules as a JSON file for CobbleDex.
 * CobbleDex reads this file at runtime — no compile-time or reflection dependency.
 */
object CobbledexDataProvider {

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

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val OUTPUT_FILE: Path = Path.of("config", "cobbleworkers", "cobbledex-job-rules.json")

    /**
     * Writes all job rules to config/cobbleworkers/cobbledex-job-rules.json.
     * Called during mod init so CobbleDex can read the file.
     */
    fun writeJobRulesFile() {
        try {
            OUTPUT_FILE.parent.createDirectories()
            val rules = buildJobRules()
            OUTPUT_FILE.writeText(gson.toJson(rules))
            Cobbleworkers.LOGGER.info("[Cobbleworkers] Wrote ${rules.size} job rules for CobbleDex")
        } catch (e: Exception) {
            Cobbleworkers.LOGGER.warn("[Cobbleworkers] Failed to write CobbleDex job rules: ${e.message}")
        }
    }

    private fun buildJobRules(): List<JobRule> {
        val config = CobbleworkersConfigHolder.config
        val rules = mutableListOf<JobRule>()

        // --- Harvesters ---

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        // --- Crop Irrigator ---

        rules += JobRule(
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
        )

        // --- Cauldron Generators ---

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        // --- Fuel Generators ---

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        // --- Fishing ---

        rules += JobRule(
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
        )

        // --- Looters ---

        rules += JobRule(
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
        )

        rules += JobRule(
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
        )

        // --- Ground Item Gatherer ---

        rules += JobRule(
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
        )

        // --- Fire Extinguisher ---

        rules += JobRule(
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
        )

        // --- Honey Collector ---

        rules += JobRule(
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
        )

        // --- Archeologist ---

        rules += JobRule(
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
        )

        // --- Healer ---

        rules += JobRule(
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
        )

        // --- Scout ---

        rules += JobRule(
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
        )

        return rules
    }
}
