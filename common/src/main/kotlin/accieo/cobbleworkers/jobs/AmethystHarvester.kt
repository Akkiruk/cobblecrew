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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object AmethystHarvester : BaseHarvester() {
    private val config get() = CobbleworkersConfigHolder.config.amethyst

    override val jobType = JobType.AmethystHarvester
    override val arrivalParticle: ParticleEffect = ParticleTypes.END_ROD
    override val arrivalTolerance = 1.5
    override val blockValidator: ((World, BlockPos) -> Boolean) = { world, pos ->
        world.getBlockState(pos).block == Blocks.AMETHYST_CLUSTER
    }

    override fun isEnabled() = config.amethystHarvestersEnabled
    override fun isEligible(pokemonEntity: PokemonEntity) =
        CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsAmethyst, pokemonEntity)
            || CobbleworkersTypeUtils.isDesignatedBySpecies(pokemonEntity, config.amethystHarvesters)

    override fun harvest(world: World, targetPos: BlockPos, pokemonEntity: PokemonEntity) {
        if (world.getBlockState(targetPos).block != Blocks.AMETHYST_CLUSTER) return
        harvestWithLoot(world, targetPos, pokemonEntity) { w, p, _ ->
            w.setBlockState(p, Blocks.AIR.defaultState)
        }
    }
}
