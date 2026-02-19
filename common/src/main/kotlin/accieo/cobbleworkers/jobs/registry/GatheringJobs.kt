/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.jobs.WorkerRegistry

/**
 * Gathering job definitions (A1–A37+). New DSL-based gathering jobs go here.
 * Existing legacy harvester jobs remain as objects in jobs/ until migrated.
 */
object GatheringJobs {
    // New DSL-based gathering jobs will be defined here in Phase 2.
    // Example:
    // val OVERWORLD_LOGGER = GatheringJob(
    //     name = "overworld_logger",
    //     targetCategory = BlockCategory.LOG_OVERWORLD,
    //     qualifyingMoves = setOf("cut", "furycutter"),
    // )

    fun register() {
        // Phase 2: WorkerRegistry.registerAll(OVERWORLD_LOGGER, JUNGLE_CHOPPER, ...)
    }
}
