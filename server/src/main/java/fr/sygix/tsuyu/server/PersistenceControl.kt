/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.TimeUnit

enum class Preparation { READY, DENIED, UNSUPPORTED, FAILED, TIMEOUT }
data class CommandResult(val exit: Int, val output: String, val timeout: Boolean = false)
data class PersistenceSnapshot(val uid: Preparation, val pkg: Preparation, val expiry: Long?) {
    val ready: Boolean get() = uid == Preparation.READY && pkg == Preparation.READY
    val schedulable: Boolean get() = ready || uid == Preparation.UNSUPPORTED && pkg == Preparation.UNSUPPORTED
}
fun interface PersistenceCommands { fun execute(args: List<String>): CommandResult }
class AndroidPersistenceCommands : PersistenceCommands {
    override fun execute(args: List<String>): CommandResult {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) return CommandResult(-1, "", true)
            return CommandResult(process.exitValue(), process.inputStream.bufferedReader().readText())
        } finally { process.destroyForcibly(); process.inputStream.close(); process.errorStream.close(); process.outputStream.close() }
    }
}
class PersistenceControl(private val commands: PersistenceCommands, private val userId: Int = 0) {
    init { require(userId in 0..21474) }
    private val pkg = "fr.sygix.tsuyu"
    fun status(): PersistenceSnapshot = PersistenceSnapshot(mode(true), mode(false), expiry())
    fun prepare(): PersistenceSnapshot {
        for (uid in listOf(true, false)) {
            if (mode(uid) == Preparation.UNSUPPORTED) continue
            commands.execute(listOf("/system/bin/appops", "set", "--user", "$userId") + (if (uid) listOf("--uid") else emptyList()) + listOf(pkg, "AUTO_START", "allow"))
        }
        return status()
    }
    private fun mode(uid: Boolean): Preparation {
        val result = try { commands.execute(listOf("/system/bin/appops", "get", "--user", "$userId") + (if (uid) listOf("--uid") else emptyList()) + listOf(pkg, "AUTO_START")) }
        catch (failure: Exception) { return Preparation.FAILED }
        if (result.timeout) return Preparation.TIMEOUT
        if (Regex("unknown (operation|op)|not a valid op", RegexOption.IGNORE_CASE).containsMatchIn(result.output)) return Preparation.UNSUPPORTED
        if (result.exit != 0) return if (result.output.contains("SecurityException") || result.output.contains("Permission denied", true)) Preparation.DENIED else Preparation.FAILED
        val lines = result.output.lineSequence().filter { if (uid) it.contains("Uid mode:", true) else !it.contains("Uid mode:", true) }
        val modes = lines.mapNotNull { Regex("AUTO_START:\\s*(allow|ignore|deny|default|foreground)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.lowercase() }.toList()
        return when { modes.size == 1 && modes.single() == "allow" -> Preparation.READY; modes.isNotEmpty() || result.output.contains("No operations.") -> Preparation.DENIED; else -> Preparation.FAILED }
    }
    private fun expiry(): Long? {
        val result = commands.execute(listOf("/system/bin/settings", "get", "global", "adb_allowed_connection_time"))
        if (result.timeout || result.exit != 0) return null
        return result.output.trim().toLongOrNull()?.takeIf { it >= 0 }
    }
    fun disableExpiry(): PersistenceSnapshot {
        if (expiry() != 0L) commands.execute(listOf("/system/bin/settings", "put", "global", "adb_allowed_connection_time", "0"))
        return status()
    }
}
