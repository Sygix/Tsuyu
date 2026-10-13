/*
 * Copyright (C) RikkaW and contributors
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: Apache-2.0
 * Modified by Sygix from Shizuku/manager/src/main/java/moe/shizuku/manager/adb/AdbClient.kt.
 */

package fr.sygix.tsuyu.adb

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class UnsupportedAdb : Exception("Legacy ADB endpoint required")

class AdbSession(private val input: InputStream, private val output: OutputStream, private val key: AdbKey) {
    private var checksum = true
    private var maxPayload = 4096
    private fun send(packet: AdbPacket) { output.write(packet.encode(checksum)); output.flush() }
    private fun receive(requireChecksum: Boolean = checksum) = AdbPacket.read(input, requireChecksum)
    fun authenticate(awaiting: () -> Unit) {
        send(AdbPacket(AdbPacket.CNXN, 0x01000001, AdbPacket.MAX_PAYLOAD, "host::\u0000".toByteArray()))
        var packet = receive(false)
        if (packet.command == AdbPacket.STLS) throw UnsupportedAdb()
        if (packet.command == AdbPacket.AUTH) {
            require(packet.arg0 == 1)
            send(AdbPacket(AdbPacket.AUTH, 2, 0, key.sign(packet.payload)))
            packet = receive(false)
            if (packet.command == AdbPacket.AUTH) {
                require(packet.arg0 == 1 && packet.payload.size == 20)
                awaiting()
                send(AdbPacket(AdbPacket.AUTH, 3, 0, key.publicKey()))
                packet = receive(false)
            }
        }
        require(packet.command == AdbPacket.CNXN) { "ADB authentication refused" }
        require(packet.arg1 in 1..AdbPacket.MAX_PAYLOAD) { "Invalid ADB negotiated payload" }
        checksum = packet.arg0 < 0x01000001
        maxPayload = packet.arg1
    }
    fun shell(command: String): String {
        val request = "shell:$command\u0000".toByteArray()
        require(request.size <= maxPayload)
        send(AdbPacket(AdbPacket.OPEN, 1, 0, request))
        val opened = receive()
        require(opened.arg1 == 1 && opened.arg0 > 0 && opened.command == AdbPacket.OKAY) { "ADB shell refused" }
        val remote = opened.arg0
        val bytes = ByteArrayOutputStream()
        while (true) {
            val packet = receive()
            require(packet.payload.size <= maxPayload) { "ADB negotiated payload exceeded" }
            require(packet.arg0 == remote && packet.arg1 == 1) { "Invalid ADB stream id" }
            when (packet.command) {
                AdbPacket.WRTE -> {
                    require(bytes.size() + packet.payload.size <= 64 * 1024) { "Excessive shell output" }
                    bytes.write(packet.payload)
                    send(AdbPacket(AdbPacket.OKAY, 1, remote))
                }
                AdbPacket.CLSE -> {
                    send(AdbPacket(AdbPacket.CLSE, 1, remote))
                    return bytes.toString(Charsets.UTF_8.name())
                }
                else -> error("Unexpected ADB stream packet")
            }
        }
    }
}
