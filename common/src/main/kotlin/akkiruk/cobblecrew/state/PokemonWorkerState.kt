/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.state

import akkiruk.cobblecrew.enums.JobPhase
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.jobs.PokemonProfile
import akkiruk.cobblecrew.jobs.Target
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import java.util.UUID

// ── Focused sub-state containers ─────────────────────────────────────

/** Job assignment & profile caching. */
class JobState {
    var activeJob: Worker? = null
    var profile: PokemonProfile? = null
    var jobAssignedTick: Long = 0L
    var lastMoveSet: Set<String> = emptySet()
    var phase: JobPhase = JobPhase.IDLE
    var cooldownUntil: Long = 0L

    fun reset(hasOrphanItems: Boolean) {
        phase = if (hasOrphanItems) JobPhase.DEPOSITING else JobPhase.IDLE
        cooldownUntil = 0L
    }
}

/** Navigation claims and pathfinding throttle. */
class NavState {
    var claim: NavigationClaim? = null
    var targetPos: BlockPos? = null
    var secondaryTargetPos: BlockPos? = null
    var lastPathfindTick: Long = 0L

    fun reset() {
        targetPos = null
        secondaryTargetPos = null
    }
}

/** Items held by Pokémon, awaiting deposit/delivery. */
class DepositState {
    val heldItems: MutableList<ItemStack> = mutableListOf()
    val failedDeposits: MutableSet<BlockPos> = mutableSetOf()
    var heldSinceTick: Long = 0L
    var depositRetryTick: Long = 0L
    var depositArrivalTick: Long? = null
    var lastDepositWarning: Long = 0L

    fun clear() {
        heldItems.clear()
        failedDeposits.clear()
        depositRetryTick = 0L
        depositArrivalTick = null
        heldSinceTick = 0L
        lastDepositWarning = 0L
    }
}

/** Idle behavior timing. */
class IdleState {
    var idleSinceTick: Long = 0L
    var idleLogTick: Long = 0L
    var lastWanderTick: Long = 0L
    var lastPickupAttemptTick: Long = 0L
    var idlePickupClaimTick: Long = 0L
    var returningHome: Boolean = false
    var lastReturnNavTick: Long = 0L

    fun resetOnJobAssign() {
        idleSinceTick = 0L
        returningHome = false
        lastReturnNavTick = 0L
    }
}

/** Visual feedback timing (arrival animations, grace period). */
class VisualState {
    var arrivalTick: Long? = null
    var graceTick: Long? = null
    var lastAnimationTick: Long = 0L

    fun resetArrival() {
        arrivalTick = null
        graceTick = null
    }
}

// ── Main state class ─────────────────────────────────────────────────

/**
 * All mutable per-Pokémon state, centralized in one place.
 * Composed of focused sub-states for clear separation of concerns.
 * Created on first tick, removed on cleanup — one call wipes everything.
 */
class PokemonWorkerState(val pokemonId: UUID) {
    val job = JobState()
    val nav = NavState()
    val deposit = DepositState()
    val idle = IdleState()
    val visual = VisualState()

    /** Job-specific scratch space for custom per-job data. */
    var lastActionTime: Long = 0L

    /** Per-job scratch data for multi-tick operations (e.g. tree felling). Cleared on job reset. */
    var scratchData: Any? = null

    // ── Convenience delegates (keep call sites concise) ──────────────

    var activeJob: Worker? by job::activeJob
    var profile: PokemonProfile? by job::profile
    var jobAssignedTick: Long by job::jobAssignedTick
    var lastMoveSet: Set<String> by job::lastMoveSet
    var phase: JobPhase by job::phase
    var cooldownUntil: Long by job::cooldownUntil

    var claim: NavigationClaim? by nav::claim
    var targetPos: BlockPos? by nav::targetPos
    var secondaryTargetPos: BlockPos? by nav::secondaryTargetPos
    var lastPathfindTick: Long by nav::lastPathfindTick

    val heldItems: MutableList<ItemStack> get() = deposit.heldItems
    val failedDeposits: MutableSet<BlockPos> get() = deposit.failedDeposits
    var heldSinceTick: Long by deposit::heldSinceTick
    var depositRetryTick: Long by deposit::depositRetryTick
    var depositArrivalTick: Long? by deposit::depositArrivalTick
    var lastDepositWarning: Long by deposit::lastDepositWarning

    var idleSinceTick: Long by idle::idleSinceTick
    var idleLogTick: Long by idle::idleLogTick
    var lastWanderTick: Long by idle::lastWanderTick
    var lastPickupAttemptTick: Long by idle::lastPickupAttemptTick
    var idlePickupClaimTick: Long by idle::idlePickupClaimTick
    var returningHome: Boolean by idle::returningHome
    var lastReturnNavTick: Long by idle::lastReturnNavTick

    var arrivalTick: Long? by visual::arrivalTick
    var graceTick: Long? by visual::graceTick
    var lastAnimationTick: Long by visual::lastAnimationTick

    /**
     * Reset job-related state without destroying idle/navigation state.
     * Called when switching jobs or transitioning to idle.
     */
    fun resetJobState() {
        job.reset(hasOrphanItems = deposit.heldItems.isNotEmpty())
        nav.reset()
        visual.resetArrival()
        lastActionTime = 0L
        scratchData = null
    }
}

/**
 * Tracks a Pokémon's claim on a target (block, player, or mob).
 * At most one claim per Pokémon at a time.
 */
data class NavigationClaim(
    val target: Target,
    val claimTick: Long,
    val timeoutTicks: Long = 200L,
)
