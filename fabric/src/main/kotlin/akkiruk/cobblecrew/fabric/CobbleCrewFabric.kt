/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.fabric

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.api.CobbleCrewApi
import akkiruk.cobblecrew.commands.CobbleCrewCommand
import akkiruk.cobblecrew.fabric.integration.FabricIntegrationHelper
import akkiruk.cobblecrew.integration.CobbleCrewIntegrationHandler
import akkiruk.cobblecrew.network.JobSyncPayload
import akkiruk.cobblecrew.network.JobSyncSerializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

/**
 * Fabric entrypoint.
 */
object CobbleCrewFabric : ModInitializer {
    override fun onInitialize() {
        CobbleCrew.init()

        // Register the job sync payload type (S2C)
        PayloadTypeRegistry.playS2C().register(JobSyncPayload.TYPE, JobSyncPayload.CODEC)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CobbleCrewCommand.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            val integrationHandler = CobbleCrewIntegrationHandler(FabricIntegrationHelper)
            integrationHandler.addIntegrations()
        }

        // Send job rules to every client that can receive them
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.execute {
                val player = handler.player
                if (!ServerPlayNetworking.canSend(player, JobSyncPayload.TYPE)) return@execute
                try {
                    val rules = CobbleCrewApi.getJobRules()
                    if (rules.isEmpty()) return@execute
                    val data = JobSyncSerializer.serialize(rules)
                    ServerPlayNetworking.send(player, JobSyncPayload(data))
                    CobbleCrew.LOGGER.info("Sent ${rules.size} job rules to ${player.name.string}")
                } catch (e: Exception) {
                    CobbleCrew.LOGGER.debug("Failed to send job rules to ${player.name.string}: ${e.message}")
                }
            }
        }
    }
}