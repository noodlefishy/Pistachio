package io.cuttlefish.components

import io.cuttlefish.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds


class Registers {
    private val maxRegisters = 8
    val registerData: Array<Short> = Array(maxRegisters) { 0 }
    var oldWrite: Pair<RegisterType, Short> = RegisterType.R0 to 20
    override fun toString(): String {
        val rNames = RegisterType.entries
        val m = mutableMapOf<String, Short>()
        registerData.forEachIndexed { index, sh -> m[rNames[index].name] = sh }
        return m.toString()
    }

    suspend fun read(register: RegisterType): Short {
        if (register == RegisterType.R0) return 0
        if (Clock.REGISTER_READ_TIME > 0L) delay(Clock.REGISTER_READ_TIME.milliseconds)
        return registerData[register.ordinal]
    }

    suspend fun write(register: RegisterType, value: Short) {
        if (register == RegisterType.R0) return
        if (Clock.REGISTER_WRITE_TIME > 0L) delay(Clock.REGISTER_WRITE_TIME.milliseconds)
        oldWrite = register to value
        registerData[register.ordinal] = value
    }

}