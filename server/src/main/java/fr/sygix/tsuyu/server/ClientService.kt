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
import android.os.RemoteException
import android.os.SystemClock
import android.system.Os
import moe.shizuku.server.*
import rikka.shizuku.ShizukuApiConstants.*
import rikka.hidden.compat.ActivityManagerApis
import java.util.concurrent.ConcurrentHashMap

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
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val showRequest: (String, Int) -> Unit = { token, user ->
        val intent = Intent()
            .setComponent(ComponentName("fr.sygix.tsuyu", "fr.sygix.tsuyu.access.AccessActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("request", token)
        ActivityManagerApis.startActivity(intent, "com.android.shell", user)
    },
    private val stopClient: (String, Int) -> Unit = ActivityManagerApis::forceStopPackage,
) : IShizukuService.Stub() {
    private val clients = ConcurrentHashMap<Pair<Int, Int>, ClientRecord>()
    private val deaths = ConcurrentHashMap<ClientRecord, IBinder.DeathRecipient>()
    private val processes = RemoteProcesses { owner ->
        check(uid() == owner.uid) { "Process owner required" }
        check(shell { access.allowed(owner.uid) && identities.resolve(owner.uid) == owner.group }) { "Permission denied" }
    }
    private val lifecycle = PrivilegeLifecycle()
    private val requests = ConsentRequests(
        access, identities, deadline, lifecycle,
        { clients[it.uid to it.pid] == it }, elapsed, showRequest, onRequestEnded, reportFailure,
    )

    fun shutdown() {
        clients.values.toList().forEach(::detach)
        processes.close()
        userServices.close()
        requests.close()
    }
    private fun detach(record: ClientRecord) {
        clients.remove(record.uid to record.pid, record)
        deaths.remove(record)?.let { record.callback.asBinder().unlinkToDeath(it, 0) }
        requests.detach(record)
        processes.stop(record.uid, record.pid)
    }
    private fun manager() { if (uid() != managerUid) throw SecurityException("Manager identity required") }
    private fun client(uidOnly: Boolean = false): ClientRecord {
        val callerUid = uid()
        val callerPid = pid()
        return clients[callerUid to callerPid] ?: if (uidOnly && callerPid == 0) {
            clients.values.firstOrNull { it.uid == callerUid && it.api >= API_VERSION && it.callback.asBinder().isBinderAlive }
                ?: throw SecurityException("Attach first")
        } else throw SecurityException("Attach first")
    }
    private fun allowed(uidOnly: Boolean = false): ClientRecord = client(uidOnly).also { record ->
        if (!shell { access.allowed(record.uid) && identities.resolve(record.uid) == record.group }) throw SecurityException("Permission denied")
    }
    private fun <T> shell(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        try { return block() } finally { Binder.restoreCallingIdentity(token) }
    }
    private fun context(): String = securityContext()
    override fun attachApplication(application: IShizukuApplication, args: Bundle) {
        val uid = uid()
        val pid = pid()
        val group = shell { identities.resolve(uid) }
        require(args.getString(ATTACH_APPLICATION_PACKAGE_NAME) in group.members) { "Package does not belong to caller" }
        val record = ClientRecord(
            uid, pid, group, args.getInt(ATTACH_APPLICATION_API_VERSION), application,
            requireNotNull(args.getString(ATTACH_APPLICATION_PACKAGE_NAME)),
        )
        val key = uid to pid
        clients[key]?.let(::detach)
        val death = IBinder.DeathRecipient { detach(record) }
        application.asBinder().linkToDeath(death, 0)
        deaths[record] = death
        clients[key] = record
        try {
            val result = shell {
                Bundle().apply {
                    putInt(BIND_APPLICATION_SERVER_VERSION, API_VERSION)
                    putInt(BIND_APPLICATION_SERVER_PATCH_VERSION, PATCH_VERSION)
                    putInt(BIND_APPLICATION_SERVER_UID, Os.getuid())
                    putString(BIND_APPLICATION_SERVER_SECONTEXT, context())
                    putBoolean(BIND_APPLICATION_PERMISSION_GRANTED, access.allowed(uid))
                    putBoolean(BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, access.rationale(uid))
                }
            }
            deadline.call(Deadline.Use.CALLBACK) { application.bindApplication(result) }
        } catch (failure: Exception) { detach(record); throw IllegalStateException("Attachment failed", failure) }
    }
    override fun getVersion(): Int { allowed(); return API_VERSION }
    override fun getUid(): Int { allowed(); return Os.getuid() }
    override fun getSELinuxContext(): String { allowed(); return shell { context() } }
    override fun checkPermission(permission: String): Int {
        allowed()
        return shell { deadline.call { ActivityManagerApis.checkPermission(permission, android.os.Process.myPid(), Os.getuid()) } }
    }
    override fun getSystemProperty(name: String, defaultValue: String?): String {
        allowed()
        return shell {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
                .invoke(null, name, defaultValue ?: "") as String
        }
    }
    override fun setSystemProperty(name: String, value: String?) {
        allowed()
        shell {
            Class.forName("android.os.SystemProperties")
                .getMethod("set", String::class.java, String::class.java)
                .invoke(null, name, value ?: "")
        }
    }
    override fun checkSelfPermission(): Boolean { val uid = client().uid; return shell { access.allowed(uid) } }
    override fun shouldShowRequestPermissionRationale(): Boolean { val uid = client().uid; return shell { access.rationale(uid) } }
    override fun isHidden(uid: Int): Boolean { if (this.uid() != managerUid) client(); return false }
    override fun dispatchPackageChanged(intent: Intent?) { manager() }
    override fun exit() { manager(); stop() }
    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        manager()
        return shell { (if (access.allowed(uid)) FLAG_ALLOWED else if (access.rationale(uid)) FLAG_DENIED else 0) and mask }
    }
    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        manager()
        require(mask and PERMISSION_MASK == mask)
        if (mask == 0) return
        lifecycle.withUid(uid) {
            val old = getFlagsForUid(uid, PERMISSION_MASK)
            val next = (old and mask.inv()) or (value and mask)
            require(next != PERMISSION_MASK) { "Conflicting permission flags" }
            if (next == old) return@withUid
            if (next and FLAG_ALLOWED != 0) shell { access.decide(uid, true) }
            else { revoke(uid); if (next == FLAG_DENIED) shell { access.decide(uid, false) } }
        }
    }
    override fun requestPermission(requestCode: Int) {
        requests.enqueue(client(), requestCode)
    }
    fun request(token: String): Bundle {
        manager()
        return requests.details(token)
    }
    fun confirm(token: String, allowed: Boolean) { manager(); requests.complete(token, allowed) }
    override fun dispatchPermissionConfirmationResult(requestUid: Int, requestPid: Int, requestCode: Int, data: Bundle) {
        manager()
        requests.confirm(requestUid, requestPid, requestCode, data)
    }
    fun accessList(): Bundle {
        manager()
        val decisions = shell { storage.read().filter { it.allowed && access.allowed(it.group.uid) } }
        return Bundle().apply {
            putIntArray("uids", decisions.map { it.group.uid }.toIntArray())
            decisions.forEach { putStringArrayList(it.group.uid.toString(), ArrayList(it.group.members.keys)) }
        }
    }
    fun revoke(uid: Int) {
        manager()
        lifecycle.withUid(uid) {
            shell {
                val group = identities.resolve(uid)
                access.clear(uid)
                Cleanup.run(
                    listOf({ processes.stop(uid) }, { userServices.revoke(uid) }) +
                        group.members.keys.map { name -> { deadline.call { stopClient(name, uid / PER_USER_RANGE) } } }
                )
            }
        }
    }
    override fun newProcess(cmd: Array<out String>, env: Array<out String>?, dir: String?): IRemoteProcess {
        return lifecycle.withUid(uid()) {
            val owner = allowed()
            shell { processes.create(owner, cmd, env, dir) }
        }
    }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        try {
            if (code != BINDER_TRANSACTION_transact) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(BINDER_DESCRIPTOR)
            val owner = allowed(flags and IBinder.FLAG_ONEWAY != 0)
            val target = requireNotNull(data.readStrongBinder())
            val targetCode = data.readInt()
            val targetFlags = if (owner.api >= API_VERSION) data.readInt() else flags
            require(targetCode > 0 && targetFlags and IBinder.FLAG_ONEWAY.inv() == 0)
            val payload = Parcel.obtain()
            try {
                payload.appendFrom(data, data.dataPosition(), data.dataAvail())
                return shell { target.transact(targetCode, payload, reply, targetFlags) }
            } finally { payload.recycle() }
        } catch (failure: RemoteException) { throw failure }
        catch (failure: RuntimeException) { throw failure }
        catch (failure: Exception) { reportFailure(); throw IllegalStateException("System operation failed; check the service and retry", failure) }
    }
    override fun addUserService(conn: IShizukuServiceConnection, args: Bundle): Int = lifecycle.withUid(uid()) {
        val owner = allowed()
        shell { userServices.add(owner.uid, identities.resolve(owner.uid), owner.api, conn, args) }
    }
    override fun removeUserService(conn: IShizukuServiceConnection?, args: Bundle): Int {
        val owner = allowed()
        return shell { userServices.unbind(owner.uid, identities.resolve(owner.uid), conn, args) }
    }
    override fun attachUserService(binder: IBinder, options: Bundle) {
        manager()
        userServices.attach(binder, requireNotNull(options.getString(USER_SERVICE_ARG_TOKEN)), options.getInt("childPid", -1))
    }
    companion object {
        private const val API_VERSION = 13
        private const val PATCH_VERSION = 6
        private const val FLAG_ALLOWED = 2
        private const val FLAG_DENIED = 4
        private const val PERMISSION_MASK = FLAG_ALLOWED or FLAG_DENIED
        private const val PER_USER_RANGE = 100_000
    }
}
