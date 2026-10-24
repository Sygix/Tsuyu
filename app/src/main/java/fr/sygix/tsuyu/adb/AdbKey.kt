/*
 * Copyright (C) RikkaW and contributors
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: Apache-2.0
 * Modified by Sygix from Shizuku/manager/src/main/java/moe/shizuku/manager/adb/AdbKey.kt.
 */

package fr.sygix.tsuyu.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class AdbKey(private val pair: KeyPair) {
    fun sign(token: ByteArray): ByteArray {
        require(token.size == 20) { "Invalid ADB token" }
        val sha1Identifier = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
        return Signature.getInstance("NONEwithRSA").apply {
            initSign(pair.private)
            update(sha1Identifier + token)
        }.sign()
    }
    fun publicKey(): ByteArray {
        val public = pair.public as RSAPublicKey
        require(public.modulus.bitLength() == 2048 && public.publicExponent == BigInteger.valueOf(65537))
        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        val n = public.modulus
        buffer.putInt(64).putInt(n.mod(BigInteger.ONE.shiftLeft(32)).modInverse(BigInteger.ONE.shiftLeft(32)).negate().toInt())
        for (value in listOf(n, BigInteger.ONE.shiftLeft(4096).mod(n))) {
            repeat(64) { index -> buffer.putInt(value.shiftRight(index * 32).toInt()) }
        }
        buffer.putInt(public.publicExponent.toInt())
        return (Base64.getEncoder().encodeToString(buffer.array()) + " tsuyu\u0000").toByteArray(Charsets.UTF_8)
    }
}
