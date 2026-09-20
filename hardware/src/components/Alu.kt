package io.cuttlefish.components

import kotlinx.coroutines.*
import kotlin.experimental.*
import kotlin.time.Duration.Companion.milliseconds

class Alu {
    suspend fun add(number1: Short, number2: Short): Short {
        delay(Clock.ALU_CALCULATION_TIME.milliseconds)
        return (number1 + number2).toShort()
    }

    suspend fun nand(number1: Short, number2: Short): Short {
        delay(Clock.ALU_CALCULATION_TIME.milliseconds)
        return (number1 and number2).inv()
    }

    suspend fun compare(number1: Short, number2: Short): Boolean {
        delay(Clock.ALU_CALCULATION_TIME.milliseconds)
        return number1 == number2
    }

}