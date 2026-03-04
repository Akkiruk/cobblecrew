/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.jobs.dsl.dslEligible
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.WorkSpeedBoostManager
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTables
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Production jobs. Loot-table-based custom workers (fishing, pickup, dive, dig site).
 */
object ProductionJobs {

    // ── Shared base for cooldown/loot-table production ───────────────

    abstract class LootProducer(
        final override val name: String,
        val defaultCd: Int,
    ) : BaseJob() {
        override val targetCategory: BlockCategory? = null
        override val requiresTarget: Boolean = false
        override val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER
        override val workPhase: WorkPhase = WorkPhase.PRODUCING

        abstract fun canWork(context: JobContext, entity: PokemonEntity): Boolean
        abstract fun generateLoot(world: World, origin: BlockPos, entity: PokemonEntity): List<ItemStack>

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? = null

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            if (!canWork(context, pokemonEntity)) return WorkResult.Done()
            WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.PRODUCING, context.world)
            val drops = generateLoot(context.world, context.origin, pokemonEntity)
            if (drops.isEmpty()) return WorkResult.Done()
            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: defaultCd) * 20L
            state.lastActionTime = WorkSpeedBoostManager.adjustCooldown(baseCd, context.origin, context.world.time)
            return WorkResult.Done(drops)
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long {
            val cd = state.lastActionTime
            if (cd > 0) { state.lastActionTime = 0L; return cd }
            return 0L
        }
    }

    // ── Loot-based Workers ───────────────────────────────────────────

    object FishingLooter : LootProducer("fishing_looter", 120) {
        override val priority = WorkerPriority.MOVE
        private val qualifyingMoves = setOf("dive")

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                treasureChance = 10,
                requiresWater = true,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun canWork(context: JobContext, entity: PokemonEntity) =
            config.requiresWater != true || entity.isTouchingWater

        override fun generateLoot(world: World, origin: BlockPos, entity: PokemonEntity): List<ItemStack> {
            val chance = (config.treasureChance?.takeIf { it > 0 } ?: 10).toDouble() / 100
            val useTreasure = world.random.nextFloat() < chance
            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.TOOL, ItemStack(Items.FISHING_ROD))
                .addOptional(LootContextParameters.THIS_ENTITY, entity)
                .build(LootContextTypes.FISHING)
            val table = if (useTreasure)
                world.server.reloadableRegistries.getLootTable(LootTables.FISHING_TREASURE_GAMEPLAY)
            else
                world.server.reloadableRegistries.getLootTable(LootTables.FISHING_GAMEPLAY)
            return table.generateLoot(lootParams)
        }
    }

    object PickupLooter : LootProducer("pickup_looter", 120) {
        override val priority = WorkerPriority.MOVE

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                requiredAbility = "pickup",
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String): Boolean {
            if (!config.enabled) return false
            val req = config.requiredAbility?.lowercase() ?: "pickup"
            return ability.lowercase() == req
        }

        override fun canWork(context: JobContext, entity: PokemonEntity) = true

        override fun generateLoot(world: World, origin: BlockPos, entity: PokemonEntity): List<ItemStack> {
            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return emptyList()
            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, entity)
                .build(LootContextTypes.CHEST)
            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            return world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
        }
    }

    object DiveCollector : LootProducer("dive_collector", 210) {
        override val priority = WorkerPriority.MOVE
        private val qualifyingMoves = setOf("waterfall")

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 210,
                qualifyingMoves = qualifyingMoves.toList(),
                requiresWater = true,
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun canWork(context: JobContext, entity: PokemonEntity) =
            config.requiresWater != true || entity.isTouchingWater

        override fun generateLoot(world: World, origin: BlockPos, entity: PokemonEntity): List<ItemStack> {
            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return emptyList()
            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, origin.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, entity)
                .build(LootContextTypes.CHEST)
            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            return world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
        }
    }

    // ── Target-based excavation ──────────────────────────────────────

    object DigSiteExcavator : BaseJob() {
        override val name = "dig_site_excavator"
        override val priority = WorkerPriority.TYPE
        override val targetCategory = BlockCategory.SUSPICIOUS
        override val arrivalParticle: ParticleEffect = ParticleTypes.COMPOSTER
        override val workPhase: WorkPhase = WorkPhase.HARVESTING
        override val config get() = JobConfigManager.get(name)

        private val qualifyingMoves = setOf("dig")

        init {
            JobConfigManager.registerDefault("production", name, JobConfig(
                enabled = true,
                cooldownSeconds = 120,
                qualifyingMoves = qualifyingMoves.toList(),
                lootTables = listOf("cobblemon:gameplay/pickup"),
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? =
            findCachedBlockTarget(state, context, BlockCategory.SUSPICIOUS) { world, pos ->
                world.getBlockState(pos.up()).isAir
            }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()
            val world = context.world

            val tables = (config.lootTables ?: emptyList()).ifEmpty { listOf("cobblemon:gameplay/pickup") }
                .mapNotNull { Identifier.tryParse(it) }
            if (tables.isEmpty()) return WorkResult.Done()

            val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
                .add(LootContextParameters.ORIGIN, pos.toCenterPos())
                .add(LootContextParameters.THIS_ENTITY, pokemonEntity)
                .build(LootContextTypes.CHEST)

            val key = RegistryKey.of(RegistryKeys.LOOT_TABLE, tables.random())
            val drops = world.server.reloadableRegistries.getLootTable(key).generateLoot(lootParams)
            if (drops.isEmpty()) return WorkResult.Done()

            val baseCd = (config.cooldownSeconds.takeIf { it > 0 } ?: 120) * 20L
            state.lastActionTime = WorkSpeedBoostManager.adjustCooldown(baseCd, context.origin, world.time)
            return WorkResult.Done(drops)
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long {
            val cd = state.lastActionTime
            if (cd > 0) { state.lastActionTime = 0L; return cd }
            return 0L
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            FishingLooter,
            PickupLooter,
            DiveCollector,
            DigSiteExcavator,
        )
    }
}
