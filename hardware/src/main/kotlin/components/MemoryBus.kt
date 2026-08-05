package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.devices.*

class MemoryBus(val ram: PhysicalMemory) : MemoryManagement {
    val addressMap = mutableMapOf<UShort, Device>()
    val attachedDevices: MutableSet<Device> = mutableSetOf()

    init {
        registerDevice(Console())
        registerDevice(Display())
    }

    override suspend fun read(address: UShort): Short {
        val device = addressMap[address]
        return device?.read(address) ?: ram.read(address)

    }

    override suspend fun write(address: UShort, value: Short) {
        val device = addressMap[address]
        device?.write(address, value) ?: ram.write(address, value)
    }


    private fun registerDevice(device: Device) {
        attachedDevices += device
        for (address in device.memoryUsed) {
            addressMap[address.toUShort()] = device
        }
    }
}
