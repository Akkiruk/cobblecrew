/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.cache.CacheKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

sealed interface JobContext {
    val origin: BlockPos
    val world: World
    val cacheKey: CacheKey

    /** Pasture Pokémon — origin is fixed, cache key is the block pos. */
    data class Pasture(
        override val origin: BlockPos,
        override val world: World,
    ) : JobContext {
        override val cacheKey: CacheKey get() = CacheKey.PastureKey(origin)
    }

    /**
     * Party Pokémon — origin is set to the player's position when each eager scan runs.
     * Between scans, origin stays fixed so cache lookups are stable.
     */
    data class Party(
        val player: ServerPlayerEntity,
        override val world: World,
    ) : JobContext {
        /** Updated every time the eager party scan runs. */
        var scanOrigin: BlockPos? = null

        override val origin: BlockPos
            get() = scanOrigin ?: player.blockPos

        override val cacheKey: CacheKey get() = CacheKey.PastureKey(origin)
    }
}
