/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.commands

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.api.CobbleCrewApi
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.CompletableFuture

internal object DebugCommands {

    fun runStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val cfg = CobbleCrewConfigHolder.config.debug
        s.sendFeedback({ header("Debug Logging") }, false)
        s.sendFeedback({ label("Master", if (cfg.enabled) "§aON" else "§cOFF") }, false)
        s.sendFeedback({ label("Verbose", if (cfg.verbose) "§aON" else "§cOFF") }, false)
        s.sendFeedback({ label("Filter", cfg.pokemonFilter.ifEmpty { "(none)" }) }, false)
        s.sendFeedback({ Text.empty() }, false)
        s.sendFeedback({ info("  Category toggles:") }, false)
        val toggles = mapOf(
            "PROFILE" to cfg.logProfile, "DISPATCH" to cfg.logDispatch,
            "NAVIGATION" to cfg.logNavigation, "HARVEST" to cfg.logHarvest,
            "DEPOSIT" to cfg.logDeposit, "PRODUCTION" to cfg.logProduction,
            "PROCESSING" to cfg.logProcessing, "PLACEMENT" to cfg.logPlacement,
            "DEFENSE" to cfg.logDefense, "SUPPORT" to cfg.logSupport,
            "SCANNER" to cfg.logScanner, "CLEANUP" to cfg.logCleanup,
        )
        for ((name, on) in toggles) {
            val icon = if (on) "§a✓" else "§c✗"
            s.sendFeedback({ Text.literal("    $icon §7$name") }, false)
        }
        return 1
    }

    fun runOn(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleCrewConfigHolder.config.debug.enabled = true
        saveConfig()
        ctx.source.sendFeedback({ success("Debug logging enabled.") }, true)
        return 1
    }

    fun runOff(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleCrewConfigHolder.config.debug.enabled = false
        saveConfig()
        ctx.source.sendFeedback({ success("Debug logging disabled.") }, true)
        return 1
    }

    fun runToggle(ctx: CommandContext<ServerCommandSource>): Int {
        val category = StringArgumentType.getString(ctx, "category").uppercase()
        val cfg = CobbleCrewConfigHolder.config.debug

        if (category == "ALL") {
            val allOn = listOf(
                cfg.logProfile, cfg.logDispatch, cfg.logNavigation, cfg.logHarvest,
                cfg.logDeposit, cfg.logProduction, cfg.logProcessing, cfg.logPlacement,
                cfg.logDefense, cfg.logSupport, cfg.logScanner, cfg.logCleanup
            ).all { it }
            val newState = !allOn
            cfg.logProfile = newState; cfg.logDispatch = newState
            cfg.logNavigation = newState; cfg.logHarvest = newState
            cfg.logDeposit = newState; cfg.logProduction = newState
            cfg.logProcessing = newState; cfg.logPlacement = newState
            cfg.logDefense = newState; cfg.logSupport = newState
            cfg.logScanner = newState; cfg.logCleanup = newState
            saveConfig()
            val state = if (newState) "ON" else "OFF"
            ctx.source.sendFeedback({ success("All debug categories set to $state.") }, true)
            return 1
        }

        val parsed = try { CobbleCrewDebugLogger.Category.valueOf(category) } catch (_: Exception) {
            ctx.source.sendFeedback({ error("Unknown category: $category") }, false)
            return 0
        }

        val newState = when (parsed) {
            CobbleCrewDebugLogger.Category.PROFILE -> { cfg.logProfile = !cfg.logProfile; cfg.logProfile }
            CobbleCrewDebugLogger.Category.DISPATCH -> { cfg.logDispatch = !cfg.logDispatch; cfg.logDispatch }
            CobbleCrewDebugLogger.Category.NAVIGATION -> { cfg.logNavigation = !cfg.logNavigation; cfg.logNavigation }
            CobbleCrewDebugLogger.Category.HARVEST -> { cfg.logHarvest = !cfg.logHarvest; cfg.logHarvest }
            CobbleCrewDebugLogger.Category.DEPOSIT -> { cfg.logDeposit = !cfg.logDeposit; cfg.logDeposit }
            CobbleCrewDebugLogger.Category.PRODUCTION -> { cfg.logProduction = !cfg.logProduction; cfg.logProduction }
            CobbleCrewDebugLogger.Category.PROCESSING -> { cfg.logProcessing = !cfg.logProcessing; cfg.logProcessing }
            CobbleCrewDebugLogger.Category.PLACEMENT -> { cfg.logPlacement = !cfg.logPlacement; cfg.logPlacement }
            CobbleCrewDebugLogger.Category.DEFENSE -> { cfg.logDefense = !cfg.logDefense; cfg.logDefense }
            CobbleCrewDebugLogger.Category.SUPPORT -> { cfg.logSupport = !cfg.logSupport; cfg.logSupport }
            CobbleCrewDebugLogger.Category.SCANNER -> { cfg.logScanner = !cfg.logScanner; cfg.logScanner }
            CobbleCrewDebugLogger.Category.CLEANUP -> { cfg.logCleanup = !cfg.logCleanup; cfg.logCleanup }
        }
        saveConfig()
        val state = if (newState) "ON" else "OFF"
        ctx.source.sendFeedback({ success("Debug category ${parsed.name} set to $state.") }, true)
        return 1
    }

    fun runVerbose(ctx: CommandContext<ServerCommandSource>): Int {
        val cfg = CobbleCrewConfigHolder.config.debug
        cfg.verbose = !cfg.verbose
        saveConfig()
        val state = if (cfg.verbose) "ON" else "OFF"
        ctx.source.sendFeedback({ success("Verbose debug mode set to $state.") }, true)
        return 1
    }

    fun runFilterSet(ctx: CommandContext<ServerCommandSource>): Int {
        val filter = StringArgumentType.getString(ctx, "filter")
        CobbleCrewConfigHolder.config.debug.pokemonFilter = filter
        saveConfig()
        ctx.source.sendFeedback({ success("Debug filter set to \"$filter\".") }, true)
        return 1
    }

    fun runFilterClear(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleCrewConfigHolder.config.debug.pokemonFilter = ""
        saveConfig()
        ctx.source.sendFeedback({ success("Debug filter cleared.") }, true)
        return 1
    }

    fun runDump(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        source.sendFeedback({ info("[CobbleCrew] Generating diagnostic report...") }, false)

        CompletableFuture.runAsync {
            try {
                val report = CobbleCrewApi.generateDiagnosticReport()
                val url = uploadToMclogs(report)
                source.server.execute {
                    if (url != null) {
                        val clickable = Text.literal(url).setStyle(
                            Style.EMPTY
                                .withColor(Formatting.AQUA)
                                .withUnderline(true)
                                .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to open")))
                        )
                        source.sendFeedback({
                            success("Diagnostic report uploaded: ").append(clickable)
                        }, false)
                    } else {
                        source.sendFeedback({ error("Upload failed — report saved to logs instead.") }, false)
                        CobbleCrew.LOGGER.info(report)
                    }
                }
            } catch (e: Exception) {
                CobbleCrew.LOGGER.error("[CobbleCrew] Debug dump failed", e)
                source.server.execute {
                    source.sendFeedback({ error("Debug dump failed: ${e.message}") }, false)
                }
            }
        }
        return 1
    }
}
