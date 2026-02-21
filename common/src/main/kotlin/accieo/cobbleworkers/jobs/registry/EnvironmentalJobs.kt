/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs.registry

import accieo.cobbleworkers.cache.CobbleworkersCacheManager
import accieo.cobbleworkers.config.JobConfig
import accieo.cobbleworkers.config.JobConfigManager
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.WorkerPriority
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.jobs.WorkerRegistry
import accieo.cobbleworkers.mixin.AbstractFurnaceBlockEntityAccessor
import accieo.cobbleworkers.mixin.BrewingStandBlockEntityAccessor
import accieo.cobbleworkers.utilities.CobbleworkersCauldronUtils
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.*
import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.block.entity.BrewingStandBlockEntity
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
        override val targetCategory = BlockCategory.WATER

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

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            val water = CobbleworkersCacheManager.getTargets(origin, BlockCategory.WATER)
            val ice = CobbleworkersCacheManager.getTargets(origin, BlockCategory.ICE)
            return water.isNotEmpty() || ice.isNotEmpty()
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findFrostTarget(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }

            if (world.getBlockState(target).isAir) {
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
                return
            }

            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.SNOWFLAKE, 3.0)) {
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
            val water = CobbleworkersCacheManager.getTargets(origin, BlockCategory.WATER)
            val ice = CobbleworkersCacheManager.getTargets(origin, BlockCategory.ICE)
            return (water + ice)
                .filter { !CobbleworkersNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
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
        override val targetCategory = BlockCategory.LAVA

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

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return CobbleworkersCacheManager.getTargets(origin, BlockCategory.LAVA).isNotEmpty()
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findLava(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }

            if (world.getBlockState(target).isAir) {
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
                return
            }

            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.CLOUD, 3.0)) {
                val state = world.getBlockState(target)
                if (state.block == Blocks.LAVA) {
                    world.setBlockState(target, Blocks.OBSIDIAN.defaultState)
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findLava(world: World, origin: BlockPos): BlockPos? {
            return CobbleworkersCacheManager.getTargets(origin, BlockCategory.LAVA)
                .filter { !CobbleworkersNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
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
        override val targetCategory = BlockCategory.GROWABLE

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

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return CobbleworkersCacheManager.getTargets(origin, BlockCategory.GROWABLE).isNotEmpty()
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findGrowable(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }

            if (world.getBlockState(target).isAir) {
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
                return
            }

            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.HAPPY_VILLAGER, 3.0)) {
                val sw = world as? ServerWorld
                if (sw != null) {
                    val state = world.getBlockState(target)
                    if (state.block is CropBlock) {
                        repeat(3) { state.randomTick(sw, target, sw.random) }
                    }
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findGrowable(world: World, origin: BlockPos): BlockPos? {
            return CobbleworkersCacheManager.getTargets(origin, BlockCategory.GROWABLE)
                .filter { !CobbleworkersNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            targets.remove(pokemonId)
        }
    }

    // ── G4: Lava Cauldron Filler ─────────────────────────────────────
    object LavaCauldronFiller : Worker {
        override val name = "lava_cauldron_filler"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.CAULDRON

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("lavaplume", "heatwave", "eruption")
        private val lastGenTime = mutableMapOf<UUID, Long>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                cooldownSeconds = 90,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "FIRE",
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "FIRE" }.uppercase()
            return ft in types
        }

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return CobbleworkersCauldronUtils.findClosestCauldron(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val cd = (config.cooldownSeconds.takeIf { it > 0 } ?: 90) * 20L
            if (now - last < cd) return

            val cauldron = CobbleworkersCauldronUtils.findClosestCauldron(world, origin) ?: return
            val current = CobbleworkersNavigationUtils.getTarget(pid, world)
            if (current == null) {
                if (!CobbleworkersNavigationUtils.isTargeted(cauldron, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, cauldron, world)
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, cauldron)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, current)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, current, world, ParticleTypes.LAVA, 3.0)) {
                CobbleworkersCauldronUtils.addFluid(world, current, CobbleworkersCauldronUtils.CauldronFluid.LAVA)
                lastGenTime[pid] = now
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
            }
        }

        override fun cleanup(pokemonId: UUID) { lastGenTime.remove(pokemonId) }
    }

    // ── G5: Water Cauldron Filler ────────────────────────────────────
    object WaterCauldronFiller : Worker {
        override val name = "water_cauldron_filler"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.CAULDRON

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("surf", "watergun", "hydropump")
        private val lastGenTime = mutableMapOf<UUID, Long>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                cooldownSeconds = 90,
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

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return CobbleworkersCauldronUtils.findClosestCauldron(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val cd = (config.cooldownSeconds.takeIf { it > 0 } ?: 90) * 20L
            if (now - last < cd) return

            val cauldron = CobbleworkersCauldronUtils.findClosestCauldron(world, origin) ?: return
            val current = CobbleworkersNavigationUtils.getTarget(pid, world)
            if (current == null) {
                if (!CobbleworkersNavigationUtils.isTargeted(cauldron, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, cauldron, world)
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, cauldron)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, current)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, current, world, ParticleTypes.SPLASH, 3.0)) {
                CobbleworkersCauldronUtils.addFluid(world, current, CobbleworkersCauldronUtils.CauldronFluid.WATER)
                lastGenTime[pid] = now
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
            }
        }

        override fun cleanup(pokemonId: UUID) { lastGenTime.remove(pokemonId) }
    }

    // ── G6: Snow Cauldron Filler ─────────────────────────────────────
    object SnowCauldronFiller : Worker {
        override val name = "snow_cauldron_filler"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.CAULDRON

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("blizzard", "iciclecrash", "powdersnow")
        private val lastGenTime = mutableMapOf<UUID, Long>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                cooldownSeconds = 90,
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

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return CobbleworkersCauldronUtils.findClosestCauldron(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val cd = (config.cooldownSeconds.takeIf { it > 0 } ?: 90) * 20L
            if (now - last < cd) return

            val cauldron = CobbleworkersCauldronUtils.findClosestCauldron(world, origin) ?: return
            val current = CobbleworkersNavigationUtils.getTarget(pid, world)
            if (current == null) {
                if (!CobbleworkersNavigationUtils.isTargeted(cauldron, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, cauldron, world)
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, cauldron)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, current)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, current, world, ParticleTypes.SNOWFLAKE, 3.0)) {
                CobbleworkersCauldronUtils.addFluid(world, current, CobbleworkersCauldronUtils.CauldronFluid.POWDER_SNOW)
                lastGenTime[pid] = now
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
            }
        }

        override fun cleanup(pokemonId: UUID) { lastGenTime.remove(pokemonId) }
    }

    // ── G7: Furnace Fueler ───────────────────────────────────────────
    object FurnaceFueler : Worker {
        override val name = "furnace_fueler"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.FURNACE

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("flamethrower", "fireblast", "firespin")
        private val lastGenTime = mutableMapOf<UUID, Long>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                cooldownSeconds = 80,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "FIRE",
                burnTimeSeconds = 200,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "FIRE" }.uppercase()
            return ft in types
        }

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return findReadyFurnace(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val cd = (config.cooldownSeconds.takeIf { it > 0 } ?: 80) * 20L
            if (now - last < cd) return

            val furnace = findReadyFurnace(world, origin) ?: return
            val current = CobbleworkersNavigationUtils.getTarget(pid, world)
            if (current == null) {
                if (!CobbleworkersNavigationUtils.isTargeted(furnace, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, furnace, world)
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, furnace)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, current)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, current, world, ParticleTypes.FLAME, 3.0)) {
                addBurnTime(world, current)
                lastGenTime[pid] = now
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
            }
        }

        private fun findReadyFurnace(world: World, origin: BlockPos): BlockPos? {
            val targets = CobbleworkersCacheManager.getTargets(origin, BlockCategory.FURNACE)
            return targets
                .filter { pos ->
                    val be = world.getBlockEntity(pos) as? AbstractFurnaceBlockEntity ?: return@filter false
                    !be.getStack(0).isEmpty
                        && !world.getBlockState(pos).get(AbstractFurnaceBlock.LIT)
                        && !CobbleworkersNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        private fun addBurnTime(world: World, pos: BlockPos) {
            val be = world.getBlockEntity(pos) as? AbstractFurnaceBlockEntity ?: return
            val accessor = be as AbstractFurnaceBlockEntityAccessor
            val bt = (config.burnTimeSeconds?.takeIf { it > 0 } ?: 200) * 20
            val clamped = bt.coerceAtMost(20000)
            accessor.setBurnTime(clamped)
            accessor.setFuelTime(clamped)
            world.setBlockState(pos, world.getBlockState(pos).with(AbstractFurnaceBlock.LIT, true))
            be.markDirty()
        }

        override fun cleanup(pokemonId: UUID) { lastGenTime.remove(pokemonId) }
    }

    // ── G8: Brewing Stand Fueler ─────────────────────────────────────
    object BrewingStandFueler : Worker {
        override val name = "brewing_stand_fueler"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.BREWING_STAND

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("dragonbreath", "dracometeor", "dragonpulse")
        private val lastGenTime = mutableMapOf<UUID, Long>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                cooldownSeconds = 80,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "DRAGON",
                addedFuel = 10,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "DRAGON" }.uppercase()
            return ft in types
        }

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return findReadyBrewingStand(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val cd = (config.cooldownSeconds.takeIf { it > 0 } ?: 80) * 20L
            if (now - last < cd) return

            val stand = findReadyBrewingStand(world, origin) ?: return
            val current = CobbleworkersNavigationUtils.getTarget(pid, world)
            if (current == null) {
                if (!CobbleworkersNavigationUtils.isTargeted(stand, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, stand, world)
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, stand)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, current)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, current, world, ParticleTypes.FLAME, 3.0)) {
                addFuel(world, current)
                lastGenTime[pid] = now
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
            }
        }

        private fun findReadyBrewingStand(world: World, origin: BlockPos): BlockPos? {
            val targets = CobbleworkersCacheManager.getTargets(origin, BlockCategory.BREWING_STAND)
            return targets
                .filter { pos ->
                    val be = world.getBlockEntity(pos) as? BrewingStandBlockEntity ?: return@filter false
                    val accessor = be as BrewingStandBlockEntityAccessor
                    accessor.fuel < BrewingStandBlockEntity.MAX_FUEL_USES
                        && !CobbleworkersNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        private fun addFuel(world: World, pos: BlockPos) {
            val be = world.getBlockEntity(pos) as? BrewingStandBlockEntity ?: return
            val accessor = be as BrewingStandBlockEntityAccessor
            val added = (accessor.fuel + (config.addedFuel?.takeIf { it > 0 } ?: 10))
                .coerceAtMost(BrewingStandBlockEntity.MAX_FUEL_USES)
            accessor.setFuel(added)
            be.markDirty()
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL)
        }

        override fun cleanup(pokemonId: UUID) { lastGenTime.remove(pokemonId) }
    }

    // ── G9: Fire Douser ──────────────────────────────────────────────
    object FireDouser : Worker {
        override val name = "fire_douser"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.FIRE

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("waterpulse", "raindance", "muddywater")
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "WATER",
                radius = 2,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "WATER" }.uppercase()
            return ft in types
        }

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return CobbleworkersCacheManager.getTargets(origin, BlockCategory.FIRE).isNotEmpty()
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findFire(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.SMOKE, 3.0)) {
                val r = config.radius?.takeIf { it > 0 } ?: 2
                BlockPos.iterate(target.add(-r, 0, -r), target.add(r, 0, r)).forEach { p ->
                    if (world.getBlockState(p).block == Blocks.FIRE || world.getBlockState(p).block == Blocks.SOUL_FIRE) {
                        world.setBlockState(p, Blocks.AIR.defaultState)
                    }
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findFire(world: World, origin: BlockPos): BlockPos? {
            val targets = CobbleworkersCacheManager.getTargets(origin, BlockCategory.FIRE)
            return targets
                .filter { !CobbleworkersNavigationUtils.isRecentlyExpired(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) { targets.remove(pokemonId) }
    }

    // ── G10: Crop Irrigator ──────────────────────────────────────────
    object CropIrrigatorWorker : Worker {
        override val name = "crop_irrigator"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.FARMLAND

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("aquaring", "lifedew", "waterpledge")
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackType = "WATER",
                radius = 2,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val ft = config.fallbackType.ifEmpty { "WATER" }.uppercase()
            return ft in types
        }

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return findDryFarmland(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val target = targets[pid]
            if (target == null) {
                val found = findDryFarmland(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.SPLASH, 3.0)) {
                val r = config.radius?.takeIf { it > 0 } ?: 2
                BlockPos.iterate(target.add(-r, 0, -r), target.add(r, 0, r)).forEach { p ->
                    val s = world.getBlockState(p)
                    if (s.block == Blocks.FARMLAND) {
                        world.setBlockState(p, s.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.NOTIFY_LISTENERS)
                    }
                }
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findDryFarmland(world: World, origin: BlockPos): BlockPos? {
            val targets = CobbleworkersCacheManager.getTargets(origin, BlockCategory.FARMLAND)
            return targets
                .filter { pos ->
                    world.getBlockState(pos).block == Blocks.FARMLAND
                        && world.getBlockState(pos).get(FarmlandBlock.MOISTURE) <= 2
                        && !CobbleworkersNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) { targets.remove(pokemonId) }
    }

    // ── G11: Bee Pollinator ──────────────────────────────────────────
    // Fills non-full beehives with honey over time (counterpart to honey_harvester gathering)
    object BeePollinator : Worker {
        override val name = "bee_pollinator"
        override val priority = WorkerPriority.SPECIES
        override val targetCategory = BlockCategory.HONEY

        private val config get() = JobConfigManager.get(name)
        private val qualifyingMoves = setOf("pollenpuff", "healorder")
        private val fallbackSpecies = listOf("Combee", "Vespiquen")
        private val lastGenTime = mutableMapOf<UUID, Long>()
        private val targets = mutableMapOf<UUID, BlockPos>()

        init {
            JobConfigManager.registerDefault("environmental", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackSpecies = fallbackSpecies,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val eff = config.qualifyingMoves.ifEmpty { qualifyingMoves }.map { it.lowercase() }.toSet()
            if (moves.any { it in eff }) return true
            val sp = config.fallbackSpecies.ifEmpty { fallbackSpecies }
            return sp.any { it.equals(species, ignoreCase = true) }
        }

        override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
            return findNonFullHive(world, origin) != null
        }

        override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
            val pid = pokemonEntity.pokemon.uuid
            val now = world.time
            val last = lastGenTime[pid] ?: 0L
            val cd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            if (now - last < cd) return

            val target = targets[pid]
            if (target == null) {
                val found = findNonFullHive(world, origin) ?: return
                if (!CobbleworkersNavigationUtils.isTargeted(found, world)) {
                    CobbleworkersNavigationUtils.claimTarget(pid, found, world)
                    targets[pid] = found
                    CobbleworkersNavigationUtils.navigateTo(pokemonEntity, found)
                }
                return
            }
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
            if (WorkerVisualUtils.handleArrival(pokemonEntity, target, world, ParticleTypes.WAX_ON, 3.0)) {
                val state = world.getBlockState(target)
                if (state.block is BeehiveBlock) {
                    val level = state.get(BeehiveBlock.HONEY_LEVEL)
                    if (level < BeehiveBlock.FULL_HONEY_LEVEL) {
                        world.setBlockState(target, state.with(BeehiveBlock.HONEY_LEVEL, level + 1), Block.NOTIFY_ALL)
                    }
                }
                lastGenTime[pid] = now
                CobbleworkersNavigationUtils.releaseTarget(pid, world)
                targets.remove(pid)
            }
        }

        private fun findNonFullHive(world: World, origin: BlockPos): BlockPos? {
            val hives = CobbleworkersCacheManager.getTargets(origin, BlockCategory.HONEY)
            return hives
                .filter { pos ->
                    val s = world.getBlockState(pos)
                    s.block is BeehiveBlock
                        && s.get(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.FULL_HONEY_LEVEL
                        && !CobbleworkersNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        }

        override fun hasActiveState(pokemonId: UUID) = pokemonId in targets
        override fun cleanup(pokemonId: UUID) {
            lastGenTime.remove(pokemonId)
            targets.remove(pokemonId)
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            FrostFormer, ObsidianForge, GrowthAccelerator,
            LavaCauldronFiller, WaterCauldronFiller, SnowCauldronFiller,
            FurnaceFueler, BrewingStandFueler,
            FireDouser, CropIrrigatorWorker, BeePollinator,
        )
    }
}
