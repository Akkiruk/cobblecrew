/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.world.World

/**
 * DSL-style production job. Self-generates items on cooldown without target blocks.
 */
open class ProductionJob(
    override val name: String,
    override val category: String = "production",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackSpecies: List<String> = emptyList(),
    val defaultCooldownSeconds: Int = 120,
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.STANDARD,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val output: (World, PokemonEntity) -> List<ItemStack>,
    val isCombo: Boolean = false,
) : BaseJob() {

    override val arrivalParticle: ParticleEffect = particle
    override val targetCategory: BlockCategory? = null
    override val requiresTarget: Boolean = false
    override val workPhase: WorkPhase = WorkPhase.PRODUCING

    override fun buildDefaultConfig() = JobConfig(
        enabled = true,
        cooldownSeconds = defaultCooldownSeconds,
        qualifyingMoves = qualifyingMoves.toList(),
        fallbackSpecies = fallbackSpecies,
    )

    init { registerConfig() }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    // Never called — requiresTarget = false skips navigation
    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? = null

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.PRODUCING, context.world)
        return WorkResult.Done(output(context.world, pokemonEntity))
    }

    override fun getCooldownTicks(state: PokemonWorkerState): Long =
        (config.cooldownSeconds.takeIf { it > 0 } ?: defaultCooldownSeconds) * 20L
}
