package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerAdd(instruction: Instruction.Add) {
    val number1 = registers.read(instruction.register2)
    val number2 = registers.read(instruction.register3)
    val result = alu.add(number1, number2)
    registers.write(instruction.register1, result)
}

fun signExtend7(value: Short): Short {
    val intValue = value.toInt() and 0x7F // Ensure we only have 7 bits
    return if ((intValue and 0x40) != 0) {
        (intValue or 0xFF80).toShort()
    } else {
        intValue.toShort()
    }
}

suspend fun Cpu.handlerAddImmediate(instruction: Instruction.Addi) {
    val number1 = registers.read(instruction.register2)
    val extendedImmediate = signExtend7(instruction.immediate)
    val result = alu.add(number1, extendedImmediate)
    registers.write(instruction.register1, result)
}

suspend fun Cpu.handlerNand(instruction: Instruction.Nand) {
    val number1 = registers.read(instruction.register2)
    val number2 = registers.read(instruction.register3)
    val result = alu.nand(number1, number2)
    registers.write(instruction.register1, result)
}

