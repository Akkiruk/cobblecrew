/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.WorkerVisualUtils
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
    private val generalConfig get() = CobbleworkersConfigHolder.config.general
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

    override fun isAvailable(world: World, origin: BlockPos, pokemonId: java.util.UUID): Boolean {
        val players = findNearbyPlayers(world, origin)
        return players.any { player ->
            val effectOk = if (skipIfAlreadyActive) !player.hasStatusEffect(statusEffect) else true
            val healthOk = !requiresDamage || player.health < player.maxHealth
            effectOk && healthOk
        }
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val nearbyPlayers = findNearbyPlayers(world, origin)
        if (nearbyPlayers.isEmpty()) {
            CobbleworkersNavigationUtils.releasePlayerTarget(pokemonId)
            return
        }

        val target = nearbyPlayers
            .filter { player ->
                if (skipIfAlreadyActive) !player.hasStatusEffect(statusEffect) else true
            }
            .filter { !requiresDamage || it.health < it.maxHealth }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        val currentTarget = CobbleworkersNavigationUtils.getPlayerTarget(pokemonId, world)

        if (currentTarget == null) {
            if (!CobbleworkersNavigationUtils.isPlayerTargeted(target, world)) {
                CobbleworkersNavigationUtils.claimTarget(pokemonId, target, world)
                CobbleworkersNavigationUtils.navigateToPlayer(pokemonEntity, target)
            }
            return
        }

        CobbleworkersNavigationUtils.navigateToPlayer(pokemonEntity, target)

        if (WorkerVisualUtils.handlePlayerArrival(pokemonEntity, target, world, arrivalParticle)) {
            applyEffect(target)
            CobbleworkersNavigationUtils.releasePlayerTarget(pokemonId)
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
        CobbleworkersNavigationUtils.releasePlayerTarget(pokemonId)
    }
}
