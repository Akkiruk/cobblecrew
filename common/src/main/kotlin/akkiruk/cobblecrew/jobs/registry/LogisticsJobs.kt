/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs.registry

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.ArrivalStyle
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobImportance
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.enums.WorkerPriority
import akkiruk.cobblecrew.jobs.BaseJob
import akkiruk.cobblecrew.jobs.JobContext
import akkiruk.cobblecrew.jobs.Target
import akkiruk.cobblecrew.jobs.WorkResult
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.jobs.dsl.dslEligible
import akkiruk.cobblecrew.jobs.dsl.dslMatchPriority
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.WorkerAnimationUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID

/**
 * Logistics jobs — the conveyor belt of your Pokémon factory.
 *
 * - **Barrel Stocker**: Moves items from chests → barrels (feeds processing jobs)
 * - **Item Consolidator**: Merges partial stacks across containers
 * - **Ground Sweeper**: Picks up dropped items in range, deposits in chests
 * - **Chest Unloader**: Empties full chests into barrels for processing
 */
object LogisticsJobs {

    // ── Barrel Stocker ───────────────────────────────────────────────
    // Finds a chest with items, moves items to a barrel that has space.
    // This is the KEY job that feeds the factory — chests receive from
    // gatherers/producers, this worker stocks the barrels that
    // processing/assembly jobs read from.
    //
    // Two-phase workflow: chest (extract) → barrel (insert)
    object BarrelStocker : BaseJob() {
        override val name = "barrel_stocker"
        override val category = "logistics"
        override val targetCategory: BlockCategory? = null
        override val arrivalParticle = ParticleTypes.HAPPY_VILLAGER
        override val workPhase = WorkPhase.PROCESSING
        override val arrivalStyle = ArrivalStyle.QUICK
        override val priority = WorkerPriority.MOVE
        override val importance = JobImportance.HIGH

        private val qualifyingMoves = setOf("strength")
        private val fallbackSpecies = listOf("Machamp", "Machoke", "Hariyama", "Conkeldurr", "Gurdurr", "Timburr")

        override fun buildDefaultConfig() = JobConfig(
            enabled = true,
            cooldownSeconds = 15,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackSpecies = fallbackSpecies,
            partyEnabled = false,
        )

        init { registerConfig() }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
            if (!config.enabled) return false
            val containers = CobbleCrewCacheManager.getTargets(context.origin, BlockCategory.CONTAINER)
            val hasChestWithItems = containers.any { pos ->
                val block = context.world.getBlockState(pos).block
                block != Blocks.BARREL && (context.world.getBlockEntity(pos) as? Inventory)?.let { inv ->
                    (0 until inv.size()).any { !inv.getStack(it).isEmpty }
                } == true
            }
            val hasBarrelWithSpace = containers.any { pos ->
                context.world.getBlockState(pos).block == Blocks.BARREL
                    && (context.world.getBlockEntity(pos) as? Inventory)?.let { inv ->
                    (0 until inv.size()).any { inv.getStack(it).isEmpty || inv.getStack(it).count < inv.getStack(it).maxCount }
                } == true
            }
            return hasChestWithItems && hasBarrelWithSpace
        }

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val containers = CobbleCrewCacheManager.getTargets(context.origin, BlockCategory.CONTAINER)
            // Find a chest (non-barrel) with items
            val sourceChest = containers
                .filter { pos ->
                    val block = context.world.getBlockState(pos).block
                    block != Blocks.BARREL
                        && !ClaimManager.isTargetedByOther(pos, state.pokemonId)
                        && (context.world.getBlockEntity(pos) as? Inventory)?.let { inv ->
                        (0 until inv.size()).any { !inv.getStack(it).isEmpty }
                    } == true
                }
                .randomOrNull() ?: return null

            // Find a barrel with space
            val targetBarrel = containers
                .filter { pos ->
                    context.world.getBlockState(pos).block == Blocks.BARREL
                        && (context.world.getBlockEntity(pos) as? Inventory)?.let { inv ->
                        (0 until inv.size()).any { inv.getStack(it).isEmpty || inv.getStack(it).count < inv.getStack(it).maxCount }
                    } == true
                }
                .randomOrNull() ?: return null

            state.secondaryTargetPos = targetBarrel
            return Target.Block(sourceChest)
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()

            // Phase 2: at barrel — deposit held items
            if (state.heldItems.isNotEmpty() && state.secondaryTargetPos != null) {
                val barrelPos = pos
                val inv = context.world.getBlockEntity(barrelPos) as? Inventory
                if (inv != null) {
                    val remaining = CobbleCrewInventoryUtils.insertStacks(inv, state.heldItems.toList())
                    state.heldItems.clear()
                    state.heldItems.addAll(remaining)
                }
                state.secondaryTargetPos = null
                // Any remaining items go through normal deposit
                return WorkResult.Done(state.heldItems.toList().also { state.heldItems.clear() })
            }

            // Phase 1: at chest — extract items
            val inv = context.world.getBlockEntity(pos) as? Inventory ?: return WorkResult.Done()
            val extracted = mutableListOf<ItemStack>()
            for (slot in 0 until inv.size()) {
                val stack = inv.getStack(slot)
                if (!stack.isEmpty) {
                    extracted.add(stack.split(minOf(stack.count, 16)))
                    inv.markDirty()
                    break
                }
            }

            if (extracted.isEmpty()) return WorkResult.Done()

            val barrelPos = state.secondaryTargetPos
            if (barrelPos == null) {
                // No barrel target — just deposit normally
                return WorkResult.Done(extracted)
            }

            WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.PROCESSING, context.world)
            state.heldItems.addAll(extracted)
            state.secondaryTargetPos = barrelPos
            return WorkResult.MoveTo(barrelPos)
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long =
            (config.cooldownSeconds.takeIf { it > 0 } ?: 15) * 20L
    }

    // ── Ground Sweeper ───────────────────────────────────────────────
    // Picks up dropped items on the ground near the pasture and deposits them.
    object GroundSweeper : BaseJob() {
        override val name = "ground_sweeper"
        override val category = "logistics"
        override val targetCategory: BlockCategory? = null
        override val arrivalParticle = ParticleTypes.HAPPY_VILLAGER
        override val arrivalTolerance = 2.0
        override val workPhase = WorkPhase.HARVESTING
        override val arrivalStyle = ArrivalStyle.QUICK
        override val priority = WorkerPriority.MOVE
        override val importance = JobImportance.STANDARD

        private val qualifyingMoves = setOf("pickup")
        private val fallbackSpecies = listOf("Munchlax", "Snorlax", "Aipom", "Ambipom", "Zigzagoon", "Linoone", "Pachirisu")

        override fun buildDefaultConfig() = JobConfig(
            enabled = true,
            cooldownSeconds = 10,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackSpecies = fallbackSpecies,
            partyEnabled = true,
        )

        init { registerConfig() }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
            if (!config.enabled) return false
            return findNearbyItems(context).isNotEmpty()
        }

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val item = findNearbyItems(context)
                .minByOrNull { it.blockPos.getSquaredDistance(context.origin) }
                ?: return null
            return Target.Block(item.blockPos)
        }

        override fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
            val pos = state.targetPos ?: return false
            return findNearbyItems(context).any { it.blockPos.getSquaredDistance(pos) < 4.0 }
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()
            val nearbyItems = context.world.getEntitiesByClass(
                ItemEntity::class.java,
                Box(pos).expand(2.0)
            ) { !it.isRemoved }

            val collected = mutableListOf<ItemStack>()
            for (item in nearbyItems.take(5)) {
                collected.add(item.stack.copy())
                item.discard()
            }

            return WorkResult.Done(collected)
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long =
            (config.cooldownSeconds.takeIf { it > 0 } ?: 10) * 20L

        private fun findNearbyItems(context: JobContext): List<ItemEntity> {
            val r = 8.0
            val box = Box(context.origin).expand(r, 4.0, r)
            return context.world.getEntitiesByClass(ItemEntity::class.java, box) { entity ->
                !entity.isRemoved && entity.age > 40 // Ignore freshly dropped items
            }
        }
    }

    // ── Item Consolidator ────────────────────────────────────────────
    // Finds containers with partial stacks and merges them to save space.
    // Crucial for keeping factory output chests organized.
    object ItemConsolidator : BaseJob() {
        override val name = "item_consolidator"
        override val category = "logistics"
        override val targetCategory: BlockCategory? = null
        override val arrivalParticle = ParticleTypes.ENCHANT
        override val workPhase = WorkPhase.PROCESSING
        override val arrivalStyle = ArrivalStyle.QUICK
        override val priority = WorkerPriority.MOVE
        override val importance = JobImportance.LOW
        override val producesItems = false

        private val qualifyingMoves = setOf("recycle")
        private val fallbackSpecies = listOf("Porygon", "Porygon2", "PorygonZ", "Metagross", "Magnezone")

        override fun buildDefaultConfig() = JobConfig(
            enabled = true,
            cooldownSeconds = 30,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackSpecies = fallbackSpecies,
        )

        init { registerConfig() }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
            if (!config.enabled) return false
            return findFragmentedContainer(context) != null
        }

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val pos = findFragmentedContainer(context) ?: return null
            return Target.Block(pos)
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()
            val inv = context.world.getBlockEntity(pos) as? Inventory ?: return WorkResult.Done()
            val actual = CobbleCrewInventoryUtils.getActualInventory(inv)
            consolidateInventory(actual)
            WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.PROCESSING, context.world)
            return WorkResult.Done()
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long =
            (config.cooldownSeconds.takeIf { it > 0 } ?: 30) * 20L

        private fun findFragmentedContainer(context: JobContext): BlockPos? {
            val containers = CobbleCrewCacheManager.getTargets(context.origin, BlockCategory.CONTAINER)
            return containers.firstOrNull { pos ->
                val inv = context.world.getBlockEntity(pos) as? Inventory ?: return@firstOrNull false
                val actual = CobbleCrewInventoryUtils.getActualInventory(inv)
                hasFragmentedStacks(actual)
            }
        }

        private fun hasFragmentedStacks(inv: Inventory): Boolean {
            val seen = mutableMapOf<String, Int>()
            for (i in 0 until inv.size()) {
                val stack = inv.getStack(i)
                if (stack.isEmpty) continue
                val key = stack.item.toString()
                val count = seen.getOrDefault(key, 0) + 1
                if (count > 1 && stack.count < stack.maxCount) return true
                seen[key] = count
            }
            return false
        }

        private fun consolidateInventory(inv: Inventory) {
            for (i in 0 until inv.size()) {
                val source = inv.getStack(i)
                if (source.isEmpty || source.count >= source.maxCount) continue
                for (j in i + 1 until inv.size()) {
                    val target = inv.getStack(j)
                    if (target.isEmpty) continue
                    if (!ItemStack.areItemsAndComponentsEqual(source, target)) continue
                    val space = source.maxCount - source.count
                    val transfer = minOf(space, target.count)
                    source.increment(transfer)
                    target.decrement(transfer)
                    if (target.isEmpty) inv.setStack(j, ItemStack.EMPTY)
                    if (source.count >= source.maxCount) break
                }
            }
            inv.markDirty()
        }
    }

    // ── Chest Unloader ───────────────────────────────────────────────
    // Moves items from FULL chests → barrels. Partner to Barrel Stocker but
    // only activates when chests are getting full (factory overflow prevention).
    object ChestUnloader : BaseJob() {
        override val name = "chest_unloader"
        override val category = "logistics"
        override val targetCategory: BlockCategory? = null
        override val arrivalParticle = ParticleTypes.SMOKE
        override val workPhase = WorkPhase.PROCESSING
        override val arrivalStyle = ArrivalStyle.QUICK
        override val priority = WorkerPriority.MOVE
        override val importance = JobImportance.HIGH

        private val qualifyingMoves = setOf("knockoff")
        private val fallbackSpecies = listOf("Primeape", "Annihilape", "Passimian", "Mankey")

        override fun buildDefaultConfig() = JobConfig(
            enabled = true,
            cooldownSeconds = 20,
            qualifyingMoves = qualifyingMoves.toList(),
            fallbackSpecies = fallbackSpecies,
        )

        init { registerConfig() }

        override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslEligible(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun matchPriority(moves: Set<String>, types: Set<String>, species: String, ability: String) =
            dslMatchPriority(config, qualifyingMoves, fallbackSpecies, moves, species)

        override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
            if (!config.enabled) return false
            return findFullChest(context) != null
        }

        override fun findTarget(state: PokemonWorkerState, context: JobContext): Target? {
            val chestPos = findFullChest(context) ?: return null
            val barrelPos = findEmptyishBarrel(context) ?: return null
            state.secondaryTargetPos = barrelPos
            return Target.Block(chestPos)
        }

        override fun doWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity): WorkResult {
            val pos = state.targetPos ?: return WorkResult.Done()

            // Phase 2: deposit into barrel
            if (state.heldItems.isNotEmpty() && state.secondaryTargetPos != null) {
                val barrelPos = pos
                val inv = context.world.getBlockEntity(barrelPos) as? Inventory
                if (inv != null) {
                    val remaining = CobbleCrewInventoryUtils.insertStacks(inv, state.heldItems.toList())
                    state.heldItems.clear()
                    state.heldItems.addAll(remaining)
                }
                state.secondaryTargetPos = null
                return WorkResult.Done(state.heldItems.toList().also { state.heldItems.clear() })
            }

            // Phase 1: extract from full chest
            val inv = context.world.getBlockEntity(pos) as? Inventory ?: return WorkResult.Done()
            val extracted = mutableListOf<ItemStack>()
            for (slot in 0 until inv.size()) {
                val stack = inv.getStack(slot)
                if (!stack.isEmpty) {
                    extracted.add(stack.split(minOf(stack.count, 32)))
                    inv.markDirty()
                    break
                }
            }

            if (extracted.isEmpty()) return WorkResult.Done()

            val barrelPos = state.secondaryTargetPos ?: return WorkResult.Done(extracted)
            WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.PROCESSING, context.world)
            state.heldItems.addAll(extracted)
            return WorkResult.MoveTo(barrelPos)
        }

        override fun getCooldownTicks(state: PokemonWorkerState): Long =
            (config.cooldownSeconds.takeIf { it > 0 } ?: 20) * 20L

        /** Finds a non-barrel container that's at least 75% full. */
        private fun findFullChest(context: JobContext): BlockPos? {
            val containers = CobbleCrewCacheManager.getTargets(context.origin, BlockCategory.CONTAINER)
            return containers.firstOrNull { pos ->
                val block = context.world.getBlockState(pos).block
                if (block == Blocks.BARREL) return@firstOrNull false
                val inv = context.world.getBlockEntity(pos) as? Inventory ?: return@firstOrNull false
                val actual = CobbleCrewInventoryUtils.getActualInventory(inv)
                val usedSlots = (0 until actual.size()).count { !actual.getStack(it).isEmpty }
                usedSlots.toFloat() / actual.size() >= 0.75f
            }
        }

        /** Finds a barrel with some empty space. */
        private fun findEmptyishBarrel(context: JobContext): BlockPos? {
            val containers = CobbleCrewCacheManager.getTargets(context.origin, BlockCategory.CONTAINER)
            return containers.firstOrNull { pos ->
                context.world.getBlockState(pos).block == Blocks.BARREL
                    && (context.world.getBlockEntity(pos) as? Inventory)?.let { inv ->
                    (0 until inv.size()).any { inv.getStack(it).isEmpty }
                } == true
            }
        }
    }

    fun register() {
        WorkerRegistry.registerAll(
            BarrelStocker,
            GroundSweeper,
            ItemConsolidator,
            ChestUnloader,
        )
    }
}
