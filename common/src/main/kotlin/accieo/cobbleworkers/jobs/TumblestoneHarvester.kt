/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object TumblestoneHarvester : BaseHarvester() {
    private val VALID_TUMBLESTONE_BLOCKS: Set<Block> = setOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER,
    )
    private val REPLACEMENT_BLOCKS: Map<Block, Block> = mapOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_TUMBLESTONE,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_BLACK_TUMBLESTONE,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_SKY_TUMBLESTONE
    )
    private val config get() = CobbleworkersConfigHolder.config.tumblestone

    override val jobType = JobType.TumblestoneHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.COMPOSTER
    override val arrivalTolerance = 1.5
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).block in VALID_TUMBLESTONE_BLOCKS
    }

    override fun isEnabled() = config.tumblestoneHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsTumblestone, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.tumblestoneHarvesters)

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        if (world.getBlockState(targetPos).block !in VALID_TUMBLESTONE_BLOCKS) return
        harvestWithLoot(world, targetPos, pokemonEntity) { w, p, state ->
            if (config.shouldReplantTumblestone) {
                val replacement = REPLACEMENT_BLOCKS[state.block] ?: return@harvestWithLoot
                var replacementState = replacement.defaultState
                val facingProperty = Properties.FACING
                if (replacementState.properties.contains(facingProperty) && state.contains(facingProperty)) {
                    replacementState = replacementState.with(facingProperty, state.get(facingProperty))
                }
                w.setBlockState(p, replacementState)
            } else {
                w.setBlockState(p, Blocks.AIR.defaultState)
            }
        }
    }
}
