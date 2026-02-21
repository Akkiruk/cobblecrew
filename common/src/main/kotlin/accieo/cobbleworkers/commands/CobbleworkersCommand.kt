/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.commands

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.api.CobbleworkersApi
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

object CobbleworkersCommand {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("cobbleworkers")
                .requires { it.hasPermissionLevel(2) }
                .then(
                    CommandManager.literal("debug")
                        .executes(::runDebugDump)
                )
        )
    }

    private fun runDebugDump(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        source.sendFeedback({ Text.literal("[Cobbleworkers] Generating diagnostic report...").formatted(Formatting.GRAY) }, false)

        CompletableFuture.runAsync {
            try {
                val report = CobbleworkersApi.generateDiagnosticReport()

                // Upload to mclo.gs
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
                            Text.literal("[Cobbleworkers] Diagnostic report uploaded: ").formatted(Formatting.GREEN)
                                .append(clickable)
                        }, false)
                    } else {
                        source.sendFeedback({
                            Text.literal("[Cobbleworkers] Upload failed — report saved to logs instead.").formatted(Formatting.RED)
                        }, false)
                        Cobbleworkers.LOGGER.info(report)
                    }
                }
            } catch (e: Exception) {
                Cobbleworkers.LOGGER.error("[Cobbleworkers] Debug dump failed", e)
                source.server.execute {
                    source.sendFeedback({
                        Text.literal("[Cobbleworkers] Debug dump failed: ${e.message}").formatted(Formatting.RED)
                    }, false)
                }
            }
        }

        return 1
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
                // Response: {"success":true,"id":"abc123","url":"https://mclo.gs/abc123"}
                val urlMatch = Regex(""""url"\s*:\s*"([^"]+)"""").find(response.body())
                urlMatch?.groupValues?.get(1)?.replace("\\/", "/")
            } else {
                Cobbleworkers.LOGGER.warn("[Cobbleworkers] mclo.gs returned status ${response.statusCode()}: ${response.body()}")
                null
            }
        } catch (e: Exception) {
            Cobbleworkers.LOGGER.warn("[Cobbleworkers] Failed to upload to mclo.gs: ${e.message}")
            null
        }
    }
}
