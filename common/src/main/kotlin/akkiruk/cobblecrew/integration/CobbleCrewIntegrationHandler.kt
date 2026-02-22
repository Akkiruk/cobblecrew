/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.integration

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.interfaces.ModIntegrationHelper
import akkiruk.cobblecrew.utilities.CobbleCrewCropUtils
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

class CobbleCrewIntegrationHandler(private val helper: ModIntegrationHelper) {
    private val FARMERS_DELIGHT = "farmersdelight"
    private val SOPHISTICATED_STORAGE = "sophisticatedstorage"

    /**
     * Runs all relevant functions to add integrations
     */
    fun addIntegrations() {
        addFarmersDelight()
        addSophisticatedStorage()
    }

    /**
     * Fetches mod blocks from block registry
     */
    private fun getModBlocks(modId: String, names: List<String>): Set<Block> {
        return names.mapNotNull { name ->
            Registries.BLOCK.getOrEmpty(Identifier.of(modId, name)).orElse(null)
        }.toSet()
    }

    /**
     * Adds integration for farmer's delight
     */
    private fun addFarmersDelight() {
        if (!helper.isModLoaded(FARMERS_DELIGHT)) return

        val farmersDelightCrops = getModBlocks(
            FARMERS_DELIGHT,
            FarmersDelightBlocks.ALL.toList()
        )

        CobbleCrewCropUtils.addCompatibility(farmersDelightCrops)
        CobbleCrew.LOGGER.info("Added integration for farmer's delight!")
    }

    /**
     * Adds integration for sophisticated storage.
     * TODO: Not yet implemented — waiting for upstream API support.
     */
    private fun addSophisticatedStorage() {
        if (!helper.isModLoaded(SOPHISTICATED_STORAGE)) return

        // val sophisticatedStorageBlocks = getModBlocks(
        //     SOPHISTICATED_STORAGE,
        //     listOf("limited_barrel_1", "limited_barrel_2", "limited_barrel_3", "limited_barrel_4")
        // )
        // CobbleCrewInventoryUtils.addCompatibility(sophisticatedStorageBlocks)
    }
}