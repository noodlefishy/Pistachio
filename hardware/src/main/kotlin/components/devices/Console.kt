package io.cuttlefish.components.devices

import io.cuttlefish.*
import io.cuttlefish.components.*
import kotlinx.coroutines.*

class Console : MemoryManagement {
    // jalr r0 r0 2 = print
    // print what is in r1
    override suspend fun read(address: Short): Short {
        delay(Clock.DEVICE_CONSOLE_READ_TIME)

        return when (address) {
            0xFF01.toShort() -> {
                withContext(Dispatchers.IO) { System.`in`.read() }.toShort()
            }

            0xFF02.toShort() -> {
                (withContext(Dispatchers.IO) { System.`in`.available() } > 0).toShort()
            }

            else -> {
                0
            }
        }
    }

    override suspend fun write(address: Short, value: Short) {
        delay(Clock.DEVICE_CONSOLE_WRITE_TIME)
        if (address == 0xFF00.toShort()) {
            print(value.toInt().toChar())
        }

    }
}


fun Boolean.toShort(): Short {
    return if (this) {
        1
    } else 0
}