/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import accieo.cobbleworkers.Cobbleworkers
import accieo.cobbleworkers.api.CobbleworkersApi
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Scans player inventories for TMCraft TM/Tutor/Egg/Star move items
 * and adds lore lines showing which Cobbleworkers jobs the move enables.
 * Fully server-side — the vanilla client renders lore automatically.
 */
object TmLoreEnricher {

    private const val SCAN_INTERVAL = 100 // 5 seconds
    private const val MARKER = "\u00a78[\u00a7dCobbleworkers\u00a78]"

    private var moveIndex: Map<String, List<CobbleworkersApi.MoveJobEntry>> = emptyMap()
    private var indexBuilt = false

    private val TM_PREFIXES = listOf("tmcraft:tm_", "tmcraft:tutor_", "tmcraft:egg_", "tmcraft:star_")

    fun rebuildIndex() {
        moveIndex = CobbleworkersApi.getMoveToJobsIndex()
        indexBuilt = true
        Cobbleworkers.LOGGER.info("[Cobbleworkers] TM lore index: ${moveIndex.size} moves mapped to jobs")
    }

    fun tick(server: MinecraftServer) {
        if (server.ticks % SCAN_INTERVAL != 0) return
        if (!indexBuilt) rebuildIndex()
        if (moveIndex.isEmpty()) return

        for (player in server.playerManager.playerList) {
            var dirty = false
            val inv = player.inventory
            for (i in 0 until inv.size()) {
                val stack = inv.getStack(i)
                if (stack.isEmpty) continue
                if (enrichStack(stack)) dirty = true
            }
            if (dirty) {
                player.currentScreenHandler.sendContentUpdates()
            }
        }
    }

    private fun enrichStack(stack: ItemStack): Boolean {
        val itemId = Registries.ITEM.getId(stack.item).toString()
        val move = extractMove(itemId) ?: return false
        val jobs = moveIndex[move] ?: return false

        val existing = stack.get(DataComponentTypes.LORE)
        if (existing != null && existing.lines.isNotEmpty()) {
            val first = existing.lines[0].string
            if (first.contains("Cobbleworkers")) return false
        }

        val lines = mutableListOf<Text>()
        lines.add(noItalic(MARKER, Formatting.DARK_GRAY))

        val (singleJobs, comboJobs) = jobs.partition { !it.isCombo }

        for (job in singleJobs) {
            lines.add(noItalic(" \u2726 ${job.displayName}", Formatting.GREEN))
        }

        for (combo in comboJobs) {
            val others = combo.allRequiredMoves
                .filter { it != move }
                .joinToString(" + ") { formatMoveName(it) }
            lines.add(noItalic(" \u2726 ${combo.displayName}", Formatting.GOLD))
            lines.add(noItalic("   also needs: $others", Formatting.GRAY))
        }

        stack.set(DataComponentTypes.LORE, LoreComponent(lines))
        return true
    }

    private fun extractMove(itemId: String): String? {
        for (prefix in TM_PREFIXES) {
            if (itemId.startsWith(prefix)) return itemId.removePrefix(prefix)
        }
        return null
    }

    private fun formatMoveName(move: String): String =
        move.replaceFirstChar { it.uppercase() }

    private fun noItalic(text: String, color: Formatting): MutableText =
        Text.literal(text).setStyle(Style.EMPTY.withItalic(false).withColor(color))
}
