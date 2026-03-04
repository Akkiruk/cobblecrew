/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos

/**
 * Caps synchronous pathfinding attempts per server tick to prevent lag spikes.
 * Each findPathTo() call is an A* search that can block the server thread.
 * Without a budget, 20 Pokémon × 3 attempts each = 60 pathfinds/tick.
 */
object PathfindingBudget {
    private const val MAX_PATHFINDS_PER_TICK = 6
    private var usedThisTick = 0
    private var lastResetTick = 0L

    enum class PathResult { REACHABLE, UNREACHABLE, DEFERRED }

    fun tryPathfind(entity: PokemonEntity, target: BlockPos, currentTick: Long): PathResult {
        maybeReset(currentTick)
        if (usedThisTick >= MAX_PATHFINDS_PER_TICK) return PathResult.DEFERRED
        usedThisTick++
        val path = entity.navigation.findPathTo(target, 1)
        return if (path != null && path.reachesTarget()) PathResult.REACHABLE else PathResult.UNREACHABLE
    }

    private fun maybeReset(currentTick: Long) {
        if (currentTick != lastResetTick) {
            lastResetTick = currentTick
            usedThisTick = 0
        }
    }
}
