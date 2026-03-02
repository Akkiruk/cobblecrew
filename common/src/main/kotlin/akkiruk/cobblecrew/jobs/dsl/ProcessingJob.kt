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
import akkiruk.cobblecrew.jobs.BaseProcessor
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
    override val importance: JobImportance = JobImportance.STANDARD,
    val particle: ParticleEffect = ParticleTypes.SMOKE,
    val inputCheck: (ItemStack) -> Boolean,
    val transformFn: (ItemStack) -> List<ItemStack>,
    val isCombo: Boolean = false,
) : BaseProcessor() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val processParticle: ParticleEffect = particle

    init {
        JobConfigManager.registerDefault(category, name, JobConfig(
            enabled = true,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackType = fallbackType,
            fallbackSpecies = fallbackSpecies,
        ))
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun inputPredicate(stack: ItemStack): Boolean = inputCheck(stack)
    override fun transform(input: ItemStack): List<ItemStack> = transformFn(input)
}
