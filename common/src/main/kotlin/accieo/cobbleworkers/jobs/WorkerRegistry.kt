/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.interfaces.Worker

/**
 * Central registry of all Worker instances.
 * Replaces the hardcoded list in WorkerDispatcher.
 * Each category's init function adds its jobs here.
 */
object WorkerRegistry {
    private val _workers = mutableListOf<Worker>()
    val workers: List<Worker> get() = _workers

    fun register(worker: Worker) {
        _workers.add(worker)
    }

    fun registerAll(vararg workers: Worker) {
        _workers.addAll(workers)
    }

    /**
     * Called once during mod init to register all existing (legacy) jobs.
     * New DSL-based jobs will register via their category init blocks in later phases.
     */
    fun init() {
        registerAll(
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

        Cobbleworkers.LOGGER.info("[Cobbleworkers] Registered ${_workers.size} jobs")
    }
}
