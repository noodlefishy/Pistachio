package io.cuttlefish.devices

import io.cuttlefish.*
import io.cuttlefish.components.*
import kotlinx.coroutines.*

class Console : Device {
    // jalr r0 r0 2 = print
    // print what is in r1

    override val deviceId: UShort = 1u
    override val memoryUsed: UIntRange = 0xFF01u..0xFF02u

    override suspend fun read(address: UShort): Short {
        delay(Clock.DEVICE_CONSOLE_READ_TIME)

        return when (address) {
            0xFF01u.toUShort() -> {
                withContext(Dispatchers.IO) { System.`in`.read() }.toShort()
            }

            0xFF02u.toUShort() -> {
                (withContext(Dispatchers.IO) { System.`in`.available() } > 0).toShort()
            }

            else -> {
                0
            }
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        delay(Clock.DEVICE_CONSOLE_WRITE_TIME)
        if (address == 0xFF00u.toUShort()) {
            System.err.print(value.toInt().toChar())
        }

    }
}
