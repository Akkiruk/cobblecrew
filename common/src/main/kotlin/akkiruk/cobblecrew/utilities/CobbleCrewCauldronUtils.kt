/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.state.ClaimManager
import net.minecraft.block.Blocks
import net.minecraft.block.LeveledCauldronBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object CobbleCrewCauldronUtils {
    enum class CauldronFluid {
        WATER, LAVA, POWDER_SNOW
    }

    /**
     * Finds a random empty cauldron
     */
    fun findClosestCauldron(world: World, origin: BlockPos): BlockPos? {
        val possibleTargets = CobbleCrewCacheManager.getTargets(origin, BlockCategory.CAULDRON)
        if (possibleTargets.isEmpty()) return null

        return possibleTargets
            .filter { pos ->
                world.getBlockState(pos).isOf(Blocks.CAULDRON)
                    && !ClaimManager.isBlacklisted(pos, world.time)
            }
            .randomOrNull()
    }

    /**
     * Adds fluid to cauldron
     */
    fun addFluid(world: World, cauldronPos: BlockPos, fluid: CauldronFluid) {
        val newState = when (fluid) {
            CauldronFluid.WATER -> Blocks.WATER_CAULDRON.defaultState.with(LeveledCauldronBlock.LEVEL, LeveledCauldronBlock.MAX_LEVEL)
            CauldronFluid.LAVA -> Blocks.LAVA_CAULDRON.defaultState
            CauldronFluid.POWDER_SNOW -> Blocks.POWDER_SNOW_CAULDRON.defaultState.with(LeveledCauldronBlock.LEVEL, LeveledCauldronBlock.MAX_LEVEL)
        }

        world.setBlockState(cauldronPos, newState)
    }
}