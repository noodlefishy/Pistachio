package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerLui(instruction: Instruction.Lui) {
    instruction.register1.write((instruction.immediate.toInt() shl 6).toShort())
}