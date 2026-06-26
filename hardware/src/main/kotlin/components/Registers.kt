package io.cuttlefish.components

import io.cuttlefish.*
import kotlinx.coroutines.*


class Registers {
    private val maxRegisters = 8
    private val registerData: Array<Short> = Array(maxRegisters) { -1 }

    suspend fun read(register: RegisterType): Short {
        delay(Clock.REGISTER_READ_TIME)
        return registerData[register.ordinal]
    }

    suspend fun write(register: RegisterType, value: Short) {
        delay(Clock.REGISTER_WRITE_TIME)
        registerData[register.ordinal] = value
    }

}