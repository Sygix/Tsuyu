/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

interface UpdateFiles {
    fun file(release: Release): File
    fun download(release: Release, target: File): UpdateCause?
    fun verify(release: Release, file: File, installed: Long): UpdateCause?
    fun clean(keep: File? = null)
}

class AndroidUpdateFiles(private val context: Context) : UpdateFiles {
    private val directory get() = File(context.cacheDir, "update").apply { mkdirs() }

    override fun file(release: Release) = File(directory, "tsuyu-${release.code}.apk")

    override fun download(release: Release, target: File): UpdateCause? {
        target.delete()
        var url = URL(release.apk)
        repeat(MAX_REDIRECTS) {
            if (url.protocol != "https") return UpdateCause.NETWORK
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/octet-stream")
                when (connection.responseCode) {
                    in 300..399 -> { url = URL(url, connection.getHeaderField("Location") ?: return UpdateCause.NETWORK); return@repeat }
                    HttpsURLConnection.HTTP_OK -> {
                        var copied = 0L
                        connection.inputStream.use { input -> target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > release.size) return UpdateCause.SIZE
                                output.write(buffer, 0, count)
                            }
                        } }
                        return null
                    }
                    else -> return UpdateCause.NETWORK
                }
            } catch (timeout: SocketTimeoutException) { target.delete(); return UpdateCause.TIMEOUT }
            catch (failure: IOException) { target.delete(); return UpdateCause.NETWORK }
            finally { connection.disconnect() }
        }
        return UpdateCause.NETWORK
    }

    override fun verify(release: Release, file: File, installed: Long): UpdateCause? {
        if (file.length() != release.size) return UpdateCause.SIZE
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != release.sha256) return UpdateCause.DIGEST
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return UpdateCause.PACKAGE
        if (archive.packageName != context.packageName) return UpdateCause.PACKAGE
        if (archive.longVersionCode != release.code || archive.longVersionCode <= installed) return UpdateCause.VERSION
        val expected = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.toSet()
        val found = archive.signingInfo?.apkContentsSigners?.toSet()
        return if (!expected.isNullOrEmpty() && expected == found) null else UpdateCause.CERTIFICATE
    }

    override fun clean(keep: File?) {
        directory.listFiles()?.filter { it != keep }?.forEach { it.delete() }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
    }
}
