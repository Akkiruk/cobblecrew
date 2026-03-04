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

/**
 * All mutable per-Pokémon state, centralized in one place.
 * Eliminates the 77 scattered UUID-keyed maps across singletons.
 * Created on first tick, removed on cleanup — one call wipes everything.
 */
class PokemonWorkerState(val pokemonId: UUID) {

    // --- Job assignment ---
    var activeJob: Worker? = null
    var profile: PokemonProfile? = null
    var jobAssignedTick: Long = 0L
    var lastMoveSet: Set<String> = emptySet()

    // --- Lifecycle phase (driven by BaseJob state machine) ---
    var phase: JobPhase = JobPhase.IDLE

    // --- Idle behavior ---
    var idleSinceTick: Long = 0L
    var idleLogTick: Long = 0L
    var lastWanderTick: Long = 0L
    var lastPickupAttemptTick: Long = 0L
    var idlePickupClaimTick: Long = 0L
    var returningHome: Boolean = false
    var lastReturnNavTick: Long = 0L

    // --- Items (held by Pokémon, awaiting deposit/delivery) ---
    val heldItems: MutableList<ItemStack> = mutableListOf()
    val failedDeposits: MutableSet<BlockPos> = mutableSetOf()
    var heldSinceTick: Long = 0L
    var depositRetryTick: Long = 0L
    var depositArrivalTick: Long? = null
    var lastDepositWarning: Long = 0L

    // --- Navigation / Claims ---
    var claim: NavigationClaim? = null
    var targetPos: BlockPos? = null
    var lastPathfindTick: Long = 0L

    // --- Visual feedback ---
    var arrivalTick: Long? = null
    var graceTick: Long? = null
    var lastAnimationTick: Long = 0L

    // --- Job-specific scratch space ---
    var secondaryTargetPos: BlockPos? = null
    var lastActionTime: Long = 0L
    var cooldownUntil: Long = 0L

    /**
     * Reset job-related state without destroying idle/navigation state.
     * Called when switching jobs.
     */
    fun resetJobState() {
        // Keep DEPOSITING if we have orphan items — they must be delivered
        phase = if (heldItems.isNotEmpty()) JobPhase.DEPOSITING else JobPhase.IDLE
        targetPos = null
        secondaryTargetPos = null
        arrivalTick = null
        graceTick = null
        lastActionTime = 0L
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
