/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import net.minecraft.entity.mob.HostileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Chunk-granularity cache for hostile entity scans so multiple defenders
 * in the same pasture don't repeat the same `getEntitiesByClass` query.
 */
object HostileScanCache {
    private data class ScanResult(val tick: Long, val hostiles: List<UUID>)

    private val cache = ConcurrentHashMap<Long, ScanResult>()

    /** Returns UUIDs of living hostile mobs in [searchBox], cached per-chunk per-tick. */
    fun getHostileIds(world: World, center: BlockPos, searchBox: Box): List<UUID> {
        val key = packChunkKey(center)
        val now = world.time
        val cached = cache[key]
        if (cached != null && now - cached.tick < 20L) return cached.hostiles

        val result = world.getEntitiesByClass(HostileEntity::class.java, searchBox) { it.isAlive }
            .map { it.uuid }
        cache[key] = ScanResult(now, result)
        return result
    }

    /** Resolve a cached UUID to a living HostileEntity, or null. */
    fun resolve(world: World, id: UUID): HostileEntity? {
        val entity = (world as? net.minecraft.server.world.ServerWorld)?.getEntity(id)
        return entity as? HostileEntity
    }

    fun sweepExpired(now: Long) {
        cache.entries.removeIf { now - it.value.tick >= 40L }
    }

    fun clear() = cache.clear()

    private fun packChunkKey(pos: BlockPos): Long =
        (pos.x.toLong() shr 4) shl 32 or ((pos.z.toLong() shr 4) and 0xFFFFFFFFL)
}
