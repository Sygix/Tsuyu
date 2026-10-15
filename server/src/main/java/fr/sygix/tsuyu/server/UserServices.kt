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

internal class UserServices(
    private val apk: String,
    private val managerUser: Int,
    private val available: (String) -> Boolean = { File(it).canExecute() },
    private val launch: (List<String>) -> java.lang.Process = { command ->
        ProcessBuilder(command).apply {
            environment()["CLASSPATH"] = apk
            redirectOutput(File("/dev/null"))
            redirectError(File("/dev/null"))
        }.start()
    },
    private val terminate: (java.lang.Process) -> Unit = { OwnedProcess(it).terminate() },
    private val childPid: (java.lang.Process) -> Int = { OwnedProcess(it).pid },
    private val timeout: (((() -> Unit)) -> Unit)? = null,
) {
    private class Record(val uid: Int, val key: String, val version: Int, var daemon: Boolean, val token: String, var process: java.lang.Process? = null, var binder: IBinder? = null, val callbacks: MutableMap<IBinder, IShizukuServiceConnection> = mutableMapOf())
    private val records = mutableMapOf<String, Record>()
    private val events = Executors.newSingleThreadScheduledExecutor()
    private val callbacks = java.util.concurrent.ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, java.util.concurrent.ArrayBlockingQueue(32))
    private val deadline = Deadline()
    private val cleanup = Executors.newFixedThreadPool(2)
    private val retiring = mutableListOf<java.util.concurrent.Future<*>>()
    private val deaths = mutableMapOf<Pair<Record, IBinder>, IBinder.DeathRecipient>()
    private fun awaitCleanup() {
        val pending = synchronized(this) { retiring.toList().also { retiring.clear() } }
        Cleanup.run(pending.map { future -> { future.get(6, TimeUnit.SECONDS); Unit } })
    }
    fun add(uid: Int, group: AccessGroup, api: Int, conn: IShizukuServiceConnection, args: Bundle): Int {
        val result = addLocked(uid, group, api, conn, args)
        awaitCleanup()
        return result
    }
    private fun connected(record: Record, conn: IShizukuServiceConnection): Boolean {
        try {
        callbacks.execute {
            val binder = synchronized(this) { record.binder?.takeIf { records[record.key] == record && conn.asBinder() in record.callbacks } } ?: return@execute
            try { deadline.call { conn.connected(binder) } }
            catch (failure: Exception) { android.util.Log.e("TsuyuServer", "UserService callback failed", failure); synchronized(this) { record.callbacks.remove(conn.asBinder()); unlink(record, conn.asBinder()); if (!record.daemon && record.callbacks.isEmpty()) remove(record) } }
        }
        return true
        } catch (failure: java.util.concurrent.RejectedExecutionException) {
            record.callbacks.remove(conn.asBinder())
            unlink(record, conn.asBinder())
            if (!record.daemon && record.callbacks.isEmpty()) remove(record)
            android.util.Log.e("TsuyuServer", "UserService connection capacity exceeded", failure)
            return false
        }
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
    @Synchronized private fun addLocked(uid: Int, group: AccessGroup, api: Int, conn: IShizukuServiceConnection, args: Bundle): Int {
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
        if (record != null && record.version != version) { remove(record); record = null }
        if (record == null) {
            val executable = if (args.getBoolean(USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS)) "/system/bin/app_process32" else "/system/bin/app_process"
            require(available(executable)) { "Requested ABI unavailable" }
            val suffix = args.getString(USER_SERVICE_ARG_PROCESS_NAME) ?: "user_service"
            require(suffix.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
            val command = mutableListOf("/system/bin/toybox", "setsid", executable)
            if (args.getBoolean(USER_SERVICE_ARG_DEBUGGABLE)) {
                command += "-Xcompiler-option"; command += "--debuggable"
                command += "-XjdwpProvider:adbconnection"; command += "-XjdwpOptions:suspend=n,server=y"
            }
            command += listOf("/system/bin", "--nice-name=${component.packageName}:$suffix", "fr.sygix.tsuyu.server.UserServiceMain")
            record = Record(uid, key, version, args.getBoolean(USER_SERVICE_ARG_DAEMON, true), UUID.randomUUID().toString())
            records[key] = record
            command += listOf(component.packageName, component.className, (uid / 100000).toString(), managerUser.toString(), record.token)
            try {
                record.process = launch(command)
                val created = record
                val expire = { synchronized(this) { if (records[key] == created && created.binder == null) { android.util.Log.e("TsuyuServer", "UserService attachment timed out"); remove(created) } } }
                if (timeout != null) timeout(expire) else events.schedule(expire, 30, TimeUnit.SECONDS)
            } catch (failure: Exception) { remove(record); throw IllegalStateException("UserService launch failed", failure) }
        }
        record.daemon = args.getBoolean(USER_SERVICE_ARG_DAEMON, true)
        register(record, conn)
        record.binder?.let { check(connected(record, conn)) { "UserService connection capacity exceeded" } }
        return 0
    }
    private fun register(record: Record, conn: IShizukuServiceConnection) {
        val binder = conn.asBinder()
        if (record.callbacks.containsKey(binder)) return
        val death = IBinder.DeathRecipient { synchronized(this) { record.callbacks.remove(binder); deaths.remove(record to binder); if (!record.daemon && record.callbacks.isEmpty()) remove(record) } }
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
        val result = unbindLocked(uid, group, conn, args)
        awaitCleanup()
        return result
    }
    @Synchronized private fun unbindLocked(uid: Int, group: AccessGroup, conn: IShizukuServiceConnection?, args: Bundle): Int {
        val record = records[key(uid, component(uid, group, args), args)] ?: return 1
        if (args.getBoolean(USER_SERVICE_ARG_REMOVE, true)) remove(record)
        else { val binder = requireNotNull(conn).asBinder(); record.callbacks.remove(binder); unlink(record, binder); if (!record.daemon && record.callbacks.isEmpty()) remove(record) }
        return 0
    }
    fun revoke(uid: Int) { synchronized(this) { records.values.filter { it.uid == uid }.toList().forEach(::remove) }; awaitCleanup() }
    fun close() {
        try { synchronized(this) { records.values.toList().forEach(::remove) }; awaitCleanup() }
        finally { events.shutdownNow(); callbacks.shutdownNow(); cleanup.shutdownNow(); deadline.close() }
    }
    private fun unlink(record: Record, binder: IBinder) { deaths.remove(record to binder)?.let { binder.unlinkToDeath(it, 0) } }
    private fun remove(record: Record) {
        if (records[record.key] != record) return
        records.remove(record.key, record)
        record.binder?.let { unlink(record, it) }
        record.callbacks.keys.toList().forEach { unlink(record, it) }
        record.process?.let { process -> retiring += cleanup.submit { terminate(process) } }
        record.callbacks.values.toList().forEach { callback -> try { callbacks.execute {
            try { deadline.call { callback.died() } } catch (failure: Exception) { android.util.Log.e("TsuyuServer", "UserService death callback failed", failure) }
        } } catch (failure: java.util.concurrent.RejectedExecutionException) { android.util.Log.e("TsuyuServer", "UserService callback capacity exceeded", failure) } }
        record.callbacks.clear()
    }
}
