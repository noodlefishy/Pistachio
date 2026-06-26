package io.cuttlefish.components

import io.cuttlefish.*
class PhysicalMemory(val size: Int = 65_536) : MemoryManagement {
    private val internals: ShortArray = ShortArray(size) { 0 }
    override fun read(address: Short): Short {
        return internals[address.toInt() and 0xFFFF]
    }

    override fun write(address: Short, value: Short) {
        internals[address.toInt() and 0xFFFF] = value
    }
}