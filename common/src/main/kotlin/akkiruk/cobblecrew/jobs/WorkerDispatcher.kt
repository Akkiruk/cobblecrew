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
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.DeferredBlockScanner
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import kotlin.random.Random

object WorkerDispatcher {
    private val workers: List<Worker> get() = WorkerRegistry.workers

    private val activeJobs = mutableMapOf<UUID, Worker>()
    private val profiles = mutableMapOf<UUID, PokemonProfile>()
    private val jobAssignedTick = mutableMapOf<UUID, Long>()
    private val idleLogTick = mutableMapOf<UUID, Long>()
    private val idleSinceTick = mutableMapOf<UUID, Long>()
    private const val JOB_STICKINESS_TICKS = 100L // 5 seconds
    private const val IDLE_LOG_INTERVAL = 600L // log idle reason every 30s
    private const val IDLE_BORED_THRESHOLD = 600L // 30 seconds idle → bored animation
    private const val IDLE_PICKUP_RADIUS = 8.0
    private const val RETURN_NAV_COOLDOWN = 40L // only re-path home every 2 seconds
    private const val ARRIVAL_RADIUS = 5.0 // blocks from origin to count as "home"
    private const val PARTY_ARRIVAL_RADIUS = 6.0
    private const val IDLE_WANDER_RADIUS = 4 // blocks to wander from origin when bored
    private const val IDLE_WANDER_COOLDOWN = 200L // 10 seconds between wanders
    private const val IDLE_PICKUP_ATTEMPT_COOLDOWN = 60L // 3 seconds between pickup scans
    private const val IDLE_PICKUP_STALE_TICKS = 100L // abandon pickup target after 5 seconds

    // Return-to-origin state
    private val returningHome = mutableSetOf<UUID>()
    private val lastReturnNavTick = mutableMapOf<UUID, Long>()

    // Idle wander state
    private val lastWanderTick = mutableMapOf<UUID, Long>()

    // Idle pickup throttle
    private val lastPickupAttemptTick = mutableMapOf<UUID, Long>()
    private val idlePickupClaimTick = mutableMapOf<UUID, Long>()

    // Idle ground pickup state — not a job, just background behavior
    private val idleHeldItems = mutableMapOf<UUID, List<ItemStack>>()
    private val idleFailedDeposits = mutableMapOf<UUID, MutableSet<BlockPos>>()

    fun tickAreaScan(context: JobContext) {
        DeferredBlockScanner.tickAreaScan(context)
    }

    /**
     * Builds or retrieves a cached PokemonProfile for the given entity.
     * Profile caches which jobs a Pokémon qualifies for — eliminates
     * per-tick shouldRun() calls across all workers.
     */
    private fun getOrBuildProfile(pokemonEntity: PokemonEntity): PokemonProfile {
        val pokemon = pokemonEntity.pokemon
        val pokemonId = pokemon.uuid

        // Include both active and benched moves — workers can use any move the Pokémon knows
        val active = pokemon.moveSet.getMoves().map { it.name.lowercase() }
        val benched = pokemon.benchedMoves.map { it.moveTemplate.name.lowercase() }
        val moves = (active + benched).toSet()

        // Invalidate cache if moves changed (e.g. player taught/forgot a move)
        profiles[pokemonId]?.let { cached ->
            if (cached.moves == moves) return cached
            CobbleCrewDebugLogger.log(CobbleCrewDebugLogger.Category.DISPATCH, "${pokemon.species.name}($pokemonId) moves changed, rebuilding profile")
            // Clean up active job — it may no longer be eligible
            activeJobs.remove(pokemonId)?.cleanup(pokemonId)
            jobAssignedTick.remove(pokemonId)
        }

        val types = pokemon.types.map { it.name.uppercase() }.toSet()
        val species = pokemon.species.name.lowercase()
        val ability = pokemon.ability.name.lowercase()

        return PokemonProfile.build(pokemonId, moves, types, species, ability, workers)
            .also {
                profiles[pokemonId] = it
                val eligible = it.allEligible()
                CobbleCrewDebugLogger.profileBuilt(species, pokemonId, moves, types, eligible.map { w -> w.name })
            }
    }

    fun tickPokemon(context: JobContext, pokemonEntity: PokemonEntity) {
        val world = context.world
        val pokemonId = pokemonEntity.pokemon.uuid
        val profile = getOrBuildProfile(pokemonEntity)
        val eligible = profile.allEligible()

        if (eligible.isEmpty()) {
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            val now = world.time
            val lastLog = idleLogTick[pokemonId] ?: 0L
            if (now - lastLog >= IDLE_LOG_INTERVAL) {
                idleLogTick[pokemonId] = now
                CobbleCrewDebugLogger.noEligibleJobs(pokemonEntity.pokemon.species.name, pokemonId)
            }
            handleIdleBehavior(context, pokemonEntity, world, pokemonId)
            return
        }

        val current = activeJobs[pokemonId]
        val now = world.time
        idleSinceTick.remove(pokemonId) // Working, not idle

        // Stick with current job while it has work or stickiness hasn't expired
        if (current != null && current in eligible) {
            val assignedAt = jobAssignedTick[pokemonId] ?: 0L
            val hasRealWork = current.hasActiveState(pokemonId)
                || CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null
                || CobbleCrewNavigationUtils.getPlayerTarget(pokemonId, world) != null
            // Break stickiness early if the job has no available targets
            val stickyAndAvailable = !hasRealWork
                && now - assignedAt < JOB_STICKINESS_TICKS
                && current.isAvailable(context, pokemonId)
            if (hasRealWork || stickyAndAvailable) {
                val reason = when {
                    current.hasActiveState(pokemonId) -> "activeState"
                    CobbleCrewNavigationUtils.getTarget(pokemonId, world) != null -> "hasTarget"
                    CobbleCrewNavigationUtils.getPlayerTarget(pokemonId, world) != null -> "hasPlayerTarget"
                    else -> "stickiness"
                }
                CobbleCrewDebugLogger.jobSticking(pokemonEntity.pokemon.species.name, pokemonId, current.name, reason)
                WorkerVisualUtils.setExcited(pokemonEntity, true)
                current.tick(context, pokemonEntity)
                return
            }
        }

        // Priority-ordered selection: check tiers in order (COMBO > MOVE > SPECIES > TYPE).
        // Within each tier, sort by importance (CRITICAL > HIGH > STANDARD > LOW > BACKGROUND).
        val job = selectBestAvailableJob(profile, context, pokemonId)

        // Clean up old job if switching away
        if (current != null && job != current) {
            current.cleanup(pokemonId)
        }

        if (job == null) {
            activeJobs.remove(pokemonId)
            WorkerVisualUtils.setExcited(pokemonEntity, false)
            handleIdleBehavior(context, pokemonEntity, world, pokemonId)

            val lastLog = idleLogTick[pokemonId] ?: 0L
            if (now - lastLog >= IDLE_LOG_INTERVAL) {
                idleLogTick[pokemonId] = now
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

        activeJobs[pokemonId] = job
        jobAssignedTick[pokemonId] = now
        idleSinceTick.remove(pokemonId)
        returningHome.remove(pokemonId)
        lastReturnNavTick.remove(pokemonId)
        CobbleCrewDebugLogger.jobAssigned(pokemonEntity.pokemon.species.name, pokemonId, job.name)
        WorkerVisualUtils.setExcited(pokemonEntity, true)
        job.tick(context, pokemonEntity)
    }

    /**
     * Selects the best available job using priority tiers.
     * COMBO jobs are checked first, then MOVE, SPECIES, TYPE.
     * Within each tier, candidates are sorted by [JobImportance]
     * (CRITICAL > HIGH > STANDARD > LOW > BACKGROUND).
     * Same-importance jobs are shuffled for fairness.
     * Stops at the first tier that has an available job.
     */
    private fun selectBestAvailableJob(profile: PokemonProfile, context: JobContext, pokemonId: java.util.UUID): Worker? {
        for (priority in WorkerPriority.entries) {
            val candidates = profile.getByPriority(priority)
            if (candidates.isEmpty()) continue
            // Group by importance, iterate from highest (CRITICAL) to lowest (BACKGROUND),
            // shuffle within each group for fairness
            val sorted = candidates
                .groupBy { it.importance }
                .toSortedMap()
                .flatMap { (_, workers) -> workers.shuffled() }
            val available = sorted.firstOrNull { it.isAvailable(context, pokemonId) }
            if (available != null) return available
        }
        return null
    }

    /**
     * Central idle behavior: pickup → return home → wander → idle anim.
     * Each step is tried in order; first one that engages wins.
     */
    private fun handleIdleBehavior(context: JobContext, pokemonEntity: PokemonEntity, world: World, pokemonId: UUID) {
        if (tryIdlePickup(context, pokemonEntity)) return
        if (returnToOrigin(pokemonEntity, context, world)) return
        // At origin — try wandering if bored
        if (tryIdleWander(pokemonEntity, context, world, pokemonId)) return
        handleIdleAnimation(pokemonEntity, world, pokemonId)
    }

    private fun handleIdleAnimation(pokemonEntity: PokemonEntity, world: World, pokemonId: UUID) {
        val now = world.time
        val idleSince = idleSinceTick.getOrPut(pokemonId) { now }
        val phase = if (now - idleSince >= IDLE_BORED_THRESHOLD) WorkPhase.IDLE_BORED else WorkPhase.IDLE_AT_ORIGIN
        WorkerAnimationUtils.playWorkAnimation(pokemonEntity, phase, world)
    }

    /**
     * Occasionally wander to a random nearby position when idle at origin.
     */
    private fun tryIdleWander(pokemonEntity: PokemonEntity, context: JobContext, world: World, pokemonId: UUID): Boolean {
        val now = world.time
        val idleSince = idleSinceTick[pokemonId] ?: now
        // Only wander once bored (after IDLE_BORED_THRESHOLD)
        if (now - idleSince < IDLE_BORED_THRESHOLD) return false

        val lastWander = lastWanderTick[pokemonId] ?: 0L
        if (now - lastWander < IDLE_WANDER_COOLDOWN) return false

        lastWanderTick[pokemonId] = now
        val origin = context.origin
        val dx = Random.nextInt(-IDLE_WANDER_RADIUS, IDLE_WANDER_RADIUS + 1)
        val dz = Random.nextInt(-IDLE_WANDER_RADIUS, IDLE_WANDER_RADIUS + 1)
        val wanderTarget = origin.add(dx, 0, dz)
        CobbleCrewNavigationUtils.navigateTo(pokemonEntity, wanderTarget)
        return true
    }

    /**
     * Idle ground pickup — not a formal job, just background behavior.
     * When a Pokémon has nothing to do, it picks up nearby ground items
     * and deposits them in containers (or delivers to player in party mode).
     * Returns true if the Pokémon is actively picking up / depositing.
     *
     * Throttled so Pokémon don't constantly scan for items, and claims
     * are abandoned if the Pokémon can't reach the item in time.
     */
    private fun tryIdlePickup(context: JobContext, pokemonEntity: PokemonEntity): Boolean {
        val world = context.world
        val origin = context.origin
        val pid = pokemonEntity.pokemon.uuid
        val now = world.time

        // Already holding items — deposit them (no throttle, always finish delivery)
        val held = idleHeldItems[pid]
        if (!held.isNullOrEmpty()) {
            if (context is JobContext.Party) {
                CobbleCrewInventoryUtils.deliverToPlayer(context.player, held, pokemonEntity)
                idleHeldItems.remove(pid)
                idleFailedDeposits.remove(pid)
                idlePickupClaimTick.remove(pid)
                return true
            }
            CobbleCrewInventoryUtils.handleDepositing(world, origin, pokemonEntity, held, idleFailedDeposits, idleHeldItems)
            return true
        }
        idleFailedDeposits.remove(pid)

        // If we have an active pickup claim, keep navigating to it
        val currentTarget = CobbleCrewNavigationUtils.getTarget(pid, world)
        if (currentTarget != null) {
            val claimedAt = idlePickupClaimTick[pid] ?: now
            // Abandon stale claim — we've been chasing this item too long
            if (now - claimedAt > IDLE_PICKUP_STALE_TICKS) {
                CobbleCrewNavigationUtils.releaseTarget(pid, world)
                idlePickupClaimTick.remove(pid)
                return false
            }
            // Check if the item still exists at the target
            val searchArea = Box(currentTarget).expand(2.0)
            val nearbyItem = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { !it.isRemoved && it.isOnGround }
                .firstOrNull()
            if (nearbyItem == null) {
                CobbleCrewNavigationUtils.releaseTarget(pid, world)
                idlePickupClaimTick.remove(pid)
                return false
            }
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, currentTarget)
            if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, currentTarget, 2.0)) {
                if (nearbyItem.isRemoved || nearbyItem.stack.isEmpty) {
                    CobbleCrewNavigationUtils.releaseTarget(pid, world)
                    idlePickupClaimTick.remove(pid)
                    return true
                }
                val stack = nearbyItem.stack.split(nearbyItem.stack.count)
                if (nearbyItem.stack.isEmpty) nearbyItem.discard()
                idleHeldItems[pid] = listOf(stack)
                CobbleCrewNavigationUtils.releaseTarget(pid, world, blacklist = false)
                idlePickupClaimTick.remove(pid)
            }
            return true
        }

        // Throttle new pickup scans — don't spam entity queries every 5 ticks
        val lastAttempt = lastPickupAttemptTick[pid] ?: 0L
        if (now - lastAttempt < IDLE_PICKUP_ATTEMPT_COOLDOWN) return false
        lastPickupAttemptTick[pid] = now

        // Scan for ground items
        val searchArea = Box(origin).expand(IDLE_PICKUP_RADIUS, IDLE_PICKUP_RADIUS, IDLE_PICKUP_RADIUS)
        val item = world.getEntitiesByClass(ItemEntity::class.java, searchArea) { !it.isRemoved && it.isOnGround }
            .firstOrNull() ?: return false

        val itemPos = item.blockPos
        if (!CobbleCrewNavigationUtils.isTargeted(itemPos, world)) {
            CobbleCrewNavigationUtils.claimTarget(pid, itemPos, world)
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, itemPos)
            idlePickupClaimTick[pid] = now
            return true
        }
        return false
    }

    /**
     * Walks the Pokémon back to its origin (pasture block or player).
     * Returns true if the Pokémon is still walking, false if already at origin.
     * Only re-paths every RETURN_NAV_COOLDOWN ticks to avoid spamming pathfinder.
     */
    private fun returnToOrigin(pokemonEntity: PokemonEntity, context: JobContext, world: World): Boolean {
        val pokemonId = pokemonEntity.pokemon.uuid
        val (target, radius) = when (context) {
            is JobContext.Pasture -> context.origin to ARRIVAL_RADIUS
            is JobContext.Party -> context.player.blockPos to PARTY_ARRIVAL_RADIUS
        }

        if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, target, radius)) {
            // We're home
            returningHome.remove(pokemonId)
            lastReturnNavTick.remove(pokemonId)
            return false
        }

        // Not home — mark as returning and (re-)path on cooldown
        returningHome.add(pokemonId)
        val now = world.time
        val lastNav = lastReturnNavTick[pokemonId] ?: 0L
        if (now - lastNav >= RETURN_NAV_COOLDOWN) {
            lastReturnNavTick[pokemonId] = now
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
        }
        return true
    }

    fun cleanupPokemon(pokemonId: UUID, world: World) {
        val species = profiles[pokemonId]?.species
        workers.forEach { it.cleanup(pokemonId) }
        activeJobs.remove(pokemonId)
        profiles.remove(pokemonId)
        jobAssignedTick.remove(pokemonId)
        idleLogTick.remove(pokemonId)
        idleSinceTick.remove(pokemonId)
        idleHeldItems.remove(pokemonId)
        idleFailedDeposits.remove(pokemonId)
        returningHome.remove(pokemonId)
        lastReturnNavTick.remove(pokemonId)
        lastWanderTick.remove(pokemonId)
        lastPickupAttemptTick.remove(pokemonId)
        idlePickupClaimTick.remove(pokemonId)
        WorkerVisualUtils.cleanup(pokemonId)
        CobbleCrewNavigationUtils.cleanupPokemon(pokemonId, world)
        CobbleCrewInventoryUtils.cleanupPokemon(pokemonId)
        CobbleCrewDebugLogger.pokemonCleanedUp(species, pokemonId)
    }

    /** Invalidate all cached profiles (e.g. on config reload). */
    fun invalidateProfiles() {
        resetAllAssignments()
        profiles.clear()
        CobbleCrewDebugLogger.profileInvalidated()
    }

    // -- Command accessors --

    fun getActiveJobsSnapshot(): Map<UUID, String> = activeJobs.mapValues { it.value.name }

    fun hasActiveJob(pokemonId: UUID): Boolean = pokemonId in activeJobs

    fun getProfilesSnapshot(): Map<UUID, PokemonProfile> = profiles.toMap()

    fun getCachedProfileCount(): Int = profiles.size

    fun getActiveWorkerCount(): Int = activeJobs.size

    fun resetAssignment(pokemonId: UUID) {
        val job = activeJobs.remove(pokemonId) ?: return
        job.cleanup(pokemonId)
        jobAssignedTick.remove(pokemonId)
        idleLogTick.remove(pokemonId)
        // Don't clear profiles — moves/types don't change on reset.
        // Profile will be invalidated if moves actually change (getOrBuildProfile checks).
    }

    fun resetAllAssignments() {
        val ids = activeJobs.keys.toList()
        ids.forEach { resetAssignment(it) }
    }
}