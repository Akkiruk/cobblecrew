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
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * Logistics jobs (H1–H2). Container-internal operations.
 */
object LogisticsJobs {

    // ── H1: Magnetizer ───────────────────────────────────────────────
    // Consolidates 9 nuggets → 1 ingot within containers
    object Magnetizer : BaseJob() {
        override val name = "magnetizer"
        override val priority = WorkerPriority.MOVE
        override val importance = JobImportance.BACKGROUND
        override val targetCategory: BlockCategory? = null
        override val requiresTarget = true
        override val producesItems = false
        override val arrivalParticle: ParticleEffect = ParticleTypes.ELECTRIC_SPARK
        override val workPhase: WorkPhase = WorkPhase.PROCESSING
        override val config get() = JobConfigManager.get(name)

        private val qualifyingMoves = setOf("magnetrise")
        private val fallbackSpecies = listOf("Magnemite", "Magneton", "Magnezone")

        private val NUGGET_TO_INGOT = mapOf(
            Items.IRON_NUGGET to Items.IRON_INGOT,
            Items.GOLD_NUGGET to Items.GOLD_INGOT,
        )

        init {
            JobConfigManager.registerDefault("logistics", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                fallbackSpecies = fallbackSpecies,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val found = CobbleCrewInventoryUtils.findInputContainer(
                context.world, context.origin, predicate = { stack ->
                    NUGGET_TO_INGOT.containsKey(stack.item) && stack.count >= 9
                }
            ) ?: return null
            if (ClaimManager.isTargetedByOther(found, state.pokemonId)) return null
            return Target.Block(found)
        }

        override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
            val pos = state.targetPos ?: return false
            val inv = context.world.getBlockEntity(pos) as? Inventory ?: return false
            return NUGGET_TO_INGOT.keys.any { nugget ->
                (0 until inv.size()).any { inv.getStack(it).item == nugget && inv.getStack(it).count >= 9 }
            }
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()
            consolidateNuggets(context.world, pos)
            return WorkResult.Done()
        }

        private fun consolidateNuggets(world: World, pos: BlockPos) {
            val inv = world.getBlockEntity(pos) as? Inventory ?: return
            for ((nugget, ingot) in NUGGET_TO_INGOT) {
                var totalNuggets = 0
                val slots = mutableListOf<Int>()
                for (i in 0 until inv.size()) {
                    val stack = inv.getStack(i)
                    if (stack.item == nugget) {
                        totalNuggets += stack.count
                        slots.add(i)
                    }
                }
                val ingots = totalNuggets / 9
                val remainder = totalNuggets % 9
                if (ingots > 0) {
                    slots.forEach { inv.setStack(it, ItemStack.EMPTY) }
                    if (remainder > 0) CobbleCrewInventoryUtils.insertStack(inv, ItemStack(nugget, remainder))
                    CobbleCrewInventoryUtils.insertStack(inv, ItemStack(ingot, ingots))
                    inv.markDirty()
                }
            }
        }
    }

    // ── H2: Ground Item Collector ────────────────────────────────────
    // Picks up items from the ground and deposits in containers
    object GroundItemCollector : BaseJob() {
        override val name = "ground_item_collector"
        override val priority = WorkerPriority.TYPE
        override val importance = JobImportance.HIGH
        override val targetCategory: BlockCategory? = null
        override val requiresTarget = true
        override val arrivalParticle: ParticleEffect = ParticleTypes.ENCHANT
        override val workPhase: WorkPhase = WorkPhase.HARVESTING
        override val config get() = JobConfigManager.get(name)

        private val qualifyingMoves = setOf("telekinesis")

        init {
            JobConfigManager.registerDefault("logistics", name, JobConfig(
                enabled = true,
                qualifyingMoves = qualifyingMoves.toList(),
                radius = 8,
            ))
        }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, emptyList(), moves, species)

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val r = config.radius?.takeIf { it > 0 } ?: 8
            val searchArea = Box(context.origin).expand(r.toDouble(), r.toDouble(), r.toDouble())
            val item = context.world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .firstOrNull { it.isOnGround } ?: return null
            val pos = item.blockPos
            if (ClaimManager.isTargetedByOther(pos, state.pokemonId)) return null
            return Target.Block(pos)
        }

        override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
            val pos = state.targetPos ?: return false
            val searchArea = Box(pos).expand(2.0)
            return context.world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .any { it.isOnGround }
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()
            val searchArea = Box(pos).expand(2.0)
            val item = context.world.getEntitiesByClass(ItemEntity::class.java, searchArea) { true }
                .firstOrNull { it.isOnGround } ?: return WorkResult.Done()
            val stack = item.stack.copy()
            item.discard()
            return WorkResult.Done(listOf(stack))
        }
    }

    fun register() {
        WorkerRegistry.registerAll(Magnetizer, GroundItemCollector)
    }
}
