/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.jobs.registry.*

/**
 * Central registry of all Worker instances.
 * Each category registers its jobs via register() calls.
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

    private var initialized = false

    fun init() {
        if (initialized) {
            CobbleCrew.LOGGER.warn("[CobbleCrew] WorkerRegistry.init() called again — skipping")
            return
        }
        initialized = true

        GatheringJobs.register()
        ProductionJobs.register()
        ProcessingJobs.register()
        PlacementJobs.register()
        DefenseJobs.register()
        SupportJobs.register()
        EnvironmentalJobs.register()
        LogisticsJobs.register()
        FactoryJobs.register()
        ComboJobs.register()

        CobbleCrew.LOGGER.info("[CobbleCrew] Registered ${_workers.size} jobs")
    }
}
