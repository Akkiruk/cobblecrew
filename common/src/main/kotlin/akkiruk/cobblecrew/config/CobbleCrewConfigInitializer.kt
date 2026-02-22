/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.config

import akkiruk.cobblecrew.CobbleCrew
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import java.nio.file.Files
import java.nio.file.Paths

object CobbleCrewConfigInitializer {
    fun init() {
        migrateOldConfig()

        val configHolder = AutoConfig.register(CobbleCrewConfig::class.java, ::GsonConfigSerializer)

        CobbleCrewConfigHolder.config = configHolder.get()
    }

    /**
     * One-time migration: copy cobbleworkers configs so existing servers
     * don't lose their settings on upgrade from Cobbleworkers → CobbleCrew.
     */
    private fun migrateOldConfig() {
        try {
            val configDir = Paths.get("config")

            // Migrate main AutoConfig file
            val oldFile = configDir.resolve("cobbleworkers.json5")
            val newFile = configDir.resolve("cobblecrew.json5")
            if (Files.exists(oldFile) && !Files.exists(newFile)) {
                Files.copy(oldFile, newFile)
                CobbleCrew.LOGGER.info("Migrated config/cobbleworkers.json5 → config/cobblecrew.json5")
            }

            // Migrate per-job config directory
            val oldDir = configDir.resolve("cobbleworkers")
            val newDir = configDir.resolve("cobblecrew")
            if (Files.isDirectory(oldDir) && !Files.exists(newDir)) {
                Files.walk(oldDir).use { stream ->
                    stream.forEach { source ->
                        val target = newDir.resolve(oldDir.relativize(source))
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target)
                        } else {
                            Files.copy(source, target)
                        }
                    }
                }
                CobbleCrew.LOGGER.info("Migrated config/cobbleworkers/ → config/cobblecrew/")
            }
        } catch (e: Exception) {
            CobbleCrew.LOGGER.warn("Failed to migrate old config: ${e.message}")
        }
    }
}