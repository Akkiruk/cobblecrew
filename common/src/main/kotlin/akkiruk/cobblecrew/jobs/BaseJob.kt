/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.jobs

import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.JobConfig
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.enums.ArrivalStyle
import akkiruk.cobblecrew.enums.BlockCategory
import akkiruk.cobblecrew.enums.JobPhase
import akkiruk.cobblecrew.enums.WorkPhase
import akkiruk.cobblecrew.interfaces.Worker
import akkiruk.cobblecrew.state.ClaimManager
import akkiruk.cobblecrew.state.PartyJobPreferences
import akkiruk.cobblecrew.state.PokemonWorkerState
import akkiruk.cobblecrew.state.StateManager
import akkiruk.cobblecrew.utilities.DepositHelper
import akkiruk.cobblecrew.utilities.WorkerVisualUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Universal base class for all jobs. Implements a single lifecycle state machine:
 *
 * ```
 * IDLE → NAVIGATING → ARRIVING → WORKING → [DEPOSITING →] IDLE
 *                                   ↓
 *                            MoveTo → NAVIGATING (placement, multi-step)
 *                            Repeat → ARRIVING   (cauldron fill, ongoing)
 * ```
 *
 * Subclasses implement 2-3 hooks:
 * - [findTarget] — where to go
 * - [doWork] — what to do there
 * - Optionally: [validateTarget], [getCooldownTicks], [afterWork]
 *
 * Everything else (navigation, claiming, visuals, deposit, overflow, cleanup)
 * is handled here once — never re-implemented per job type.
 */
abstract class BaseJob : Worker {

    // --- Identity (set by DSL constructors) ---
    abstract override val name: String
    abstract val category: String
    abstract val arrivalParticle: ParticleEffect
    open val arrivalTolerance: Double = 3.0
    open val workPhase: WorkPhase = WorkPhase.HARVESTING

    /** How long to pause at the target before starting work. */
    open val arrivalStyle: ArrivalStyle = ArrivalStyle.ANIMATED

    /** False for defense, support, environmental — jobs that don't produce items. */
    open val producesItems: Boolean = true

    /** If true, skips NAVIGATING/ARRIVING and goes straight to WORKING (production jobs). */
    open val requiresTarget: Boolean = true

    /** Config access — reads from JobConfigManager by name. */
    open val config: JobConfig get() = JobConfigManager.get(name)

    /**
     * Build the default [JobConfig] for this job. Override to add type-specific
     * fields (cooldown, effect duration, etc.).
     */
    protected open fun buildDefaultConfig(): JobConfig = JobConfig(enabled = true)

    /** Call from subclass init {} to register the default config. */
    protected fun registerConfig() {
        JobConfigManager.registerDefault(category, name, buildDefaultConfig())
    }

    // ---- Lifecycle hooks (the ONLY things DSL classes override) ----

    /**
     * Find the next target for this job.
     * Return null = no work available right now.
     */
    abstract fun findTarget(state: PokemonWorkerState, context: JobContext): Target?

    /**
     * Execute the work action at the target.
     * Returns a [WorkResult] describing what happened:
     * - [WorkResult.Done] with items → transition to DEPOSITING
     * - [WorkResult.Done] empty → transition to IDLE
     * - [WorkResult.MoveTo] → navigate to a new position and work again
     * - [WorkResult.Repeat] → stay at target, re-enter ARRIVING with cooldown
     */
    abstract fun doWork(
        state: PokemonWorkerState,
        context: JobContext,
        pokemonEntity: PokemonEntity,
    ): WorkResult

    /** Validate the target still exists. Default: block at pos is not air. */
    open fun validateTarget(state: PokemonWorkerState, context: JobContext): Boolean {
        val pos = state.targetPos ?: return false
        return !context.world.getBlockState(pos).isAir
    }

    /** Ticks between work cycles. Override for cooldown-based jobs. */
    open fun getCooldownTicks(state: PokemonWorkerState): Long = 0L

    /** Hook after work completes (e.g. replant, play sound). Not called for Repeat results. */
    open fun afterWork(state: PokemonWorkerState, context: JobContext, pokemonEntity: PokemonEntity) {}

    // ---- The universal tick — never override ----

    final override fun tick(context: JobContext, pokemonEntity: PokemonEntity) {
        val state = StateManager.getOrCreate(pokemonEntity.pokemon.uuid)

        // Safety: if items are held but phase isn't DEPOSITING, force deposit.
        // Handles orphan items from job switches or interrupted idle pickups.
        if (state.heldItems.isNotEmpty() && state.phase != JobPhase.DEPOSITING && producesItems) {
            state.phase = JobPhase.DEPOSITING
        }

        when (state.phase) {
            JobPhase.IDLE -> tickIdle(state, context, pokemonEntity)
            JobPhase.NAVIGATING -> tickNavigating(state, context, pokemonEntity)
            JobPhase.ARRIVING -> tickArriving(state, context, pokemonEntity)
            JobPhase.WORKING -> tickWorking(state, context, pokemonEntity)
            JobPhase.DEPOSITING -> tickDepositing(state, context, pokemonEntity)
        }
    }

    private fun tickIdle(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity) {
        if (context.world.time < state.cooldownUntil) return

        if (!requiresTarget) {
            // Production-style jobs: go directly to WORKING
            state.phase = JobPhase.WORKING
            return
        }

        val target = findTarget(state, context) ?: return
        state.targetPos = target.pos
        ClaimManager.claim(state, target, context.world)
        ClaimManager.navigateTo(entity, target.pos, state)
        state.phase = JobPhase.NAVIGATING
    }

    private fun tickNavigating(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity) {
        val targetPos = state.targetPos
        if (targetPos == null) {
            resetToIdle(state, context)
            return
        }

        if (!validateTarget(state, context)) {
            ClaimManager.release(state)
            resetToIdle(state, context)
            return
        }

        if (ClaimManager.isPokemonAtPosition(entity, targetPos, arrivalTolerance)) {
            entity.navigation.stop()
            state.phase = JobPhase.ARRIVING
        } else {
            ClaimManager.navigateTo(entity, targetPos, state)
        }
    }

    private fun tickArriving(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity) {
        val targetPos = state.targetPos ?: run { resetToIdle(state, context); return }

        if (checkArrival(state, context, entity)) {
            state.phase = JobPhase.WORKING
        }
    }

    /**
     * Check if the Pokémon has completed its arrival animation.
     * Default: delegates to [WorkerVisualUtils.handleArrival].
     * Override for player-targeted jobs that need [WorkerVisualUtils.handlePlayerArrival].
     */
    protected open fun checkArrival(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity): Boolean {
        val targetPos = state.targetPos ?: return false
        return WorkerVisualUtils.handleArrival(entity, targetPos, context.world, arrivalParticle, arrivalTolerance, workPhase, arrivalStyle.delayTicks)
    }

    private fun tickWorking(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity) {
        val result = doWork(state, context, entity)

        when (result) {
            is WorkResult.Done -> {
                afterWork(state, context, entity)
                ClaimManager.release(state)

                if (result.items.isNotEmpty() && producesItems) {
                    state.heldItems.addAll(result.items)
                    if (state.heldSinceTick == 0L) state.heldSinceTick = context.world.time
                    state.phase = JobPhase.DEPOSITING
                } else {
                    state.phase = JobPhase.IDLE
                    state.cooldownUntil = context.world.time + getCooldownTicks(state)
                    state.targetPos = null
                }
            }

            is WorkResult.MoveTo -> {
                afterWork(state, context, entity)
                ClaimManager.release(state)
                state.targetPos = result.target
                ClaimManager.claim(state, Target.Block(result.target), context.world)
                ClaimManager.navigateTo(entity, result.target, state)
                state.phase = JobPhase.NAVIGATING
            }

            is WorkResult.Repeat -> {
                // Stay at target, re-enter ARRIVING (with renewal)
                ClaimManager.renewClaim(state, context.world)
                state.arrivalTick = null // reset arrival animation
                state.cooldownUntil = context.world.time + getCooldownTicks(state)
                state.phase = JobPhase.IDLE
                // The next tick will re-enter IDLE → see cooldown → wait → then findTarget
                // returns the same target → navigate (already there) → arrive → work → repeat
            }

            is WorkResult.Continue -> {
                // Multi-tick job in progress — stay in WORKING, doWork called next tick
            }
        }
    }

    private fun tickDepositing(state: PokemonWorkerState, context: JobContext, entity: PokemonEntity) {
        val stillDepositing = DepositHelper.tick(state, context, entity)
        if (!stillDepositing) {
            state.phase = JobPhase.IDLE
            state.cooldownUntil = context.world.time + getCooldownTicks(state)
            state.targetPos = null
        }
    }

    private fun resetToIdle(state: PokemonWorkerState, context: JobContext) {
        state.phase = JobPhase.IDLE
        state.targetPos = null
        state.arrivalTick = null
        state.graceTick = null
    }

    // ---- Worker interface (final — state is centralized) ----

    override fun isAvailable(context: JobContext, pokemonId: UUID): Boolean {
        if (!config.enabled) return false
        // Party Pokémon: per-job partyEnabled flag + player-level blocked lists
        if (context is JobContext.Party) {
            if (config.partyEnabled != true) return false
            if (PartyJobPreferences.isBlocked(context.player.uuid, name, category)) return false
        }
        if (!requiresTarget) return true
        // Lightweight: just check if the cache has ANY targets for our category
        val cat = targetCategory ?: return true
        return CobbleCrewCacheManager.getTargets(context.origin, cat).isNotEmpty()
    }

    final override fun hasActiveState(pokemonId: UUID): Boolean {
        val state = StateManager.get(pokemonId) ?: return false
        return state.phase != JobPhase.IDLE || state.heldItems.isNotEmpty()
    }

    final override fun cleanup(pokemonId: UUID) {
        // No-op — StateManager.remove() handles everything.
        // Individual job cleanup is no longer needed since all state is centralized.
    }

    final override fun getHeldItems(pokemonId: UUID): List<ItemStack>? {
        val state = StateManager.get(pokemonId) ?: return null
        return state.heldItems.takeIf { it.isNotEmpty() }?.toList()
    }

    // ---- Shared target-finding utilities for DSL subclasses ----

    /**
     * Find a random block in the scanner cache that matches [category],
     * passes [readyCheck], is unclaimed, not blacklisted, and not unreachable.
     */
    protected fun findCachedBlockTarget(
        state: PokemonWorkerState,
        context: JobContext,
        category: BlockCategory,
        readyCheck: ((World, BlockPos) -> Boolean)? = null,
    ): Target? {
        val world = context.world
        val origin = context.origin
        val pokemonId = state.pokemonId
        val now = world.time
        val targets = CobbleCrewCacheManager.getTargets(origin, category)
        if (targets.isEmpty()) return null

        val valid = targets
            .filter { pos ->
                !ClaimManager.isTargetedByOther(pos, pokemonId)
                    && !ClaimManager.isUnreachable(pokemonId, pos, now)
                    && (readyCheck == null || readyCheck(world, pos))
            }

        return valid.randomOrNull()?.let { Target.Block(it) }
    }

    /**
     * Harvest a block using loot tables (shared by gathering/combo jobs).
     * Returns the dropped items without modifying state.
     */
    protected fun harvestWithLoot(
        world: World,
        pos: BlockPos,
        entity: PokemonEntity,
        tool: ItemStack = ItemStack.EMPTY,
        afterBreak: ((World, BlockPos, net.minecraft.block.BlockState) -> Unit)? = null,
    ): List<ItemStack> {
        val state = world.getBlockState(pos)
        if (state.isAir) return emptyList()

        val drops = net.minecraft.block.Block.getDroppedStacks(
            state,
            world as net.minecraft.server.world.ServerWorld,
            pos,
            world.getBlockEntity(pos),
            entity,
            tool,
        )

        if (afterBreak != null) {
            afterBreak(world, pos, state)
        } else {
            world.setBlockState(pos, net.minecraft.block.Blocks.AIR.defaultState)
        }

        // Remove from cache
        val category = targetCategory
        if (category != null) {
            CobbleCrewCacheManager.removeTargetGlobal(category, pos)
        }

        return drops
    }
}
