/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.adb

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

class AdbKeyStore(private val file: File) {
    @Synchronized fun load(): AdbKey {
        if (file.exists()) {
            require(file.length() in 1..4096) { "Invalid private key storage" }
            val factory = KeyFactory.getInstance("RSA")
            val private = factory.generatePrivate(PKCS8EncodedKeySpec(file.readBytes())) as RSAPrivateCrtKey
            return AdbKey(KeyPair(factory.generatePublic(RSAPublicKeySpec(private.modulus, private.publicExponent)), private))
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val temporary = File(file.parentFile, file.name + ".new")
        try {
            FileOutputStream(temporary).use { it.write(pair.private.encoded); it.fd.sync() }
            check(temporary.setReadable(false, false) && temporary.setWritable(false, false))
            check(temporary.setReadable(true, true) && temporary.setWritable(true, true))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { if (temporary.exists()) check(temporary.delete()) }
        return AdbKey(pair)
    }
}
