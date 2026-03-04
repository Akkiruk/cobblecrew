/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BasePlacer
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * DSL-style placement job. Takes items from containers and places them as blocks.
 */
class PlacementJob(
    override val name: String,
    val category: String = "placement",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.LOW,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val itemCheck: (ItemStack) -> Boolean,
    val findTarget: (World, BlockPos) -> BlockPos?,
    val placeFn: (World, BlockPos, ItemStack) -> Unit,
) : BasePlacer() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val placeParticle: ParticleEffect = particle

    init {
        JobConfigManager.registerDefault(category, name, JobConfig(
            enabled = true,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackType = fallbackType,
            fallbackSpecies = fallbackSpecies,
        ))
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, types = types)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, types = types)

    override fun itemPredicate(stack: ItemStack): Boolean = itemCheck(stack)
    override fun findPlacementTarget(world: World, origin: BlockPos): BlockPos? = findTarget(world, origin)
    override fun placeBlock(world: World, pos: BlockPos, item: ItemStack) = placeFn(world, pos, item)
}
