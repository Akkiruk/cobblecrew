/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

/**
 * Base class for defense workers that find hostile mobs in range,
 * navigate to them, and apply damage or status effects.
 *
 * Pattern: find hostile → claim → navigate → arrive → apply effect → release.
 */
abstract class BaseDefender : Worker {
    private val generalConfig get() = CobbleCrewConfigHolder.config.general
    protected val searchRadius get() = generalConfig.searchRadius
    protected val searchHeight get() = generalConfig.searchHeight

    open val attackParticle: ParticleEffect = ParticleTypes.CRIT

    private val lastHostileScan = mutableMapOf<UUID, Long>()
    private val cachedHostiles = mutableMapOf<UUID, List<HostileEntity>>()
    private companion object { const val HOSTILE_SCAN_INTERVAL = 20L }

    /** Apply the defense effect to the target mob. */
    abstract fun applyEffect(world: World, pokemonEntity: PokemonEntity, target: HostileEntity)

    override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
        val searchBox = Box(origin).expand(searchRadius.toDouble(), searchHeight.toDouble(), searchRadius.toDouble())
        return world.getEntitiesByClass(HostileEntity::class.java, searchBox) { it.isAlive }.isNotEmpty()
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid

        val currentMobTarget = CobbleCrewNavigationUtils.getMobTarget(pokemonId)
        if (currentMobTarget != null) {
            val targetMob = world.getEntityById(currentMobTarget) as? HostileEntity
            if (targetMob == null || !targetMob.isAlive) {
                CobbleCrewNavigationUtils.releaseMobTarget(pokemonId)
                return
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, targetMob.blockPos)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, targetMob.blockPos, world, attackParticle, 2.0)) {
                applyEffect(world, pokemonEntity, targetMob)
                CobbleCrewNavigationUtils.releaseMobTarget(pokemonId)
            }
            return
        }

        // Find a new hostile target (throttled to every 20 ticks)
        val now = world.time
        val hostiles = if (now - (lastHostileScan[pokemonId] ?: 0L) >= HOSTILE_SCAN_INTERVAL) {
            lastHostileScan[pokemonId] = now
            findNearbyHostiles(world, origin).also { cachedHostiles[pokemonId] = it }
        } else {
            cachedHostiles[pokemonId] ?: emptyList()
        }
        if (hostiles.isEmpty()) return

        val target = hostiles
            .filter { !CobbleCrewNavigationUtils.isMobTargeted(it.id) }
            .minByOrNull { it.squaredDistanceTo(pokemonEntity.pos) }
            ?: return

        CobbleCrewNavigationUtils.claimMobTarget(pokemonId, target.id)
    }

    private fun findNearbyHostiles(world: World, origin: BlockPos): List<HostileEntity> {
        val searchBox = Box(origin).expand(searchRadius.toDouble(), searchHeight.toDouble(), searchRadius.toDouble())
        return world.getEntitiesByClass(HostileEntity::class.java, searchBox) { it.isAlive }
    }

    override fun cleanup(pokemonId: UUID) {
        CobbleCrewNavigationUtils.releaseMobTarget(pokemonId)
        lastHostileScan.remove(pokemonId)
        cachedHostiles.remove(pokemonId)
    }
}
