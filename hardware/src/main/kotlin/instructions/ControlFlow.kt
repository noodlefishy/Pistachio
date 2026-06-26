package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerBeq(instruction: Instruction.Beq) {
    val number1 = instruction.register1.read()
    val number2 = instruction.register2.read()
    val extendedImmediate = signExtend7(instruction.immediate)

    if (alu.compare(number1, number2)) pc = (pc + 1 + extendedImmediate).toShort()
}

suspend fun Cpu.handlerJalr(instruction: Instruction.Jalr) {
    instruction.register1.write((pc + 1).toShort())
    pc = instruction.register2.read()

}