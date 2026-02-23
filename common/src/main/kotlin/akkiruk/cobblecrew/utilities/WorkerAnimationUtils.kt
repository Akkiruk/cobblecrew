/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.utilities

import akkiruk.cobblecrew.enums.WorkPhase
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World
import java.util.UUID

/**
 * Plays Cobblemon's native Bedrock animations on working Pokémon.
 *
 * Sends a [PlayPosableAnimationPacket] directly with the full fallback
 * chain so the client tries each animation in order and plays the first
 * one that actually exists on the Pokémon's model.
 *
 * Internally throttled so animations aren't spammed faster than once
 * per [ANIM_COOLDOWN_TICKS] per Pokémon.
 */
object WorkerAnimationUtils {

    private val lastAnimationTick = mutableMapOf<UUID, Long>()
    private const val ANIM_COOLDOWN_TICKS = 20L // 1 second between animations

    /**
     * Play the best available animation for the given [phase].
     * The full fallback chain is sent — the client picks the first
     * animation that exists on the model.
     */
    fun playWorkAnimation(
        entity: PokemonEntity,
        phase: WorkPhase,
        world: World,
    ) {
        val pokemonId = entity.pokemon.uuid
        val now = world.time
        if (now - (lastAnimationTick[pokemonId] ?: 0L) < ANIM_COOLDOWN_TICKS) return
        lastAnimationTick[pokemonId] = now
        sendAnimationPacket(entity, phase, world)
    }

    /**
     * Force-play an animation regardless of cooldown.
     * Used for important one-shot reactions (hostile spotted, work complete).
     */
    fun playImmediate(
        entity: PokemonEntity,
        phase: WorkPhase,
        world: World,
    ) {
        lastAnimationTick[entity.pokemon.uuid] = world.time
        sendAnimationPacket(entity, phase, world)
    }

    private fun sendAnimationPacket(entity: PokemonEntity, phase: WorkPhase, world: World) {
        if (phase.animations.isEmpty()) return
        val serverWorld = world as? ServerWorld ?: return
        // Pass full fallback chain — client picks the first animation that exists on the model
        val packet = PlayPosableAnimationPacket(entity.id, phase.animations.toSet(), emptyList())
        // Last param is exclusionCondition — returns true to EXCLUDE a player.
        // false = don't exclude anyone within range.
        packet.sendToPlayersAround(
            entity.x, entity.y, entity.z,
            128.0,
            serverWorld.registryKey
        ) { false }
    }

    fun cleanup(pokemonId: UUID) {
        lastAnimationTick.remove(pokemonId)
    }
}
