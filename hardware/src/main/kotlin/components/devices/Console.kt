package io.cuttlefish.components.devices

import io.cuttlefish.*
import io.cuttlefish.components.*
import kotlinx.coroutines.*

class Console : MemoryManagement {
    // jalr r0 r0 2 = print
    // print what is in r1
    override suspend fun read(address: Short): Short {
        delay(Clock.DEVICE_CONSOLE_READ_TIME)
        return withContext(Dispatchers.IO) {
            return@withContext System.`in`.read()
        }.toShort()
    }

    override suspend fun write(address: Short, value: Short) {
        delay(Clock.DEVICE_CONSOLE_WRITE_TIME)

    }
}