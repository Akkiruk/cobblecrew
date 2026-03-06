/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.commands

import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.jobs.PartyWorkerManager
import akkiruk.cobblecrew.jobs.WorkerDispatcher
import akkiruk.cobblecrew.state.PartyJobPreferences
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

internal object PartyCommands {

    fun runToggle(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val player = s.playerOrThrow
        val nowEnabled = PartyWorkerManager.togglePartyJobs(player.uuid)
        if (nowEnabled) {
            s.sendFeedback({ success("Party jobs enabled. Your sent-out Pokémon will work.") }, false)
        } else {
            s.sendFeedback({ error("Party jobs disabled. Your sent-out Pokémon will idle.") }, false)
        }
        return 1
    }

    fun runJobs(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val player = s.playerOrThrow
        val prefs = PartyJobPreferences.getPrefs(player.uuid)
        val globalConfig = CobbleCrewConfigHolder.config.party

        s.sendFeedback({ header("Party Job Controls") }, false)

        if (globalConfig.blockedJobs.isNotEmpty() || globalConfig.blockedCategories.isNotEmpty()) {
            s.sendFeedback({ Text.literal("  §7Server-wide blocks:").formatted(Formatting.GRAY) }, false)
            if (globalConfig.blockedCategories.isNotEmpty()) {
                s.sendFeedback({ Text.literal("    Categories: §c${globalConfig.blockedCategories.joinToString(", ")}") }, false)
            }
            if (globalConfig.blockedJobs.isNotEmpty()) {
                s.sendFeedback({ Text.literal("    Jobs: §c${globalConfig.blockedJobs.joinToString(", ")}") }, false)
            }
        }

        s.sendFeedback({ Text.empty() }, false)
        s.sendFeedback({ Text.literal("  §bYour personal blocks:").formatted(Formatting.AQUA) }, false)

        if (prefs.blockedCategories.isEmpty() && prefs.blockedJobs.isEmpty()) {
            s.sendFeedback({ info("    None — all jobs allowed") }, false)
        } else {
            if (prefs.blockedCategories.isNotEmpty()) {
                s.sendFeedback({ Text.literal("    Categories: §c${prefs.blockedCategories.joinToString(", ")}") }, false)
            }
            if (prefs.blockedJobs.isNotEmpty()) {
                s.sendFeedback({ Text.literal("    Jobs: §c${prefs.blockedJobs.joinToString(", ")}") }, false)
            }
        }

        s.sendFeedback({ Text.empty() }, false)
        s.sendFeedback({ info("  /cobblecrew party block job <name>     — block a specific job") }, false)
        s.sendFeedback({ info("  /cobblecrew party block category <name> — block an entire category") }, false)
        s.sendFeedback({ info("  /cobblecrew party allow job <name>     — unblock a job") }, false)
        s.sendFeedback({ info("  /cobblecrew party allow category <name> — unblock a category") }, false)
        s.sendFeedback({ info("  /cobblecrew party reset                — clear all personal blocks") }, false)
        return 1
    }

    fun runBlockJob(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.playerOrThrow
        val jobName = StringArgumentType.getString(ctx, "job").lowercase()
        if (!JobConfigManager.allJobNames().any { it.equals(jobName, ignoreCase = true) }) {
            ctx.source.sendFeedback({ error("Unknown job: $jobName") }, false)
            return 0
        }
        PartyJobPreferences.blockJob(player.uuid, jobName)
        resetActivePartyWorkers(player.uuid)
        ctx.source.sendFeedback({ success("Blocked '$jobName' for your party Pokémon.") }, false)
        return 1
    }

    fun runBlockCategory(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.playerOrThrow
        val category = StringArgumentType.getString(ctx, "category").lowercase()
        PartyJobPreferences.blockCategory(player.uuid, category)
        resetActivePartyWorkers(player.uuid)
        ctx.source.sendFeedback({ success("Blocked category '$category' for your party Pokémon.") }, false)
        return 1
    }

    fun runAllowJob(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.playerOrThrow
        val jobName = StringArgumentType.getString(ctx, "job").lowercase()
        PartyJobPreferences.allowJob(player.uuid, jobName)
        ctx.source.sendFeedback({ success("Allowed '$jobName' for your party Pokémon.") }, false)
        return 1
    }

    fun runAllowCategory(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.playerOrThrow
        val category = StringArgumentType.getString(ctx, "category").lowercase()
        PartyJobPreferences.allowCategory(player.uuid, category)
        ctx.source.sendFeedback({ success("Allowed category '$category' for your party Pokémon.") }, false)
        return 1
    }

    fun runReset(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.playerOrThrow
        PartyJobPreferences.resetPlayer(player.uuid)
        ctx.source.sendFeedback({ success("Cleared all personal party job blocks.") }, false)
        return 1
    }

    private fun resetActivePartyWorkers(playerId: UUID) {
        val workers = PartyWorkerManager.getActivePartyWorkers()
        for ((pokemonId, entry) in workers) {
            if (entry.owner.uuid == playerId) {
                WorkerDispatcher.resetAssignment(pokemonId)
            }
        }
    }

    fun runStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val partyConfig = CobbleCrewConfigHolder.config.party
        val workers = PartyWorkerManager.getActivePartyWorkers()

        s.sendFeedback({ header("Party Workers") }, false)
        s.sendFeedback({ label("Enabled", if (partyConfig.enabled) "§aYes" else "§cNo") }, false)

        val player = s.player
        if (player != null) {
            val personal = PartyWorkerManager.isPartyEnabled(player.uuid)
            s.sendFeedback({ label("Your party jobs", if (personal) "§aOn" else "§cOff §7(/cobblecrew party toggle)") }, false)
            val prefs = PartyJobPreferences.getPrefs(player.uuid)
            val blockedCount = prefs.blockedJobs.size + prefs.blockedCategories.size
            if (blockedCount > 0) {
                s.sendFeedback({ label("Your blocked", "§c$blockedCount §7(/cobblecrew party jobs)") }, false)
            }
        }
        s.sendFeedback({ label("Active party workers", workers.size.toString()) }, false)
        s.sendFeedback({ label("Max work distance", "${partyConfig.maxWorkDistance} blocks") }, false)
        s.sendFeedback({ label("Scan interval", "${partyConfig.scanIntervalTicks} ticks") }, false)
        s.sendFeedback({ label("Scan radius", "${partyConfig.searchRadius} blocks") }, false)
        s.sendFeedback({ label("Teleport distance", "${partyConfig.teleportDistance} blocks") }, false)

        if (workers.isEmpty()) {
            s.sendFeedback({ info("  No party Pokémon currently working.") }, false)
            return 1
        }

        val byPlayer = workers.values.groupBy { it.owner.name.string }
        for ((playerName, entries) in byPlayer) {
            s.sendFeedback({ Text.empty() }, false)
            s.sendFeedback({ Text.literal("  §b$playerName §7(${entries.size} Pokémon):") }, false)
            val activeJobs = WorkerDispatcher.getActiveJobsSnapshot()
            for (entry in entries) {
                val species = entry.pokemonEntity.pokemon.species.name
                val job = activeJobs[entry.pokemonId] ?: "idle"
                val shortId = entry.pokemonId.toString().take(8)
                s.sendFeedback({
                    Text.literal("    §e$species§7(§8$shortId§7) → §a$job")
                }, false)
            }
        }
        return 1
    }
}
