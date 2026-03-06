/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.commands

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.config.CobbleCrewConfig
import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

internal fun header(text: String): MutableText =
    Text.literal("═══ $text ═══").formatted(Formatting.GOLD)

internal fun info(text: String): MutableText =
    Text.literal(text).formatted(Formatting.GRAY)

internal fun success(text: String): MutableText =
    Text.literal("[CobbleCrew] $text").formatted(Formatting.GREEN)

internal fun error(text: String): MutableText =
    Text.literal("[CobbleCrew] $text").formatted(Formatting.RED)

internal fun label(key: String, value: String): MutableText =
    Text.literal("  $key: ").formatted(Formatting.AQUA)
        .append(Text.literal(value).formatted(Formatting.WHITE))

internal fun bullet(text: String, color: Formatting = Formatting.WHITE): MutableText =
    Text.literal("  • ").formatted(Formatting.DARK_GRAY)
        .append(Text.literal(text).formatted(color))

internal fun saveConfig() {
    try {
        AutoConfig.getConfigHolder(CobbleCrewConfig::class.java).save()
    } catch (e: Exception) {
        CobbleCrew.LOGGER.error("[CobbleCrew] Failed to save config: ${e.message}")
    }
}

internal fun uploadToMclogs(content: String): String? {
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
