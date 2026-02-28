/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

/**
 * Base class for support-style workers that find nearby players and apply
 * positive status effects. Generalizes the Healer pattern.
 *
 * Pattern: find player without effect → navigate → arrive → apply effect → release.
 */
abstract class BaseSupport : Worker {
    private val generalConfig get() = CobbleCrewConfigHolder.config.general
    protected val searchRadius get() = generalConfig.searchRadius
    protected val searchHeight get() = generalConfig.searchHeight

    open val arrivalParticle: ParticleEffect = ParticleTypes.HEART

    /** The status effect to apply. */
    abstract val statusEffect: RegistryEntry<StatusEffect>

    /** Duration of the effect in ticks. */
    abstract val effectDurationTicks: Int

    /** Amplifier level (0 = level I, 1 = level II, etc). */
    open val effectAmplifier: Int = 0

    /** Whether to skip players who already have this effect. */
    open val skipIfAlreadyActive: Boolean = true

    /** Whether this support job only targets damaged players (e.g. Healer). */
    open val requiresDamage: Boolean = false

    /**
     * Work speed boost percentage this support Pokémon provides to nearby workers.
     * 0 = no boost. 15 = nearby workers' cooldowns are 15% shorter.
     * Multiple support Pokémon stack diminishingly, capped at 40% total.
     */
    open val workBoostPercent: Int = 0

    /**
     * Per-Pokémon cooldown after applying an effect. Prevents the tight loop where
     * the Pokémon applies → releases → re-assigns → re-applies every few ticks.
     * Cooldown = half the effect duration so it waits near the player.
     */
    private val cooldownUntil = mutableMapOf<UUID, Long>()
    private companion object {
        const val MIN_COOLDOWN_TICKS = 200L  // 10 seconds minimum
    }

    override fun hasActiveState(pokemonId: UUID): Boolean = pokemonId in cooldownUntil

    override fun isAvailable(context: JobContext, pokemonId: java.util.UUID): Boolean {
        // Still on cooldown from last application
        cooldownUntil[pokemonId]?.let { until ->
            if (context.world.time < until) return false
            cooldownUntil.remove(pokemonId)
        }

        val players = findNearbyPlayers(context.world, context.origin)
        return players.any { player ->
            val effectOk = if (skipIfAlreadyActive) !player.hasStatusEffect(statusEffect) else true
            val healthOk = !requiresDamage || player.health < player.maxHealth
            effectOk && healthOk
        }
    }

    override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val origin = context.origin
        val pokemonId = pokemonEntity.pokemon.uuid

        // Register work speed aura while this support Pokémon is active
        if (workBoostPercent > 0) {
            WorkSpeedBoostManager.registerBoost(origin, pokemonId, workBoostPercent, world.time)
        }

        // On cooldown — just idle near the target, don't re-evaluate
        cooldownUntil[pokemonId]?.let { until ->
            if (world.time < until) return
            cooldownUntil.remove(pokemonId)
        }

        val nearbyPlayers = findNearbyPlayers(world, origin)
        if (nearbyPlayers.isEmpty()) {
            CobbleCrewNavigationUtils.releasePlayerTarget(pokemonId)
            return
        }

        val target = nearbyPlayers
            .filter { player ->
                if (skipIfAlreadyActive) !player.hasStatusEffect(statusEffect) else true
            }
            .filter { !requiresDamage || it.health < it.maxHealth }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        val currentTarget = CobbleCrewNavigationUtils.getPlayerTarget(pokemonId, world)

        if (currentTarget == null) {
            if (!CobbleCrewNavigationUtils.isPlayerTargeted(target, world)) {
                CobbleCrewDebugLogger.supportTargetFound(pokemonEntity, name, target.name.string)
                CobbleCrewNavigationUtils.claimTarget(pokemonId, target, world)
                CobbleCrewNavigationUtils.navigateToPlayer(pokemonEntity, target)
            }
            return
        }

        CobbleCrewNavigationUtils.navigateToPlayer(pokemonEntity, target)

        if (WorkerVisualUtils.handlePlayerArrival(pokemonEntity, target, world, arrivalParticle)) {
            applyEffect(target)
            CobbleCrewDebugLogger.supportEffectApplied(pokemonEntity, name, target.name.string)
            CobbleCrewNavigationUtils.releasePlayerTarget(pokemonId)
            // Cooldown: wait at least MIN_COOLDOWN_TICKS or half the effect duration
            val cooldown = maxOf(MIN_COOLDOWN_TICKS, effectDurationTicks / 2L)
            cooldownUntil[pokemonId] = world.time + cooldown
        }
    }

    protected open fun applyEffect(player: PlayerEntity) {
        if (!player.hasStatusEffect(statusEffect)) {
            player.addStatusEffect(StatusEffectInstance(statusEffect, effectDurationTicks, effectAmplifier))
        }
    }

    private fun findNearbyPlayers(world: World, origin: BlockPos): List<PlayerEntity> {
        val searchBox = Box(origin).expand(searchRadius.toDouble(), searchHeight.toDouble(), searchRadius.toDouble())
        return world.getEntitiesByClass(PlayerEntity::class.java, searchBox) { true }
    }

    override fun cleanup(pokemonId: UUID) {
        CobbleCrewNavigationUtils.releasePlayerTarget(pokemonId)
        cooldownUntil.remove(pokemonId)
    }
}
