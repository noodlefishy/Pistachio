package io.cuttlefish.components

import io.cuttlefish.*

class PhysicalMemory(val size: Short = 1024) : MemoryManagement {
    private val internals: ShortArray = ShortArray(size.toInt()) { 0 }
    override fun read(address: Short): Short {
        return internals[address.toInt()]
    }

    override fun write(address: Short, value: Short) {
        internals[address.toInt()] = value
    }
}