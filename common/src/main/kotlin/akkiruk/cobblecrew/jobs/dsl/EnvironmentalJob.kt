/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * DSL-style environmental job. Navigates to a block, performs an in-world action,
 * then releases. No item harvesting/depositing — pure block modification.
 *
 * Handles two patterns:
 * - **Instant**: find block → navigate → arrive → act → release (FrostFormer, ObsidianForge, etc.)
 * - **Cooldown**: same as instant, but requires a per-Pokémon cooldown between actions (cauldron fillers, fuelers)
 *
 * Usage:
 * ```
 * val FIRE_DOUSER = EnvironmentalJob(
 *     name = "fire_douser",
 *     category = "environmental",
 *     targetCategory = BlockCategory.FIRE,
 *     qualifyingMoves = setOf("waterpulse", "raindance"),
 *     particle = ParticleTypes.SMOKE,
 *     findTarget = { world, origin -> ... },
 *     action = { world, pos -> world.setBlockState(pos, Blocks.AIR.defaultState) },
 * )
 * ```
 */
open class EnvironmentalJob(
    override val name: String,
    val category: String = "environmental",
    override val targetCategory: BlockCategory,
    override val additionalScanCategories: Set<BlockCategory> = emptySet(),
    val qualifyingMoves: Set<String> = emptySet(),
    val fallbackSpecies: List<String> = emptyList(),
    override val priority: WorkerPriority = WorkerPriority.MOVE,
    override val importance: JobImportance = JobImportance.LOW,
    val particle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val defaultCooldownSeconds: Int = 0,
    val defaultRadius: Int? = null,
    val defaultBurnTimeSeconds: Int? = null,
    val defaultAddedFuel: Int? = null,
    val findTarget: (World, BlockPos) -> BlockPos?,
    val action: (World, BlockPos) -> Unit,
    val validate: ((World, BlockPos) -> Boolean)? = null,
) : Worker {

    val config get() = JobConfigManager.get(name)
    private val targets = mutableMapOf<UUID, BlockPos>()
    private val lastActionTime = mutableMapOf<UUID, Long>()

    init {
        val defaultConfig = JobConfig(
            enabled = true,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackSpecies = fallbackSpecies,
            cooldownSeconds = if (defaultCooldownSeconds > 0) defaultCooldownSeconds else 30,
            radius = defaultRadius,
            burnTimeSeconds = defaultBurnTimeSeconds,
            addedFuel = defaultAddedFuel,
        )
        JobConfigManager.registerDefault(category, name, defaultConfig)
    }

    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean =
        dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

    override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
        val found = findTarget(context.world, context.origin) ?: return false
        return !CobbleCrewNavigationUtils.isTargeted(found, context.world)
    }

    override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val origin = context.origin
        val pid = pokemonEntity.pokemon.uuid
        val now = world.time

        // Cooldown check (skip if no cooldown configured)
        if (defaultCooldownSeconds > 0) {
            val last = lastActionTime[pid] ?: 0L
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: defaultCooldownSeconds) * 20L
            val cd = WorkSpeedBoostManager.adjustCooldown(baseCd, origin, now)
            if (now - last < cd) return
        }

        val target = targets[pid]
        if (target == null) {
            val found = findTarget(world, origin) ?: return
            if (!CobbleCrewNavigationUtils.isTargeted(found, world)) {
                CobbleCrewNavigationUtils.claimTarget(pid, found, world)
                targets[pid] = found
                CobbleCrewNavigationUtils.navigateTo(pokemonEntity, found)
            }
            return
        }

        // Optional validation (e.g. block still exists)
        val valid = validate?.invoke(world, target) ?: !world.getBlockState(target).isAir
        if (!valid) {
            CobbleCrewNavigationUtils.releaseTarget(pid, world)
            targets.remove(pid)
            return
        }

        CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
        if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, particle, 3.0, WorkPhase.ENVIRONMENTAL)) {
            action(world, target)
            if (defaultCooldownSeconds > 0) lastActionTime[pid] = now
            CobbleCrewNavigationUtils.releaseTarget(pid, world, blacklist = false)
            targets.remove(pid)
        }
    }

    override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
    override fun cleanup(pokemonId: UUID) {
        targets.remove(pokemonId)
        lastActionTime.remove(pokemonId)
    }
}
