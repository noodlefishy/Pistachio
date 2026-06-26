package io.cuttlefish.components

import kotlinx.coroutines.*
import kotlin.experimental.*

class Alu {
    suspend fun add(number1: Short, number2: Short): Short {
        delay(Clock.ALU_CALCULATION_TIME)
        return (number1 + number2).toShort()
    }

    suspend fun nand(number1: Short, number2: Short): Short {
        delay(Clock.ALU_CALCULATION_TIME)
        return (number1 and number2).inv()
    }

}