package io.cuttlefish.components

import kotlinx.coroutines.*
import kotlin.experimental.*
import kotlin.time.Duration.Companion.milliseconds

class Alu {
    suspend fun add(number1: Short, number2: Short): Short {
        if (Clock.ALU_CALCULATION_TIME > 0L) delay(Clock.ALU_CALCULATION_TIME.milliseconds)
        return (number1 + number2).toShort()
    }

    suspend fun nand(number1: Short, number2: Short): Short {
        if (Clock.ALU_CALCULATION_TIME > 0L) delay(Clock.ALU_CALCULATION_TIME.milliseconds)
        return (number1 and number2).inv()
    }

    suspend fun compare(number1: Short, number2: Short): Boolean {
        if (Clock.ALU_CALCULATION_TIME > 0L) delay(Clock.ALU_CALCULATION_TIME.milliseconds)
        return number1 == number2
    }

}