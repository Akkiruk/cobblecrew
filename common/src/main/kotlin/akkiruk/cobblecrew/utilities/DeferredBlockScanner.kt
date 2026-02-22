/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.jobs.WorkerRegistry
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World

object DeferredBlockScanner {
    private val config get() = CobbleCrewConfigHolder.config.general
    private val BLOCKS_PER_TICK get() = config.blocksScannedPerTick
    private val searchRadius get() = config.searchRadius
    private val searchHeight get() = config.searchHeight
    private const val SCAN_COOLDOWN_TICKS = 60 * 20L

    private data class ScanJob(
        val iterator: Iterator<BlockPos>,
        var lastTickProcessed: Long
    )

    private val activeScans = mutableMapOf<BlockPos, ScanJob>()
    private val lastScanCompletion = mutableMapOf<BlockPos, Long>()

    /** Categories actually needed by registered workers — computed once after init. */
    private var neededCategories: Set<BlockCategory>? = null

    private fun getNeededCategories(): Set<BlockCategory> {
        neededCategories?.let { return it }
        val cats = WorkerRegistry.workers.mapNotNull { it.targetCategory }.toMutableSet()
        // CONTAINER is always needed (deposit targets)
        cats.add(BlockCategory.CONTAINER)
        return cats.also { neededCategories = it }
    }

    /** Call on config reload to recompute needed categories. */
    fun invalidate() { neededCategories = null }

    /**
     * Initiates or continues a deferred area scan for a pasture for one tick.
     * Populates BlockCategory caches for all registered validators.
     */
    fun tickPastureAreaScan(
        world: World,
        pastureOrigin: BlockPos,
    ) {
        val currentTick = world.time

        clearExpiredCompletions(currentTick)

        lastScanCompletion[pastureOrigin]?.let { lastTick ->
            if (currentTick - lastTick < SCAN_COOLDOWN_TICKS) return
        }

        val scanJob = activeScans.getOrPut(pastureOrigin) {
            CobbleCrewCacheManager.removeAllCategoryTargets(pastureOrigin)

            val radius = searchRadius.toDouble()
            val height = searchHeight.toDouble()
            val searchArea = Box(pastureOrigin).expand(radius, height, radius)
            ScanJob(BlockPos.stream(searchArea).iterator(), currentTick - 1)
        }

        if (scanJob.lastTickProcessed == currentTick) return
        scanJob.lastTickProcessed = currentTick

        val categoryValidators = BlockCategoryValidators.validators
        val needed = getNeededCategories()

        repeat(BLOCKS_PER_TICK) {
            if (!scanJob.iterator.hasNext()) {
                activeScans.remove(pastureOrigin)
                lastScanCompletion[pastureOrigin] = currentTick
                return
            }

            val pos = scanJob.iterator.next()

            val chunkPos = ChunkPos(pos)
            if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) return@repeat

            val immutablePos = pos.toImmutable()

            for ((category, validator) in categoryValidators) {
                if (category !in needed) continue
                if (validator(world, pos)) {
                    CobbleCrewCacheManager.addTarget(pastureOrigin, category, immutablePos)
                }
            }
        }
    }

    /** Checks whether a scan job is running for the given pasture origin. */
    fun isScanActive(pastureOrigin: BlockPos): Boolean = activeScans.containsKey(pastureOrigin)

    private fun clearExpiredCompletions(currentTick: Long) {
        val iterator = lastScanCompletion.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTick - entry.value >= SCAN_COOLDOWN_TICKS) {
                iterator.remove()
            }
        }
    }
}