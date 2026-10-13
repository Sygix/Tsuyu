/*
 * Copyright (C) RikkaW and contributors
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: Apache-2.0
 * Modified by Sygix from Shizuku/manager/src/main/java/moe/shizuku/manager/adb/AdbMessage.kt.
 */

package fr.sygix.tsuyu.adb

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AdbPacket(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray = byteArrayOf()) {
    fun encode(checksum: Boolean = true): ByteArray = ByteBuffer.allocate(24 + payload.size)
        .order(ByteOrder.LITTLE_ENDIAN).putInt(command).putInt(arg0).putInt(arg1)
        .putInt(payload.size).putInt(if (checksum) sum(payload) else 0).putInt(command.inv()).put(payload).array()

    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val WRTE = 0x45545257
        const val CLSE = 0x45534c43
        const val STLS = 0x534c5453
        const val MAX_PAYLOAD = 1024 * 1024
        private fun sum(data: ByteArray) = data.sumOf { it.toInt() and 255 }
        fun read(input: InputStream, checksum: Boolean): AdbPacket {
            val stream = DataInputStream(input)
            val header = ByteArray(24).also(stream::readFully)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val length = buffer.int
            val expectedSum = buffer.int
            require(buffer.int == command.inv()) { "Invalid ADB magic" }
            require(length in 0..MAX_PAYLOAD) { "Invalid ADB length" }
            val payload = ByteArray(length).also(stream::readFully)
            require(!(checksum || expectedSum != 0) || sum(payload) == expectedSum) { "Invalid ADB checksum" }
            return AdbPacket(command, arg0, arg1, payload)
        }
    }
}
