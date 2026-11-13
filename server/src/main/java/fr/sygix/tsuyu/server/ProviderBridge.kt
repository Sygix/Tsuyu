/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Build
import android.os.Bundle
import android.os.IBinder
import rikka.hidden.compat.ActivityManagerApis

internal class ProviderBridge(
    private val userId: Int,
    private val acquire: (String, Int) -> Any? = { authority, user ->
        ActivityManagerApis.getContentProviderExternal(authority, user, null, "tsuyu")
    },
    private val release: (String) -> Unit = { ActivityManagerApis.removeContentProviderExternal(it, null) },
    private val invoke: (Any, String, String, Bundle) -> Bundle? = ProviderCall::call,
) {
    fun publish(server: IBinder, token: String, generation: String): IBinder {
        return call("fr.sygix.tsuyu.manager", "sendServer", Bundle().apply {
            putBinder("server", server); putString("token", token); putString("generation", generation)
        })?.getBinder("client") ?: error("Manager rejected handshake")
    }
    fun call(authority: String, method: String, extra: Bundle): Bundle? {
        val provider = acquire(authority, userId) ?: throw ProviderUnavailable(authority)
        try {
            return invoke(provider, authority, method, extra)
        } finally {
            release(authority)
        }
    }
}

internal class ProviderUnavailable(authority: String) : IllegalStateException("Provider unavailable: $authority")

internal object ProviderCall {
    fun call(provider: Any, authority: String, method: String, extra: Bundle): Bundle? {
        val type = Class.forName("android.content.IContentProvider")
        if (Build.VERSION.SDK_INT >= 31) return AttributionProviderCall.call(type, provider, authority, method, extra)
        val identity = when {
            Build.VERSION.SDK_INT >= 30 -> arrayOf("com.android.shell", null, authority)
            Build.VERSION.SDK_INT >= 29 -> arrayOf("com.android.shell", authority)
            else -> arrayOf("com.android.shell")
        }
        val types = Array<Class<*>>(identity.size + 2) { String::class.java } + Bundle::class.java
        return type.getMethod("call", *types).invoke(provider, *identity, method, null, extra) as? Bundle
    }
}

private object AttributionProviderCall {
    @android.annotation.TargetApi(31)
    fun call(type: Class<*>, provider: Any, authority: String, method: String, extra: Bundle): Bundle? {
        val source = android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build()
        return type.getMethod("call", android.content.AttributionSource::class.java, String::class.java,
            String::class.java, String::class.java, Bundle::class.java)
            .invoke(provider, source, authority, method, null, extra) as? Bundle
    }
}
