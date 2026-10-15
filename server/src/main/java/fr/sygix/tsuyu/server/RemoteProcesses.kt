/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class RemoteProcesses(private val authorize: (ClientRecord) -> Unit) : AutoCloseable {
    private val processes = ConcurrentHashMap<RemoteProcess, ClientRecord>()

    fun create(owner: ClientRecord, command: Array<out String>, environment: Array<out String>?, directory: String?): RemoteProcess {
        check(processes.values.count { it.uid == owner.uid } < MAX_PER_UID) {
            "Too many remote processes; release finished processes and retry"
        }
        require(command.isNotEmpty() && command.size <= MAX_ARGUMENTS && command.all {
            it.length <= MAX_ARGUMENT_LENGTH && '\u0000' !in it
        })
        val process = ProcessBuilder(listOf("/system/bin/toybox", "setsid") + command.toList()).apply {
            if (directory != null) directory(File(directory))
            if (environment != null) {
                environment().clear()
                environment.forEach { value ->
                    val index = value.indexOf('=')
                    require(index > 0)
                    environment()[value.substring(0, index)] = value.substring(index + 1)
                }
            }
        }.start()
        val remote = RemoteProcess(process, { processes.remove(it) }) { authorize(owner) }
        processes[remote] = owner
        remote.watch()
        return remote
    }

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
