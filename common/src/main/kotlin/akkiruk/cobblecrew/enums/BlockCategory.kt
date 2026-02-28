/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.enums

/**
 * Block-centric categories for the deferred scanner.
 * Multiple jobs can share a category (e.g. Logger and Tree Feller both use LOG).
 * One validator per category in BlockCategoryValidators.
 *
 * @param requiresExposedFace If true, the scanner excludes fully buried blocks (all 6 faces opaque).
 */
enum class BlockCategory(val requiresExposedFace: Boolean = false) {
    CONTAINER,

    // Cobblemon growables
    APRICORN, BERRY, MINT, TUMBLESTONE,

    // Vanilla/modded growables
    AMETHYST, NETHERWART,
    CROP_GRAIN, CROP_ROOT, CROP_BEET,
    SWEET_BERRY, PUMPKIN_MELON, COCOA, CHORUS, CAVE_VINE, DRIPLEAF,

    // Wood / plant structures
    LOG, BAMBOO, SUGAR_CANE, CACTUS,
    VINE, HONEY,

    // Stone / earth — these can be underground, require an exposed face to be reachable
    STONE(requiresExposedFace = true),
    IGNEOUS(requiresExposedFace = true),
    DEEPSLATE(requiresExposedFace = true),
    ORE(requiresExposedFace = true),
    DIRT(requiresExposedFace = true),
    SAND(requiresExposedFace = true),
    CLAY(requiresExposedFace = true),

    // Specialty
    ICE, SNOW_BLOCK, SCULK, CORAL, MOSS,
    MUSHROOM, FLOWER, LEAVES, VEGETATION,

    // Functional blocks
    FURNACE, BREWING_STAND, CAULDRON, COMPOSTER, SUSPICIOUS,

    // Fire
    FIRE,

    // Fluids / farmland
    WATER, LAVA, FARMLAND,

    // Growable (crops + saplings for Growth Accelerator)
    GROWABLE,

    // Aquatic (pending test)
    KELP, LILY_PAD, SEA_PICKLE,
}
