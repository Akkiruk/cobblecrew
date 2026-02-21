/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.cache.CobbleworkersCacheManager
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.utilities.CobbleworkersInventoryUtils
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Base class for all harvester-style workers that find blocks, navigate to them,
 * harvest items, and deposit them into nearby containers.
 *
 * Subclasses only need to define block-specific logic (validation, readiness,
 * harvest behavior) — all navigation, depositing, and lifecycle boilerplate
 * is handled here.
 */
abstract class BaseHarvester : Worker {
    protected val heldItemsByPokemon = mutableMapOf<UUID, List<ItemStack>>()
    protected val failedDepositLocations = mutableMapOf<UUID, MutableSet<BlockPos>>()
    private val heldItemsSinceTick = mutableMapOf<UUID, Long>()

    abstract val arrivalParticle: ParticleEffect
    open val arrivalTolerance: Double = 1.0

    /** Tool used in loot context. Override for silk touch/fortune. */
    open val harvestTool: ItemStack = ItemStack.EMPTY

    abstract fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity)

    /** Additional readiness check beyond blockValidator (e.g. crop maturity). */
    open fun isTargetReady(world: World, pos: BlockPos): Boolean = true

    companion object {
        private const val OVERFLOW_TIMEOUT_TICKS = 600L // 30 seconds
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val heldItems = heldItemsByPokemon[pokemonId]

        if (heldItems.isNullOrEmpty()) {
            failedDepositLocations.remove(pokemonId)
            heldItemsSinceTick.remove(pokemonId)
            handleHarvesting(world, origin, pokemonEntity)
        } else {
            // Overflow protection: drop items if stuck for too long
            val heldSince = heldItemsSinceTick.getOrPut(pokemonId) { world.time }
            if (world.time - heldSince >= OVERFLOW_TIMEOUT_TICKS) {
                heldItems.forEach { stack -> Block.dropStack(world, pokemonEntity.blockPos, stack) }
                heldItemsByPokemon.remove(pokemonId)
                failedDepositLocations.remove(pokemonId)
                heldItemsSinceTick.remove(pokemonId)
                return
            }

            CobbleworkersInventoryUtils.handleDepositing(
                world, origin, pokemonEntity, heldItems,
                failedDepositLocations, heldItemsByPokemon
            )
        }
    }

    protected open fun handleHarvesting(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val closestTarget = findClosestTarget(world, origin) ?: return
        val currentTarget = CobbleworkersNavigationUtils.getTarget(pokemonId, world)

        if (currentTarget == null) {
            if (!CobbleworkersNavigationUtils.isTargeted(closestTarget, world)
                && !CobbleworkersNavigationUtils.isRecentlyExpired(closestTarget, world)) {
                CobbleworkersNavigationUtils.claimTarget(pokemonId, closestTarget, world)
            }
            return
        }

        if (currentTarget == closestTarget) {
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, closestTarget)
        }

        if (WorkerVisualUtils.handleArrival(pokemonEntity, currentTarget, world, arrivalParticle, arrivalTolerance)) {
            harvest(world, closestTarget, pokemonEntity)
            CobbleworkersNavigationUtils.releaseTarget(pokemonId, world)
        }
    }

    open fun findClosestTarget(world: World, origin: BlockPos): BlockPos? {
        val cat = targetCategory ?: return null
        val targets = CobbleworkersCacheManager.getTargets(origin, cat)
        if (targets.isEmpty()) return null
        return targets
            .filter { pos ->
                isTargetReady(world, pos)
                    && !CobbleworkersNavigationUtils.isRecentlyExpired(pos, world)
            }
            .minByOrNull { it.getSquaredDistance(origin) }
    }

    /**
     * Extracts drops via loot context, stores them in heldItemsByPokemon,
     * then calls [afterHarvest] with the original block state for cleanup.
     */
    protected fun harvestWithLoot(
        world: World,
        targetPos: BlockPos,
        pokemonEntity: PokemonEntity,
        afterHarvest: (World, BlockPos, BlockState) -> Unit
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val state = world.getBlockState(targetPos)

        val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
            .add(LootContextParameters.ORIGIN, targetPos.toCenterPos())
            .add(LootContextParameters.BLOCK_STATE, state)
            .add(LootContextParameters.TOOL, harvestTool)
            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)

        val drops = state.getDroppedStacks(lootParams)
        if (drops.isNotEmpty()) {
            heldItemsByPokemon[pokemonId] = drops
        }

        afterHarvest(world, targetPos, state)
    }

    override fun hasActiveState(pokemonId: UUID): Boolean = pokemonId in heldItemsByPokemon

    override fun cleanup(pokemonId: UUID) {
        heldItemsByPokemon.remove(pokemonId)
        failedDepositLocations.remove(pokemonId)
        heldItemsSinceTick.remove(pokemonId)
    }
}
