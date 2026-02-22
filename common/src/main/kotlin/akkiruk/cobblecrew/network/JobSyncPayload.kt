/*
 * Copyright (C) 2025-2026 Akkiruk
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package akkiruk.cobblecrew.network

import akkiruk.cobblecrew.CobbleCrew
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * S2C payload that sends job eligibility rules to connected clients.
 * Any client mod can register to receive this (e.g. CobbleDex).
 * Clients without a handler simply ignore it — no crash.
 */
class JobSyncPayload(val data: ByteArray) : CustomPayload {

    override fun getId(): CustomPayload.Id<JobSyncPayload> = TYPE

    companion object {
        val TYPE = CustomPayload.Id<JobSyncPayload>(
            Identifier.of(CobbleCrew.MODID, "job_sync")
        )

        private const val MAX_PAYLOAD_SIZE = 1_048_576

        val CODEC: PacketCodec<PacketByteBuf, JobSyncPayload> = PacketCodec.of(
            { payload: JobSyncPayload, buf: PacketByteBuf -> buf.writeByteArray(payload.data) },
            { buf: PacketByteBuf -> JobSyncPayload(buf.readByteArray(MAX_PAYLOAD_SIZE)) }
        )
    }
}
