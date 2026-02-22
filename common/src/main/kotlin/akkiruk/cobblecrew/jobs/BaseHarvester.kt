/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
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
    open val arrivalTolerance: Double = 3.0

    /** Tool used in loot context. Override for silk touch/fortune. */
    open val harvestTool: ItemStack = ItemStack.EMPTY

    abstract fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity)

    /** Additional readiness check beyond blockValidator (e.g. crop maturity). */
    open fun isTargetReady(world: World, pos: BlockPos): Boolean = true

    override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
        return findClosestTarget(world, origin) != null
    }

    companion object {
        private const val OVERFLOW_TIMEOUT_TICKS = 6000L // 5 minutes — last resort safety valve
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

            CobbleCrewInventoryUtils.handleDepositing(
                world, origin, pokemonEntity, heldItems,
                failedDepositLocations, heldItemsByPokemon
            )
        }
    }

    protected open fun handleHarvesting(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val currentTarget = CobbleCrewNavigationUtils.getTarget(pokemonId, world)

        if (currentTarget == null) {
            val closestTarget = findClosestTarget(world, origin) ?: return
            if (!CobbleCrewNavigationUtils.isTargeted(closestTarget, world)
                && !CobbleCrewNavigationUtils.isRecentlyExpired(closestTarget, world)) {
                CobbleCrewNavigationUtils.claimTarget(pokemonId, closestTarget, world)
                CobbleCrewNavigationUtils.navigateTo(pokemonEntity, closestTarget)
            }
            return
        }

        // Validate target is still a viable block (not air, still ready)
        if (world.getBlockState(currentTarget).isAir || !isTargetReady(world, currentTarget)) {
            CobbleCrewNavigationUtils.releaseTarget(pokemonId, world)
            return
        }

        CobbleCrewNavigationUtils.navigateTo(pokemonEntity, currentTarget)

        if (WorkerVisualUtils.handleArrival(pokemonEntity, currentTarget, world, arrivalParticle, arrivalTolerance)) {
            harvest(world, currentTarget, pokemonEntity)
            CobbleCrewNavigationUtils.releaseTarget(pokemonId, world)
        }
    }

    open fun findClosestTarget(world: World, origin: BlockPos): BlockPos? {
        val cat = targetCategory ?: return null
        val targets = CobbleCrewCacheManager.getTargets(origin, cat)
        if (targets.isEmpty()) return null
        return targets
            .filter { pos ->
                isTargetReady(world, pos)
                    && !CobbleCrewNavigationUtils.isRecentlyExpired(pos, world)
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
