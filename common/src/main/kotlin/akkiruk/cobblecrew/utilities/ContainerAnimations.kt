/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import net.minecraft.block.BarrelBlock
import net.minecraft.block.ChestBlock
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Manages chest/barrel open-close animations for the deposit system.
 * All mutable animation state lives here — nothing else tracks pending closes.
 */
object ContainerAnimations {

    private data class PendingClose(val pos: BlockPos, val closeTick: Long)

    private val pendingCloses = mutableListOf<PendingClose>()
    private const val CHEST_OPEN_DURATION = 20L

    /** Tick pending close animations. Call once per server tick from each manager. */
    fun tickAnimations(world: World) {
        if (pendingCloses.isEmpty()) return
        val now = world.time
        val iter = pendingCloses.iterator()
        while (iter.hasNext()) {
            val pending = iter.next()
            if (now >= pending.closeTick) {
                closeContainer(world, pending.pos)
                iter.remove()
            }
        }
    }

    /** Open a chest/barrel with sound. */
    fun openContainer(world: World, pos: BlockPos) {
        val state = world.getBlockState(pos)
        when (val block = state.block) {
            is ChestBlock -> {
                world.addSyncedBlockEvent(pos, block, 1, 1)
                world.playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1.0f)
            }
            is BarrelBlock -> {
                world.setBlockState(pos, state.with(BarrelBlock.OPEN, true))
                world.playSound(null, pos, SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.5f, 1.0f)
            }
        }
    }

    /** Schedule a container close after the standard duration. */
    fun scheduleClose(world: World, pos: BlockPos) {
        pendingCloses.add(PendingClose(pos.toImmutable(), world.time + CHEST_OPEN_DURATION))
    }

    /** Schedule a container close at a specific tick. */
    fun scheduleCloseAt(pos: BlockPos, closeTick: Long) {
        pendingCloses.add(PendingClose(pos.toImmutable(), closeTick))
    }

    private fun closeContainer(world: World, pos: BlockPos) {
        val state = world.getBlockState(pos)
        when (val block = state.block) {
            is ChestBlock -> {
                world.addSyncedBlockEvent(pos, block, 1, 0)
                world.playSound(null, pos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5f, 1.0f)
            }
            is BarrelBlock -> {
                world.setBlockState(pos, state.with(BarrelBlock.OPEN, false))
                world.playSound(null, pos, SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.5f, 1.0f)
            }
        }
    }
}
