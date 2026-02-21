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
import accieo.cobbleworkers.jobs.BaseProducer
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
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val output: (World, PokemonEntity) -> List<ItemStack>,
) : BaseProducer() {

    private val config get() = JobConfigManager.get(name)

    override val targetCategory: BlockCategory? = null
    override val productionParticle: ParticleEffect = particle
    override val cooldownTicks: Long get() = (config.cooldownSeconds.takeIf { it > 0 } ?: defaultCooldownSeconds) * 20L

    fun defaultConfig(): JobConfig = JobConfig(
        enabled = true,
        cooldownSeconds = defaultCooldownSeconds,
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

    override fun produce(world: World, origin: BlockPos, pokemonEntity: PokemonEntity): List<ItemStack> {
        return output(world, pokemonEntity)
    }
}
