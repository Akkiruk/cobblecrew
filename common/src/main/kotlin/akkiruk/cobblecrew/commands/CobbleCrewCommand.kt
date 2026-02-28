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
import akkiruk.cobblecrew.cache.CobbleCrewCacheManager
import akkiruk.cobblecrew.config.CobbleCrewConfig
import akkiruk.cobblecrew.config.CobbleCrewConfigHolder
import akkiruk.cobblecrew.config.JobConfigManager
import akkiruk.cobblecrew.jobs.PartyWorkerManager
import akkiruk.cobblecrew.jobs.WorkerDispatcher
import akkiruk.cobblecrew.jobs.WorkerRegistry
import akkiruk.cobblecrew.utilities.CobbleCrewDebugLogger
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture

object CobbleCrewCommand {

    // -- Text helpers --

    private fun header(text: String): MutableText =
        Text.literal("═══ $text ═══").formatted(Formatting.GOLD)

    private fun info(text: String): MutableText =
        Text.literal(text).formatted(Formatting.GRAY)

    private fun success(text: String): MutableText =
        Text.literal("[CobbleCrew] $text").formatted(Formatting.GREEN)

    private fun error(text: String): MutableText =
        Text.literal("[CobbleCrew] $text").formatted(Formatting.RED)

    private fun label(key: String, value: String): MutableText =
        Text.literal("  $key: ").formatted(Formatting.AQUA)
            .append(Text.literal(value).formatted(Formatting.WHITE))

    private fun bullet(text: String, color: Formatting = Formatting.WHITE): MutableText =
        Text.literal("  • ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal(text).formatted(color))

    // -- Suggestion providers --

    private val JOB_SUGGESTIONS = SuggestionProvider<ServerCommandSource> { _, builder ->
        JobConfigManager.allJobNames().filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private val CATEGORY_SUGGESTIONS = SuggestionProvider<ServerCommandSource> { _, builder ->
        val categories = listOf("gathering", "production", "processing", "placement", "defense", "support", "environmental", "logistics", "combo")
        categories.filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private val DEBUG_CATEGORY_SUGGESTIONS = SuggestionProvider<ServerCommandSource> { _, builder ->
        val cats = CobbleCrewDebugLogger.Category.entries.map { it.name.lowercase() } + "all"
        cats.filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private val PROFILE_UUID_SUGGESTIONS = SuggestionProvider<ServerCommandSource> { _, builder ->
        WorkerDispatcher.getProfilesSnapshot().forEach { (uuid, profile) ->
            val display = "${profile.species}(${uuid.toString().take(8)})"
            if (display.startsWith(builder.remaining, ignoreCase = true)
                || uuid.toString().startsWith(builder.remaining, ignoreCase = true)
            ) {
                builder.suggest(uuid.toString(), Text.literal(display))
            }
        }
        builder.buildFuture()
    }

    // -- Registration --

    private fun requiresOp() = { src: ServerCommandSource -> src.hasPermissionLevel(2) }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("cobblecrew")
                .executes(::runHelp)
                .then(literal("help").executes(::runHelp))

                // ── debug (view = anyone, mutate = op) ──
                .then(literal("debug")
                    .executes(::runDebugStatus)
                    .then(literal("dump").executes(::runDebugDump))
                    .then(literal("toggle").requires(requiresOp())
                        .then(argument("category", StringArgumentType.word())
                            .suggests(DEBUG_CATEGORY_SUGGESTIONS)
                            .executes(::runDebugToggle)))
                    .then(literal("verbose").requires(requiresOp()).executes(::runDebugVerbose))
                    .then(literal("filter").requires(requiresOp())
                        .executes(::runDebugFilterClear)
                        .then(argument("filter", StringArgumentType.greedyString())
                            .executes(::runDebugFilterSet)))
                    .then(literal("on").requires(requiresOp()).executes(::runDebugOn))
                    .then(literal("off").requires(requiresOp()).executes(::runDebugOff))
                )

                // ── jobs (list/info = anyone, enable/disable/reload = op) ──
                .then(literal("jobs")
                    .executes(::runJobsList)
                    .then(literal("list")
                        .executes(::runJobsList)
                        .then(argument("category", StringArgumentType.word())
                            .suggests(CATEGORY_SUGGESTIONS)
                            .executes(::runJobsListCategory)))
                    .then(literal("info")
                        .then(argument("job", StringArgumentType.word())
                            .suggests(JOB_SUGGESTIONS)
                            .executes(::runJobInfo)))
                    .then(literal("enable").requires(requiresOp())
                        .then(literal("all").executes { ctx -> runJobsSetAll(ctx, true) })
                        .then(argument("job", StringArgumentType.word())
                            .suggests(JOB_SUGGESTIONS)
                            .executes { ctx -> runJobSet(ctx, true) }))
                    .then(literal("disable").requires(requiresOp())
                        .then(literal("all").executes { ctx -> runJobsSetAll(ctx, false) })
                        .then(argument("job", StringArgumentType.word())
                            .suggests(JOB_SUGGESTIONS)
                            .executes { ctx -> runJobSet(ctx, false) }))
                    .then(literal("reload").requires(requiresOp()).executes(::runJobsReload))
                )

                // ── workers (view = anyone, reset = op) ──
                .then(literal("workers")
                    .executes(::runWorkersStatus)
                    .then(literal("list").executes(::runWorkersList))
                    .then(literal("status").executes(::runWorkersStatus))
                    .then(literal("reset").requires(requiresOp())
                        .executes(::runWorkersResetAll)
                        .then(argument("uuid", StringArgumentType.word())
                            .suggests(PROFILE_UUID_SUGGESTIONS)
                            .executes(::runWorkersReset)))
                )

                // ── config (view = anyone, set/reload = op) ──
                .then(literal("config")
                    .executes(::runConfigShow)
                    .then(literal("show").executes(::runConfigShow))
                    .then(literal("searchRadius").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(1, 10))
                            .executes(::runConfigSearchRadius)))
                    .then(literal("searchHeight").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(1, 8))
                            .executes(::runConfigSearchHeight)))
                    .then(literal("scanRate").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(10, 30))
                            .executes(::runConfigScanRate)))
                    .then(literal("scanCooldown").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(5, 120))
                            .executes(::runConfigScanCooldown)))
                    .then(literal("targetGrace").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(2, 30))
                            .executes(::runConfigTargetGrace)))
                    .then(literal("blacklistShort").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(5, 60))
                            .executes(::runConfigBlacklistShort)))
                    .then(literal("blacklistMedium").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(10, 120))
                            .executes(::runConfigBlacklistMedium)))
                    .then(literal("blacklistLong").requires(requiresOp())
                        .then(argument("value", IntegerArgumentType.integer(30, 600))
                            .executes(::runConfigBlacklistLong)))
                    .then(literal("reload").requires(requiresOp()).executes(::runConfigReload))
                )

                // ── cache (view = anyone, clear = op) ──
                .then(literal("cache")
                    .executes(::runCacheStatus)
                    .then(literal("status").executes(::runCacheStatus))
                    .then(literal("clear").requires(requiresOp())
                        .executes(::runCacheClearAll)
                        .then(argument("x", IntegerArgumentType.integer())
                            .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                    .executes(::runCacheClearPos)))))
                )

                // ── profiles (view = anyone, invalidate = op) ──
                .then(literal("profiles")
                    .executes(::runProfilesList)
                    .then(literal("list").executes(::runProfilesList))
                    .then(literal("info")
                        .then(argument("uuid", StringArgumentType.word())
                            .suggests(PROFILE_UUID_SUGGESTIONS)
                            .executes(::runProfileInfo)))
                    .then(literal("invalidate").requires(requiresOp()).executes(::runProfilesInvalidate))
                )

                // ── party (view party worker status, toggle per-player) ──
                .then(literal("party")
                    .executes(::runPartyStatus)
                    .then(literal("status").executes(::runPartyStatus))
                    .then(literal("toggle").executes(::runPartyToggle))
                )
        )
    }

    // ══════════════════════════════════════════
    //  HELP
    // ══════════════════════════════════════════

    private fun runHelp(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        s.sendFeedback({ header("CobbleCrew Commands") }, false)
        s.sendFeedback({ Text.empty() }, false)
        s.sendFeedback({ bullet("/cobblecrew debug", Formatting.YELLOW).append(info(" — Toggle logging, dump diagnostics")) }, false)
        s.sendFeedback({ bullet("/cobblecrew jobs", Formatting.YELLOW).append(info(" — List, inspect, enable/disable jobs")) }, false)
        s.sendFeedback({ bullet("/cobblecrew workers", Formatting.YELLOW).append(info(" — View active workers, reset assignments")) }, false)
        s.sendFeedback({ bullet("/cobblecrew config", Formatting.YELLOW).append(info(" — View/change general config")) }, false)
        s.sendFeedback({ bullet("/cobblecrew cache", Formatting.YELLOW).append(info(" — View/clear block scan caches")) }, false)
        s.sendFeedback({ bullet("/cobblecrew profiles", Formatting.YELLOW).append(info(" — View/invalidate Pokémon profiles")) }, false)
        s.sendFeedback({ bullet("/cobblecrew party", Formatting.YELLOW).append(info(" — View party worker status, toggle on/off")) }, false)
        return 1
    }

    // ══════════════════════════════════════════
    //  DEBUG
    // ══════════════════════════════════════════

    private fun runDebugStatus(ctx: CommandContext<ServerCommandSource>): Int {
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

    private fun runDebugOn(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleCrewConfigHolder.config.debug.enabled = true
        saveConfig()
        ctx.source.sendFeedback({ success("Debug logging enabled.") }, true)
        return 1
    }

    private fun runDebugOff(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleCrewConfigHolder.config.debug.enabled = false
        saveConfig()
        ctx.source.sendFeedback({ success("Debug logging disabled.") }, true)
        return 1
    }

    private fun runDebugToggle(ctx: CommandContext<ServerCommandSource>): Int {
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

    private fun runDebugVerbose(ctx: CommandContext<ServerCommandSource>): Int {
        val cfg = CobbleCrewConfigHolder.config.debug
        cfg.verbose = !cfg.verbose
        saveConfig()
        val state = if (cfg.verbose) "ON" else "OFF"
        ctx.source.sendFeedback({ success("Verbose debug mode set to $state.") }, true)
        return 1
    }

    private fun runDebugFilterSet(ctx: CommandContext<ServerCommandSource>): Int {
        val filter = StringArgumentType.getString(ctx, "filter")
        CobbleCrewConfigHolder.config.debug.pokemonFilter = filter
        saveConfig()
        ctx.source.sendFeedback({ success("Debug filter set to \"$filter\".") }, true)
        return 1
    }

    private fun runDebugFilterClear(ctx: CommandContext<ServerCommandSource>): Int {
        CobbleCrewConfigHolder.config.debug.pokemonFilter = ""
        saveConfig()
        ctx.source.sendFeedback({ success("Debug filter cleared.") }, true)
        return 1
    }

    private fun runDebugDump(ctx: CommandContext<ServerCommandSource>): Int {
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

    // ══════════════════════════════════════════
    //  JOBS
    // ══════════════════════════════════════════

    private fun runJobsList(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val byCategory = JobConfigManager.allJobsByCategory()
        val totalJobs = byCategory.values.sumOf { it.size }

        s.sendFeedback({ header("Jobs ($totalJobs total)") }, false)

        for ((category, jobs) in byCategory.entries.sortedBy { it.key }) {
            val enabledCount = jobs.count { JobConfigManager.isEnabled(it) }
            s.sendFeedback({ Text.empty() }, false)
            s.sendFeedback({
                Text.literal("  [$category] ").formatted(Formatting.YELLOW)
                    .append(Text.literal("($enabledCount/${jobs.size} enabled)").formatted(Formatting.GRAY))
            }, false)
            for (job in jobs.sorted()) {
                val enabled = JobConfigManager.isEnabled(job)
                val icon = if (enabled) "§a●" else "§c○"
                s.sendFeedback({ Text.literal("    $icon §f$job") }, false)
            }
        }
        return 1
    }

    private fun runJobsListCategory(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val category = StringArgumentType.getString(ctx, "category").lowercase()
        val byCategory = JobConfigManager.allJobsByCategory()
        val jobs = byCategory[category]

        if (jobs == null) {
            s.sendFeedback({ error("Unknown category: $category") }, false)
            s.sendFeedback({ info("Available: ${byCategory.keys.sorted().joinToString(", ")}") }, false)
            return 0
        }

        val enabledCount = jobs.count { JobConfigManager.isEnabled(it) }
        s.sendFeedback({ header("$category ($enabledCount/${jobs.size} enabled)") }, false)
        for (job in jobs.sorted()) {
            val config = JobConfigManager.get(job)
            val icon = if (config.enabled) "§a●" else "§c○"
            val cooldown = "${config.cooldownSeconds}s"
            s.sendFeedback({
                Text.literal("  $icon §f$job §7(${cooldown})")
            }, false)
        }
        return 1
    }

    private fun runJobInfo(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val jobName = StringArgumentType.getString(ctx, "job")
        val config = JobConfigManager.get(jobName)
        val worker = WorkerRegistry.workers.find { it.name == jobName }

        if (worker == null) {
            s.sendFeedback({ error("Unknown job: $jobName") }, false)
            return 0
        }

        val rule = CobbleCrewApi.getJobRules().find { it.id == jobName }

        s.sendFeedback({ header(jobName) }, false)
        if (rule != null && rule.description.isNotEmpty()) {
            s.sendFeedback({ Text.literal("  ${rule.description}").formatted(Formatting.ITALIC, Formatting.GRAY) }, false)
        }
        s.sendFeedback({ Text.empty() }, false)
        s.sendFeedback({ label("Enabled", if (config.enabled) "§aYes" else "§cNo") }, false)
        s.sendFeedback({ label("Priority", worker.priority.name) }, false)
        s.sendFeedback({ label("Category", worker.targetCategory?.name ?: "(none)") }, false)
        s.sendFeedback({ label("Cooldown", "${config.cooldownSeconds}s") }, false)

        if (config.qualifyingMoves.isNotEmpty()) {
            s.sendFeedback({ label("Moves", config.qualifyingMoves.joinToString(", ")) }, false)
        }
        if (config.fallbackType.isNotEmpty()) {
            s.sendFeedback({ label("Type", config.fallbackType) }, false)
        }
        if (config.fallbackSpecies.isNotEmpty()) {
            s.sendFeedback({ label("Species", config.fallbackSpecies.joinToString(", ")) }, false)
        }
        if (!config.requiredAbility.isNullOrEmpty()) {
            s.sendFeedback({ label("Ability", config.requiredAbility) }, false)
        }
        config.effectDurationSeconds?.let { s.sendFeedback({ label("Effect Duration", "${it}s") }, false) }
        config.effectAmplifier?.let { s.sendFeedback({ label("Effect Amplifier", it.toString()) }, false) }
        config.radius?.let { s.sendFeedback({ label("Radius", "${it} blocks") }, false) }
        config.replant?.let { s.sendFeedback({ label("Replant", if (it) "Yes" else "No") }, false) }

        return 1
    }

    private fun runJobSet(ctx: CommandContext<ServerCommandSource>, enabled: Boolean): Int {
        val jobName = StringArgumentType.getString(ctx, "job")
        if (!JobConfigManager.setEnabled(jobName, enabled)) {
            ctx.source.sendFeedback({ error("Unknown job: $jobName") }, false)
            return 0
        }
        WorkerDispatcher.invalidateProfiles()
        val state = if (enabled) "enabled" else "disabled"
        ctx.source.sendFeedback({ success("Job \"$jobName\" $state. Profiles invalidated.") }, true)
        return 1
    }

    private fun runJobsSetAll(ctx: CommandContext<ServerCommandSource>, enabled: Boolean): Int {
        var count = 0
        for (name in JobConfigManager.allJobNames()) {
            if (JobConfigManager.setEnabled(name, enabled)) count++
        }
        WorkerDispatcher.invalidateProfiles()
        val state = if (enabled) "enabled" else "disabled"
        ctx.source.sendFeedback({ success("$count jobs $state. Profiles invalidated.") }, true)
        return 1
    }

    private fun runJobsReload(ctx: CommandContext<ServerCommandSource>): Int {
        JobConfigManager.reload()
        WorkerDispatcher.invalidateProfiles()
        val count = JobConfigManager.allJobNames().size
        ctx.source.sendFeedback({ success("Reloaded $count job configs from disk. Profiles invalidated.") }, true)
        return 1
    }

    // ══════════════════════════════════════════
    //  WORKERS
    // ══════════════════════════════════════════

    private fun runWorkersStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val active = WorkerDispatcher.getActiveJobsSnapshot()
        val profileCount = WorkerDispatcher.getCachedProfileCount()

        s.sendFeedback({ header("Worker Status") }, false)
        s.sendFeedback({ label("Active workers", active.size.toString()) }, false)
        s.sendFeedback({ label("Party workers", PartyWorkerManager.getActivePartyWorkerCount().toString()) }, false)
        s.sendFeedback({ label("Cached profiles", profileCount.toString()) }, false)
        s.sendFeedback({ label("Registered jobs", WorkerRegistry.workers.size.toString()) }, false)
        s.sendFeedback({ label("Tracked pastures", CobbleCrewCacheManager.getPastureCount().toString()) }, false)

        if (active.isNotEmpty()) {
            val distribution = active.values.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
            s.sendFeedback({ Text.empty() }, false)
            s.sendFeedback({ info("  Job distribution:") }, false)
            for ((job, count) in distribution) {
                s.sendFeedback({ Text.literal("    §e$count§7x §f$job") }, false)
            }
        }
        return 1
    }

    private fun runWorkersList(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val active = WorkerDispatcher.getActiveJobsSnapshot()
        val profiles = WorkerDispatcher.getProfilesSnapshot()

        s.sendFeedback({ header("Active Workers (${active.size})") }, false)

        if (active.isEmpty()) {
            s.sendFeedback({ info("  No active workers.") }, false)
            return 1
        }

        for ((uuid, jobName) in active) {
            val profile = profiles[uuid]
            val species = profile?.species ?: "unknown"
            val shortId = uuid.toString().take(8)
            s.sendFeedback({
                Text.literal("  §e$species§7(§8$shortId§7) → §a$jobName")
            }, false)
        }
        return 1
    }

    private fun runWorkersReset(ctx: CommandContext<ServerCommandSource>): Int {
        val uuidStr = StringArgumentType.getString(ctx, "uuid")
        val uuid = try { UUID.fromString(uuidStr) } catch (_: Exception) {
            ctx.source.sendFeedback({ error("Invalid UUID: $uuidStr") }, false)
            return 0
        }
        WorkerDispatcher.resetAssignment(uuid)
        ctx.source.sendFeedback({ success("Reset assignment for $uuidStr.") }, true)
        return 1
    }

    private fun runWorkersResetAll(ctx: CommandContext<ServerCommandSource>): Int {
        val count = WorkerDispatcher.getActiveWorkerCount()
        WorkerDispatcher.resetAllAssignments()
        ctx.source.sendFeedback({ success("Reset $count worker assignments.") }, true)
        return 1
    }

    // ══════════════════════════════════════════
    //  PARTY
    // ══════════════════════════════════════════

    private fun runPartyToggle(ctx: CommandContext<ServerCommandSource>): Int {
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

    private fun runPartyStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val partyConfig = CobbleCrewConfigHolder.config.party
        val workers = PartyWorkerManager.getActivePartyWorkers()

        s.sendFeedback({ header("Party Workers") }, false)
        s.sendFeedback({ label("Enabled", if (partyConfig.enabled) "§aYes" else "§cNo") }, false)

        // Show per-player opt-out status if run by a player
        val player = s.player
        if (player != null) {
            val personal = PartyWorkerManager.isPartyEnabled(player.uuid)
            s.sendFeedback({ label("Your party jobs", if (personal) "§aOn" else "§cOff §7(/cobblecrew party toggle)") }, false)
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

    // ══════════════════════════════════════════
    //  CONFIG
    // ══════════════════════════════════════════

    private fun runConfigShow(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val cfg = CobbleCrewConfigHolder.config.general
        s.sendFeedback({ header("General Config") }, false)
        s.sendFeedback({ label("Search Radius", "${cfg.searchRadius} chunks") }, false)
        s.sendFeedback({ label("Search Height", "${cfg.searchHeight} chunks") }, false)
        s.sendFeedback({ label("Scan Rate", "${cfg.blocksScannedPerTick} blocks/tick") }, false)
        s.sendFeedback({ label("Scan Cooldown", "${cfg.scanCooldownSeconds}s") }, false)
        s.sendFeedback({ label("Target Grace", "${cfg.targetGracePeriodSeconds}s") }, false)
        s.sendFeedback({ label("Blacklist (1 fail)", "${cfg.blacklistShortSeconds}s") }, false)
        s.sendFeedback({ label("Blacklist (2 fails)", "${cfg.blacklistMediumSeconds}s") }, false)
        s.sendFeedback({ label("Blacklist (3+ fails)", "${cfg.blacklistLongSeconds}s") }, false)
        s.sendFeedback({ Text.empty() }, false)
        s.sendFeedback({ info("  Use /cobblecrew config <key> <value> to change.") }, false)
        s.sendFeedback({ info("  Keys: searchRadius, searchHeight, scanRate, scanCooldown,") }, false)
        s.sendFeedback({ info("        targetGrace, blacklistShort, blacklistMedium, blacklistLong") }, false)
        return 1
    }

    private fun runConfigSearchRadius(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.searchRadius = value
        saveConfig()
        CobbleCrewCacheManager.clearAll()
        ctx.source.sendFeedback({ success("Search radius set to $value. Caches cleared for rescan.") }, true)
        return 1
    }

    private fun runConfigSearchHeight(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.searchHeight = value
        saveConfig()
        CobbleCrewCacheManager.clearAll()
        ctx.source.sendFeedback({ success("Search height set to $value. Caches cleared for rescan.") }, true)
        return 1
    }

    private fun runConfigScanRate(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.blocksScannedPerTick = value
        saveConfig()
        ctx.source.sendFeedback({ success("Scan rate set to $value blocks/tick.") }, true)
        return 1
    }

    private fun runConfigScanCooldown(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.scanCooldownSeconds = value
        saveConfig()
        ctx.source.sendFeedback({ success("Scan cooldown set to ${value}s.") }, true)
        return 1
    }

    private fun runConfigTargetGrace(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.targetGracePeriodSeconds = value
        saveConfig()
        ctx.source.sendFeedback({ success("Target grace period set to ${value}s.") }, true)
        return 1
    }

    private fun runConfigBlacklistShort(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.blacklistShortSeconds = value
        saveConfig()
        ctx.source.sendFeedback({ success("Blacklist (1 fail) set to ${value}s.") }, true)
        return 1
    }

    private fun runConfigBlacklistMedium(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.blacklistMediumSeconds = value
        saveConfig()
        ctx.source.sendFeedback({ success("Blacklist (2 fails) set to ${value}s.") }, true)
        return 1
    }

    private fun runConfigBlacklistLong(ctx: CommandContext<ServerCommandSource>): Int {
        val value = IntegerArgumentType.getInteger(ctx, "value")
        CobbleCrewConfigHolder.config.general.blacklistLongSeconds = value
        saveConfig()
        ctx.source.sendFeedback({ success("Blacklist (3+ fails) set to ${value}s.") }, true)
        return 1
    }

    private fun runConfigReload(ctx: CommandContext<ServerCommandSource>): Int {
        try {
            AutoConfig.getConfigHolder(CobbleCrewConfig::class.java).load()
            CobbleCrewConfigHolder.config = AutoConfig.getConfigHolder(CobbleCrewConfig::class.java).config
            ctx.source.sendFeedback({ success("Config reloaded from disk.") }, true)
        } catch (e: Exception) {
            ctx.source.sendFeedback({ error("Failed to reload config: ${e.message}") }, false)
        }
        return 1
    }

    // ══════════════════════════════════════════
    //  CACHE
    // ══════════════════════════════════════════

    private fun runCacheStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val status = CobbleCrewCacheManager.getPastureStatus()

        s.sendFeedback({ header("Block Cache (${status.size} pastures)") }, false)

        if (status.isEmpty()) {
            s.sendFeedback({ info("  No pastures being tracked.") }, false)
            return 1
        }

        for ((pos, categories) in status) {
            val totalBlocks = categories.values.sum()
            s.sendFeedback({
                Text.literal("  §e(${pos.x}, ${pos.y}, ${pos.z})§7 — §f$totalBlocks§7 targets in §f${categories.size}§7 categories")
            }, false)
            if (categories.size <= 8) {
                for ((cat, count) in categories.entries.sortedByDescending { it.value }) {
                    s.sendFeedback({ Text.literal("    §7$cat: §f$count") }, false)
                }
            }
        }
        return 1
    }

    private fun runCacheClearAll(ctx: CommandContext<ServerCommandSource>): Int {
        val count = CobbleCrewCacheManager.getPastureCount()
        CobbleCrewCacheManager.clearAll()
        ctx.source.sendFeedback({ success("Cleared all caches ($count pastures). Will rescan automatically.") }, true)
        return 1
    }

    private fun runCacheClearPos(ctx: CommandContext<ServerCommandSource>): Int {
        val x = IntegerArgumentType.getInteger(ctx, "x")
        val y = IntegerArgumentType.getInteger(ctx, "y")
        val z = IntegerArgumentType.getInteger(ctx, "z")
        val pos = BlockPos(x, y, z)
        CobbleCrewCacheManager.removePasture(pos)
        ctx.source.sendFeedback({ success("Cleared cache for pasture at ($x, $y, $z). Will rescan automatically.") }, true)
        return 1
    }

    // ══════════════════════════════════════════
    //  PROFILES
    // ══════════════════════════════════════════

    private fun runProfilesList(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val profiles = WorkerDispatcher.getProfilesSnapshot()

        s.sendFeedback({ header("Pokémon Profiles (${profiles.size})") }, false)

        if (profiles.isEmpty()) {
            s.sendFeedback({ info("  No cached profiles.") }, false)
            return 1
        }

        for ((uuid, profile) in profiles) {
            val total = profile.allEligible().size
            val shortId = uuid.toString().take(8)
            val active = WorkerDispatcher.getActiveJobsSnapshot()[uuid]
            val activeText = if (active != null) " §7→ §a$active" else " §7(idle)"

            s.sendFeedback({
                Text.literal("  §e${profile.species}§7(§8$shortId§7) §f$total§7 eligible jobs$activeText")
            }, false)
        }
        return 1
    }

    private fun runProfileInfo(ctx: CommandContext<ServerCommandSource>): Int {
        val s = ctx.source
        val uuidStr = StringArgumentType.getString(ctx, "uuid")
        val uuid = try { UUID.fromString(uuidStr) } catch (_: Exception) {
            ctx.source.sendFeedback({ error("Invalid UUID: $uuidStr") }, false)
            return 0
        }

        val profile = WorkerDispatcher.getProfilesSnapshot()[uuid]
        if (profile == null) {
            ctx.source.sendFeedback({ error("No cached profile for UUID $uuidStr.") }, false)
            return 0
        }

        s.sendFeedback({ header(profile.species) }, false)
        s.sendFeedback({ label("UUID", uuid.toString()) }, false)
        s.sendFeedback({ label("Moves", profile.moves.joinToString(", ").ifEmpty { "(none)" }) }, false)
        s.sendFeedback({ label("Types", profile.types.joinToString(", ").ifEmpty { "(none)" }) }, false)
        s.sendFeedback({ label("Ability", profile.ability) }, false)

        val active = WorkerDispatcher.getActiveJobsSnapshot()[uuid]
        s.sendFeedback({ label("Active Job", active ?: "(idle)") }, false)

        s.sendFeedback({ Text.empty() }, false)

        fun showTier(name: String, jobs: List<akkiruk.cobblecrew.interfaces.Worker>) {
            if (jobs.isEmpty()) return
            s.sendFeedback({
                Text.literal("  §6$name§7 (${jobs.size}):")
            }, false)
            for (w in jobs) {
                val enabled = JobConfigManager.isEnabled(w.name)
                val icon = if (enabled) "§a●" else "§c○"
                s.sendFeedback({ Text.literal("    $icon §f${w.name}") }, false)
            }
        }

        showTier("COMBO", profile.comboEligible)
        showTier("MOVE", profile.moveEligible)
        showTier("SPECIES", profile.speciesEligible)
        showTier("TYPE", profile.typeEligible)
        return 1
    }

    private fun runProfilesInvalidate(ctx: CommandContext<ServerCommandSource>): Int {
        val count = WorkerDispatcher.getCachedProfileCount()
        WorkerDispatcher.invalidateProfiles()
        ctx.source.sendFeedback({ success("Invalidated $count profiles. Will rebuild on next tick.") }, true)
        return 1
    }

    // ══════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════

    private fun saveConfig() {
        try {
            AutoConfig.getConfigHolder(CobbleCrewConfig::class.java).save()
        } catch (e: Exception) {
            CobbleCrew.LOGGER.error("[CobbleCrew] Failed to save config: ${e.message}")
        }
    }

    private fun uploadToMclogs(content: String): String? {
        return try {
            val client = HttpClient.newBuilder().build()
            val body = "content=" + URLEncoder.encode(content, StandardCharsets.UTF_8)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mclo.gs/1/log"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val urlMatch = Regex(""""url"\s*:\s*"([^"]+)"""").find(response.body())
                urlMatch?.groupValues?.get(1)?.replace("\\/", "/")
            } else {
                CobbleCrew.LOGGER.warn("[CobbleCrew] mclo.gs returned status ${response.statusCode()}: ${response.body()}")
                null
            }
        } catch (e: Exception) {
            CobbleCrew.LOGGER.warn("[CobbleCrew] Failed to upload to mclo.gs: ${e.message}")
            null
        }
    }
}
