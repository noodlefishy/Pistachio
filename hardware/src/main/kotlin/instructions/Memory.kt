package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerLit(instruction: Instruction.Lit) {
    val destination = instruction.destination
    val value = instruction.value
    registers.write(destination, value)
}