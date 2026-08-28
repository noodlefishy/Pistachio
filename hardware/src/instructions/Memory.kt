package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerLui(instruction: Instruction.Lui) {
    instruction.register1.write((instruction.immediate.toInt() shl 6).toShort())
}

suspend fun Cpu.handleLw(instruction: Instruction.Lw) {
    val number1 = instruction.register2.read()
    val extendedImmediate = signExtend7(instruction.immediate)
    val address = alu.add(number1, extendedImmediate)
    val result = mmu.read(address.toUShort())
    instruction.register1.write(result)
}


suspend fun Cpu.handleSw(instruction: Instruction.Sw) {
    val extendedImmediate = signExtend7(instruction.immediate)
    val number1 = instruction.register2.read()
    val address = alu.add(number1, extendedImmediate)
    mmu.write(address.toUShort(), instruction.register1.read())
}
