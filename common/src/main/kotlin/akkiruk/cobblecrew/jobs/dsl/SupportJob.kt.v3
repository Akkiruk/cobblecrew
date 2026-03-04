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
import akkiruk.cobblecrew.jobs.BaseSupport
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.entry.RegistryEntry

/**
 * DSL-style support job. Applies status effects to nearby players.
 */
open class SupportJob(
    override val name: String,
    val category: String = "support",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.STANDARD,
    val particle: ParticleEffect = ParticleTypes.HEART,
    override val statusEffect: RegistryEntry<StatusEffect>,
    val defaultDurationSeconds: Int = 20,
    override val effectAmplifier: Int = 0,
    override val requiresDamage: Boolean = false,
    override val workBoostPercent: Int = 0,
    val isCombo: Boolean = false,
) : BaseSupport() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val arrivalParticle: ParticleEffect = particle
    override val effectDurationTicks: Int get() =
        (config.effectDurationSeconds ?: defaultDurationSeconds) * 20

    init {
        JobConfigManager.registerDefault(category, name, JobConfig(
            enabled = true,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackSpecies = fallbackSpecies,
            effectDurationSeconds = defaultDurationSeconds,
            effectAmplifier = effectAmplifier,
        ))
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)
}
