/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.fabric

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.commands.CobbleworkersCommand
import accieo.cobbleworkers.fabric.integration.FabricIntegrationHelper
import accieo.cobbleworkers.integration.CobbleworkersIntegrationHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

/**
 * Fabric entrypoint.
 */
object CobbleworkersFabric : ModInitializer {
    override fun onInitialize() {
        Cobbleworkers.init()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CobbleworkersCommand.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            val integrationHandler = CobbleworkersIntegrationHandler(FabricIntegrationHelper)
            integrationHandler.addIntegrations()
        }
    }
}