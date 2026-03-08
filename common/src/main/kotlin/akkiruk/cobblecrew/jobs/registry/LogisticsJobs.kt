/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.jobs.dsl.dslEligible
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * Logistics jobs — all removed in v3 schema migration.
 */
object LogisticsJobs {
    fun register() {
        // All logistics jobs removed
    }
}
