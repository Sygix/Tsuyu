/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/server-shared/src/main/java/rikka/shizuku/server/api/RemoteProcessHolder.java.
 */

package fr.sygix.tsuyu.server

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import moe.shizuku.server.IRemoteProcess

internal class RemoteProcess(
    process: Process,
    released: (Resources) -> Unit = {},
    private val authorize: () -> Unit,
) : IRemoteProcess.Stub() {
    val resources = Resources(process, released)
    private val process get() = resources.process

    fun watch() = resources.watch()
    override fun getOutputStream(): ParcelFileDescriptor { authorize(); return resources.stdin[1] }
    override fun getInputStream(): ParcelFileDescriptor { authorize(); return ParcelFileDescriptor.dup(resources.stdout[0].fileDescriptor) }
    override fun getErrorStream(): ParcelFileDescriptor { authorize(); return ParcelFileDescriptor.dup(resources.stderr[0].fileDescriptor) }
    override fun waitFor(): Int { authorize(); return process.waitFor() }
    override fun exitValue(): Int { authorize(); return process.exitValue() }
    override fun alive(): Boolean { authorize(); return process.isAlive }
    override fun waitForTimeout(timeout: Long, unit: String): Boolean {
        authorize()
        require(timeout >= 0)
        return process.waitFor(timeout, TimeUnit.valueOf(unit))
    }
    override fun destroy() { authorize(); close() }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        try { return super.onTransact(code, data, reply, flags) }
        catch (failure: RemoteException) { throw failure }
        catch (failure: RuntimeException) { throw failure }
        catch (failure: Exception) { throw IllegalStateException("Process operation failed", failure) }
    }

    fun close() = resources.close()
    protected fun finalize() { resources.close() }

    class Resources(val process: Process, private val released: (Resources) -> Unit) {
        private val closed = AtomicBoolean()
        private val stdinFinished = AtomicBoolean()
        val stdin = ParcelFileDescriptor.createPipe()
        val stdout = ParcelFileDescriptor.createPipe()
        val stderr = ParcelFileDescriptor.createPipe()

        init {
            pump(ParcelFileDescriptor.AutoCloseInputStream(stdin[0]), process.outputStream) { stdinFinished.get() }
            pump(process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(stdout[1]))
            pump(process.errorStream, ParcelFileDescriptor.AutoCloseOutputStream(stderr[1]))
        }

        fun watch() {
            Thread {
                try {
                    process.waitFor()
                    stdinFinished.set(true)
                    stdin[0].close()
                    stdin[1].close()
                    process.outputStream.close()
                } catch (failure: Exception) {
                    Log.e("TsuyuServer", "Process completion cleanup failed", failure)
                }
            }.apply { isDaemon = true; start() }
        }

        private fun pump(input: InputStream, output: OutputStream, expectedClosure: () -> Boolean = { false }) {
            Thread {
                try { input.use { source -> output.use { target -> source.copyTo(target, BUFFER_SIZE) } } }
                catch (failure: java.io.IOException) {
                    if (!closed.get() && !expectedClosure()) Log.e("TsuyuServer", "Process stream failed", failure)
                }
            }.apply { isDaemon = true; start() }
        }

        @Synchronized fun close() {
            if (closed.get()) return
            OwnedProcess(process).terminate()
            closed.set(true)
            try {
                Cleanup.run((stdin + stdout + stderr).map { descriptor -> { descriptor.close() } } + listOf(
                    { process.inputStream.close() },
                    { process.errorStream.close() },
                    { process.outputStream.close() },
                ))
            } finally { released(this) }
        }

        companion object { private const val BUFFER_SIZE = 8192 }
    }
}
