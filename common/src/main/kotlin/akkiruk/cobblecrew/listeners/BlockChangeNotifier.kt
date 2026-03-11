/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.listeners

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.utilities.BlockCategoryValidators
import akkiruk.cobblecrew.utilities.DeferredBlockScanner
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives block change notifications from [WorldSetBlockStateMixin] and
 * updates the per-pasture block caches in real time.
 *
 * Only processes changes within range of an active pasture, and only
 * for [BlockCategory] values that registered workers actually care about.
 */
object BlockChangeNotifier {

    /** Active pasture origins → search radius. */
    private val activePastures = ConcurrentHashMap<BlockPos, Int>()

    /** Chunk-based spatial index: packed chunkX/chunkZ → set of pasture origins covering that chunk. */
    private val chunkIndex = ConcurrentHashMap<Long, MutableSet<BlockPos>>()

    private val config get() = CobbleCrewConfigHolder.config.general

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    /** Compute all chunks that a pasture's search area overlaps. */
    private fun coveredChunks(origin: BlockPos, radius: Int): List<Long> {
        val minCx = (origin.x - radius) shr 4
        val maxCx = (origin.x + radius) shr 4
        val minCz = (origin.z - radius) shr 4
        val maxCz = (origin.z + radius) shr 4
        val result = mutableListOf<Long>()
        for (cx in minCx..maxCx) for (cz in minCz..maxCz) result.add(chunkKey(cx, cz))
        return result
    }

    fun registerPasture(origin: BlockPos) {
        val radius = config.searchRadius
        activePastures[origin] = radius
        for (ck in coveredChunks(origin, radius)) {
            chunkIndex.getOrPut(ck) { ConcurrentHashMap.newKeySet() }.add(origin)
        }
    }

    fun unregisterPasture(origin: BlockPos) {
        val radius = activePastures.remove(origin) ?: return
        for (ck in coveredChunks(origin, radius)) {
            chunkIndex[ck]?.let { set ->
                set.remove(origin)
                if (set.isEmpty()) chunkIndex.remove(ck)
            }
        }
    }

    /**
     * Called from the mixin after a successful setBlockState().
     * Bails out fast if no pastures are in range.
     */
    fun onBlockChanged(world: World, pos: BlockPos, newState: BlockState) {
        if (world.isClient) return
        if (activePastures.isEmpty()) return

        val needed = DeferredBlockScanner.getNeededCategories()
        val validators = BlockCategoryValidators.validators

        // Look up only pastures whose search area covers this block's chunk
        val ck = chunkKey(pos.x shr 4, pos.z shr 4)
        val candidates = chunkIndex[ck] ?: return

        for (origin in candidates) {
            val radius = activePastures[origin] ?: continue
            val dx = pos.x - origin.x
            val dy = pos.y - origin.y
            val dz = pos.z - origin.z
            val searchHeight = config.searchHeight
            if (dx * dx + dz * dz > radius * radius) continue
            if (dy < -searchHeight || dy > searchHeight) continue

            // In range of this pasture — update its cache
            if (newState.isAir) {
                // Block was removed — clear from all categories
                for (cat in needed) {
                    CobbleCrewCacheManager.removeTarget(origin, cat, pos)
                }
            } else {
                // Block was placed/changed — classify and update
                val block = newState.block
                for ((category, validator) in validators) {
                    if (category !in needed) continue
                    if (validator(block, newState)) {
                        if (category.requiresExposedFace && !DeferredBlockScanner.hasExposedFace(world, pos)) continue
                        CobbleCrewCacheManager.addTarget(origin, category, pos.toImmutable())
                    } else {
                        CobbleCrewCacheManager.removeTarget(origin, category, pos)
                    }
                }
            }
        }
    }

    fun clearAll() {
        activePastures.clear()
        chunkIndex.clear()
    }
}
