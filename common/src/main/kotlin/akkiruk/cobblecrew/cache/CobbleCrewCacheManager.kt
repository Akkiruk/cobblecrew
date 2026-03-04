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
    private var structuresCache: Set<Identifier>? = null
    private val structureLocationCache = ConcurrentHashMap<Identifier, com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Structure>>>()
    private val structureLocationExpiry = ConcurrentHashMap<Identifier, Long>()
    private const val CACHE_TTL = 20L * 60L * 15L

    private fun newCategoryMap(): MutableMap<BlockCategory, MutableSet<BlockPos>> =
        BlockCategory.entries.associateWith { mutableSetOf<BlockPos>() }.toMutableMap()

    fun getTargets(origin: BlockPos, category: BlockCategory): Set<BlockPos> =
        caches[origin]?.get(category)?.toSet() ?: emptySet()

    /** Remove a position from ALL origin caches (handles overlapping pastures). */
    fun removeTargetGlobal(category: BlockCategory, pos: BlockPos) {
        caches.values.forEach { it[category]?.remove(pos) }
    }

    /** Atomically replace all category targets for an origin with new scan results. */
    fun replaceAllCategoryTargets(origin: BlockPos, newTargets: Map<BlockCategory, Set<BlockPos>>) {
        val cache = caches.getOrPut(origin) { newCategoryMap() }
        for ((category, set) in cache) {
            set.clear()
            newTargets[category]?.let { set.addAll(it) }
        }
    }

    /** Remove an entire origin's cache (pasture removed or party scan origin changed). */
    fun removeCache(origin: BlockPos) {
        caches.remove(origin)
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
        structuresCache = null
        structureLocationCache.clear()
        structureLocationExpiry.clear()
    }
}