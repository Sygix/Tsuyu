/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.io.DataInputStream
import java.io.DataOutputStream

internal object ProcessProtocol {
    const val SESSION = 1
    const val EXECUTED = 2
    const val FAILED = 3
    const val EXITED = 4
    const val DEADLINE_MS = 10_000
    private const val MAX_BYTES = 1_048_576
    private const val MAX_VALUES = 4096

    fun writeText(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES && '\u0000' !in value)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    fun readText(input: DataInputStream): String {
        val size = input.readInt()
        require(size in 0..MAX_BYTES)
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8).also {
            require('\u0000' !in it)
        }
    }

    fun writeValues(output: DataOutputStream, values: Array<out String>?) {
        require(values == null || values.size <= MAX_VALUES)
        output.writeInt(values?.size ?: -1)
        values?.forEach { writeText(output, it) }
    }

    fun readValues(input: DataInputStream): Array<String>? {
        val size = input.readInt()
        require(size in -1..MAX_VALUES)
        return if (size < 0) null else Array(size) { readText(input) }
    }
}
