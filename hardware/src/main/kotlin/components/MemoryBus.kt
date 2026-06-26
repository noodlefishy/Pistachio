package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.components.devices.*

class MemoryBus(val ram: PhysicalMemory, val display: DisplayDevice) : MemoryManagement {


    override fun read(address: Short): Short {
        return when (address) {
            in MemoryMapRanges.vectorRange -> 0 // I just don't want the error
            in MemoryMapRanges.kernalRange -> TODO()
            in MemoryMapRanges.userLandRange -> TODO()
            in MemoryMapRanges.stackRange -> TODO()
            in MemoryMapRanges.mmioRange -> TODO()
            else -> error("Unknown addresses?")
        }
    }

    override fun write(address: Short, value: Short) {
        when (address) {
            in MemoryMapRanges.vectorRange -> 0 // I just don't want the error
            in MemoryMapRanges.kernalRange -> TODO()
            in MemoryMapRanges.userLandRange -> TODO()
            in MemoryMapRanges.stackRange -> TODO()
            in MemoryMapRanges.mmioRange -> TODO()
            else -> error("Unknown addresses?")
        }
    }
}

object MemoryMapRanges {
    val vectorRange = IntRange(0x0000, 0x000F) // 16
    val kernalRange = IntRange(0x0010, 0x0FFF) // 4 080
    val userLandRange = IntRange(0x1000, 0xEFFF) // 57 344
    val stackRange = IntRange(0xF000, 0xFEFF) // 3 840
    val mmioRange = IntRange(0xFF00, 0xFFFF) // 256

}