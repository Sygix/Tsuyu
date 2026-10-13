/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Looper
import android.os.Process
import android.system.Os
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object ServerMain {
    private const val PACKAGE = "fr.sygix.tsuyu"
    private var packageWatch: PackageWatch? = null
    @JvmStatic fun main(args: Array<String>) {
        try { run(args) } catch (error: Throwable) {
            Log.e("TsuyuServer", "Startup failed", error)
            exitProcess(1)
        }
    }
    private fun run(args: Array<String>) {
        check(Os.getuid() == 2000) { "Shell uid required" }
        require(args.size == 3 && args[1].matches(Regex("[a-f0-9]{32}")) && args[2].matches(Regex("[a-f0-9]{32}")))
        val runtime = Class.forName("dalvik.system.VMRuntime")
        val instance = runtime.getDeclaredMethod("getRuntime").invoke(null)
        runtime.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java).invoke(instance, arrayOf("L"))
        val userId = Os.getuid() / 100000
        val socketName = "$PACKAGE.server.$userId"
        val socket = try { LocalServerSocket(socketName) } catch (occupied: java.io.IOException) {
            LocalSocket().use { peer ->
                peer.connect(LocalSocketAddress(socketName), 5000)
                peer.soTimeout = 5000
                check(peer.peerCredentials.uid == 2000)
                DataOutputStream(peer.outputStream).apply { writeUTF(args[1]); writeUTF(args[2]); flush() }
                check(DataInputStream(peer.inputStream).readBoolean()) { "Existing server handoff failed" }
            }
            return
        }
        Looper.prepareMainLooper()
        val thread = Class.forName("android.app.ActivityThread")
        val activityThread = thread.getDeclaredMethod("systemMain").invoke(null)
        val context = thread.getDeclaredMethod("getSystemContext").invoke(activityThread) as Context
        val executor = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue())
        val info = executor.submit<android.content.pm.ApplicationInfo> { context.packageManager.getApplicationInfo(PACKAGE, 0) }.get(5, TimeUnit.SECONDS)
        check(info.sourceDir == args[0]) { "Installed APK changed" }
        val managerUid = info.uid
        val bridge = ProviderBridge(managerUid / 100000)
        val events = Executors.newSingleThreadScheduledExecutor()
        val ready = AtomicBoolean(false)
        val binder = ServerControl(managerUid, ready::get, { exitProcess(0) })
        fun snapshot(): PackageStamp? = try {
            val installed = context.packageManager.getPackageInfo(PACKAGE, 0)
            PackageStamp(requireNotNull(installed.applicationInfo).sourceDir, installed.lastUpdateTime, requireNotNull(installed.applicationInfo).uid)
        } catch (missing: android.content.pm.PackageManager.NameNotFoundException) { null }
        val installed = executor.submit<PackageStamp?> { snapshot() }.get(5, TimeUnit.SECONDS)
        val watch = PackageWatch(requireNotNull(installed), ::snapshot) { exitProcess(0) }
        packageWatch = watch
        check(executor.submit<Boolean> { watch.reconcile() }.get(5, TimeUnit.SECONDS))
        var generation = args[2]
        lateinit var reconnect: (Int) -> Unit
        fun publish(nextGeneration: String) {
            val call = executor.submit<android.os.IBinder> { bridge.publish(binder, args[1], nextGeneration) }
            val client = try { call.get(5, TimeUnit.SECONDS) } finally { if (!call.isDone) call.cancel(true) }
            val checkPackage = executor.submit<Boolean> { watch.reconcile() }
            checkPackage.get(5, TimeUnit.SECONDS)
            generation = nextGeneration
            client.linkToDeath({ ready.set(false); events.schedule({ reconnect(0) }, 250, TimeUnit.MILLISECONDS) }, 0)
            ready.set(true)
        }
        reconnect = { attempt ->
            try {
                val reconcile = executor.submit<Boolean> { watch.reconcile() }
                try { reconcile.get(5, TimeUnit.SECONDS) } finally { if (!reconcile.isDone) reconcile.cancel(true) }
                publish(generation)
            } catch (failure: Throwable) {
                Log.e("TsuyuServer", "Manager lifecycle reconciliation failed", failure)
                if (attempt < 2) events.schedule({ reconnect(attempt + 1) }, 500, TimeUnit.MILLISECONDS)
                else exitProcess(1)
            }
        }
        publish(generation)
        Thread {
            try {
                while (true) socket.accept().use { peer ->
                    peer.soTimeout = 5000
                    if (peer.peerCredentials.uid == 2000) {
                        val input = DataInputStream(peer.inputStream)
                        val token = input.readUTF()
                        val next = input.readUTF()
                        val valid = token == args[1] && next.matches(Regex("[a-f0-9]{32}"))
                        if (valid) publish(next)
                        DataOutputStream(peer.outputStream).apply { writeBoolean(valid); flush() }
                    }
                }
            } catch (failure: Throwable) {
                Log.e("TsuyuServer", "Handoff failed", failure)
                exitProcess(1)
            }
        }.start()
        Log.i("TsuyuServer", "Server ready uid=${Process.myUid()}")
        Looper.loop()
        socket.close()
        executor.shutdownNow()
        events.shutdownNow()
    }
}
