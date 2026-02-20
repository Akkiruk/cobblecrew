/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.config.JobConfig
import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.WorkerPriority
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.SaplingBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Environmental jobs (G1–G3). Modify blocks in-world without breaking them.
 */
object EnvironmentalJobs {

    // ── G1: Frost Former ─────────────────────────────────────────────
    // Water → Ice → Packed Ice → Blue Ice (progressive chain)
    object FrostFormer : Worker {
        override val name = "frost_former"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("icebeam", "sheercold", "auroraveil")
        private val targets = mutableMapOf<UUID, BlockPos>()

        private val CHAIN = mapOf(
            Blocks.WATER to Blocks.ICE,
            Blocks.ICE to Blocks.PACKED_ICE,
            Blocks.PACKED_ICE to Blocks.BLUE_ICE,
        )

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "ICE",
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "ICE" }.uppercase()
            return ft in types
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findFrostTarget(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.SNOWFLAKE)) {
                val state = world.getBlockState(target)
                val next = CHAIN[state.block]
                if (next != null) {
                    world.setBlockState(target, next.defaultState)
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findFrostTarget(world: World, origin: BlockPos): BlockPos? {
            val r = 8
            return BlockPos.iterateOutwards(origin, r, r, r)
                .filter { CHAIN.containsKey(world.getBlockState(it).block) }
                .filter { !CobbleworkersNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
                ?.toImmutable()
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            targets.remove(pokemonId)
        }
    }

    // ── G2: Obsidian Forge ───────────────────────────────────────────
    // Lava source → obsidian (break + deposit via loot)
    object ObsidianForge : Worker {
        override val name = "obsidian_forge"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("hydropump", "scald", "aquatail", "brine")
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "WATER",
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "WATER" }.uppercase()
            return ft in types
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findLava(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.CLOUD)) {
                val state = world.getBlockState(target)
                if (state.block == Blocks.LAVA) {
                    world.setBlockState(target, Blocks.OBSIDIAN.defaultState)
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findLava(world: World, origin: BlockPos): BlockPos? {
            val r = 8
            return BlockPos.iterateOutwards(origin, r, r, r)
                .filter { world.getBlockState(it).block == Blocks.LAVA }
                .filter { !CobbleworkersNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
                ?.toImmutable()
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            targets.remove(pokemonId)
        }
    }

    // ── G3: Growth Accelerator ───────────────────────────────────────
    // Random-ticks crops in range to speed up growth
    object GrowthAccelerator : Worker {
        override val name = "growth_accelerator"
        override val priority = WorkerPriority.MOVE
        override val targetCategory: BlockCategory? = null

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("growth", "sunnyday", "grassyterrain")
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "GRASS",
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "GRASS" }.uppercase()
            return ft in types
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findGrowable(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.HAPPY_VILLAGER)) {
                val sw = world as? ServerWorld
                if (sw != null) {
                    val state = world.getBlockState(target)
                    if (state.block is CropBlock) {
                        // Apply several random ticks to accelerate growth
                        repeat(3) { state.randomTick(sw, target, sw.random) }
                    }
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findGrowable(world: World, origin: BlockPos): BlockPos? {
            val r = 8
            return BlockPos.iterateOutwards(origin, r, r, r)
                .filter { pos ->
                    val block = world.getBlockState(pos).block
                    block is CropBlock || block is SaplingBlock
                }
                .filter { !CobbleworkersNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
                ?.toImmutable()
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            targets.remove(pokemonId)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            FrostFormer, ObsidianForge, GrowthAccelerator,
        )
    }
}
