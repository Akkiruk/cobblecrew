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
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Pure stateless inventory utilities — finding containers, inserting/extracting
 * items, validating blocks. No mutable state lives here.
 *
 * Animation state → [ContainerAnimations]
 * Deposit orchestration → [DepositHelper]
 */
object CobbleCrewInventoryUtils {
    private val validInventoryBlocks: MutableSet<Block> = mutableSetOf(
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.BARREL,
        CobblemonBlocks.GILDED_CHEST,
        CobblemonBlocks.BLUE_GILDED_CHEST,
        CobblemonBlocks.PINK_GILDED_CHEST,
        CobblemonBlocks.BLACK_GILDED_CHEST,
        CobblemonBlocks.WHITE_GILDED_CHEST,
        CobblemonBlocks.GREEN_GILDED_CHEST,
        CobblemonBlocks.YELLOW_GILDED_CHEST,
    )

    /** Add inventory integrations dynamically at runtime. */
    fun addCompatibility(externalBlocks: Set<Block>) {
        validInventoryBlocks.addAll(externalBlocks)
    }

    /** Validates whether the block at [pos] is a valid inventory block. */
    fun blockValidator(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.block in validInventoryBlocks
    }

    /** Fast block-identity check for the scanner (no world/pos needed). */
    fun isValidInventoryBlock(block: Block): Boolean = block in validInventoryBlocks

    /**
     * Finds closest inventory that has space for at least one of the given items.
     * If no items are specified, returns any valid container.
     */
    fun findClosestInventory(
        world: World,
        origin: BlockPos,
        ignorePos: Set<BlockPos> = emptySet(),
        itemsToDeposit: List<ItemStack> = emptyList(),
    ): BlockPos? {
        val possibleTargets = CobbleCrewCacheManager.getTargets(origin, BlockCategory.CONTAINER)
        if (possibleTargets.isEmpty()) return null

        // Sort by distance first, then check space — avoids calling hasSpaceFor on far containers
        return possibleTargets
            .filter { pos -> blockValidator(world, pos) && pos !in ignorePos }
            .sortedBy { it.getSquaredDistance(origin) }
            .firstOrNull { pos -> itemsToDeposit.isEmpty() || hasSpaceFor(world, pos, itemsToDeposit) }
    }

    /**
     * Returns true if the container at [pos] can accept at least one item from [items].
     */
    internal fun hasSpaceFor(world: World, pos: BlockPos, items: List<ItemStack>): Boolean {
        val inv = world.getBlockEntity(pos) as? Inventory ?: return false
        val actual = getActualInventory(inv)
        for (i in 0 until actual.size()) {
            if (actual.getStack(i).isEmpty) return true
        }
        for (item in items) {
            for (i in 0 until actual.size()) {
                val slot = actual.getStack(i)
                if (ItemStack.areItemsAndComponentsEqual(slot, item) && slot.count < slot.maxCount) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Attempt to get actual inventory (wrapper to handle double chests)
     */
    fun getActualInventory(inventory: Inventory): Inventory {
        if (inventory !is ChestBlockEntity) return inventory
        val world = inventory.world ?: return inventory
        val pos = inventory.pos
        val state = world.getBlockState(pos)
        val block = state.block

        if (block is ChestBlock) {
            return ChestBlock.getInventory(block, state, world, pos, true) ?: inventory
        }

        return inventory
    }

    /**
     * Inserts a list of ItemStack into an inventory, returning the remainder
     */
    fun insertStacks(inventory: Inventory, stacks: List<ItemStack>): List<ItemStack> {
        val actualInventory = getActualInventory(inventory)

        val remainingDrops = mutableListOf<ItemStack>()
        stacks.forEach { stack ->
            val remaining = insertStack(actualInventory, stack.copy())
            if (!remaining.isEmpty) {
                remainingDrops.add(remaining)
            }
        }

        return remainingDrops
    }

    /**
     * Inserts an ItemStack into an inventory, returning the remainder.
     * Prioritizes existing slots first, then empty slots.
     */
    fun insertStack(inventory: Inventory, stack: ItemStack): ItemStack {
        if (stack.isEmpty) {
            return ItemStack.EMPTY
        }

        var remainingStack = stack.copy()
        remainingStack = fillExistingStacks(inventory, remainingStack)
        if (!remainingStack.isEmpty) {
            remainingStack = fillEmptySlots(inventory, remainingStack)
        }

        return remainingStack
    }

    /**
     * Iterates through the inventory to find empty slots to place remaining items in.
     */
    private fun fillEmptySlots(inventory: Inventory, stack: ItemStack): ItemStack {
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                // If a slot is empty, item's stack size limits the amount we can place.
                val toTransfer = minOf(stack.count, stack.maxCount)
                inventory.setStack(i, stack.split(toTransfer))
                inventory.markDirty()
            }

            if (stack.isEmpty) {
                return ItemStack.EMPTY
            }
        }

        return stack
    }

    /**
     * Iterates through the inventory to find non-null stacks of the same item and adds them.
     */
    private fun fillExistingStacks(inventory: Inventory, stack: ItemStack): ItemStack {
        for (i in 0 until inventory.size()) {
            val inventoryStack = inventory.getStack(i)

            if (inventoryStack.isEmpty || !ItemStack.areItemsAndComponentsEqual(inventoryStack, stack)) {
                continue
            }

            val availableSpace = inventoryStack.maxCount - inventoryStack.count
            if (availableSpace > 0) {
                val toTransfer = minOf(stack.count, availableSpace)

                inventoryStack.increment(toTransfer)
                stack.decrement(toTransfer)
                inventory.markDirty()
            }

            if (stack.isEmpty) {
                return ItemStack.EMPTY
            }
        }

        return stack
    }

    // --- Input container extraction (barrels = input, chests = output) ---

    /**
     * Finds the closest barrel-type container with items matching the predicate.
     */
    fun findInputContainer(
        world: World,
        origin: BlockPos,
        predicate: (ItemStack) -> Boolean,
        ignorePos: Set<BlockPos> = emptySet(),
    ): BlockPos? {
        val containerTargets = CobbleCrewCacheManager.getTargets(
            origin,
            akkiruk.cobblecrew.enums.BlockCategory.CONTAINER
        )
        if (containerTargets.isEmpty()) return null

        return containerTargets
            .filter { pos ->
                pos !in ignorePos
                    && world.getBlockState(pos).block == Blocks.BARREL
                    && (world.getBlockEntity(pos) as? Inventory)?.let { inv ->
                        (0 until inv.size()).any { slot -> predicate(inv.getStack(slot)) }
                    } == true
            }
            .minByOrNull { it.getSquaredDistance(origin) }
    }

    /**
     * Extracts up to [maxAmount] items matching [predicate] from a barrel at [pos].
     * Returns the extracted stack, or EMPTY if nothing matched.
     */
    fun extractFromContainer(
        world: World,
        pos: BlockPos,
        predicate: (ItemStack) -> Boolean,
        maxAmount: Int = 1,
    ): ItemStack {
        val inv = world.getBlockEntity(pos) as? Inventory ?: return ItemStack.EMPTY
        for (slot in 0 until inv.size()) {
            val stack = inv.getStack(slot)
            if (!stack.isEmpty && predicate(stack)) {
                val taken = stack.split(minOf(maxAmount, stack.count))
                inv.markDirty()
                return taken
            }
        }
        return ItemStack.EMPTY
    }

    /**
     * Delivers items directly to a player's inventory. Items that don't fit
     * are dropped at the player's feet.
     */
    fun deliverToPlayer(player: ServerPlayerEntity, items: List<ItemStack>, pokemonEntity: PokemonEntity) {
        for (stack in items) {
            if (stack.isEmpty) continue
            if (!player.inventory.insertStack(stack.copy())) {
                val drop = ItemEntity(player.world, player.x, player.y, player.z, stack.copy())
                drop.setPickupDelay(10)
                player.world.spawnEntity(drop)
            }
        }
    }
}