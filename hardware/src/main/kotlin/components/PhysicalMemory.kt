package io.cuttlefish.components

import io.cuttlefish.*
import kotlinx.coroutines.*

class PhysicalMemory(size: Int = 65_536) : MemoryManagement {
    private val internals: ShortArray = ShortArray(size) { 0 }
    override suspend fun read(address: UShort): Short {
        delay(Clock.MEMORY_READ_TIME)
        return internals[address.toInt() and 0xFFFF]
    }

    override suspend fun write(address: UShort, value: Short) {
        delay(Clock.MEMORY_WRITE_TIME)
        internals[address.toInt() and 0xFFFF] = value
    }
}