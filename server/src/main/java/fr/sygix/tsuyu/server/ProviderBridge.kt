/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.AttributionSource
import android.os.Bundle
import android.os.IBinder

internal class ProviderBridge(private val userId: Int) {
    fun publish(server: IBinder, token: String, generation: String): IBinder {
        return call("fr.sygix.tsuyu.manager", "sendServer", Bundle().apply {
            putBinder("server", server); putString("token", token); putString("generation", generation)
        })?.getBinder("client") ?: error("Manager rejected handshake")
    }
    fun call(authority: String, method: String, extra: Bundle): Bundle? {
        val manager = Class.forName("android.app.ActivityManager").getDeclaredMethod("getService").invoke(null)
        val type = Class.forName("android.app.IActivityManager")
        val acquire = type.getMethod("getContentProviderExternal", String::class.java, Int::class.javaPrimitiveType, IBinder::class.java, String::class.java)
        val holder = acquire.invoke(manager, authority, userId, null, "tsuyu") ?: throw ProviderUnavailable(authority)
        try {
            val provider = holder.javaClass.getField("provider").get(holder) ?: throw ProviderUnavailable(authority)
            val source = AttributionSource.Builder(2000).setPackageName("com.android.shell").build()
            val call = Class.forName("android.content.IContentProvider").getMethod("call", AttributionSource::class.java, String::class.java, String::class.java, String::class.java, Bundle::class.java)
            return call.invoke(provider, source, authority, method, null, extra) as? Bundle
        } finally {
            type.getMethod("removeContentProviderExternal", String::class.java, IBinder::class.java).invoke(manager, authority, null)
        }
    }
}

internal class ProviderUnavailable(authority: String) : IllegalStateException("Provider unavailable: $authority")
