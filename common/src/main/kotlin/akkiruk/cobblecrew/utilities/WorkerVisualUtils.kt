/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.enums.WorkPhase
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Handles visual feedback and work delays for Pokémon workers.
 * Provides look-at, particles, animations, and excited flag behavior.
 */
object WorkerVisualUtils {
    private val arrivalTick = mutableMapOf<UUID, Long>()
    private val graceTick = mutableMapOf<UUID, Long>()
    private const val WORK_DELAY_TICKS = 30L // 1.5 seconds
    private const val GRACE_PERIOD_TICKS = 10L

    /**
     * Handles the "working" animation when a Pokémon arrives at its target block.
     * Returns true when the work delay is complete and the action should execute.
     *
     * On first arrival: plays work animation, looks at target, starts timer.
     * During delay: keeps looking at target.
     * On completion: plays completion animation, spawns particles.
     */
    fun handleArrival(
        pokemonEntity: PokemonEntity,
        targetPos: BlockPos,
        world: World,
        particleType: ParticleEffect? = null,
        offset: Double = 3.0,
        workPhase: WorkPhase = WorkPhase.HARVESTING,
    ): Boolean {
        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time

        if (!CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, targetPos, offset)) {
            // Grace period: don't reset timer immediately if briefly bumped out
            if (arrivalTick.containsKey(pokemonId)) {
                val grace = graceTick.getOrPut(pokemonId) { now }
                if (now - grace < GRACE_PERIOD_TICKS) {
                    lookAt(pokemonEntity, targetPos)
                    return false
                }
                arrivalTick.remove(pokemonId)
                graceTick.remove(pokemonId)
            }
            return false
        }

        graceTick.remove(pokemonId)
        val arrived = arrivalTick[pokemonId]

        if (arrived == null) {
            arrivalTick[pokemonId] = now
            WorkerAnimationUtils.playWorkAnimation(pokemonEntity, workPhase, world)
            lookAt(pokemonEntity, targetPos)
            pokemonEntity.navigation.stop()
            return false
        }

        if (now - arrived < WORK_DELAY_TICKS) {
            lookAt(pokemonEntity, targetPos)
            return false
        }

        // Work delay complete
        arrivalTick.remove(pokemonId)
        CobbleCrewDebugLogger.arrivedAtTarget(pokemonEntity, targetPos)
        WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.WORK_COMPLETE, world)
        if (particleType != null) spawnParticles(world, targetPos, particleType)
        return true
    }

    /**
     * Same as handleArrival but for player targets (support jobs).
     */
    fun handlePlayerArrival(
        pokemonEntity: PokemonEntity,
        player: PlayerEntity,
        world: World,
        particleType: ParticleEffect? = null,
        workPhase: WorkPhase = WorkPhase.HEALING,
    ): Boolean {
        if (!CobbleCrewNavigationUtils.isPokemonNearPlayer(pokemonEntity, player)) {
            arrivalTick.remove(pokemonEntity.pokemon.uuid)
            return false
        }

        val pokemonId = pokemonEntity.pokemon.uuid
        val now = world.time
        val arrived = arrivalTick[pokemonId]

        if (arrived == null) {
            arrivalTick[pokemonId] = now
            WorkerAnimationUtils.playWorkAnimation(pokemonEntity, workPhase, world)
            pokemonEntity.lookControl.lookAt(player.x, player.eyeY, player.z)
            return false
        }

        if (now - arrived < WORK_DELAY_TICKS) {
            pokemonEntity.lookControl.lookAt(player.x, player.eyeY, player.z)
            return false
        }

        arrivalTick.remove(pokemonId)
        WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.WORK_COMPLETE, world)
        if (particleType != null) spawnParticles(world, player.blockPos, particleType)
        return true
    }

    fun setExcited(pokemonEntity: PokemonEntity, excited: Boolean) {
        pokemonEntity.setBehaviourFlag(PokemonBehaviourFlag.EXCITED, excited)
    }

    private fun lookAt(pokemonEntity: PokemonEntity, pos: BlockPos) {
        pokemonEntity.lookControl.lookAt(
            pos.x + 0.5,
            pos.y + 0.5,
            pos.z + 0.5
        )
    }

    fun spawnParticles(world: World, pos: BlockPos, particleType: ParticleEffect, count: Int = 6) {
        val sw = world as? ServerWorld ?: return
        sw.spawnParticles(
            particleType,
            pos.x + 0.5, pos.y + 1.0, pos.z + 0.5,
            count, 0.3, 0.3, 0.3, 0.02
        )
    }

    fun cleanup(pokemonId: UUID) {
        arrivalTick.remove(pokemonId)
        graceTick.remove(pokemonId)
        WorkerAnimationUtils.cleanup(pokemonId)
    }
}
