package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.devices.*

class MemoryBus(val ram: PhysicalMemory) : MemoryManagement {
    val devices: Array<Device> = arrayOf(Console(), Display())

    override suspend fun read(address: Short): Short {
        return when (val addressInt = address.toUShort().toInt()) {
            in MemoryMapRanges.vectorRange -> ram.read(address)
            in MemoryMapRanges.kernelRange -> ram.read(address)
            in MemoryMapRanges.userLandRange -> ram.read(address)
            in MemoryMapRanges.mmioRange -> {
                for (device in devices) {
                    if (addressInt in device.memoryUsed) {
                        return device.read(address)
                    }
                }
                throw IllegalAccessException("Device ${addressInt.toUShort()} not found")
            }

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
