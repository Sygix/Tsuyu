/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.ConcurrentHashMap

internal class RemoteProcesses(
    private val launch: (Array<out String>, Array<out String>?, String?) -> Process,
    private val authorize: (ClientRecord) -> Unit,
) : AutoCloseable {
    private val processes = ConcurrentHashMap<RemoteProcess.Resources, ClientRecord>()

    fun create(owner: ClientRecord, command: Array<out String>, environment: Array<out String>?, directory: String?): RemoteProcess {
        check(processes.values.count { it.uid == owner.uid } < MAX_PER_UID) {
            "Too many remote processes; release finished processes and retry"
        }
        require(command.isNotEmpty() && command.size <= MAX_ARGUMENTS && command.all {
            it.length <= MAX_ARGUMENT_LENGTH && '\u0000' !in it
        })
        val process = launch(command, environment, directory)
        val remote = try {
            RemoteProcess(process, { processes.remove(it) }) { authorize(owner) }
        } catch (failure: Exception) {
            try { OwnedProcess(process).terminate() }
            catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
        processes[remote.resources] = owner
        remote.watch()
        return remote
    }

    val idle: Boolean get() = processes.isEmpty()

    fun stop(uid: Int, pid: Int? = null) {
        Cleanup.run(processes.entries.filter { it.value.uid == uid && (pid == null || it.value.pid == pid) }
            .map { entry -> { entry.key.close() } })
    }

    override fun close() {
        Cleanup.run(processes.keys.toList().map { process -> { process.close() } })
        processes.clear()
    }

    companion object {
        private const val MAX_PER_UID = 16
        private const val MAX_ARGUMENTS = 256
        private const val MAX_ARGUMENT_LENGTH = 32_768
    }
}
