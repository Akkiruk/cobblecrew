/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew

import akkiruk.cobblecrew.config.CobbleCrewConfigInitializer
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.jobs.WorkerRegistry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object CobbleCrew {
    const val MODID = "cobblecrew"

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(MODID)

    fun init() {
        LOGGER.info("Launching {}...", MODID)

        CobbleCrewConfigInitializer.init()
        WorkerRegistry.init()      // must run before load() — DSL jobs register defaults here
        JobConfigManager.load()
    }
}