package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.components.devices.*

class MemoryBus(val ram: PhysicalMemory, val display: DisplayDevice) : MemoryManagement {
    val space = ram.size
    val spaceMMIO = (display.dimensions.height * display.dimensions.width).toShort()

    val userAddressSpace = IntRange(0, space.toInt() - spaceMMIO - 1)
    val mmioAddressSpace = IntRange(space.toInt() - spaceMMIO, space.toInt())

    override fun read(address: Short): Short {
        return when (address) {
            in userAddressSpace -> ram.read(address)
            in mmioAddressSpace -> display.read(address)
            else -> error("Invalid address: $address")
        }
    }

    override fun write(address: Short, value: Short) {
        when (address) {
            in userAddressSpace -> ram.write(address, value)
            in mmioAddressSpace -> display.write(address,value)
            else -> error("Invalid address: $address")
        }
    }
}