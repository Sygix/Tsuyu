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

internal class UserServices(private val apk: String, private val managerUser: Int) {
    private data class Record(val uid: Int, val key: String, val version: Int, var daemon: Boolean, val token: String, var process: java.lang.Process? = null, var binder: IBinder? = null, val callbacks: MutableMap<IBinder, IShizukuServiceConnection> = mutableMapOf())
    private val records = mutableMapOf<String, Record>()
    private val events = Executors.newSingleThreadScheduledExecutor()
    private val deadline = Deadline()
    private fun connected(record: Record, conn: IShizukuServiceConnection) {
        events.execute {
            val binder = synchronized(this) { record.binder?.takeIf { records[record.key] == record && conn.asBinder() in record.callbacks } } ?: return@execute
            try { deadline.call { conn.connected(binder) } }
            catch (failure: Exception) { android.util.Log.e("TsuyuServer", "UserService callback failed", failure); synchronized(this) { record.callbacks.remove(conn.asBinder()); if (!record.daemon && record.callbacks.isEmpty()) remove(record) } }
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
    @Synchronized fun add(uid: Int, group: AccessGroup, api: Int, conn: IShizukuServiceConnection, args: Bundle): Int {
        val component = component(uid, group, args)
        val key = key(uid, component, args)
        val version = args.getInt(USER_SERVICE_ARG_VERSION_CODE, 1)
        var record = records[key]
        if (args.getBoolean(USER_SERVICE_ARG_NO_CREATE)) {
            if (record?.binder?.isBinderAlive != true) return if (api >= 13) -1 else 1
            register(record, conn)
            connected(record, conn)
            return if (api >= 13) record.version else 0
        }
        if (record != null && record.version != version) { remove(record); record = null }
        if (record == null) {
            val executable = if (args.getBoolean(USER_SERVICE_ARG_USE_32_BIT_APP_PROCESS)) "/system/bin/app_process32" else "/system/bin/app_process"
            require(File(executable).canExecute()) { "Requested ABI unavailable" }
            val suffix = args.getString(USER_SERVICE_ARG_PROCESS_NAME) ?: "user_service"
            require(suffix.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
            val command = mutableListOf(executable)
            if (args.getBoolean(USER_SERVICE_ARG_DEBUGGABLE)) {
                command += "-Xcompiler-option"; command += "--debuggable"
                command += "-XjdwpProvider:adbconnection"; command += "-XjdwpOptions:suspend=n,server=y"
            }
            command += listOf("/system/bin", "--nice-name=${component.packageName}:$suffix", "fr.sygix.tsuyu.server.UserServiceMain")
            record = Record(uid, key, version, args.getBoolean(USER_SERVICE_ARG_DAEMON, true), UUID.randomUUID().toString())
            records[key] = record
            command += listOf(component.packageName, component.className, (uid / 100000).toString(), managerUser.toString(), record.token)
            try {
                record.process = ProcessBuilder(command).apply { environment()["CLASSPATH"] = apk; redirectOutput(File("/dev/null")); redirectError(File("/dev/null")) }.start()
                val created = record
                events.schedule({ synchronized(this) { if (records[key] == created && created.binder == null) { android.util.Log.e("TsuyuServer", "UserService attachment timed out"); remove(created) } } }, 30, TimeUnit.SECONDS)
            } catch (failure: Exception) { remove(record); throw IllegalStateException("UserService launch failed", failure) }
        }
        record.daemon = args.getBoolean(USER_SERVICE_ARG_DAEMON, true)
        register(record, conn)
        record.binder?.let { connected(record, conn) }
        return 0
    }
    private fun register(record: Record, conn: IShizukuServiceConnection) {
        val binder = conn.asBinder()
        if (record.callbacks.containsKey(binder)) return
        binder.linkToDeath({ synchronized(this) { record.callbacks.remove(binder); if (!record.daemon && record.callbacks.isEmpty()) remove(record) } }, 0)
        record.callbacks[binder] = conn
    }
    @Synchronized fun attach(binder: IBinder, token: String, pid: Int) {
        val record = records.values.singleOrNull { it.token == token && it.binder == null } ?: throw SecurityException("Invalid launch token")
        val process = requireNotNull(record.process)
        val ownedPid = process.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(process)
        require(ownedPid == pid) { "Child identity mismatch" }
        record.binder = binder
        binder.linkToDeath({ synchronized(this) { remove(record) } }, 0)
        record.callbacks.values.toList().forEach { connected(record, it) }
    }
    @Synchronized fun unbind(uid: Int, group: AccessGroup, conn: IShizukuServiceConnection, args: Bundle): Int {
        val record = records[key(uid, component(uid, group, args), args)] ?: return 1
        if (args.getBoolean(USER_SERVICE_ARG_REMOVE, true)) remove(record)
        else { record.callbacks.remove(conn.asBinder()); if (!record.daemon && record.callbacks.isEmpty()) remove(record) }
        return 0
    }
    @Synchronized fun revoke(uid: Int) { records.values.filter { it.uid == uid }.toList().forEach(::remove) }
    @Synchronized fun close() { records.values.toList().forEach(::remove); events.shutdown(); deadline.close() }
    private fun remove(record: Record) {
        if (!records.remove(record.key, record)) return
        record.process?.destroyForcibly()
        record.callbacks.values.toList().forEach { callback -> events.execute {
            try { deadline.call { callback.died() } } catch (failure: Exception) { android.util.Log.e("TsuyuServer", "UserService death callback failed", failure) }
        } }
        record.callbacks.clear()
    }
}
