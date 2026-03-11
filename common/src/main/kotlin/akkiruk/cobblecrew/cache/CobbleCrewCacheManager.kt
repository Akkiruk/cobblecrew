/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.cache

import akkiruk.cobblecrew.enums.BlockCategory
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.structure.Structure
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-origin block target cache. Keys are origin [BlockPos] (pasture pos or party scan center).
 * Each origin gets a map of [BlockCategory] → set of target positions.
 */
object CobbleCrewCacheManager {
    private val caches = ConcurrentHashMap<BlockPos, MutableMap<BlockCategory, MutableSet<BlockPos>>>()

    /** Reverse index: (category, targetPos) → set of origins that cache this target. */
    private val reverseIndex = ConcurrentHashMap<BlockCategory, ConcurrentHashMap<BlockPos, MutableSet<BlockPos>>>()

    private var structuresCache: Set<Identifier>? = null
    private val structureLocationCache = ConcurrentHashMap<Identifier, com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>>()
    private val structureLocationExpiry = ConcurrentHashMap<Identifier, Long>()
    private const val CACHE_TTL = 20L * 60L * 15L

    private fun newCategoryMap(): MutableMap<BlockCategory, MutableSet<BlockPos>> =
        BlockCategory.entries.associateWith { mutableSetOf<BlockPos>() }.toMutableMap()

    fun getTargets(origin: BlockPos, category: BlockCategory): Set<BlockPos> =
        caches[origin]?.get(category)?.toSet() ?: emptySet()

    /** Remove a position from ALL origin caches (handles overlapping pastures). O(1) via reverse index. */
    fun removeTargetGlobal(category: BlockCategory, pos: BlockPos) {
        val origins = reverseIndex[category]?.remove(pos) ?: return
        for (origin in origins) {
            caches[origin]?.get(category)?.remove(pos)
        }
    }

    /** Add a single position to a specific origin's cache (live block change updates). */
    fun addTarget(origin: BlockPos, category: BlockCategory, pos: BlockPos) {
        val cache = caches[origin] ?: return
        if (cache.getOrPut(category) { mutableSetOf() }.add(pos)) {
            reverseIndex.getOrPut(category) { ConcurrentHashMap() }
                .getOrPut(pos) { ConcurrentHashMap.newKeySet() }
                .add(origin)
        }
    }

    /** Remove a single position from a specific origin's cache. */
    fun removeTarget(origin: BlockPos, category: BlockCategory, pos: BlockPos) {
        if (caches[origin]?.get(category)?.remove(pos) == true) {
            reverseIndex[category]?.get(pos)?.let { origins ->
                origins.remove(origin)
                if (origins.isEmpty()) reverseIndex[category]?.remove(pos)
            }
        }
    }

    /** Atomically replace all category targets for an origin with new scan results. */
    fun replaceAllCategoryTargets(origin: BlockPos, newTargets: Map<BlockCategory, Set<BlockPos>>) {
        val cache = caches.getOrPut(origin) { newCategoryMap() }
        for ((category, set) in cache) {
            // Remove old reverse entries for this origin
            for (oldPos in set) {
                reverseIndex[category]?.get(oldPos)?.let { origins ->
                    origins.remove(origin)
                    if (origins.isEmpty()) reverseIndex[category]?.remove(oldPos)
                }
            }
            set.clear()
            newTargets[category]?.let { newPositions ->
                set.addAll(newPositions)
                // Add new reverse entries
                val catIndex = reverseIndex.getOrPut(category) { ConcurrentHashMap() }
                for (pos in newPositions) {
                    catIndex.getOrPut(pos) { ConcurrentHashMap.newKeySet() }.add(origin)
                }
            }
        }
    }

    /** Remove an entire origin's cache (pasture removed or party scan origin changed). */
    fun removeCache(origin: BlockPos) {
        val cache = caches.remove(origin) ?: return
        for ((category, positions) in cache) {
            for (pos in positions) {
                reverseIndex[category]?.get(pos)?.let { origins ->
                    origins.remove(origin)
                    if (origins.isEmpty()) reverseIndex[category]?.remove(pos)
                }
            }
        }
    }

    // --- Structure location cache (for Scout job) ---

    fun getStructures(world: ServerWorld, useAll: Boolean, tags: List<String>): Set<Identifier> {
        structuresCache?.let { return it }

        val registryManager = world.server.registryManager
        val structureRegistry = registryManager.get(RegistryKeys.STRUCTURE)

        val structures = if (useAll) {
            structureRegistry.keys.map { it.value }.toSet()
        } else {
            tags.mapNotNull { Identifier.tryParse(it) }.toSet()
        }

        structuresCache = structures
        return structures
    }

    fun getCachedStructure(id: Identifier, now: Long): com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>? {
        val expiry = structureLocationExpiry[id] ?: return null
        if (now > expiry) {
            structureLocationCache.remove(id)
            structureLocationExpiry.remove(id)
            return null
        }
        return structureLocationCache[id]
    }

    fun cacheStructure(id: Identifier, result: com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>, now: Long) {
        structureLocationCache[id] = result
        structureLocationExpiry[id] = now + CACHE_TTL
    }

    // --- Status / diagnostics ---

    fun getPastureStatus(): Map<BlockPos, Map<String, Int>> =
        caches.mapValues { (_, cache) ->
            cache.filter { it.value.isNotEmpty() }
                .mapKeys { it.key.name }
                .mapValues { it.value.size }
        }

    fun getPastureCount(): Int = caches.size

    fun clearAll() {
        caches.clear()
        reverseIndex.clear()
        structuresCache = null
        structureLocationCache.clear()
        structureLocationExpiry.clear()
    }
}