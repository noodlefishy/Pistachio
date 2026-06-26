package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.components.devices.*

class MemoryBus(val ram: PhysicalMemory, val display: DisplayDevice) : MemoryManagement {


    override suspend fun read(address: Short): Short {
        return when (address) {
            in MemoryMapRanges.vectorRange -> ram.read(address)
            in MemoryMapRanges.kernalRange -> ram.read(address)
            in MemoryMapRanges.userLandRange -> ram.read(address)
            in MemoryMapRanges.stackRange -> ram.read(address)
            in MemoryMapRanges.mmioRange -> display.read(address)
            else -> error("Unknown addresses?")
        }
    }

    override suspend fun write(address: Short, value: Short) {
        when (address) {
            in MemoryMapRanges.vectorRange -> ram.write(address, value)
            in MemoryMapRanges.kernalRange -> ram.write(address, value)
            in MemoryMapRanges.userLandRange -> ram.write(address, value)
            in MemoryMapRanges.stackRange -> ram.write(address, value)
            in MemoryMapRanges.mmioRange -> display.write(address, value)
            else -> error("Unknown addresses?")
        }
    }
}

object MemoryMapRanges { // 64 KB
    val vectorRange = IntRange(0x0000, 0x000F)    // | 16     | // 0,015625 kb |
    val kernalRange = IntRange(0x0010, 0x0FFF)    // | 4 080  | // 3,984375 kb |
    val userLandRange = IntRange(0x1000, 0xEFFF)  // | 57 344 | // 56       kb |
    val stackRange = IntRange(0xF000, 0xFEFF)     // | 3 840  | // 3,75     kb |
    val mmioRange = IntRange(0xFF00, 0xFFFF)      // | 256    | // 0,25     kb |

}