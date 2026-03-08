/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * What a job is currently targeting. Encapsulates position for navigation
 * regardless of whether the underlying target is a block, player, or mob.
 */
sealed interface Target {
    val pos: BlockPos

    data class Block(override val pos: BlockPos) : Target
    data class Player(val playerId: UUID, override val pos: BlockPos) : Target
    data class Mob(val entityId: UUID, override val pos: BlockPos) : Target
}

/**
 * Result of a [BaseJob.doWork] call.
 */
sealed interface WorkResult {
    /** Work cycle complete. Any non-empty items list triggers DEPOSITING phase. */
    data class Done(val items: List<net.minecraft.item.ItemStack> = emptyList()) : WorkResult

    /** Job needs another navigate→work cycle at a different position (e.g. placement jobs). */
    data class MoveTo(val target: BlockPos) : WorkResult

    /** Stay at current target and re-enter ARRIVING after cooldown (e.g. cauldron fillers). */
    data object Repeat : WorkResult

    /** Work in progress — stay in WORKING phase, doWork called again next tick. */
    data object Continue : WorkResult
}
