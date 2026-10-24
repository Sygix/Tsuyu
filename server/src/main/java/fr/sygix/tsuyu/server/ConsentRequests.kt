/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/server-shared/src/main/java/rikka/shizuku/server/Service.java.
 */

package fr.sygix.tsuyu.server

import android.os.Binder
import android.os.Bundle
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED

internal class ConsentRequests(
    private val access: AccessAuthority,
    private val identities: ClientIdentity,
    private val deadline: Deadline,
    private val lifecycle: PrivilegeLifecycle,
    private val live: (ClientRecord) -> Boolean,
    private val elapsed: () -> Long,
    private val show: (String, Int) -> Unit,
    private val ended: (String) -> Unit,
    private val failed: () -> Unit,
) : AutoCloseable {
    private data class Request(
        val token: String,
        val client: ClientRecord,
        val code: Int,
        val expires: Long,
        val group: AccessGroup,
    )
    private val requests = ConcurrentHashMap<String, Request>()
    private val events = Executors.newSingleThreadScheduledExecutor()

    val idle: Boolean get() = requests.isEmpty()

    fun detach(client: ClientRecord) {
        requests.values.filter { it.client == client }.forEach { requests.remove(it.token); ended(it.token) }
    }

    fun cancel(uid: Int) {
        requests.values.filter { it.client.uid == uid }.forEach { complete(it.token, false) }
    }

    fun enqueue(client: ClientRecord, code: Int) {
        val request = lifecycle.withUid(client.uid) {
            shell {
                val current = identities.resolve(client.uid)
                check(current.members[client.packageName] == client.group.members[client.packageName]) {
                    "Client identity changed; reopen the application"
                }
                if (access.allowed(client.uid)) null else synchronized(requests) {
                    check(requests.size < MAX_PENDING) { "Too many pending requests" }
                    check(requests.values.count { it.client.uid == client.uid } < MAX_PER_UID) {
                        "This client already has pending requests"
                    }
                    check(requests.values.none { it.client == client && it.code == code }) { "Request already pending" }
                    Request(UUID.randomUUID().toString(), client, code, elapsed() + DECISION_TIMEOUT_MS, current)
                        .also { requests[it.token] = it }
                }
            }
        }
        if (request == null) {
            respond(client, code, true)
            return
        }
        events.schedule({ complete(request.token, false) }, DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        try { shell { deadline.call { show(request.token, client.uid / PER_USER_RANGE) } } }
        catch (failure: Exception) {
            complete(request.token, false)
            throw IllegalStateException("Open Tsuyu and retry permission", failure)
        }
    }

    fun details(token: String): Bundle {
        val request = requests[token] ?: error("Request expired")
        if (elapsed() >= request.expires) { complete(token, false); error("Request expired") }
        check(shell { identities.resolve(request.client.uid) } == request.group) { "Identity changed" }
        return Bundle().apply {
            putInt("uid", request.client.uid)
            putStringArrayList("packages", ArrayList(listOf(request.client.packageName) + request.group.members.keys.filterNot { it == request.client.packageName }))
            putString("token", request.token)
        }
    }

    fun complete(token: String, allowed: Boolean) {
        val owner = requests[token]?.client ?: return
        val completed = lifecycle.withUid(owner.uid) {
            val request = requests.remove(token) ?: return@withUid emptyList<Pair<Request, Boolean>>()
            val now = elapsed()
            val targets = mutableListOf(request)
            var result = false
            try {
                if (live(request.client)) shell {
                    check(identities.resolve(request.client.uid) == request.group) { "Consent identity changed" }
                    if (now < request.expires) synchronized(requests) {
                        requests.values.filter { it.group == request.group }.toList().forEach { pending ->
                            if (requests.remove(pending.token, pending)) targets.add(pending)
                        }
                    }
                    if (now < request.expires || !access.allowed(request.client.uid)) {
                        access.decide(request.group, allowed && now < request.expires)
                    }
                    result = allowed && now < request.expires
                }
            } catch (failure: Exception) {
                Log.e("TsuyuServer", "Consent commit failed", failure)
                failed()
            }
            targets.map { it to (result && now < it.expires) }
        }
        completed.forEach { (request, granted) ->
            ended(request.token)
            if (live(request.client)) {
                try { respond(request.client, request.code, granted) }
                catch (failure: Exception) {
                    Log.e("TsuyuServer", "Permission callback failed", failure)
                    failed()
                }
            }
        }
    }

    fun confirm(uid: Int, pid: Int, code: Int, data: Bundle) {
        val request = requests.values.singleOrNull { it.client.uid == uid && it.client.pid == pid && it.code == code }
            ?: error("No unique pending request")
        complete(request.token, data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED))
    }

    private fun respond(client: ClientRecord, code: Int, granted: Boolean) {
        deadline.call(Deadline.Use.CALLBACK) {
            client.callback.dispatchRequestPermissionResult(code, Bundle().apply {
                putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, granted)
            })
        }
    }

    override fun close() { events.shutdownNow() }

    private fun <T> shell(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        try { return block() } finally { Binder.restoreCallingIdentity(token) }
    }

    companion object {
        private const val MAX_PENDING = 8
        private const val MAX_PER_UID = 2
        private const val DECISION_TIMEOUT_MS = 120_000L
        private const val PER_USER_RANGE = 100_000
    }
}
