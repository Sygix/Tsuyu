/*
 * Copyright (c) 2021 RikkaW
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: MIT
 * Modified by Sygix from Shizuku-API/api/src/main/java/rikka/shizuku/RemoteProcessHolder.java.
 */

package fr.sygix.tsuyu.server

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteProcess(private val process: java.lang.Process, private val authorize: () -> Unit) : IRemoteProcess.Stub() {
    private val closed = AtomicBoolean()
    private val stdin = ParcelFileDescriptor.createPipe()
    private val stdout = ParcelFileDescriptor.createPipe()
    private val stderr = ParcelFileDescriptor.createPipe()
    init {
        pump(ParcelFileDescriptor.AutoCloseInputStream(stdin[0]), process.outputStream)
        pump(process.inputStream, ParcelFileDescriptor.AutoCloseOutputStream(stdout[1]))
        pump(process.errorStream, ParcelFileDescriptor.AutoCloseOutputStream(stderr[1]))
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
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        process.destroyForcibly()
        (stdin + stdout + stderr).forEach { it.close() }
        process.inputStream.close(); process.errorStream.close(); process.outputStream.close()
    }
}
