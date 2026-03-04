/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.state.StateManager
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.DepositHelper
import akkiruk.cobblecrew.utilities.DeferredBlockScanner
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import kotlin.random.Random

/**
 * Central dispatcher — assigns jobs to Pokémon and manages idle behavior.
 *
 * All per-Pokémon state lives in [StateManager]. This object holds only
 * constants and stateless logic (profile building, job selection, idle behavior).
 */
object WorkerDispatcher {
    private val workers: List<Worker> get() = WorkerRegistry.workers

    private const val JOB_STICKINESS_TICKS = 100L
    private const val IDLE_LOG_INTERVAL = 600L
    private const val IDLE_BORED_THRESHOLD = 600L
    private const val IDLE_PICKUP_RADIUS = 8.0
    private const val RETURN_NAV_COOLDOWN = 40L
    private const val ARRIVAL_RADIUS = 5.0
    private const val PARTY_ARRIVAL_RADIUS = 6.0
    private const val IDLE_WANDER_RADIUS = 4
    private const val IDLE_WANDER_COOLDOWN = 200L
    private const val IDLE_PICKUP_ATTEMPT_COOLDOWN = 60L
    private const val IDLE_PICKUP_STALE_TICKS = 100L

    fun tickAreaScan(context: JobContext) {
        DeferredBlockScanner.tickAreaScan(context)
    }

    private fun getOrBuildProfile(pokemonEntity: PokemonEntity, state: PokemonWorkerState): PokemonProfile {
        val pokemon = pokemonEntity.pokemon
        val pokemonId = pokemon.uuid

        val active = pokemon.moveSet.getMoves().map { it.name.lowercase() }
        val benched = pokemon.benchedMoves.map { it.moveTemplate.name.lowercase() }
        val moves = (active + benched).toSet()

        val cached = state.profile
        if (cached != null && state.lastMoveSet == moves) return cached

        if (cached != null) {
            CobbleCrewDebugLogger.log(CobbleCrewDebugLogger.Category.DISPATCH,
                "${pokemon.species.name}($pokemonId) moves changed, rebuilding profile")
            state.activeJob?.cleanup(pokemonId)
            state.activeJob = null
            state.jobAssignedTick = 0L
        }

        val types = pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemon.species.name.lowercase()
        val ability = pokemon.ability.name.lowercase()

        return PokemonProfile.build(pokemonId, moves, types, species, ability, workers)
            .also {
                state.profile = it
                state.lastMoveSet = moves
                val eligible = it.allEligible()
                CobbleCrewDebugLogger.profileBuilt(species, pokemonId, moves, types, eligible.map { w -> w.name })
            }
    }

    fun tickPokemon(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val state = StateManager.getOrCreate(pokemonId)
        val profile = getOrBuildProfile(pokemonEntity, state)
        val eligible = profile.allEligible()

        if (eligible.isEmpty()) {
            state.activeJob = null
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            val now = world.time
            if (now - state.idleLogTick >= IDLE_LOG_INTERVAL) {
                state.idleLogTick = now
                CobbleCrewDebugLogger.noEligibleJobs(pokemonEntity.pokemon.species.name, pokemonId)
            }
            handleIdleBehavior(state, context, pokemonEntity, world)
            return
        }

        val current = state.activeJob
        val now = world.time
        state.idleSinceTick = 0L

        if (current != null && current in eligible) {
            val hasRealWork = current.hasActiveState(pokemonId) || state.claim != null
            val stickyAndAvailable = !hasRealWork
                && now - state.jobAssignedTick < JOB_STICKINESS_TICKS
                && current.isAvailable(context, pokemonId)
            if (hasRealWork || stickyAndAvailable) {
                val reason = when {
                    current.hasActiveState(pokemonId) -> "activeState"
                    state.claim != null -> "hasClaim"
                    else -> "stickiness"
                }
                CobbleCrewDebugLogger.jobSticking(pokemonEntity.pokemon.species.name, pokemonId, current.name, reason)
                WorkerVisualUtils.setExcited(pokemonEntity, true)
                current.tick(context, pokemonEntity)
                return
            }
        }

        val job = selectBestAvailableJob(profile, context, pokemonId)

        if (current != null && job != current) {
            current.cleanup(pokemonId)
            ClaimManager.release(state, world, blacklist = false)
            state.resetJobState()
        }

        if (job == null) {
            state.activeJob = null
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            if (current != null) state.lastPickupAttemptTick = now
            handleIdleBehavior(state, context, pokemonEntity, world)

            if (now - state.idleLogTick >= IDLE_LOG_INTERVAL) {
                state.idleLogTick = now
                val availInfo = eligible.map { w ->
                    val avail = w.isAvailable(context, pokemonId)
                    "${w.name}=$avail"
                }
                CobbleCrewDebugLogger.jobIdle(
                    pokemonEntity.pokemon.species.name, pokemonId,
                    eligible.size, availInfo
                )
            }
            return
        }

        state.activeJob = job
        state.jobAssignedTick = now
        state.idleSinceTick = 0L
        state.returningHome = false
        state.lastReturnNavTick = 0L
        // Release stale idle-pickup claims when transitioning from idle to job
        if (current == null) {
            ClaimManager.release(state, world, blacklist = false)
            if (state.heldItems.isNotEmpty()) {
                state.heldItems.forEach { stack -> Block.dropStack(world, pokemonEntity.blockPos, stack) }
                state.heldItems.clear()
            }
            state.failedDeposits.clear()
            state.idlePickupClaimTick = 0L
        }
        CobbleCrewDebugLogger.jobAssigned(pokemonEntity.pokemon.species.name, pokemonId, job.name)
        WorkerVisualUtils.setExcited(pokemonEntity, true)
        job.tick(context, pokemonEntity)
    }

    private fun selectBestAvailableJob(profile: PokemonProfile, context: JobContext, pokemonId: UUID): Worker? {
        for (priority in WorkerPriority.entries) {
            val candidates = profile.getByPriority(priority)
            if (candidates.isEmpty()) continue
            val sorted = candidates
                .groupBy { it.importance }
                .toSortedMap()
                .flatMap { (_, workers) -> workers.shuffled() }
            val available = sorted.firstOrNull { it.isAvailable(context, pokemonId) }
            if (available != null) return available
        }
        return null
    }

    private fun handleIdleBehavior(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity, world: World) {
        if (tryIdlePickup(state, context, pokemonEntity)) return
        if (returnToOrigin(state, pokemonEntity, context, world)) return
        if (tryIdleWander(state, pokemonEntity, context, world)) return
        handleIdleAnimation(state, pokemonEntity, world)
    }

    private fun handleIdleAnimation(state: PokemonWorkerState, pokemonEntity: PokemonEntity, world: World) {
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
        CobbleCrewNavigationUtils.navigateTo(pokemonEntity, wanderTarget)
        return true
    }

    private fun tryIdlePickup(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): Boolean {
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
        if (currentTarget != null && state.activeJob == null) {
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
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, currentTarget)
            if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, currentTarget, 2.0)) {
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
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, itemPos)
            state.idlePickupClaimTick = now
            return true
        }
        return false
    }

    private fun returnToOrigin(state: PokemonWorkerState, pokemonEntity: PokemonEntity, context: JobContext, world: World): Boolean {
        val (target, radius) = when (context) {
            is JobContext.Pasture -> context.origin to ARRIVAL_RADIUS
            is JobContext.Party -> context.player.blockPos to PARTY_ARRIVAL_RADIUS
        }

        if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, target, radius)) {
            state.returningHome = false
            state.lastReturnNavTick = 0L
            return false
        }

        state.returningHome = true
        val now = world.time
        if (now - state.lastReturnNavTick >= RETURN_NAV_COOLDOWN) {
            state.lastReturnNavTick = now
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
        }
        return true
    }

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        val state = StateManager.get(pokemonId)
        val species = state?.profile?.species

        workers.forEach { it.cleanup(pokemonId) }
        ClaimManager.cleanupPokemon(pokemonId, world)
        WorkerVisualUtils.cleanup(pokemonId)
        CobbleCrewNavigationUtils.cleanupPokemon(pokemonId, world)
        CobbleCrewInventoryUtils.cleanupPokemon(pokemonId)
        StateManager.remove(pokemonId)

        CobbleCrewDebugLogger.pokemonCleanedUp(species, pokemonId)
    }

    fun invalidateProfiles() {
        resetAllAssignments()
        for (state in StateManager.all()) {
            state.profile = null
            state.lastMoveSet = emptySet()
        }
        CobbleCrewDebugLogger.profileInvalidated()
    }

    // -- Command accessors --

    fun getActiveJobsSnapshot(): Map<UUID, String> = StateManager.activeJobsSnapshot()

    fun hasActiveJob(pokemonId: UUID): Boolean = StateManager.get(pokemonId)?.activeJob != null

    fun getProfilesSnapshot(): Map<UUID, PokemonProfile> = StateManager.profilesSnapshot()

    fun getCachedProfileCount(): Int = StateManager.cachedProfileCount()

    fun getActiveWorkerCount(): Int = StateManager.activeWorkerCount()

    fun resetAssignment(pokemonId: UUID) {
        val state = StateManager.get(pokemonId) ?: return
        val job = state.activeJob ?: return
        job.cleanup(pokemonId)
        state.activeJob = null
        state.jobAssignedTick = 0L
        state.idleLogTick = 0L
    }

    fun resetAllAssignments() {
        for (state in StateManager.all()) {
            if (state.activeJob != null) {
                state.activeJob!!.cleanup(state.pokemonId)
                state.activeJob = null
                state.jobAssignedTick = 0L
                state.idleLogTick = 0L
            }
        }
    }
}