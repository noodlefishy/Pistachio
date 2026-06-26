package io.cuttlefish.components.devices

import io.cuttlefish.*
import io.cuttlefish.components.*
import kotlinx.coroutines.*

class Console : MemoryManagement {
    override suspend fun read(address: Short): Short {
        delay(Clock.DEVICE_CONSOLE_READ_TIME)
        val char1 = (readlnOrNull()?.firstOrNull() ?: 0) as Char
        return char1.code.toShort()
    }

    override suspend fun write(address: Short, value: Short) {
        delay(Clock.DEVICE_CONSOLE_WRITE_TIME)

        TODO("Not yet implemented")
    }
}