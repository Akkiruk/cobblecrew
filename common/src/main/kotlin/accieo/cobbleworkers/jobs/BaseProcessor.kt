/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.utilities.CobbleworkersInventoryUtils
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Base class for processing workers that pull items from a barrel (input),
 * transform them, and deposit results in a chest (output).
 *
 * Pattern: find barrel with input → navigate → extract → transform → hold → deposit.
 */
abstract class BaseProcessor : Worker {
    private enum class Phase { IDLE, NAVIGATING_INPUT, DEPOSITING }

    private val phases = mutableMapOf<UUID, Phase>()
    private val inputTargets = mutableMapOf<UUID, BlockPos>()
    private val heldItemsByPokemon = mutableMapOf<UUID, List<ItemStack>>()
    private val failedDepositLocations = mutableMapOf<UUID, MutableSet<BlockPos>>()

    open val processParticle: ParticleEffect = ParticleTypes.SMOKE

    /** Predicate for items this processor accepts from barrels. */
    abstract fun inputPredicate(stack: ItemStack): Boolean

    /** Transform extracted items into output items. */
    abstract fun transform(input: ItemStack): List<ItemStack>

    override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
        return CobbleworkersInventoryUtils.findInputContainer(
            world, origin, ::inputPredicate
        ) != null
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        when (phases.getOrDefault(pokemonId, Phase.IDLE)) {
            Phase.IDLE -> {
                val barrel = CobbleworkersInventoryUtils.findInputContainer(
                    world, origin, ::inputPredicate
                ) ?: return
                inputTargets[pokemonId] = barrel
                phases[pokemonId] = Phase.NAVIGATING_INPUT
            }
            Phase.NAVIGATING_INPUT -> {
                val target = inputTargets[pokemonId] ?: run {
                    phases[pokemonId] = Phase.IDLE
                    return
                }
                CobbleworkersNavigationUtils.navigateTo(pokemonEntity, target)
                if (CobbleworkersNavigationUtils.isPokemonAtPosition(pokemonEntity, target, 2.0)) {
                    val taken = CobbleworkersInventoryUtils.extractFromContainer(
                        world, target, ::inputPredicate
                    )
                    if (taken.isEmpty) {
                        phases[pokemonId] = Phase.IDLE
                        return
                    }
                    WorkerVisualUtils.spawnParticles(world, pokemonEntity.blockPos, processParticle, 5)
                    heldItemsByPokemon[pokemonId] = transform(taken)
                    phases[pokemonId] = Phase.DEPOSITING
                }
            }
            Phase.DEPOSITING -> {
                val heldItems = heldItemsByPokemon[pokemonId]
                if (heldItems.isNullOrEmpty()) {
                    heldItemsByPokemon.remove(pokemonId)
                    failedDepositLocations.remove(pokemonId)
                    phases[pokemonId] = Phase.IDLE
                    return
                }
                CobbleworkersInventoryUtils.handleDepositing(
                    world, origin, pokemonEntity, heldItems,
                    failedDepositLocations, heldItemsByPokemon
                )
                if (pokemonId !in heldItemsByPokemon) {
                    phases[pokemonId] = Phase.IDLE
                }
            }
        }
    }

    override fun hasActiveState(pokemonId: UUID): Boolean =
        pokemonId in heldItemsByPokemon || phases.getOrDefault(pokemonId, Phase.IDLE) != Phase.IDLE

    override fun cleanup(pokemonId: UUID) {
        phases.remove(pokemonId)
        inputTargets.remove(pokemonId)
        heldItemsByPokemon.remove(pokemonId)
        failedDepositLocations.remove(pokemonId)
    }
}
