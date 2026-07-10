package io.cuttlefish.components
import io.cuttlefish.*
import io.cuttlefish.components.devices.*

class MemoryBus(val ram: PhysicalMemory, val display: DisplayDevice) : MemoryManagement {


    override suspend fun read(address: Short): Short {
        return when (address.toUShort().toInt()) {
            in MemoryMapRanges.vectorRange -> ram.read(address)
            in MemoryMapRanges.kernelRange -> ram.read(address)
            in MemoryMapRanges.userLandRange -> ram.read(address)
            in MemoryMapRanges.mmioRange -> display.read(address)
            else -> error("Unknown addresses?")
        }
    }

    override suspend fun write(address: Short, value: Short) {
        when (address.toUShort().toInt()) {
            in MemoryMapRanges.vectorRange -> ram.write(address, value)
            in MemoryMapRanges.kernelRange -> ram.write(address, value)
            in MemoryMapRanges.userLandRange -> ram.write(address, value)
            in MemoryMapRanges.mmioRange -> Console().write(address, value)
            else -> error("Unknown addresses?")
        }
    }
}

object MemoryMapRanges { // 64 KB
    @JvmStatic internal val vectorRange: IntRange   by lazy { return@lazy 0x0000.rangeTo(other = 0x003F) } // 0,0625  kb | 64w
    @JvmStatic internal val kernelRange: IntRange   by lazy { return@lazy 0x0040.rangeTo(other = 0x2FFF) } // 11,9375 kb | 12 224w
    @JvmStatic internal val userLandRange: IntRange by lazy { return@lazy 0x3000.rangeTo(other = 0xFDFF) } // 51,5    kb | 52 736w
    @JvmStatic internal val mmioRange: IntRange     by lazy { return@lazy 0xFE00.rangeTo(other = 0xFFFF) } // 0,5     kb | 512w
}