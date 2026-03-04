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
import akkiruk.cobblecrew.jobs.JobContext
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
    private val SCAN_COOLDOWN_TICKS: Long get() = config.scanCooldownSeconds.toLong() * 20L

    private data class ScanJob(
        val iterator: Iterator<BlockPos>,
        var lastTickProcessed: Long,
        val staged: MutableMap<BlockCategory, MutableSet<BlockPos>> = mutableMapOf()
    )

    private val activeScans = mutableMapOf<BlockPos, ScanJob>()
    private val lastScanCompletion = mutableMapOf<BlockPos, Long>()

    /** Categories actually needed by registered workers — computed once after init. */
    private var neededCategories: Set<BlockCategory>? = null

    fun getNeededCategories(): Set<BlockCategory> {
        neededCategories?.let { return it }
        val cats = WorkerRegistry.workers.flatMap { w ->
            listOfNotNull(w.targetCategory) + w.additionalScanCategories
        }.toMutableSet()
        cats.add(BlockCategory.CONTAINER)
        return cats.also { neededCategories = it }
    }

    /** Call on config reload to recompute needed categories. */
    fun invalidate() { neededCategories = null }

    /**
     * Initiates or continues a deferred area scan for one tick.
     * Populates BlockCategory caches for all registered validators.
     * Used for pasture contexts; party contexts use eager scanning instead.
     */
    fun tickAreaScan(context: JobContext) {
        val world = context.world
        val key = context.origin
        val scanCenter = context.origin
        val currentTick = world.time

        clearExpiredCompletions(currentTick)

        lastScanCompletion[key]?.let { lastTick ->
            if (currentTick - lastTick < SCAN_COOLDOWN_TICKS) return
        }

        val scanJob = activeScans.getOrPut(key) {
            val radius = searchRadius.toDouble()
            val height = searchHeight.toDouble()
            val searchArea = Box(scanCenter).expand(radius, height, radius)
            CobbleCrewDebugLogger.scanStarted(scanCenter, searchRadius, searchHeight)
            ScanJob(BlockPos.stream(searchArea).iterator(), currentTick - 1)
        }

        if (scanJob.lastTickProcessed == currentTick) return
        scanJob.lastTickProcessed = currentTick

        val categoryValidators = BlockCategoryValidators.validators
        val needed = getNeededCategories()

        repeat(BLOCKS_PER_TICK) {
            if (!scanJob.iterator.hasNext()) {
                CobbleCrewCacheManager.replaceAllCategoryTargets(key, scanJob.staged)
                activeScans.remove(key)
                lastScanCompletion[key] = currentTick

                val counts = needed.associateWith { cat ->
                    CobbleCrewCacheManager.getTargets(key, cat).size
                }
                CobbleCrewDebugLogger.scanComplete(
                    scanCenter,
                    counts.map { (k, v) -> k.name to v }.toMap()
                )
                return
            }

            val pos = scanJob.iterator.next()

            val chunkPos = ChunkPos(pos)
            if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) return@repeat

            val state = world.getBlockState(pos)
            if (state.isAir) return@repeat

            classifyBlock(world, pos, state.block, state, needed, categoryValidators, scanJob.staged)
        }
    }

    /** Backward-compat wrapper: delegates to tickAreaScan. */
    fun tickPastureAreaScan(world: World, pastureOrigin: BlockPos) {
        tickAreaScan(JobContext.Pasture(pastureOrigin, world))
    }

    /** Checks whether a scan job is running for the given origin. */
    fun isScanActive(origin: BlockPos): Boolean = activeScans.containsKey(origin)

    /** Abort any in-progress scan and clear cooldown for the given origin. */
    fun clearScan(origin: BlockPos) {
        activeScans.remove(origin)
        lastScanCompletion.remove(origin)
    }

    /**
     * Classifies a single block position into matching categories.
     * Shared by both deferred (pasture) and eager (party) scanners.
     * Block state is fetched ONCE by the caller and passed in.
     */
    fun classifyBlock(
        world: World,
        pos: BlockPos,
        block: net.minecraft.block.Block,
        state: net.minecraft.block.BlockState,
        needed: Set<BlockCategory>,
        validators: Map<BlockCategory, (net.minecraft.block.Block, net.minecraft.block.BlockState) -> Boolean>,
        staged: MutableMap<BlockCategory, MutableSet<BlockPos>>,
    ) {
        val immutablePos = pos.toImmutable()
        for ((category, validator) in validators) {
            if (category !in needed) continue
            if (validator(block, state)) {
                if (category.requiresExposedFace && !hasExposedFace(world, pos)) continue
                staged.getOrPut(category) { mutableSetOf() }.add(immutablePos)
            }
        }
    }

    fun hasExposedFace(world: World, pos: BlockPos): Boolean {
        return net.minecraft.util.math.Direction.entries.any { dir ->
            val adjacent = pos.offset(dir)
            !world.getBlockState(adjacent).isOpaqueFullCube(world, adjacent)
        }
    }

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