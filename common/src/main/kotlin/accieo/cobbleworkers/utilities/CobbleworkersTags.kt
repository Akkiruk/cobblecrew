/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Centralized tag references. Prefer these over hardcoded block/item sets
 * so that modded additions and datapack overrides are automatically supported.
 */
object CobbleworkersTags {

    object Blocks {
        // Cobblemon
        val APRICORNS = cobblemon("apricorns")
        val BERRIES = cobblemon("berries")
        val MINTS = cobblemon("mints")

        // Vanilla
        val SAND = vanilla("sand")
        val DIRT = vanilla("dirt")
        val SMALL_FLOWERS = vanilla("small_flowers")
        val TALL_FLOWERS = vanilla("tall_flowers")
        val FIRE = vanilla("fire")
        val SAPLINGS = vanilla("saplings")
        val LOGS = vanilla("logs")
        val LEAVES = vanilla("leaves")

        private fun cobblemon(path: String): TagKey<Block> =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", path))

        private fun vanilla(path: String): TagKey<Block> =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft", path))
    }

    object Items {
        // Vanilla
        val SAPLINGS = vanilla("saplings")
        val BANNERS = vanilla("banners")

        // Common tags (c: namespace — provided by Fabric API / NeoForge)
        val RAW_MATERIALS = common("raw_materials")

        private fun vanilla(path: String): TagKey<Item> =
            TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", path))

        private fun common(path: String): TagKey<Item> =
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", path))
    }
}
