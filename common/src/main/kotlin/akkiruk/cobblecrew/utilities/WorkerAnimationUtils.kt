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
import net.minecraft.world.World
import java.util.UUID

/**
 * Plays Cobblemon's native Bedrock animations on working Pokémon.
 *
 * Calls `PokemonEntity.playAnimation(name, expressions)` which sends a
 * `PlayPosableAnimationPacket` to nearby clients. Missing animations
 * are silently ignored by Cobblemon, so any name is safe to attempt.
 *
 * Internally throttled so animations aren't spammed faster than once
 * per [ANIM_COOLDOWN_TICKS] per Pokémon.
 */
object WorkerAnimationUtils {

    private val lastAnimationTick = mutableMapOf<UUID, Long>()
    private const val ANIM_COOLDOWN_TICKS = 20L // 1 second between animations

    /**
     * Play the best available animation for the given [phase].
     * The first animation in the phase's chain is used — Cobblemon
     * silently skips animations that don't exist on the model.
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

        val anim = phase.animations.firstOrNull() ?: return
        entity.playAnimation(anim, emptyList())
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
        val anim = phase.animations.firstOrNull() ?: return
        entity.playAnimation(anim, emptyList())
    }

    fun cleanup(pokemonId: UUID) {
        lastAnimationTick.remove(pokemonId)
    }
}
