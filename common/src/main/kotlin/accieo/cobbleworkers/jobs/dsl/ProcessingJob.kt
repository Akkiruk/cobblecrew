/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.dsl

import accieo.cobbleworkers.config.JobConfig
import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.WorkerPriority
import accieo.cobbleworkers.jobs.BaseProcessor
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes

/**
 * DSL-style processing job. Pulls items from barrels, transforms, deposits in chests.
 */
open class ProcessingJob(
    override val name: String,
    val category: String = "processing",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    val particle: ParticleEffect = ParticleTypes.SMOKE,
    val inputCheck: (ItemStack) -> Boolean,
    val transformFn: (ItemStack) -> List<ItemStack>,
) : BaseProcessor() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val processParticle: ParticleEffect = particle

    fun defaultConfig(): JobConfig = JobConfig(
        enabled = true,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackType = fallbackType,
        fallbackSpecies = fallbackSpecies,
    )

    init {
        JobConfigManager.registerDefault(category, name, defaultConfig())
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
        if (!config.enabled) return false
        val effectiveMoves = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
        return moves.any { it in effectiveMoves }
    }

    override fun inputPredicate(stack: ItemStack): Boolean = inputCheck(stack)
    override fun transform(input: ItemStack): List<ItemStack> = transformFn(input)
}
