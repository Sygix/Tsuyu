/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.adb

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class AdbTest {
    @Test fun fragmentedPacketsRoundTrip() {
        val packet = AdbPacket(AdbPacket.WRTE, 4, 1, byteArrayOf(1, 2, -1))
        val stream = object : ByteArrayInputStream(packet.encode()) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
        }
        assertArrayEquals(packet.payload, AdbPacket.read(stream, true).payload)
    }
    @Test fun invalidMagicLengthChecksumAndEofAreRejected() {
        val encoded = AdbPacket(AdbPacket.WRTE, 1, 1, byteArrayOf(1)).encode()
        for (index in listOf(12, 16, 20)) {
            val corrupt = encoded.clone()
            corrupt[index] = (corrupt[index].toInt() xor 127).toByte()
            assertThrows(Exception::class.java) { AdbPacket.read(ByteArrayInputStream(corrupt), true) }
        }
        assertThrows(Exception::class.java) { AdbPacket.read(ByteArrayInputStream(encoded.copyOf(23)), true) }
    }
    @Test fun negotiatedModernChecksumCanBeZero() {
        val encoded = AdbPacket(AdbPacket.WRTE, 1, 1, byteArrayOf(7)).encode(false)
        assertArrayEquals(byteArrayOf(7), AdbPacket.read(ByteArrayInputStream(encoded), false).payload)
    }
    @Test fun acceptedSignatureDoesNotSendAnotherPublicKey() {
        val key = AdbKey(KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair())
        val input = AdbPacket(AdbPacket.AUTH, 1, 0, ByteArray(20) { 11 }).encode(false) + AdbPacket(AdbPacket.CNXN, 0x01000001, 4096).encode(false)
        val output = ByteArrayOutputStream()
        AdbSession(ByteArrayInputStream(input), output, key).authenticate { fail("Accepted key must not ask again") }
        val packets = ByteArrayInputStream(output.toByteArray())
        assertEquals(AdbPacket.CNXN, AdbPacket.read(packets, false).command)
        assertEquals(2, AdbPacket.read(packets, false).arg0)
        assertEquals(0, packets.available())
    }
    @Test fun shellAcknowledgesOutputAndCloseWithoutKeepingTheStream() {
        val key = AdbKey(KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair())
        val input = listOf(AdbPacket(AdbPacket.CNXN, 0x01000001, 4096), AdbPacket(AdbPacket.OKAY, 7, 1),
            AdbPacket(AdbPacket.WRTE, 7, 1, "ready".toByteArray()), AdbPacket(AdbPacket.CLSE, 7, 1)).flatMap { it.encode(false).toList() }.toByteArray()
        val output = ByteArrayOutputStream()
        val session = AdbSession(ByteArrayInputStream(input), output, key)
        session.authenticate {}
        assertEquals("ready", session.shell("true"))
        val sent = ByteArrayInputStream(output.toByteArray())
        AdbPacket.read(sent, false); AdbPacket.read(sent, false)
        assertEquals(AdbPacket.OKAY, AdbPacket.read(sent, false).command)
        assertEquals(AdbPacket.CLSE, AdbPacket.read(sent, false).command)
    }
    @Test fun oversizedHeaderFailsBeforePayloadAllocation() {
        val packet = AdbPacket(AdbPacket.WRTE, 1, 1).encode()
        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putInt(12, AdbPacket.MAX_PAYLOAD + 1)
        assertThrows(IllegalArgumentException::class.java) { AdbPacket.read(ByteArrayInputStream(packet), false) }
    }
    @Test fun authenticationSendsSignatureThenPublicKey() {
        val key = AdbKey(KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair())
        val input = listOf(
            AdbPacket(AdbPacket.AUTH, 1, 0, ByteArray(20) { 7 }),
            AdbPacket(AdbPacket.AUTH, 1, 0, ByteArray(20) { 7 }),
            AdbPacket(AdbPacket.CNXN, 0x01000001, 4096)
        ).flatMap { it.encode(false).toList() }.toByteArray()
        val output = ByteArrayOutputStream()
        var awaiting = false
        AdbSession(ByteArrayInputStream(input), output, key).authenticate { awaiting = true }
        val sent = ByteArrayInputStream(output.toByteArray())
        assertEquals(AdbPacket.CNXN, AdbPacket.read(sent, true).command)
        assertEquals(2, AdbPacket.read(sent, true).arg0)
        assertEquals(3, AdbPacket.read(sent, true).arg0)
        assertTrue(awaiting)
    }
    @Test fun tlsAndWrongStreamIdsAreRejected() {
        val key = AdbKey(KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair())
        assertThrows(UnsupportedAdb::class.java) {
            AdbSession(ByteArrayInputStream(AdbPacket(AdbPacket.STLS, 0, 0).encode()), ByteArrayOutputStream(), key).authenticate {}
        }
        val input = AdbPacket(AdbPacket.OKAY, 9, 99).encode()
        assertThrows(Exception::class.java) {
            AdbSession(ByteArrayInputStream(input), ByteArrayOutputStream(), key).shell("true")
        }
    }
    @Test fun privateKeySurvivesStorageReloadAndCorruptionIsNotReplaced() {
        val file = Files.createTempDirectory("adb-key-test").resolve("key").toFile()
        try {
            val first = AdbKeyStore(file).load()
            assertArrayEquals(first.publicKey(), AdbKeyStore(file).load().publicKey())
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertThrows(Exception::class.java) { AdbKeyStore(file).load() }
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        } finally { file.delete(); requireNotNull(file.parentFile).delete() }
    }
    @Test fun androidPublicKeyAndPrehashedSignatureMatchRsa() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key = AdbKey(pair)
        val raw = Base64.getDecoder().decode(String(key.publicKey()).substringBefore(' '))
        val bytes = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(524, raw.size)
        assertEquals(64, bytes.int)
        val n0inv = bytes.int.toLong() and 0xffffffffL
        val modulus = ByteArray(256).also { bytes.get(it) }
        val rr = ByteArray(256).also { bytes.get(it) }
        val n = (pair.public as RSAPublicKey).modulus
        assertEquals(n, BigInteger(1, modulus.reversedArray()))
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), BigInteger(1, rr.reversedArray()))
        assertEquals(65537, bytes.int)
        assertEquals(0xffffffffL, ((ByteBuffer.wrap(modulus).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL) * n0inv) and 0xffffffffL)
        val challenge = ByteArray(32) { it.toByte() }
        val token = MessageDigest.getInstance("SHA-1").digest(challenge)
        val signature = key.sign(token)
        assertEquals(256, signature.size)
        val verifier = Signature.getInstance("SHA1withRSA").apply { initVerify(pair.public) }
        verifier.update(challenge)
        assertTrue(verifier.verify(signature))
        verifier.update(challenge + byteArrayOf(1))
        assertFalse(verifier.verify(signature))
    }
    @Test fun signatureRejectsInvalidTokenLengths() {
        val key = AdbKey(KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair())
        for (size in listOf(0, 19, 21, 256)) {
            assertThrows(IllegalArgumentException::class.java) { key.sign(ByteArray(size)) }
        }
    }
}
