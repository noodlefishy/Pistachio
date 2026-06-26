package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerBeq(instruction: Instruction.Beq) {
    val number1 = instruction.register1.read()
    val number2 = instruction.register2.read()
    val extendedImmediate = signExtend7(instruction.immediate)
    val pc = registers.read(RegisterType.PC)

    if (alu.compare(number1, number2)) {
        RegisterType.PC.write((pc + extendedImmediate).toShort())
    }
}

suspend fun Cpu.handlerJalr(instruction: Instruction.Jalr) {
    // Read the destination address FIRST just in case reg1 and reg2 are the same register!
    val destination = instruction.register2.read()

    instruction.register1.write(RegisterType.PC.read())

    RegisterType.PC.write(destination)
}