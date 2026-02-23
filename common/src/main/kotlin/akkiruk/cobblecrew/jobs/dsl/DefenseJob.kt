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
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseDefender
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.world.World

/**
 * DSL-style defense job. Finds hostile mobs and applies effects.
 */
open class DefenseJob(
    override val name: String,
    val category: String = "defense",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    val particle: ParticleEffect = ParticleTypes.CRIT,
    val phase: WorkPhase = WorkPhase.ATTACKING,
    val effectFn: (World, PokemonEntity, HostileEntity) -> Unit,
) : BaseDefender() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val attackParticle: ParticleEffect = particle
    override val combatPhase: WorkPhase = phase

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
        if (moves.any { it in effectiveMoves }) return true
        val sp = config.fallbackSpecies.ifEmpty { fallbackSpecies }
        return sp.any { it.equals(species, ignoreCase = true) }
    }

    override fun applyEffect(world: World, pokemonEntity: PokemonEntity, target: HostileEntity) {
        effectFn(world, pokemonEntity, target)
    }
}
