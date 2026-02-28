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
import akkiruk.cobblecrew.enums.WorkPhase
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.BarrelBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import kotlin.collections.mutableSetOf
import kotlin.collections.set

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

    // Chest open/close animation tracking
    private data class PendingClose(val pos: BlockPos, val closeTick: Long)
    private val pendingCloses = mutableListOf<PendingClose>()
    private val depositArrival = mutableMapOf<UUID, Long>()
    private const val CHEST_OPEN_DURATION = 20L // ticks chest stays open
    private const val DEPOSIT_DELAY = 15L // ticks before depositing after arrival

    // Retry logic: when all containers are full, wait then try again instead of dropping
    private val depositRetryTimers = mutableMapOf<UUID, Long>()
    private const val DEPOSIT_RETRY_COOLDOWN = 200L // 10 seconds before retrying all containers

    // Rate-limit deposit failure warnings to avoid log spam
    private val lastDepositWarning = mutableMapOf<UUID, Long>()

    /**
     * Ticks pending chest close animations. Call once per server tick.
     */
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

    private fun openContainer(world: World, pos: BlockPos) {
        val state = world.getBlockState(pos)
        val block = state.block
        when (block) {
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

    private fun closeContainer(world: World, pos: BlockPos) {
        val state = world.getBlockState(pos)
        val block = state.block
        when (block) {
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

    /**
     * Add inventory integrations dynamically at runtime
     */
    fun addCompatibility(externalBlocks: Set<Block>) {
        validInventoryBlocks.addAll(externalBlocks)
    }

    /**
     * Validates whether the block is a valid inventory block.
     */
    fun blockValidator(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.block in validInventoryBlocks
    }

    /**
     * Fast block-identity check for the scanner (no world/pos needed).
     */
    fun isValidInventoryBlock(block: net.minecraft.block.Block): Boolean = block in validInventoryBlocks

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

        return possibleTargets
            .filter { pos ->
                blockValidator(world, pos)
                    && pos !in ignorePos
                    && (itemsToDeposit.isEmpty() || hasSpaceFor(world, pos, itemsToDeposit))
            }
            .minByOrNull { it.getSquaredDistance(origin) }
    }

    /**
     * Returns true if the container at [pos] can accept at least one item from [items].
     */
    private fun hasSpaceFor(world: World, pos: BlockPos, items: List<ItemStack>): Boolean {
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

    /**
     * Handles depositing items into an inventory.
     */
    /**
     * Handles logic for finding and depositing items into an inventory when the Pokémon is holding items.
     * It will try multiple inventories nearby iteratively
     */
    fun handleDepositing(
        world: World,
        origin: BlockPos,
        pokemonEntity: PokemonEntity,
        itemsToDeposit: List<ItemStack>,
        failedDepositLocations: MutableMap<UUID, MutableSet<BlockPos>>,
        heldItemsByPokemon: MutableMap<UUID, List<ItemStack>>
    ) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val triedPositions = failedDepositLocations.getOrPut(pokemonId) { mutableSetOf() }
        val allContainers = CobbleCrewCacheManager.getTargets(origin, BlockCategory.CONTAINER)
        val inventoryPos = findClosestInventory(world, origin, triedPositions, itemsToDeposit)

        if (inventoryPos == null) {
            val now = world.time
            val lastWarn = lastDepositWarning[pokemonId] ?: 0L
            if (now - lastWarn >= 200L) { // log at most once per 10 seconds per Pokémon
                CobbleCrewDebugLogger.depositNoContainers(pokemonEntity, allContainers.size, triedPositions.size)
                lastDepositWarning[pokemonId] = now
            }
            // Don't drop items yet if scan is running as inventory might be found within the next ticks.
            if (DeferredBlockScanner.isScanActive(origin)) {
                heldItemsByPokemon[pokemonId] = itemsToDeposit
                return
            }

            // All containers exhausted — hold items and retry after cooldown instead of dropping.
            // Containers may get emptied by players or other Pokémon.
            val retryStart = depositRetryTimers.getOrPut(pokemonId) { world.time }
            if (world.time - retryStart >= DEPOSIT_RETRY_COOLDOWN) {
                // Cooldown elapsed: clear failed locations so we re-check all containers
                CobbleCrewDebugLogger.depositRetryReset(pokemonEntity)
                failedDepositLocations.remove(pokemonId)
                depositRetryTimers.remove(pokemonId)
            }

            // Keep holding items and wander back towards origin while waiting
            heldItemsByPokemon[pokemonId] = itemsToDeposit
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, origin)
            return
        }

        if (CobbleCrewNavigationUtils.isPokemonAtPosition(pokemonEntity, inventoryPos, 2.0)) {
            val now = world.time
            val arrived = depositArrival[pokemonId]

            // First tick at container: verify space, then open and start deposit delay
            if (arrived == null) {
                if (!hasSpaceFor(world, inventoryPos, itemsToDeposit)) {
                    CobbleCrewDebugLogger.depositContainerFull(pokemonEntity, inventoryPos)
                    triedPositions.add(inventoryPos)
                    return
                }
                depositArrival[pokemonId] = now
                pokemonEntity.navigation.stop()
                WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.DEPOSITING, world)
                pokemonEntity.lookControl.lookAt(
                    inventoryPos.x + 0.5, inventoryPos.y + 0.5, inventoryPos.z + 0.5
                )
                openContainer(world, inventoryPos)
                return
            }

            // Wait for deposit delay
            if (now - arrived < DEPOSIT_DELAY) {
                pokemonEntity.lookControl.lookAt(
                    inventoryPos.x + 0.5, inventoryPos.y + 0.5, inventoryPos.z + 0.5
                )
                return
            }

            depositArrival.remove(pokemonId)

            val inventory = world.getBlockEntity(inventoryPos) as? Inventory
            if (inventory == null) {
                CobbleCrewDebugLogger.log(
                    CobbleCrewDebugLogger.Category.DEPOSIT, pokemonEntity,
                    "no block entity at (${inventoryPos.x},${inventoryPos.y},${inventoryPos.z}), marking tried"
                )
                triedPositions.add(inventoryPos)
                pendingCloses.add(PendingClose(inventoryPos.toImmutable(), now + 5L))
                return
            }

            WorkerAnimationUtils.playImmediate(pokemonEntity, WorkPhase.DEPOSIT_SUCCESS, world)
            val remainingDrops = insertStacks(inventory, itemsToDeposit)

            if (remainingDrops.size == itemsToDeposit.size) {
                triedPositions.add(inventoryPos)
            }

            // Schedule chest close
            pendingCloses.add(PendingClose(inventoryPos.toImmutable(), now + CHEST_OPEN_DURATION))

            if (remainingDrops.isNotEmpty()) {
                heldItemsByPokemon[pokemonId] = remainingDrops
            } else {
                CobbleCrewDebugLogger.depositSuccess(pokemonEntity, inventoryPos, itemsToDeposit)
                heldItemsByPokemon.remove(pokemonId)
                failedDepositLocations.remove(pokemonId)
                depositRetryTimers.remove(pokemonId)
                lastDepositWarning.remove(pokemonId)
                pokemonEntity.navigation.stop()
            }
        } else {
            CobbleCrewNavigationUtils.navigateTo(pokemonEntity, inventoryPos)
        }
    }

    // --- V2: Input container extraction (barrels = input, chests = output) ---

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

    /**
     * Cleans up deposit tracking state for a Pokémon (on recall/disconnect).
     */
    fun cleanupPokemon(pokemonId: UUID) {
        depositArrival.remove(pokemonId)
        depositRetryTimers.remove(pokemonId)
        lastDepositWarning.remove(pokemonId)
    }
}