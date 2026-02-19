/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import accieo.cobbleworkers.cache.CobbleworkersCacheManager
import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.JobType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World

object DeferredBlockScanner {
    private val config get() = CobbleworkersConfigHolder.config.general
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

    /**
     * Initiates or continues a deferred area scan for a pasture for one tick.
     * Populates both legacy JobType caches and new BlockCategory caches.
     */
    fun tickPastureAreaScan(
        world: World,
        pastureOrigin: BlockPos,
        jobValidators: Map<JobType, (World, BlockPos) -> Boolean>
    ) {
        val currentTick = world.time

        clearExpiredCompletions(currentTick)

        lastScanCompletion[pastureOrigin]?.let { lastTick ->
            if (currentTick - lastTick < SCAN_COOLDOWN_TICKS) return
        }

        val scanJob = activeScans.getOrPut(pastureOrigin) {
            @Suppress("DEPRECATION")
            CobbleworkersCacheManager.removeTargets(pastureOrigin)
            CobbleworkersCacheManager.removeAllCategoryTargets(pastureOrigin)

            val radius = searchRadius.toDouble()
            val height = searchHeight.toDouble()
            val searchArea = Box(pastureOrigin).expand(radius, height, radius)
            ScanJob(BlockPos.stream(searchArea).iterator(), currentTick - 1)
        }

        if (scanJob.lastTickProcessed == currentTick) return
        scanJob.lastTickProcessed = currentTick

        val categoryValidators = BlockCategoryValidators.validators

        repeat(BLOCKS_PER_TICK) {
            if (!scanJob.iterator.hasNext()) {
                activeScans.remove(pastureOrigin)
                lastScanCompletion[pastureOrigin] = currentTick
                return
            }

            val pos = scanJob.iterator.next()

            // Chunk loading guard
            val chunkPos = ChunkPos(pos)
            if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) return@repeat

            val immutablePos = pos.toImmutable()

            // V2 BlockCategory scanning
            for ((category, validator) in categoryValidators) {
                if (validator(world, pos)) {
                    CobbleworkersCacheManager.addTarget(pastureOrigin, category, immutablePos)
                }
            }

            // Legacy JobType scanning (kept during migration)
            val isValidInventory = CobbleworkersInventoryUtils.blockValidator(world, pos)
            if (isValidInventory) {
                @Suppress("DEPRECATION")
                CobbleworkersCacheManager.addTarget(pastureOrigin, JobType.Generic, immutablePos)
            }

            for ((jobType, validator) in jobValidators) {
                if (validator(world, pos)) {
                    @Suppress("DEPRECATION")
                    CobbleworkersCacheManager.addTarget(pastureOrigin, jobType, immutablePos)
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