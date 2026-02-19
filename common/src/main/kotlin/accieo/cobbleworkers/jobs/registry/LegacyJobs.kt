/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.*

/**
 * Registers the original 22 legacy jobs.
 * These will be migrated to DSL-based definitions in later phases.
 */
object LegacyJobs {
    fun register() {
        WorkerRegistry.registerAll(
            ApricornHarvester,
            AmethystHarvester,
            Archeologist,
            BerryHarvester,
            BrewingStandFuelGenerator,
            CropHarvester,
            CropIrrigator,
            DiveLooter,
            FireExtinguisher,
            FishingLootGenerator,
            FuelGenerator,
            GroundItemGatherer,
            Healer,
            HoneyCollector,
            LavaGenerator,
            MintHarvester,
            NetherwartHarvester,
            PickUpLooter,
            Scout,
            SnowGenerator,
            TumblestoneHarvester,
            WaterGenerator,
        )
    }
}
