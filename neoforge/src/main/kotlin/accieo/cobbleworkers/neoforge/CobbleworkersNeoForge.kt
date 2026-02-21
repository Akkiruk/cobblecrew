/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.neoforge

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.api.CobbleworkersApi
import accieo.cobbleworkers.commands.CobbleworkersCommand
import accieo.cobbleworkers.integration.CobbleworkersIntegrationHandler
import accieo.cobbleworkers.network.JobSyncPayload
import accieo.cobbleworkers.network.JobSyncSerializer
import accieo.cobbleworkers.neoforge.client.config.CobbleworkersModListScreen
import accieo.cobbleworkers.neoforge.integration.NeoForgeIntegrationHelper
import accieo.cobbleworkers.utilities.TmLoreEnricher
import net.minecraft.client.MinecraftClient
import net.minecraft.server.network.ServerPlayerEntity
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
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
@Mod(Cobbleworkers.MODID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object CobbleworkersNeoForge {
    init {
        Cobbleworkers.init()

        MOD_BUS.addListener(::onRegisterPayloadHandlers)

        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            CobbleworkersCommand.register(event.dispatcher)
        }

        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)

        // Enrich TM items with job info lore
        NeoForge.EVENT_BUS.addListener { _: ServerStartedEvent -> TmLoreEnricher.rebuildIndex() }
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post -> TmLoreEnricher.tick(event.server) }

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
        val registrar = event.registrar(Cobbleworkers.MODID).optional()
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
                val rules = CobbleworkersApi.getJobRules()
                if (rules.isEmpty()) return@execute
                val data = JobSyncSerializer.serialize(rules)
                PacketDistributor.sendToPlayer(player, JobSyncPayload(data))
                Cobbleworkers.LOGGER.info("Sent ${rules.size} job rules to ${player.name.string}")
            } catch (e: Exception) {
                Cobbleworkers.LOGGER.debug("Failed to send job rules to ${player.name.string}: ${e.message}")
            }
        }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        CobbleworkersModListScreen.registerModScreen()
    }

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        //
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        val integrationHandler = CobbleworkersIntegrationHandler(NeoForgeIntegrationHelper)
        integrationHandler.addIntegrations()
    }
}