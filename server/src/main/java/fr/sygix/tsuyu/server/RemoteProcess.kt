/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/server-shared/src/main/java/rikka/shizuku/server/api/RemoteProcessHolder.java.
 */

package fr.sygix.tsuyu.server

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteProcess(
    private val process: java.lang.Process,
    private val released: (RemoteProcess) -> Unit = {},
    private val authorize: () -> Unit,
) : IRemoteProcess.Stub() {
    private val closed = AtomicBoolean()
    private val stdin = ParcelFileDescriptor.createPipe()
    private val stdout = ParcelFileDescriptor.createPipe()
    private val stderr = ParcelFileDescriptor.createPipe()
    init {
        pump(ParcelFileDescriptor.AutoCloseInputStream(stdin[0]), process.outputStream)
        pump(process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(stdout[1]))
        pump(process.errorStream, ParcelFileDescriptor.AutoCloseOutputStream(stderr[1]))
    }
    fun watch() {
        Thread {
            try {
                process.waitFor()
                stdin[0].close()
                stdin[1].close()
                process.outputStream.close()
            } finally { released(this) }
        }.apply { isDaemon = true; start() }
    }
    private fun pump(input: java.io.InputStream, output: java.io.OutputStream) {
        Thread {
            try { input.use { source -> output.use { target -> source.copyTo(target, 8192) } } }
            catch (failure: java.io.IOException) { if (!closed.get()) android.util.Log.e("TsuyuServer", "Process stream failed", failure) }
        }.apply { isDaemon = true; start() }
    }
    override fun getOutputStream(): ParcelFileDescriptor { authorize(); return stdin[1] }
    override fun getInputStream(): ParcelFileDescriptor { authorize(); return ParcelFileDescriptor.dup(stdout[0].fileDescriptor) }
    override fun getErrorStream(): ParcelFileDescriptor { authorize(); return ParcelFileDescriptor.dup(stderr[0].fileDescriptor) }
    override fun waitFor(): Int { authorize(); return process.waitFor() }
    override fun exitValue(): Int { authorize(); return process.exitValue() }
    override fun alive(): Boolean { authorize(); return process.isAlive }
    override fun waitForTimeout(timeout: Long, unit: String): Boolean {
        authorize(); require(timeout >= 0)
        return process.waitFor(timeout, TimeUnit.valueOf(unit))
    }
    override fun destroy() { authorize(); close() }
    override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
        try { return super.onTransact(code, data, reply, flags) }
        catch (failure: android.os.RemoteException) { throw failure }
        catch (failure: RuntimeException) { throw failure }
        catch (failure: Exception) { throw IllegalStateException("Process operation failed", failure) }
    }
    @Synchronized fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { OwnedProcess(process).terminate() }
        finally {
            try {
                Cleanup.run((stdin + stdout + stderr).map { descriptor -> { descriptor.close() } } + listOf(
                    { process.inputStream.close() },
                    { process.errorStream.close() },
                    { process.outputStream.close() },
                ))
            } finally { released(this) }
        }
    }
    protected fun finalize() { close() }
}
