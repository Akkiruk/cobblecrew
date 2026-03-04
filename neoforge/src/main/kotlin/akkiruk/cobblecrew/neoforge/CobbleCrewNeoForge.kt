/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.neoforge

import akkiruk.cobblecrew.CobbleCrew
import akkiruk.cobblecrew.api.CobbleCrewApi
import akkiruk.cobblecrew.commands.CobbleCrewCommand
import akkiruk.cobblecrew.integration.CobbleCrewIntegrationHandler
import akkiruk.cobblecrew.jobs.PartyWorkerManager
import akkiruk.cobblecrew.jobs.PastureWorkerManager
import akkiruk.cobblecrew.jobs.WorkerDispatcher
import akkiruk.cobblecrew.network.JobSyncPayload
import akkiruk.cobblecrew.network.JobSyncSerializer
import akkiruk.cobblecrew.neoforge.client.config.CobbleCrewModListScreen
import akkiruk.cobblecrew.neoforge.integration.NeoForgeIntegrationHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.server.network.ServerPlayerEntity
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent

/**
 * NeoForge entrypoint.
 */
@Mod(CobbleCrew.MODID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object CobbleCrewNeoForge {
    init {
        CobbleCrew.init()

        MOD_BUS.addListener(::onRegisterPayloadHandlers)

        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            CobbleCrewCommand.register(event.dispatcher)
        }

        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)

        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post ->
            event.server.let {
                WorkerDispatcher.tickMaintenance(it.overworld.time)
                PartyWorkerManager.tick(it)
            }
        }

        NeoForge.EVENT_BUS.addListener { _: net.neoforged.neoforge.event.server.ServerStoppingEvent ->
            PastureWorkerManager.clearAll()
        }

        NeoForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedOutEvent ->
            (event.entity as? ServerPlayerEntity)?.let {
                PartyWorkerManager.cleanupPlayer(it.uuid)
            }
        }

        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)
                MinecraftClient.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)
            }
        )
    }

    private fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CobbleCrew.MODID).optional()
        registrar.playToClient(
            JobSyncPayload.TYPE,
            JobSyncPayload.CODEC
        ) { _, _ ->
            // Server-side mod — client handler is a no-op here.
            // Any client mod (e.g. CobbleDex) registers its own receiver.
        }
    }

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player !is ServerPlayerEntity) return
        player.server?.execute {
            try {
                val rules = CobbleCrewApi.getJobRules()
                if (rules.isEmpty()) return@execute
                val data = JobSyncSerializer.serialize(rules)
                PacketDistributor.sendToPlayer(player, JobSyncPayload(data))
                CobbleCrew.LOGGER.info("Sent ${rules.size} job rules to ${player.name.string}")
            } catch (e: Exception) {
                CobbleCrew.LOGGER.debug("Failed to send job rules to ${player.name.string}: ${e.message}")
            }
        }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        CobbleCrewModListScreen.registerModScreen()
    }

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        //
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        val integrationHandler = CobbleCrewIntegrationHandler(NeoForgeIntegrationHelper)
        integrationHandler.addIntegrations()
    }
}