/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.StateManager
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
 * Timing state (arrivalTick, graceTick) lives in [StateManager].
 */
object WorkerVisualUtils {
    private const val WORK_DELAY_TICKS = 30L
    private const val GRACE_PERIOD_TICKS = 10L

    fun handleArrival(
        pokemonEntity: PokemonEntity,
        targetPos: BlockPos,
        world: World,
        particleType: ParticleEffect? = null,
        offset: Double = 3.0,
        workPhase: WorkPhase = WorkPhase.HARVESTING,
    ): Boolean {
        val state = StateManager.getOrCreate(pokemonEntity.pokemon.uuid)
        val now = world.time

        if (!ClaimManager.isPokemonAtPosition(pokemonEntity, targetPos, offset)) {
            if (state.arrivalTick != null) {
                val grace = state.graceTick ?: run { state.graceTick = now; now }
                if (now - grace < GRACE_PERIOD_TICKS) {
                    lookAt(pokemonEntity, targetPos)
                    return false
                }
                state.arrivalTick = null
                state.graceTick = null
            }
            return false
        }

        state.graceTick = null
        pokemonEntity.navigation.stop()
        val arrived = state.arrivalTick

        if (arrived == null) {
            state.arrivalTick = now
            WorkerAnimationUtils.playWorkAnimation(pokemonEntity, workPhase, world)
            lookAt(pokemonEntity, targetPos)
            return false
        }

        if (now - arrived < WORK_DELAY_TICKS) {
            lookAt(pokemonEntity, targetPos)
            return false
        }

        state.arrivalTick = null
        CobbleCrewDebugLogger.arrivedAtTarget(pokemonEntity, targetPos)
        WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.WORK_COMPLETE, world)
        if (particleType != null) spawnParticles(world, targetPos, particleType)
        return true
    }

    fun handlePlayerArrival(
        pokemonEntity: PokemonEntity,
        player: PlayerEntity,
        world: World,
        particleType: ParticleEffect? = null,
        workPhase: WorkPhase = WorkPhase.HEALING,
    ): Boolean {
        val state = StateManager.getOrCreate(pokemonEntity.pokemon.uuid)

        if (!ClaimManager.isPokemonNearPlayer(pokemonEntity, player)) {
            state.arrivalTick = null
            return false
        }

        pokemonEntity.navigation.stop()
        val now = world.time
        val arrived = state.arrivalTick

        if (arrived == null) {
            state.arrivalTick = now
            WorkerAnimationUtils.playWorkAnimation(pokemonEntity, workPhase, world)
            pokemonEntity.lookControl.lookAt(player.x, player.eyeY, player.z)
            return false
        }

        if (now - arrived < WORK_DELAY_TICKS) {
            pokemonEntity.lookControl.lookAt(player.x, player.eyeY, player.z)
            return false
        }

        state.arrivalTick = null
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
}
