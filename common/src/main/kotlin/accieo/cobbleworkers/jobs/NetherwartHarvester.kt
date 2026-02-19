/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.enums.BlockCategory
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.NetherWartBlock
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object NetherwartHarvester : BaseHarvester() {
    private val config get() = CobbleworkersConfigHolder.config.netherwartHarvest

    override val jobType = JobType.NetherwartHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.SMOKE
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).block is NetherWartBlock
    }

    override val name = "netherwart_harvester"
    override val targetCategory = BlockCategory.NETHERWART
    override fun isEligible(moves: Set<String>, types: Set<String>, species: String, ability: String) =
        isEnabled() && (config.typeHarvestsNetherwart.name in types
            || config.netherwartHarvesters.any { it.equals(species, ignoreCase = true) })

    override fun isEnabled() = config.netherwartHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsNetherwart, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.netherwartHarvesters)

    override fun isTargetReady(world: World, pos: BlockPos) =
        world.getBlockState(pos).get(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        harvestWithLoot(world, targetPos, pokemonEntity) { w, p, state ->
            val newState = if (config.shouldReplantNetherwart) {
                state.with(NetherWartBlock.AGE, 0)
            } else {
                Blocks.AIR.defaultState
            }
            w.setBlockState(p, newState, Block.NOTIFY_ALL)
        }
    }
}
