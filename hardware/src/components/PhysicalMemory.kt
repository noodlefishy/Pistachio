package io.cuttlefish.components

import io.cuttlefish.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class PhysicalMemory(size: Int = 65_536) : MemoryManagement {
    val internals: ShortArray = ShortArray(size) { 0 }
    override suspend fun read(address: UShort): Short {
        if (Clock.MEMORY_READ_TIME > 0L) delay(Clock.MEMORY_READ_TIME.milliseconds)
        return internals[address.toInt() and 0xFFFF]
    }

    override suspend fun write(address: UShort, value: Short) {
        if (Clock.MEMORY_WRITE_TIME > 0L) delay(Clock.MEMORY_WRITE_TIME.milliseconds)
        internals[address.toInt() and 0xFFFF] = value
    }
}