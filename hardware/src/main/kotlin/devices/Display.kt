package io.cuttlefish.devices


class Display : Device {
    override val deviceId: UShort = 2u
    val dimensions = 8 to 8
    override val memoryUsed: UIntRange = 0xFF03u..0xFF4Fu
    override suspend fun read(address: UShort): Short {
        TODO("Not yet implemented")
    }

    override suspend fun write(address: UShort, value: Short) {
        TODO("Not yet implemented")
    }
}