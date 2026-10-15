/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/server-shared/src/main/java/rikka/shizuku/server/UserServiceManager.java.
 */

package fr.sygix.tsuyu.server

import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder
import moe.shizuku.server.IShizukuServiceConnection
import rikka.shizuku.ShizukuApiConstants.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import android.util.Log

internal class UserServices(
    private val apk: String,
    private val managerUser: Int,
    private val available: (String) -> Boolean = { File(it).canExecute() },
    private val launch: (List<String>) -> java.lang.Process = { command ->
        ProcessSession(apk).launch(command.toTypedArray(), discardOutput = true)
    },
    private val terminate: (java.lang.Process) -> Unit = { OwnedProcess(it).terminate() },
    private val childPid: (java.lang.Process) -> Int = { OwnedProcess(it).pid },
    private val timeout: (((() -> Unit)) -> Unit)? = null,
) {
    private class Record(
        val uid: Int,
        val key: String,
        val version: Int,
        var daemon: Boolean,
        val token: String,
        var process: java.lang.Process? = null,
        var binder: IBinder? = null,
        val callbacks: MutableMap<IBinder, IShizukuServiceConnection> = mutableMapOf(),
    )
    private val records = mutableMapOf<String, Record>()
    private val events = Executors.newSingleThreadScheduledExecutor()
    private val callbacks = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(32))
    private val deadline = Deadline()
    private val cleanup = Executors.newFixedThreadPool(2)
    private val retiring = mutableMapOf<Record, Future<*>>()
    private val deaths = mutableMapOf<Pair<Record, IBinder>, IBinder.DeathRecipient>()
    private fun <T> operation(block: (MutableList<Future<*>>) -> T): T {
        val pending = mutableListOf<Future<*>>()
        val result = synchronized(this) { block(pending) }
        Cleanup.run(pending.map { future -> { future.get(6, TimeUnit.SECONDS); Unit } })
        return result
    }
    fun add(uid: Int, group: AccessGroup, api: Int, conn: IShizukuServiceConnection, args: Bundle): Int {
        return operation { pending -> addLocked(uid, group, api, conn, args, pending) }
    }
    private fun connected(record: Record, conn: IShizukuServiceConnection): Boolean {
        return try {
            callbacks.execute {
                val binder = synchronized(this) {
                    record.binder?.takeIf { records[record.key] == record && conn.asBinder() in record.callbacks }
                } ?: return@execute
                try { deadline.call { conn.connected(binder) } }
                catch (failure: Exception) {
                    Log.e("TsuyuServer", "UserService callback failed", failure)
                    synchronized(this) { disconnect(record, conn.asBinder()) }
                }
            }
            true
        } catch (failure: RejectedExecutionException) {
            disconnect(record, conn.asBinder())
            Log.e("TsuyuServer", "UserService connection capacity exceeded", failure)
            return false
        }
    }
    private fun disconnect(record: Record, binder: IBinder): Future<*>? {
        record.callbacks.remove(binder)
        unlink(record, binder)
        return if (!record.daemon && record.callbacks.isEmpty()) remove(record) else null
    }
    private fun component(uid: Int, group: AccessGroup, args: Bundle): ComponentName {
        val component = requireNotNull(args.getParcelable(USER_SERVICE_ARG_COMPONENT, ComponentName::class.java))
        require(group.uid == uid && component.packageName in group.members)
        require(component.className.matches(Regex("[A-Za-z_$][A-Za-z0-9_.$]*")))
        return component
    }
    private fun key(uid: Int, component: ComponentName, args: Bundle): String {
        val tag = args.getString(USER_SERVICE_ARG_TAG) ?: component.className
        require(tag.length in 1..256 && '\u0000' !in tag)
        return "$uid:${component.packageName}:$tag"
    }
    private fun addLocked(uid: Int, group: AccessGroup, api: Int, conn: IShizukuServiceConnection, args: Bundle, pending: MutableList<Future<*>>): Int {
        val component = component(uid, group, args)
        val key = key(uid, component, args)
        val version = args.getInt(USER_SERVICE_ARG_VERSION_CODE, 1)
        var record = records[key]
        if (args.getBoolean(USER_SERVICE_ARG_NO_CREATE)) {
            if (record?.binder?.isBinderAlive != true) return if (api >= 13) -1 else 1
            register(record, conn)
            check(connected(record, conn)) { "UserService connection capacity exceeded" }
            return if (api >= 13) record.version else 0
        }
        if (record != null && record.version != version) {
            pending += remove(record)
            record = null
        }
        if (record == null) {
            val executable = if (args.getBoolean(USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS)) "/system/bin/app_process32" else "/system/bin/app_process"
            require(available(executable)) { "Requested ABI unavailable" }
            val suffix = args.getString(USER_SERVICE_ARG_PROCESS_NAME) ?: "user_service"
            require(suffix.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
            val command = mutableListOf(executable)
            if (args.getBoolean(USER_SERVICE_ARG_DEBUGGABLE)) {
                command += listOf("-Xcompiler-option", "--debuggable", "-XjdwpProvider:adbconnection", "-XjdwpOptions:suspend=n,server=y")
            }
            command += listOf("/system/bin", "--nice-name=${component.packageName}:$suffix", "fr.sygix.tsuyu.server.UserServiceMain")
            record = Record(uid, key, version, args.getBoolean(USER_SERVICE_ARG_DAEMON, true), UUID.randomUUID().toString())
            records[key] = record
            command += listOf(component.packageName, component.className, (uid / 100000).toString(), managerUser.toString(), record.token)
            try {
                record.process = launch(command)
                val created = record
                val expire: () -> Unit = {
                    synchronized(this) {
                        if (records[key] == created && created.binder == null) {
                            Log.e("TsuyuServer", "UserService attachment timed out")
                            remove(created)
                        }
                    }
                }
                if (timeout != null) timeout(expire) else events.schedule(expire, 30, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                remove(record)
                throw IllegalStateException("UserService launch failed", failure)
            }
        }
        record.daemon = args.getBoolean(USER_SERVICE_ARG_DAEMON, true)
        register(record, conn)
        record.binder?.let { check(connected(record, conn)) { "UserService connection capacity exceeded" } }
        return 0
    }
    private fun register(record: Record, conn: IShizukuServiceConnection) {
        val binder = conn.asBinder()
        if (record.callbacks.containsKey(binder)) return
        val death = IBinder.DeathRecipient { synchronized(this) { disconnect(record, binder) } }
        binder.linkToDeath(death, 0)
        deaths[record to binder] = death
        record.callbacks[binder] = conn
    }
    @Synchronized fun attach(binder: IBinder, token: String, pid: Int) {
        val record = records.values.singleOrNull { it.token == token && it.binder == null } ?: throw SecurityException("Invalid launch token")
        val process = requireNotNull(record.process)
        val ownedPid = childPid(process)
        require(ownedPid == pid) { "Child identity mismatch" }
        record.binder = binder
        val death = IBinder.DeathRecipient { synchronized(this) { remove(record) } }
        binder.linkToDeath(death, 0)
        deaths[record to binder] = death
        record.callbacks.values.toList().forEach { connected(record, it) }
    }
    fun unbind(uid: Int, group: AccessGroup, conn: IShizukuServiceConnection?, args: Bundle): Int {
        return operation { pending -> unbindLocked(uid, group, conn, args, pending) }
    }
    private fun unbindLocked(uid: Int, group: AccessGroup, conn: IShizukuServiceConnection?, args: Bundle, pending: MutableList<Future<*>>): Int {
        val record = records[key(uid, component(uid, group, args), args)] ?: return 1
        if (args.getBoolean(USER_SERVICE_ARG_REMOVE, true)) pending += remove(record)
        else disconnect(record, requireNotNull(conn).asBinder())?.let { pending += it }
        return 0
    }
    fun revoke(uid: Int) {
        operation { pending ->
            (records.values + retiring.keys).filter { it.uid == uid }.distinct().forEach { pending += remove(it) }
        }
    }
    fun close() {
        try {
            operation { pending -> (records.values + retiring.keys).distinct().forEach { pending += remove(it) } }
        } finally {
            events.shutdownNow()
            callbacks.shutdownNow()
            cleanup.shutdownNow()
            deadline.close()
        }
    }
    private fun unlink(record: Record, binder: IBinder) { deaths.remove(record to binder)?.let { binder.unlinkToDeath(it, 0) } }
    private fun remove(record: Record): Future<*> {
        retiring[record]?.takeIf { !it.isDone }?.let { return it }
        records.remove(record.key, record)
        record.binder?.let { unlink(record, it) }
        record.callbacks.keys.toList().forEach { unlink(record, it) }
        val future = cleanup.submit {
            try {
                record.process?.let(terminate)
                synchronized(this) { retiring.remove(record) }
            } catch (failure: Exception) {
                Log.e("TsuyuServer", "UserService termination unverified; retry revocation", failure)
                throw failure
            }
        }
        retiring[record] = future
        record.callbacks.values.toList().forEach(::notifyDeath)
        record.callbacks.clear()
        return future
    }
    private fun notifyDeath(callback: IShizukuServiceConnection) {
        try {
            callbacks.execute {
                try { deadline.call { callback.died() } }
                catch (failure: Exception) { Log.e("TsuyuServer", "UserService death callback failed", failure) }
            }
        } catch (failure: RejectedExecutionException) {
            Log.e("TsuyuServer", "UserService callback capacity exceeded", failure)
        }
    }
}
