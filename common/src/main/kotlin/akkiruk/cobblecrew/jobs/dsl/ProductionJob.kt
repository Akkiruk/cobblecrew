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
import akkiruk.cobblecrew.jobs.BaseProducer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * DSL-style production job. Self-generates items on cooldown without target blocks.
 */
open class ProductionJob(
    override val name: String,
    val category: String = "production",
    val qualifyingMoves: Set<String> = emptySet(),
    val typeGatedMoves: Map<String, String> = emptyMap(),
    val fallbackType: String = "",
    val fallbackSpecies: List<String> = emptyList(),
    val defaultCooldownSeconds: Int = 120,
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.STANDARD,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val output: (World, PokemonEntity) -> List<ItemStack>,
    val isCombo: Boolean = false,
) : BaseProducer() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val productionParticle: ParticleEffect = particle
    override val cooldownTicks: Long get() = (config.cooldownSeconds.takeIf { it > 0 } ?: defaultCooldownSeconds) * 20L

    init {
        JobConfigManager.registerDefault(category, name, JobConfig(
            enabled = true,
            cooldownSeconds = defaultCooldownSeconds,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackType = fallbackType,
            fallbackSpecies = fallbackSpecies,
        ))
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species, isCombo)

    override fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity): List<ItemStack> {
        return output(world, pokemonEntity)
    }
}
