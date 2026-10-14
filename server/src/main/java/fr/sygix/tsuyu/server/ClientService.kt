/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/server-shared/src/main/java/rikka/shizuku/server/Service.java.
 */

package fr.sygix.tsuyu.server

import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.system.Os
import moe.shizuku.server.*
import rikka.shizuku.ShizukuApiConstants.*
import rikka.hidden.compat.ActivityManagerApis
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ClientService(
    private val managerUid: Int,
    private val access: AccessAuthority,
    private val identities: ClientIdentity,
    private val storage: AccessStorage,
    private val deadline: Deadline,
    private val stop: () -> Unit,
    private val userServices: UserServices,
    private val uid: () -> Int = Binder::getCallingUid,
    private val pid: () -> Int = Binder::getCallingPid,
    private val securityContext: () -> String = { Class.forName("android.os.SELinux").getMethod("getContext").invoke(null) as String },
    private val onRequestEnded: (String) -> Unit = {},
    private val reportFailure: () -> Unit = {},
) : IShizukuService.Stub() {
    private data class Client(val uid: Int, val pid: Int, val group: AccessGroup, val api: Int, val callback: IShizukuApplication)
    private data class Request(val token: String, val client: Client, val code: Int)
    private val clients = ConcurrentHashMap<Pair<Int, Int>, Client>()
    private val requests = ConcurrentHashMap<String, Request>()
    private val processes = ConcurrentHashMap<RemoteProcess, Client>()
    private val events = Executors.newSingleThreadScheduledExecutor()
    private val lifecycle = PrivilegeLifecycle()
    fun shutdown() { processes.keys.forEach { it.close() }; processes.clear(); userServices.close(); events.shutdownNow() }
    private fun manager() { if (uid() != managerUid) throw SecurityException("Manager identity required") }
    private fun client(): Client = clients[uid() to pid()] ?: throw SecurityException("Attach first")
    private fun allowed(): Client = client().also { if (!shell { access.allowed(it.uid) && identities.resolve(it.uid) == it.group }) throw SecurityException("Permission denied") }
    private fun <T> shell(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        try { return block() } finally { Binder.restoreCallingIdentity(token) }
    }
    private fun context(): String = securityContext()
    override fun attachApplication(application: IShizukuApplication, args: Bundle) {
        val uid = uid(); val pid = pid()
        val group = shell { identities.resolve(uid) }
        require(args.getString(ATTACH_APPLICATION_PACKAGE_NAME) in group.members) { "Package does not belong to caller" }
        val record = Client(uid, pid, group, args.getInt(ATTACH_APPLICATION_API_VERSION), application)
        val key = uid to pid
        application.asBinder().linkToDeath({
            clients.remove(key, record)
            requests.values.filter { it.client == record }.forEach { requests.remove(it.token); onRequestEnded(it.token) }
            processes.entries.filter { it.value == record }.forEach { it.key.close(); processes.remove(it.key) }
        }, 0)
        clients[key] = record
        try {
            val result = shell { Bundle().apply {
                putInt(BIND_APPLICATION_SERVER_VERSION, 13); putInt(BIND_APPLICATION_SERVER_PATCH_VERSION, 6)
                putInt(BIND_APPLICATION_SERVER_UID, Os.getuid()); putString(BIND_APPLICATION_SERVER_SECONTEXT, context())
                putBoolean(BIND_APPLICATION_PERMISSION_GRANTED, access.allowed(uid))
                putBoolean(BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, access.rationale(uid))
            } }
            deadline.call { application.bindApplication(result) }
        } catch (failure: Exception) { clients.remove(key, record); throw IllegalStateException("Attachment failed", failure) }
    }
    override fun getVersion(): Int { allowed(); return 13 }
    override fun getUid(): Int { allowed(); return Os.getuid() }
    override fun getSELinuxContext(): String { allowed(); return shell { context() } }
    override fun checkPermission(permission: String): Int { allowed(); return shell { deadline.call { ActivityManagerApis.checkPermission(permission, android.os.Process.myPid(), Os.getuid()) } } }
    override fun getSystemProperty(name: String, defaultValue: String): String { allowed(); return shell { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java).invoke(null, name, defaultValue) as String } }
    override fun setSystemProperty(name: String, value: String) { allowed(); shell { Class.forName("android.os.SystemProperties").getMethod("set", String::class.java, String::class.java).invoke(null, name, value) } }
    override fun checkSelfPermission(): Boolean { val uid = client().uid; return shell { access.allowed(uid) } }
    override fun shouldShowRequestPermissionRationale(): Boolean { val uid = client().uid; return shell { access.rationale(uid) } }
    override fun isHidden(uid: Int): Boolean { if (Binder.getCallingUid() != managerUid) client(); return false }
    override fun dispatchPackageChanged(intent: Intent) { manager() }
    override fun exit() { manager(); stop() }
    override fun getFlagsForUid(uid: Int, mask: Int): Int { manager(); return shell { (if (access.allowed(uid)) 2 else if (access.rationale(uid)) 4 else 0) and mask } }
    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        manager(); require(mask and 6 == mask)
        if (mask == 0) return
        lifecycle.withUid(uid) {
            val old = getFlagsForUid(uid, 6)
            val next = (old and mask.inv()) or (value and mask)
            require(next != 6) { "Conflicting permission flags" }
            if (next == old) return@withUid
            if (next and 2 != 0) shell { access.decide(uid, true) }
            else { revoke(uid); if (next == 0) shell { access.clear(uid) } }
        }
    }
    override fun requestPermission(requestCode: Int) {
        val record = client()
        check(requests.size < 8) { "Too many pending requests" }
        val request = Request(UUID.randomUUID().toString(), record, requestCode)
        requests[request.token] = request
        events.schedule({ complete(request.token, false) }, 120, TimeUnit.SECONDS)
        try {
            shell { deadline.call { ActivityManagerApis.startActivity(Intent().setComponent(ComponentName("fr.sygix.tsuyu", "fr.sygix.tsuyu.access.PermissionActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("request", request.token), "com.android.shell", record.uid / 100000) } }
        } catch (failure: Exception) { complete(request.token, false); throw IllegalStateException("Open Tsuyu and retry permission", failure) }
    }
    fun request(token: String): Bundle {
        manager()
        val request = requests[token] ?: error("Request expired")
        check(shell { identities.resolve(request.client.uid) } == request.client.group) { "Identity changed" }
        return Bundle().apply { putInt("uid", request.client.uid); putStringArrayList("packages", ArrayList(request.client.group.members.keys)); putString("token", request.token) }
    }
    fun confirm(token: String, allowed: Boolean) { manager(); complete(token, allowed) }
    private fun complete(token: String, allowed: Boolean) {
        val request = requests.remove(token) ?: return
        onRequestEnded(token)
        if (clients[request.client.uid to request.client.pid] != request.client) return
        val result = try {
            shell {
                check(identities.resolve(request.client.uid) == request.client.group)
                access.decide(request.client.uid, allowed)
            }; allowed
        } catch (failure: Exception) { android.util.Log.e("TsuyuServer", "Consent commit failed", failure); reportFailure(); false }
        try { deadline.call { request.client.callback.dispatchRequestPermissionResult(request.code, Bundle().apply { putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, result) }) } }
        catch (failure: Exception) { android.util.Log.e("TsuyuServer", "Permission callback failed", failure); reportFailure() }
    }
    override fun dispatchPermissionConfirmationResult(requestUid: Int, requestPid: Int, requestCode: Int, data: Bundle) {
        manager()
        val request = requests.values.singleOrNull { it.client.uid == requestUid && it.client.pid == requestPid && it.code == requestCode } ?: error("No unique pending request")
        complete(request.token, data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED))
    }
    fun accessList(): Bundle {
        manager()
        val decisions = shell { storage.read().filter { it.allowed && access.allowed(it.group.uid) } }
        return Bundle().apply { putIntArray("uids", decisions.map { it.group.uid }.toIntArray()); decisions.forEach { putStringArrayList(it.group.uid.toString(), ArrayList(it.group.members.keys)) } }
    }
    fun revoke(uid: Int) {
        manager()
        lifecycle.withUid(uid) { shell {
            val group = identities.resolve(uid)
            access.decide(uid, false)
            processes.entries.filter { it.value.uid == uid }.forEach { it.key.close(); processes.remove(it.key) }
            userServices.revoke(uid)
            group.members.keys.forEach { name -> deadline.call { ActivityManagerApis.forceStopPackage(name, uid / 100000) } }
        } }
    }
    override fun newProcess(cmd: Array<out String>, env: Array<out String>?, dir: String?): IRemoteProcess {
        return lifecycle.withUid(uid()) {
        val owner = allowed()
        require(cmd.isNotEmpty() && cmd.size <= 256 && cmd.all { it.length <= 32768 && '\u0000' !in it })
        val process = shell { ProcessBuilder(cmd.toList()).apply {
            if (dir != null) directory(java.io.File(dir))
            if (env != null) { environment().clear(); env.forEach { val index = it.indexOf('='); require(index > 0); environment()[it.substring(0, index)] = it.substring(index + 1) } }
        }.start() }
        val remote = RemoteProcess(process) {
            check(Binder.getCallingUid() == owner.uid && Binder.getCallingPid() == owner.pid) { "Process owner required" }
            allowed()
        }
        processes[remote] = owner
        remote
        }
    }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != BINDER_TRANSACTION_transact) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(BINDER_DESCRIPTOR)
        val owner = allowed()
        val target = requireNotNull(data.readStrongBinder())
        val targetCode = data.readInt()
        val targetFlags = if (owner.api >= 13) data.readInt() else flags
        require(targetCode > 0 && targetFlags and IBinder.FLAG_ONEWAY.inv() == 0)
        val payload = Parcel.obtain()
        try { payload.appendFrom(data, data.dataPosition(), data.dataAvail()); return shell { target.transact(targetCode, payload, reply, targetFlags) } }
        finally { payload.recycle() }
    }
    override fun addUserService(conn: IShizukuServiceConnection, args: Bundle): Int = lifecycle.withUid(uid()) { val owner = allowed(); shell { userServices.add(owner.uid, identities.resolve(owner.uid), owner.api, conn, args) } }
    override fun removeUserService(conn: IShizukuServiceConnection?, args: Bundle): Int { val owner = allowed(); return shell { userServices.unbind(owner.uid, identities.resolve(owner.uid), conn, args) } }
    override fun attachUserService(binder: IBinder, options: Bundle) {
        manager()
        userServices.attach(binder, requireNotNull(options.getString(USER_SERVICE_ARG_TOKEN)), options.getInt("childPid", -1))
    }
}
