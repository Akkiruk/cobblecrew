/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.dsl

import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import java.util.UUID

/**
 * DSL-style support job. Finds nearby players needing a status effect,
 * navigates to them, and applies the effect.
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
    val statusEffect: RegistryEntry<StatusEffect>,
    val defaultDurationSeconds: Int = 20,
    val effectAmplifier: Int = 0,
    val requiresDamage: Boolean = false,
    val workBoostPercent: Int = 0,
    val isCombo: Boolean = false,
) : BaseJob() {

    private val generalConfig get() = CobbleCrewConfigHolder.config.general

    override val arrivalParticle: ParticleEffect = particle
    override val targetCategory: BlockCategory? = null
    override val workPhase: WorkPhase = WorkPhase.HEALING
    override val producesItems: Boolean = false
    override val config: JobConfig get() = JobConfigManager.get(name)

    protected val effectDurationTicks: Int get() =
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

    override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
        if (!config.enabled) return false
        return findNearbyPlayers(context).any { needsEffect(it) }
    }

    override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
        val player = findNearbyPlayers(context)
            .filter { needsEffect(it) }
            .minByOrNull { it.blockPos.getSquaredDistance(context.origin) }
            ?: return null
        return Target.Player(player.uuid, player.blockPos)
    }

    override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
        val claim = state.claim ?: return false
        val playerTarget = claim.target as? Target.Player ?: return false
        val player = (context.world as? ServerWorld)?.getEntity(playerTarget.playerId) as? ServerPlayerEntity
            ?: return false
        if (!player.isAlive || !needsEffect(player)) return false
        state.targetPos = player.blockPos
        return true
    }

    override fun checkArrival(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity): Boolean {
        val claim = state.claim ?: return false
        val playerTarget = claim.target as? Target.Player ?: return false
        val player = (context.world as? ServerWorld)?.getEntity(playerTarget.playerId) as? PlayerEntity
            ?: return false
        return WorkerVisualUtils.handlePlayerArrival(entity, player, context.world, arrivalParticle, workPhase)
    }

    override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
        val claim = state.claim ?: return WorkResult.Done()
        val playerTarget = claim.target as? Target.Player ?: return WorkResult.Done()
        val player = (context.world as? ServerWorld)?.getEntity(playerTarget.playerId) as? PlayerEntity
            ?: return WorkResult.Done()
        applyEffect(player)
        return WorkResult.Done()
    }

    /** Apply the status effect. Override for custom behavior (cleanse, multi-buff). */
    protected open fun applyEffect(player: PlayerEntity) {
        if (!player.hasStatusEffect(statusEffect)) {
            player.addStatusEffect(StatusEffectInstance(statusEffect, effectDurationTicks, effectAmplifier))
        }
    }

    override fun getCooldownTicks(state: PokemonWorkerState): Long =
        maxOf(200L, effectDurationTicks / 2L)

    private fun needsEffect(player: PlayerEntity): Boolean {
        val effectOk = !player.hasStatusEffect(statusEffect)
        val healthOk = !requiresDamage || player.health < player.maxHealth
        return effectOk && healthOk
    }

    private fun findNearbyPlayers(context: JobContext): List<PlayerEntity> {
        val r = generalConfig.searchRadius.toDouble()
        val h = generalConfig.searchHeight.toDouble()
        val searchBox = Box(context.origin).expand(r, h, r)
        return context.world.getEntitiesByClass(PlayerEntity::class.java, searchBox) { true }
    }
}
