package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.devices.*

class MemoryBus(val ram: PhysicalMemory) : MemoryManagement {
    val devices: Array<Device> = arrayOf(Console(), Display())

    override suspend fun read(address: UShort): Short {
        return when (address) {
            in MemoryMapRanges.vectorRange -> ram.read(address)
            in MemoryMapRanges.kernelRange -> ram.read(address)
            in MemoryMapRanges.userLandRange -> ram.read(address)
            in MemoryMapRanges.mmioRange -> {
                for (device in devices) {
                    if (address in device.memoryUsed) {
                        return device.read(address)
                    }
                }
                throw IllegalAccessException("Device $address not found")
            }

            else -> error("Unknown addresses?")
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        when (address) {
            in MemoryMapRanges.vectorRange -> ram.write(address, value)
            in MemoryMapRanges.kernelRange -> ram.write(address, value)
            in MemoryMapRanges.userLandRange -> ram.write(address, value)
            in MemoryMapRanges.mmioRange -> Console().write(address, value)
            else -> error("Unknown addresses?")
        }
    }
}
