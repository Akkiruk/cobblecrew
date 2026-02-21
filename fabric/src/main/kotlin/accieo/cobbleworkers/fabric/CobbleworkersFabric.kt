/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.fabric

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.api.CobbleworkersApi
import accieo.cobbleworkers.commands.CobbleworkersCommand
import accieo.cobbleworkers.fabric.integration.FabricIntegrationHelper
import accieo.cobbleworkers.integration.CobbleworkersIntegrationHandler
import accieo.cobbleworkers.network.JobSyncPayload
import accieo.cobbleworkers.network.JobSyncSerializer
import accieo.cobbleworkers.utilities.TmLoreEnricher
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

/**
 * Fabric entrypoint.
 */
object CobbleworkersFabric : ModInitializer {
    override fun onInitialize() {
        Cobbleworkers.init()

        // Register the job sync payload type (S2C)
        PayloadTypeRegistry.playS2C().register(JobSyncPayload.TYPE, JobSyncPayload.CODEC)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CobbleworkersCommand.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            val integrationHandler = CobbleworkersIntegrationHandler(FabricIntegrationHelper)
            integrationHandler.addIntegrations()
        }

        // Enrich TM items with job info lore
        ServerLifecycleEvents.SERVER_STARTED.register { _ -> TmLoreEnricher.rebuildIndex() }
        ServerTickEvents.END_SERVER_TICK.register { server -> TmLoreEnricher.tick(server) }

        // Send job rules to every client that can receive them
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            server.execute {
                val player = handler.player
                if (!ServerPlayNetworking.canSend(player, JobSyncPayload.TYPE)) return@execute
                try {
                    val rules = CobbleworkersApi.getJobRules()
                    if (rules.isEmpty()) return@execute
                    val data = JobSyncSerializer.serialize(rules)
                    ServerPlayNetworking.send(player, JobSyncPayload(data))
                    Cobbleworkers.LOGGER.info("Sent ${rules.size} job rules to ${player.name.string}")
                } catch (e: Exception) {
                    Cobbleworkers.LOGGER.debug("Failed to send job rules to ${player.name.string}: ${e.message}")
                }
            }
        }
    }
}