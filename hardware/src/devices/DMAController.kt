package io.cuttlefish.devices

import io.cuttlefish.components.MemoryBus

class DMAController(private val memoryBus: MemoryBus) : Device {
    override val name: String = "DMA Controller"
    override val deviceId: UShort = 6u
    override val memoryUsed: UIntRange = 0xFF30u..0xFF34u

    private var sourceOrValue: Short = 0
    private var destination: Short = 0
    private var length: Short = 0

    private val ram = memoryBus.ram
    override suspend fun read(address: UShort): Short {
        return when (address.toInt()) {
            0xFF30 -> sourceOrValue
            0xFF31 -> destination
            0xFF32 -> length
            0xFF34 -> 0 // Status: 0 = Idle
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        when (address.toInt()) {
            0xFF30 -> sourceOrValue = value
            0xFF31 -> destination = value
            0xFF32 -> length = value
            0xFF33 -> executeCommand(value.toInt() and 0xFFFF)
        }
    }

    private suspend fun executeCommand(command: Int) {
        val destIdx = destination.toInt() and 0xFFFF
        val len = length.toInt() and 0xFFFF

        // Hardware safety bounds check
        if (destIdx + len > ram.internals.size) return

        when (command) {
            1 -> {
                // COMMAND 1: MEM_COPY via Kotlin stdlib copyInto
                val srcIdx = sourceOrValue.toInt() and 0xFFFF
                if (srcIdx + len > ram.internals.size) return

                ram.internals.copyInto(
                    destination = ram.internals,
                    destinationOffset = destIdx,
                    startIndex = srcIdx,
                    endIndex = srcIdx + len
                )
            }

            2 -> {
                // COMMAND 2: MEM_FILL via Kotlin stdlib fill
                ram.internals.fill(sourceOrValue, destIdx, destIdx + len)
            }

            3 -> {
                val srcIdx = sourceOrValue.toInt() and 0xFFFF
                val port = destination.toUShort()
                if (srcIdx + len > ram.internals.size) return
                for (i in 0 until len) {
                    memoryBus.write(port, ram.internals[srcIdx + i])
                }
            }

            4 -> {
                val value = sourceOrValue
                val port = destination.toUShort()
                for (i in 0 until len) {
                    memoryBus.write(port, value)
                }
            }
        }
    }
}