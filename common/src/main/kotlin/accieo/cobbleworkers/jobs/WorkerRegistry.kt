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
import accieo.cobbleworkers.jobs.registry.*

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

    fun init() {
        GatheringJobs.register()
        ProductionJobs.register()
        ProcessingJobs.register()
        PlacementJobs.register()
        DefenseJobs.register()
        SupportJobs.register()
        EnvironmentalJobs.register()
        LogisticsJobs.register()
        ComboJobs.register()

        Cobbleworkers.LOGGER.info("[Cobbleworkers] Registered ${_workers.size} jobs")
    }
}
