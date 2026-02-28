/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.EnvironmentalJob
import akkiruk.cobblecrew.mixin.AbstractFurnaceBlockEntityAccessor
import akkiruk.cobblecrew.mixin.BrewingStandBlockEntityAccessor
import akkiruk.cobblecrew.utilities.CobbleCrewCauldronUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import net.minecraft.block.*
import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.block.entity.BrewingStandBlockEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Environmental jobs (G1–G11). Modify blocks in-world without breaking them.
 * Most use [EnvironmentalJob] DSL; FurnaceFueler and BrewingStandFueler use
 * custom findTarget/action for block entity manipulation via mixin accessors.
 */
object EnvironmentalJobs {

    private val FROST_CHAIN = mapOf(
        Blocks.WATER to Blocks.ICE,
        Blocks.ICE to Blocks.PACKED_ICE,
        Blocks.PACKED_ICE to Blocks.BLUE_ICE,
    )

    // ── G1: Frost Former ─────────────────────────────────────────────
    val FrostFormer = EnvironmentalJob(
        name = "frost_former",
        targetCategory = BlockCategory.WATER,
        additionalScanCategories = setOf(BlockCategory.ICE),
        qualifyingMoves = setOf("icebeam"),
        priority = WorkerPriority.MOVE,
        particle = ParticleTypes.SNOWFLAKE,
        findTarget = { world, origin ->
            val water = CobbleCrewCacheManager.getTargets(origin, BlockCategory.WATER)
            val ice = CobbleCrewCacheManager.getTargets(origin, BlockCategory.ICE)
            (water + ice)
                .filter { !CobbleCrewNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
        },
        action = { world, pos ->
            val next = FROST_CHAIN[world.getBlockState(pos).block]
            if (next != null) world.setBlockState(pos, next.defaultState)
        },
    )

    // ── G2: Obsidian Forge ───────────────────────────────────────────
    val ObsidianForge = EnvironmentalJob(
        name = "obsidian_forge",
        targetCategory = BlockCategory.LAVA,
        qualifyingMoves = setOf("hydropump", "scald"),
        priority = WorkerPriority.MOVE,
        particle = ParticleTypes.CLOUD,
        findTarget = { world, origin ->
            CobbleCrewCacheManager.getTargets(origin, BlockCategory.LAVA)
                .filter { !CobbleCrewNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
        },
        action = { world, pos ->
            if (world.getBlockState(pos).block == Blocks.LAVA)
                world.setBlockState(pos, Blocks.OBSIDIAN.defaultState)
        },
    )

    // ── G3: Growth Accelerator ───────────────────────────────────────
    val GrowthAccelerator = EnvironmentalJob(
        name = "growth_accelerator",
        targetCategory = BlockCategory.GROWABLE,
        qualifyingMoves = setOf("growth", "sunnyday"),
        priority = WorkerPriority.MOVE,
        particle = ParticleTypes.HAPPY_VILLAGER,
        findTarget = { world, origin ->
            CobbleCrewCacheManager.getTargets(origin, BlockCategory.GROWABLE)
                .filter { !CobbleCrewNavigationUtils.isTargeted(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
        },
        action = { world, pos ->
            val sw = world as? ServerWorld ?: return@EnvironmentalJob
            val state = world.getBlockState(pos)
            if (state.block is CropBlock || state.block is SaplingBlock) {
                repeat(3) { state.randomTick(sw, pos, sw.random) }
            }
        },
    )

    // ── G4–G6: Cauldron Fillers ──────────────────────────────────────
    private fun cauldronFiller(
        name: String,
        move: String,
        particle: ParticleEffect,
        fluid: CobbleCrewCauldronUtils.CauldronFluid,
    ) = EnvironmentalJob(
        name = name,
        targetCategory = BlockCategory.CAULDRON,
        qualifyingMoves = setOf(move),
        priority = WorkerPriority.TYPE,
        particle = particle,
        defaultCooldownSeconds = 90,
        findTarget = { world, origin -> CobbleCrewCauldronUtils.findClosestCauldron(world, origin) },
        action = { world, pos -> CobbleCrewCauldronUtils.addFluid(world, pos, fluid) },
        validate = { _, _ -> true },
    )

    val LavaCauldronFiller = cauldronFiller("lava_cauldron_filler", "lavaplume", ParticleTypes.LAVA, CobbleCrewCauldronUtils.CauldronFluid.LAVA)
    val WaterCauldronFiller = cauldronFiller("water_cauldron_filler", "surf", ParticleTypes.SPLASH, CobbleCrewCauldronUtils.CauldronFluid.WATER)
    val SnowCauldronFiller = cauldronFiller("snow_cauldron_filler", "blizzard", ParticleTypes.SNOWFLAKE, CobbleCrewCauldronUtils.CauldronFluid.POWDER_SNOW)

    // ── G7: Furnace Fueler ───────────────────────────────────────────
    val FurnaceFueler = EnvironmentalJob(
        name = "furnace_fueler",
        targetCategory = BlockCategory.FURNACE,
        qualifyingMoves = setOf("fireblast"),
        priority = WorkerPriority.TYPE,
        particle = ParticleTypes.FLAME,
        defaultCooldownSeconds = 80,
        defaultBurnTimeSeconds = 200,
        findTarget = ::findReadyFurnace,
        action = ::addBurnTime,
    )

    private fun findReadyFurnace(world: World, origin: BlockPos): BlockPos? =
        CobbleCrewCacheManager.getTargets(origin, BlockCategory.FURNACE)
            .filter { pos ->
                val be = world.getBlockEntity(pos) as? AbstractFurnaceBlockEntity ?: return@filter false
                !be.getStack(0).isEmpty
                    && !world.getBlockState(pos).get(AbstractFurnaceBlock.LIT)
                    && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
            }
            .minByOrNull { it.getSquaredDistance(origin) }

    private fun addBurnTime(world: World, pos: BlockPos) {
        val be = world.getBlockEntity(pos) as? AbstractFurnaceBlockEntity ?: return
        val accessor = be as AbstractFurnaceBlockEntityAccessor
        val config = JobConfigManager.get("furnace_fueler")
        val bt = (config.burnTimeSeconds?.takeIf { it > 0 } ?: 200) * 20
        val clamped = bt.coerceAtMost(20000)
        accessor.setBurnTime(clamped)
        accessor.setFuelTime(clamped)
        world.setBlockState(pos, world.getBlockState(pos).with(AbstractFurnaceBlock.LIT, true))
        be.markDirty()
    }

    // ── G8: Brewing Stand Fueler ─────────────────────────────────────
    val BrewingStandFueler = EnvironmentalJob(
        name = "brewing_stand_fueler",
        targetCategory = BlockCategory.BREWING_STAND,
        qualifyingMoves = setOf("dragonbreath"),
        priority = WorkerPriority.TYPE,
        particle = ParticleTypes.FLAME,
        defaultCooldownSeconds = 80,
        defaultAddedFuel = 10,
        findTarget = ::findReadyBrewingStand,
        action = ::addBrewingFuel,
    )

    private fun findReadyBrewingStand(world: World, origin: BlockPos): BlockPos? =
        CobbleCrewCacheManager.getTargets(origin, BlockCategory.BREWING_STAND)
            .filter { pos ->
                val be = world.getBlockEntity(pos) as? BrewingStandBlockEntity ?: return@filter false
                val accessor = be as BrewingStandBlockEntityAccessor
                accessor.fuel < BrewingStandBlockEntity.MAX_FUEL_USES
                    && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
            }
            .minByOrNull { it.getSquaredDistance(origin) }

    private fun addBrewingFuel(world: World, pos: BlockPos) {
        val be = world.getBlockEntity(pos) as? BrewingStandBlockEntity ?: return
        val accessor = be as BrewingStandBlockEntityAccessor
        val config = JobConfigManager.get("brewing_stand_fueler")
        val added = (accessor.fuel + (config.addedFuel?.takeIf { it > 0 } ?: 10))
            .coerceAtMost(BrewingStandBlockEntity.MAX_FUEL_USES)
        accessor.setFuel(added)
        be.markDirty()
        world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL)
    }

    // ── G9: Fire Douser ──────────────────────────────────────────────
    val FireDouser = EnvironmentalJob(
        name = "fire_douser",
        targetCategory = BlockCategory.FIRE,
        qualifyingMoves = setOf("waterpulse", "raindance"),
        priority = WorkerPriority.TYPE,
        particle = ParticleTypes.SMOKE,
        defaultRadius = 2,
        findTarget = { world, origin ->
            CobbleCrewCacheManager.getTargets(origin, BlockCategory.FIRE)
                .filter { !CobbleCrewNavigationUtils.isRecentlyExpired(it, world) }
                .minByOrNull { it.getSquaredDistance(origin) }
        },
        action = { world, pos ->
            val r = JobConfigManager.get("fire_douser").radius?.takeIf { it > 0 } ?: 2
            BlockPos.iterate(pos.add(-r, 0, -r), pos.add(r, 0, r)).forEach { p ->
                val block = world.getBlockState(p).block
                if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE)
                    world.setBlockState(p, Blocks.AIR.defaultState)
            }
        },
    )

    // ── G10: Crop Irrigator ──────────────────────────────────────────
    val CropIrrigatorWorker = EnvironmentalJob(
        name = "crop_irrigator",
        targetCategory = BlockCategory.FARMLAND,
        qualifyingMoves = setOf("muddywater"),
        priority = WorkerPriority.TYPE,
        particle = ParticleTypes.SPLASH,
        defaultRadius = 2,
        findTarget = { world, origin ->
            CobbleCrewCacheManager.getTargets(origin, BlockCategory.FARMLAND)
                .filter { pos ->
                    world.getBlockState(pos).block == Blocks.FARMLAND
                        && world.getBlockState(pos).get(FarmlandBlock.MOISTURE) <= 2
                        && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        },
        action = { world, pos ->
            val r = JobConfigManager.get("crop_irrigator").radius?.takeIf { it > 0 } ?: 2
            BlockPos.iterate(pos.add(-r, 0, -r), pos.add(r, 0, r)).forEach { p ->
                val s = world.getBlockState(p)
                if (s.block == Blocks.FARMLAND)
                    world.setBlockState(p, s.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.NOTIFY_LISTENERS)
            }
        },
        validate = { world, pos ->
            world.getBlockState(pos).block == Blocks.FARMLAND
                && world.getBlockState(pos).get(FarmlandBlock.MOISTURE) <= 2
        },
    )

    // ── G11: Bee Pollinator ──────────────────────────────────────────
    val BeePollinator = EnvironmentalJob(
        name = "bee_pollinator",
        targetCategory = BlockCategory.HONEY,
        qualifyingMoves = setOf("pollenpuff"),
        fallbackSpecies = listOf("Combee", "Vespiquen"),
        priority = WorkerPriority.SPECIES,
        particle = ParticleTypes.WAX_ON,
        defaultCooldownSeconds = 120,
        findTarget = { world, origin ->
            CobbleCrewCacheManager.getTargets(origin, BlockCategory.HONEY)
                .filter { pos ->
                    val s = world.getBlockState(pos)
                    s.block is BeehiveBlock
                        && s.get(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.FULL_HONEY_LEVEL
                        && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
                }
                .minByOrNull { it.getSquaredDistance(origin) }
        },
        action = { world, pos ->
            val state = world.getBlockState(pos)
            if (state.block is BeehiveBlock) {
                val level = state.get(BeehiveBlock.HONEY_LEVEL)
                if (level < BeehiveBlock.FULL_HONEY_LEVEL)
                    world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, level + 1), Block.NOTIFY_ALL)
            }
        },
        validate = { world, pos ->
            val s = world.getBlockState(pos)
            s.block is BeehiveBlock && s.get(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.FULL_HONEY_LEVEL
        },
    )

    fun register() {
        WorkerRegistry.registerAll(
            FrostFormer, ObsidianForge, GrowthAccelerator,
            LavaCauldronFiller, WaterCauldronFiller, SnowCauldronFiller,
            FurnaceFueler, BrewingStandFueler,
            FireDouser, CropIrrigatorWorker, BeePollinator,
        )
    }
}
