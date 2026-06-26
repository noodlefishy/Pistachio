package io.cuttlefish.components

import io.cuttlefish.*
import kotlinx.coroutines.delay

class PhysicalMemory(val size: Int = 65_536) : MemoryManagement {
    private val internals: ShortArray = ShortArray(size) { 0 }
    override suspend fun read(address: Short): Short {
        delay(Clock.MEMORY_READ_TIME)
        return internals[address.toInt() and 0xFFFF]
    }

    override suspend fun write(address: Short, value: Short) {
        delay(Clock.MEMORY_WRITE_TIME)
        internals[address.toInt() and 0xFFFF] = value
    }
}