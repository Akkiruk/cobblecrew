/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.enums

/**
 * Distinct visual phases a working Pokémon can be in.
 * Each phase carries an ordered animation fallback chain — the first
 * animation that exists on the Pokémon's model will play.
 *
 * Cobblemon's `playAnimation()` silently no-ops for missing animations,
 * so it's safe to attempt any name.
 */
enum class WorkPhase(val animations: List<String>) {

    // -- Work actions --
    HARVESTING(listOf("physical", "cry")),
    PRODUCING(listOf("special", "happy", "cry")),
    PROCESSING(listOf("physical", "cry")),
    PLACING(listOf("physical", "cry")),
    ATTACKING(listOf("physical", "battle_cry", "angry")),
    DEBUFFING(listOf("special", "status", "angry")),
    HEALING(listOf("status", "happy")),
    ENVIRONMENTAL(listOf("special", "cry")),

    // -- Reactions --
    WORK_COMPLETE(listOf("happy", "cry")),
    DEPOSITING(listOf("physical", "cry")),
    DEPOSIT_SUCCESS(listOf("happy", "cry")),
    DEPOSIT_FAILED(listOf("sad", "unamused")),
    TARGET_LOST(listOf("shocked", "shock", "sad")),
    NO_TARGETS(listOf("unamused", "sad")),
    COOLDOWN_WAITING(listOf("sleep", "ground_idle")),

    // -- Combat-specific --
    HOSTILE_SPOTTED(listOf("angry", "battle_cry", "battle_idle")),
    COMBAT_READY(listOf("battle_idle", "pose")),

    // -- Idle --
    IDLE_AT_ORIGIN(listOf("ground_idle")),
    IDLE_BORED(listOf("unamused", "sleep", "ground_idle")),
}
