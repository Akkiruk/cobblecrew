/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Unified debug logging for CobbleCrew.
 *
 * Only logs state-change events (job assigned, target claimed, block harvested, etc.)
 * — never periodic tick noise. Controlled entirely by config toggles.
 */
object CobbleCrewDebugLogger {

    enum class Category {
        PROFILE,
        DISPATCH,
        NAVIGATION,
        HARVEST,
        DEPOSIT,
        PRODUCTION,
        PROCESSING,
        PLACEMENT,
        DEFENSE,
        SUPPORT,
        SCANNER,
        CLEANUP
    }

    private val debugConfig get() = CobbleCrewConfigHolder.config.debug

    private fun isEnabled(category: Category): Boolean {
        if (!debugConfig.enabled) return false
        return when (category) {
            Category.PROFILE -> debugConfig.logProfile
            Category.DISPATCH -> debugConfig.logDispatch
            Category.NAVIGATION -> debugConfig.logNavigation
            Category.HARVEST -> debugConfig.logHarvest
            Category.DEPOSIT -> debugConfig.logDeposit
            Category.PRODUCTION -> debugConfig.logProduction
            Category.PROCESSING -> debugConfig.logProcessing
            Category.PLACEMENT -> debugConfig.logPlacement
            Category.DEFENSE -> debugConfig.logDefense
            Category.SUPPORT -> debugConfig.logSupport
            Category.SCANNER -> debugConfig.logScanner
            Category.CLEANUP -> debugConfig.logCleanup
        }
    }

    private fun passesFilter(species: String?, pokemonId: UUID?): Boolean {
        val filter = debugConfig.pokemonFilter.trim()
        if (filter.isEmpty()) return true
        if (species != null && species.equals(filter, ignoreCase = true)) return true
        if (pokemonId != null && pokemonId.toString().startsWith(filter, ignoreCase = true)) return true
        return false
    }

    private fun pokemonTag(species: String?, pokemonId: UUID?): String {
        if (species == null && pokemonId == null) return ""
        val name = species ?: "?"
        val id = if (debugConfig.verbose) pokemonId?.toString() ?: "?" else pokemonId?.toString()?.take(8) ?: "?"
        return "$name($id)"
    }

    private fun posTag(pos: BlockPos?): String = pos?.let { "(${it.x},${it.y},${it.z})" } ?: ""

    private fun itemsTag(items: List<ItemStack>): String =
        items.joinToString(", ") { "${it.count}x ${it.item}" }

    fun log(category: Category, message: String) {
        if (!isEnabled(category)) return
        CobbleCrew.LOGGER.info("[CobbleCrew:${category.name}] $message")
    }

    // -- Convenience methods for common patterns --

    fun log(category: Category, species: String?, pokemonId: UUID?, message: String) {
        if (!isEnabled(category)) return
        if (!passesFilter(species, pokemonId)) return
        CobbleCrew.LOGGER.info("[CobbleCrew:${category.name}] ${pokemonTag(species, pokemonId)} $message")
    }

    fun log(category: Category, entity: PokemonEntity, message: String) {
        log(category, entity.pokemon.species.name, entity.pokemon.uuid, message)
    }

    // -- Profile --

    fun profileBuilt(species: String, pokemonId: UUID, moves: Set<String>, types: Set<String>, eligibleJobs: List<String>) {
        log(Category.PROFILE, species, pokemonId,
            "profile built: moves=$moves, types=$types, eligible=[${eligibleJobs.joinToString()}]")
    }

    fun profileInvalidated() {
        log(Category.PROFILE, "all profiles invalidated (config reload)")
    }

    // -- Dispatch --

    fun jobAssigned(species: String, pokemonId: UUID, jobName: String) {
        log(Category.DISPATCH, species, pokemonId, "assigned job: $jobName")
    }

    fun jobSticking(species: String, pokemonId: UUID, jobName: String, reason: String) {
        if (!debugConfig.verbose) return
        log(Category.DISPATCH, species, pokemonId, "sticking with $jobName ($reason)")
    }

    fun jobIdle(species: String, pokemonId: UUID, eligibleCount: Int, availabilityInfo: List<String>) {
        log(Category.DISPATCH, species, pokemonId,
            "idle: $eligibleCount eligible, none available. [${availabilityInfo.joinToString()}]")
    }

    fun noEligibleJobs(species: String, pokemonId: UUID) {
        log(Category.DISPATCH, species, pokemonId, "no eligible jobs — returning to pasture")
    }

    // -- Navigation --

    fun targetClaimed(species: String?, pokemonId: UUID, pos: BlockPos) {
        log(Category.NAVIGATION, species, pokemonId, "claimed target ${posTag(pos)}")
    }

    fun targetReleased(species: String?, pokemonId: UUID, pos: BlockPos) {
        log(Category.NAVIGATION, species, pokemonId, "released target ${posTag(pos)}")
    }

    fun targetExpired(pokemonId: UUID, pos: BlockPos) {
        log(Category.NAVIGATION, null, pokemonId, "claim expired at ${posTag(pos)}")
    }

    fun playerTargetClaimed(species: String?, pokemonId: UUID, playerId: UUID) {
        log(Category.NAVIGATION, species, pokemonId, "claimed player target $playerId")
    }

    fun playerTargetReleased(pokemonId: UUID) {
        log(Category.NAVIGATION, null, pokemonId, "released player target")
    }

    fun mobTargetClaimed(species: String?, pokemonId: UUID, entityId: Int) {
        log(Category.NAVIGATION, species, pokemonId, "claimed mob target entity#$entityId")
    }

    fun mobTargetReleased(pokemonId: UUID) {
        log(Category.NAVIGATION, null, pokemonId, "released mob target")
    }

    fun arrivedAtTarget(entity: PokemonEntity, pos: BlockPos) {
        if (!debugConfig.verbose) return
        log(Category.NAVIGATION, entity, "arrived at ${posTag(pos)}")
    }

    // -- Harvest --

    fun harvestTargetFound(entity: PokemonEntity, jobName: String, pos: BlockPos) {
        log(Category.HARVEST, entity, "$jobName found target ${posTag(pos)}")
    }

    fun harvestExecuted(entity: PokemonEntity, jobName: String, pos: BlockPos, drops: List<ItemStack>) {
        log(Category.HARVEST, entity, "$jobName harvested ${posTag(pos)}: [${itemsTag(drops)}]")
    }

    fun harvestTargetInvalid(entity: PokemonEntity, jobName: String, pos: BlockPos, reason: String) {
        log(Category.HARVEST, entity, "$jobName target ${posTag(pos)} invalid: $reason")
    }

    // -- Deposit --

    fun depositSuccess(entity: PokemonEntity, pos: BlockPos, items: List<ItemStack>) {
        log(Category.DEPOSIT, entity, "deposited [${itemsTag(items)}] at ${posTag(pos)}")
    }

    fun depositContainerFull(entity: PokemonEntity, pos: BlockPos) {
        log(Category.DEPOSIT, entity, "container full at ${posTag(pos)}, trying next")
    }

    fun depositNoContainers(entity: PokemonEntity, totalCached: Int, triedCount: Int) {
        log(Category.DEPOSIT, entity, "no containers with space: $totalCached cached, $triedCount tried")
    }

    fun depositRetryReset(entity: PokemonEntity) {
        log(Category.DEPOSIT, entity, "retry cooldown elapsed, re-checking all containers")
    }

    fun depositOverflow(entity: PokemonEntity, items: List<ItemStack>) {
        log(Category.DEPOSIT, entity, "overflow timeout — dropped [${itemsTag(items)}]")
    }

    // -- Production --

    fun productionProduced(entity: PokemonEntity, jobName: String, items: List<ItemStack>) {
        log(Category.PRODUCTION, entity, "$jobName produced [${itemsTag(items)}]")
    }

    fun productionOnCooldown(entity: PokemonEntity, jobName: String, ticksRemaining: Long) {
        if (!debugConfig.verbose) return
        log(Category.PRODUCTION, entity, "$jobName on cooldown (${ticksRemaining}t remaining)")
    }

    // -- Processing --

    fun processingPhaseChange(entity: PokemonEntity, jobName: String, from: String, to: String) {
        log(Category.PROCESSING, entity, "$jobName phase: $from → $to")
    }

    fun processingExtracted(entity: PokemonEntity, jobName: String, input: ItemStack, output: List<ItemStack>) {
        log(Category.PROCESSING, entity, "$jobName extracted ${input.count}x ${input.item} → [${itemsTag(output)}]")
    }

    fun processingNoInput(entity: PokemonEntity, jobName: String) {
        log(Category.PROCESSING, entity, "$jobName no matching input in barrels")
    }

    // -- Placement --

    fun placementTargetFound(entity: PokemonEntity, jobName: String, pos: BlockPos) {
        log(Category.PLACEMENT, entity, "$jobName placement target ${posTag(pos)}")
    }

    fun placementExecuted(entity: PokemonEntity, jobName: String, pos: BlockPos, item: ItemStack) {
        log(Category.PLACEMENT, entity, "$jobName placed ${item.count}x ${item.item} at ${posTag(pos)}")
    }

    fun placementNoTarget(entity: PokemonEntity, jobName: String) {
        log(Category.PLACEMENT, entity, "$jobName no valid placement positions")
    }

    // -- Defense --

    fun defenseTargetFound(entity: PokemonEntity, jobName: String, targetEntityId: Int) {
        log(Category.DEFENSE, entity, "$jobName targeting hostile entity#$targetEntityId")
    }

    fun defenseEffectApplied(entity: PokemonEntity, jobName: String, targetEntityId: Int) {
        log(Category.DEFENSE, entity, "$jobName applied effect to entity#$targetEntityId")
    }

    fun defenseTargetDead(entity: PokemonEntity, jobName: String, targetEntityId: Int) {
        log(Category.DEFENSE, entity, "$jobName target entity#$targetEntityId dead/gone")
    }

    // -- Support --

    fun supportTargetFound(entity: PokemonEntity, jobName: String, playerName: String) {
        log(Category.SUPPORT, entity, "$jobName targeting player $playerName")
    }

    fun supportEffectApplied(entity: PokemonEntity, jobName: String, playerName: String) {
        log(Category.SUPPORT, entity, "$jobName applied effect to $playerName")
    }

    // -- Scanner --

    fun scanStarted(pastureOrigin: BlockPos, radius: Int, height: Int) {
        log(Category.SCANNER, "scan started at ${posTag(pastureOrigin)}, radius=$radius, height=$height")
    }

    fun scanComplete(pastureOrigin: BlockPos, categoryCounts: Map<String, Int>) {
        log(Category.SCANNER, "scan complete at ${posTag(pastureOrigin)}: $categoryCounts")
    }

    // -- Cleanup --

    fun pokemonCleanedUp(species: String?, pokemonId: UUID) {
        log(Category.CLEANUP, species, pokemonId, "cleaned up — removed from all tracking")
    }
}
