/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.TimeUnit
import fr.sygix.tsuyu.ipc.Preparation

data class CommandResult(val exit: Int, val output: String, val timeout: Boolean = false)
data class PersistenceSnapshot(val uid: Preparation, val pkg: Preparation, val expiry: Long?) {
    val ready: Boolean get() = uid == Preparation.READY && pkg == Preparation.READY
    val schedulable: Boolean get() = ready || uid == Preparation.UNSUPPORTED && pkg == Preparation.UNSUPPORTED
}
fun interface PersistenceCommands {
    fun execute(args: List<String>): CommandResult
}
class AndroidPersistenceCommands : PersistenceCommands {
    override fun execute(args: List<String>): CommandResult {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) return CommandResult(-1, "", true)
            return CommandResult(process.exitValue(), process.inputStream.bufferedReader().readText())
        } finally {
            process.destroyForcibly()
            Cleanup.run(listOf(
                { process.inputStream.close() },
                { process.errorStream.close() },
                { process.outputStream.close() },
            ))
        }
    }
}
class PersistenceControl(private val commands: PersistenceCommands, private val userId: Int = 0) {
    init {
        require(userId in 0..21474)
    }
    private val pkg = "fr.sygix.tsuyu"
    fun status(): PersistenceSnapshot = PersistenceSnapshot(mode(true), mode(false), expiry())
    fun prepare(): PersistenceSnapshot {
        val uid = prepareMode(true)
        val packageMode = prepareMode(false)
        return PersistenceSnapshot(uid, packageMode, expiry())
    }
    private fun prepareMode(uid: Boolean): Preparation {
        if (mode(uid) == Preparation.UNSUPPORTED) return Preparation.UNSUPPORTED
        val mutation = try { commands.execute(appops("set", uid) + "allow") }
        catch (failure: Exception) { return Preparation.FAILED }
        val observed = mode(uid)
        return if (observed == Preparation.READY) observed
        else if (mutation.timeout) Preparation.TIMEOUT else observed
    }
    private fun mode(uid: Boolean): Preparation {
        val result = try { commands.execute(appops("get", uid)) }
        catch (failure: Exception) { return Preparation.FAILED }
        if (result.timeout) return Preparation.TIMEOUT
        if (Regex("unknown (operation|op)|not a valid op", RegexOption.IGNORE_CASE).containsMatchIn(result.output)) return Preparation.UNSUPPORTED
        if (result.exit != 0) {
            return if (result.output.contains("SecurityException") || result.output.contains("Permission denied", true)) {
                Preparation.DENIED
            } else Preparation.FAILED
        }
        val lines = result.output.lineSequence().filter { if (uid) it.contains("Uid mode:", true) else !it.contains("Uid mode:", true) }
        val modes = lines.mapNotNull { MODE.find(it)?.groupValues?.get(1)?.lowercase() }.toList()
        return when {
            modes.size == 1 && modes.single() == "allow" -> Preparation.READY
            modes.isNotEmpty() || result.output.contains("No operations.") -> Preparation.DENIED
            else -> Preparation.FAILED
        }
    }
    private fun appops(action: String, uid: Boolean): List<String> =
        listOf("/system/bin/appops", action, "--user", "$userId") +
            (if (uid) listOf("--uid") else emptyList()) + listOf(pkg, "AUTO_START")
    private fun expiry(): Long? {
        val result = try { commands.execute(listOf("/system/bin/settings", "get", "global", "adb_allowed_connection_time")) }
        catch (failure: Exception) { return null }
        if (result.timeout || result.exit != 0) return null
        return result.output.trim().toLongOrNull()?.takeIf { it >= 0 }
    }
    fun disableExpiry(): PersistenceSnapshot {
        if (expiry() != 0L) commands.execute(listOf("/system/bin/settings", "put", "global", "adb_allowed_connection_time", "0"))
        return status()
    }
    companion object {
        private val MODE = Regex("AUTO_START:\\s*(allow|ignore|deny|default|foreground)", RegexOption.IGNORE_CASE)
    }
}
