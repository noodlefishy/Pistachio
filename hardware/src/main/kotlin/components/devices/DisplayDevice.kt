package io.cuttlefish.components.devices

import io.cuttlefish.*


data class Dimensions(val width: Short, val height: Short)
class DisplayDevice : MemoryManagement {
    val dimensions = Dimensions(8, 8)
    override fun read(address: Short): Short {
        TODO("Not yet implemented")
    }

    override fun write(address: Short, value: Short) {
        TODO("Not yet implemented")
    }
}