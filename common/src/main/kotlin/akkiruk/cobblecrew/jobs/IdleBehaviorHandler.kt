/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.DepositHelper
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.Box
import net.minecraft.world.World
import kotlin.random.Random

/**
 * Handles all idle-state behavior: returning to origin, bored wandering,
 * ground-item pickup, and idle animations.
 */
object IdleBehaviorHandler {
    private const val IDLE_BORED_THRESHOLD = 600L
    private const val IDLE_PICKUP_RADIUS = 8.0
    private const val RETURN_NAV_COOLDOWN = 40L
    private const val ARRIVAL_RADIUS = 5.0
    private const val PARTY_ARRIVAL_RADIUS = 6.0
    private const val IDLE_WANDER_RADIUS = 4
    private const val IDLE_WANDER_COOLDOWN = 200L
    private const val IDLE_PICKUP_ATTEMPT_COOLDOWN = 10L
    private const val IDLE_PICKUP_STALE_TICKS = 100L

    fun handle(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity, world: World) {
        if (tryIdlePickup(state, context, pokemonEntity)) return
        if (returnToOrigin(state, pokemonEntity, context, world)) return
        if (tryIdleWander(state, pokemonEntity, context, world)) return
        playIdleAnimation(state, pokemonEntity, world)
    }

    private fun playIdleAnimation(state: PokemonWorkerState, pokemonEntity: PokemonEntity, world: World) {
        val now = world.time
        if (state.idleSinceTick == 0L) state.idleSinceTick = now
        val phase = if (now - state.idleSinceTick >= IDLE_BORED_THRESHOLD) WorkPhase.IDLE_BORED else WorkPhase.IDLE_AT_ORIGIN
        WorkerAnimationUtils.playWorkAnimation(pokemonEntity, phase, world)
    }

    private fun tryIdleWander(state: PokemonWorkerState, pokemonEntity: PokemonEntity, context: JobContext, world: World): Boolean {
        val now = world.time
        if (state.idleSinceTick == 0L || now - state.idleSinceTick < IDLE_BORED_THRESHOLD) return false
        if (now - state.lastWanderTick < IDLE_WANDER_COOLDOWN) return false

        state.lastWanderTick = now
        val origin = context.origin
        val dx = Random.nextInt(-IDLE_WANDER_RADIUS, IDLE_WANDER_RADIUS + 1)
        val dz = Random.nextInt(-IDLE_WANDER_RADIUS, IDLE_WANDER_RADIUS + 1)
        val wanderTarget = origin.add(dx, 0, dz)
        ClaimManager.navigateTo(pokemonEntity, wanderTarget, state)
        return true
    }

    private fun tryIdlePickup(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): Boolean =
        handlePickup(state, context, pokemonEntity)

    /**
     * Attempt to pick up ground items. Called from both idle behavior and
     * the dispatcher (before job selection) to ensure pickup always wins.
     */
    fun handlePickup(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): Boolean {
        val world = context.world
        val origin = context.origin
        val now = world.time

        // Already holding items — deposit them
        if (state.heldItems.isNotEmpty()) {
            if (context is JobContext.Party) {
                CobbleCrewInventoryUtils.deliverToPlayer(context.player, state.heldItems, pokemonEntity)
                state.heldItems.clear()
                state.failedDeposits.clear()
                state.idlePickupClaimTick = 0L
                return true
            }
            DepositHelper.tick(state, context, pokemonEntity)
            return true
        }
        state.failedDeposits.clear()

        // Active pickup claim — keep navigating to it
        val currentTarget = (state.claim?.target as? Target.Block)?.pos
        if (currentTarget != null && state.idlePickupClaimTick > 0L) {
            val claimedAt = state.idlePickupClaimTick.takeIf { it > 0L } ?: now
            if (now - claimedAt > IDLE_PICKUP_STALE_TICKS) {
                ClaimManager.release(state, world)
                state.idlePickupClaimTick = 0L
                return false
            }
            val searchArea = Box(currentTarget).expand(2.0)
            val nearbyItem = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { !it.isRemoved && it.isOnGround }
                .firstOrNull()
            if (nearbyItem == null) {
                ClaimManager.release(state, world)
                state.idlePickupClaimTick = 0L
                return false
            }
            ClaimManager.navigateTo(pokemonEntity, currentTarget, state)
            if (ClaimManager.isPokemonAtPosition(pokemonEntity, currentTarget, 2.0)) {
                if (nearbyItem.isRemoved || nearbyItem.stack.isEmpty) {
                    ClaimManager.release(state, world)
                    state.idlePickupClaimTick = 0L
                    return true
                }
                val stack = nearbyItem.stack.split(nearbyItem.stack.count)
                if (nearbyItem.stack.isEmpty) nearbyItem.discard()
                state.heldItems.add(stack)
                ClaimManager.release(state, world, blacklist = false)
                state.idlePickupClaimTick = 0L
            }
            return true
        }

        // Throttle new pickup scans
        if (now - state.lastPickupAttemptTick < IDLE_PICKUP_ATTEMPT_COOLDOWN) return false
        state.lastPickupAttemptTick = now

        // Scan for ground items
        val searchArea = Box(origin).expand(IDLE_PICKUP_RADIUS, IDLE_PICKUP_RADIUS, IDLE_PICKUP_RADIUS)
        val item = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { !it.isRemoved && it.isOnGround }
            .firstOrNull() ?: return false

        val itemPos = item.blockPos
        if (!ClaimManager.isTargeted(itemPos)) {
            ClaimManager.claim(state, Target.Block(itemPos), world)
            ClaimManager.navigateTo(pokemonEntity, itemPos, state)
            state.idlePickupClaimTick = now
            return true
        }
        return false
    }

    fun returnToOrigin(state: PokemonWorkerState, pokemonEntity: PokemonEntity, context: JobContext, world: World): Boolean {
        val (target, radius) = when (context) {
            is JobContext.Pasture -> context.origin to ARRIVAL_RADIUS
            is JobContext.Party -> context.player.blockPos to PARTY_ARRIVAL_RADIUS
        }

        if (ClaimManager.isPokemonAtPosition(pokemonEntity, target, radius)) {
            state.returningHome = false
            state.lastReturnNavTick = 0L
            return false
        }

        state.returningHome = true
        val now = world.time
        if (now - state.lastReturnNavTick >= RETURN_NAV_COOLDOWN) {
            state.lastReturnNavTick = now
            ClaimManager.navigateTo(pokemonEntity, target, state)
        }
        return true
    }
}
