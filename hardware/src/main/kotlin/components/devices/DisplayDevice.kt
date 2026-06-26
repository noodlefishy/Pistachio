package io.cuttlefish.components.devices

import io.cuttlefish.*


class DisplayDevice : MemoryManagement {
    val dimensions = 8 to 8
    override suspend fun read(address: Short): Short {
        TODO("Not yet implemented")
    }

    override suspend fun write(address: Short, value: Short) {
        TODO("Not yet implemented")
    }
}