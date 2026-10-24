/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class InstallStatus { SUCCESS, DIGEST, INSTALLER, TIMEOUT }
data class InstallOutcome(val status: InstallStatus, val detail: String = "")

class UpdateInstaller(
    private val userId: Int,
    private val launch: (List<String>) -> Process = { ProcessBuilder(it).redirectErrorStream(true).start() },
    private val stepTimeoutMs: Long = 90_000,
) {
    init { require(userId in 0..21474) }

    fun install(apk: InputStream, size: Long, sha256: String): InstallOutcome {
        require(size in 1..MAX_SIZE && sha256.matches(Regex("[a-f0-9]{64}")))
        val created = step(listOf(PM, "install-create", "-r", "--user", userId.toString(), "--pkg", PACKAGE, "-S", size.toString()))
            ?: return InstallOutcome(InstallStatus.TIMEOUT)
        val session = Regex("""\[(\d+)]""").find(created)?.groupValues?.get(1)
            ?: return InstallOutcome(InstallStatus.INSTALLER, failure(created))
        var committed = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val written = step(listOf(PM, "install-write", "-S", size.toString(), session, "base.apk", "-")) { output ->
                copy(apk, size) { buffer, count -> digest.update(buffer, 0, count); output.write(buffer, 0, count) }
            } ?: return InstallOutcome(InstallStatus.TIMEOUT)
            if (!success(written)) return InstallOutcome(InstallStatus.INSTALLER, failure(written))
            if (digest.digest().joinToString("") { "%02x".format(it) } != sha256) return InstallOutcome(InstallStatus.DIGEST)
            committed = true
            val result = step(listOf(PM, "install-commit", session)) ?: return InstallOutcome(InstallStatus.TIMEOUT)
            return if (success(result)) InstallOutcome(InstallStatus.SUCCESS) else InstallOutcome(InstallStatus.INSTALLER, failure(result))
        } catch (failure: Exception) {
            return InstallOutcome(InstallStatus.INSTALLER, failure.javaClass.simpleName)
        } finally {
            if (!committed) try { step(listOf(PM, "install-abandon", session)) } catch (cleanup: Exception) { android.util.Log.e("TsuyuServer", "Update session abandon failed", cleanup) }
        }
    }

    private fun step(command: List<String>, input: ((java.io.OutputStream) -> Unit)? = null): String? {
        val process = launch(command)
        try {
            val writer = input?.let { feed ->
                Thread { try { process.outputStream.use(feed) } catch (failure: Exception) { process.destroyForcibly() } }.apply { start() }
            } ?: run { process.outputStream.close(); null }
            val output = StringBuilder()
            val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
            if (!process.waitFor(stepTimeoutMs, TimeUnit.MILLISECONDS)) return null
            writer?.join(5_000)
            reader.join(5_000)
            return output.toString().trim()
        } finally {
            process.destroyForcibly()
            Cleanup.run(listOf({ process.inputStream.close() }, { process.outputStream.close() }))
        }
    }

    private fun success(output: String) = output.lineSequence().any { it.trim().startsWith("Success") }
    private fun failure(output: String) = Regex("""\[?([A-Z][A-Z_]{3,})""").find(output)?.groupValues?.get(1) ?: "UNKNOWN"

    private fun copy(input: InputStream, size: Long, sink: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count > 0) { "Update file shorter than announced" }
            sink(buffer, count)
            remaining -= count
        }
    }

    companion object {
        const val PACKAGE = "fr.sygix.tsuyu"
        const val MAX_SIZE = 200L * 1024 * 1024
        private const val PM = "/system/bin/pm"
    }
}
