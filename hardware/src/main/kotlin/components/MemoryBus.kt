package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.components.devices.*
import kotlin.experimental.*

class MemoryBus(val ram: PhysicalMemory, val display: DisplayDevice) : MemoryManagement {


    override suspend fun read(address: Short): Short {
        return when (address and 0xFFFF.toShort()) {
            in MemoryMapRanges.vectorRange -> ram.read(address)
            in MemoryMapRanges.kernalRange -> ram.read(address)
            in MemoryMapRanges.userLandRange -> ram.read(address)
            in MemoryMapRanges.mmioRange -> display.read(address)
            else -> error("Unknown addresses?")
        }
    }

    override suspend fun write(address: Short, value: Short) {
        when (address and 0xFFFF.toShort()) {
            in MemoryMapRanges.vectorRange -> ram.write(address, value)
            in MemoryMapRanges.kernalRange -> ram.write(address, value)
            in MemoryMapRanges.userLandRange -> ram.write(address, value)
            in MemoryMapRanges.mmioRange -> display.write(address, value)
            else -> error("Unknown addresses?")
        }
    }
}

object MemoryMapRanges { // 64 KB
    val vectorRange: IntRange   = 0x0000..0x003F // 0,0625  kb | 64w
    val kernalRange: IntRange   = 0x0040..0x2FFF // 11,9375 kb | 12 224w
    val userLandRange: IntRange = 0x3000..0xFDFF // 51,5    kb | 52 736w
    val mmioRange: IntRange     = 0xFE00..0xFFFF // 0,5     kb | 512w
}