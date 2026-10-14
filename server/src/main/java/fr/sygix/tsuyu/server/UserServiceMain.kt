/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/server-shared/src/main/java/rikka/shizuku/server/UserService.java.
 */

package fr.sygix.tsuyu.server

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.UserHandle
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import kotlin.system.exitProcess

object UserServiceMain {
    @JvmStatic fun main(args: Array<String>) {
        try {
            check(android.system.Os.getuid() == 2000)
            require(args.size == 5)
            val runtime = Class.forName("dalvik.system.VMRuntime")
            runtime.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java).invoke(runtime.getDeclaredMethod("getRuntime").invoke(null), arrayOf("L"))
            Looper.prepareMainLooper()
            val thread = Class.forName("android.app.ActivityThread")
            val activityThread = thread.getDeclaredMethod("systemMain").invoke(null)
            val context = thread.getDeclaredMethod("getSystemContext").invoke(activityThread) as Context
            val user = UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(args[2].toInt())
            val packageContext = Context::class.java.getMethod("createPackageContextAsUser", String::class.java, Int::class.javaPrimitiveType, UserHandle::class.java).invoke(context, args[0], Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY, user) as Context
            val loadedApk = requireNotNull(packageContext.javaClass.getDeclaredField("mPackageInfo").apply { isAccessible = true }.get(packageContext))
            val application = loadedApk.javaClass.getDeclaredMethod("makeApplication", Boolean::class.javaPrimitiveType, android.app.Instrumentation::class.java).invoke(loadedApk, true, null) as android.app.Application
            thread.getDeclaredField("mInitialApplication").apply { isAccessible = true }.set(activityThread, application)
            val type = application.classLoader.loadClass(args[1])
            val instance = try { type.getConstructor(Context::class.java).newInstance(application) } catch (missing: NoSuchMethodException) { type.getConstructor().newInstance() }
            val binder = when (instance) { is IBinder -> instance; is IInterface -> instance.asBinder(); else -> error("Service must expose a binder") }
            val deadline = Deadline()
            val result = deadline.call { ProviderBridge(args[3].toInt()).call("fr.sygix.tsuyu.manager", "attachUserService", Bundle().apply { putString(USER_SERVICE_ARG_TOKEN, args[4]); putBinder("service", binder) }) }
            val server = requireNotNull(result?.getBinder("server"))
            server.linkToDeath({ exitProcess(0) }, 0)
            check(server.isBinderAlive)
            deadline.close()
            Looper.loop()
        } catch (failure: Throwable) { android.util.Log.e("TsuyuServer", "UserService startup failed", failure); exitProcess(1) }
    }
}
