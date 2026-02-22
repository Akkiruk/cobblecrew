/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.utilities.CobbleCrewInventoryUtils
import akkiruk.cobblecrew.utilities.CobbleCrewNavigationUtils
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
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

    /** Minimum number of items to extract per batch. Override for recipes needing >1. */
    open val minExtractAmount: Int = 1

    override fun isAvailable(world: World, origin: BlockPos, pokemonId: UUID): Boolean {
        return CobbleCrewInventoryUtils.findInputContainer(
            world, origin, ::inputPredicate
        ) != null
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        when (phases.getOrDefault(pokemonId, Phase.IDLE)) {
            Phase.IDLE -> {
                val barrel = CobbleCrewInventoryUtils.findInputContainer(
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
                CobbleCrewNavigationUtils.navigateTo(pokemonEntity, target)
                if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, target, 2.0)) {
                    val taken = CobbleCrewInventoryUtils.extractFromContainer(
                        world, target, ::inputPredicate, maxAmount = minExtractAmount
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
                CobbleCrewInventoryUtils.handleDepositing(
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
